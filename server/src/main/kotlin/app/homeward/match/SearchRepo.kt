package app.homeward.match

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

data class StoredSearch(
    val id: UUID,
    val shelterId: UUID,
    val searcherPersonId: UUID?,
    val soughtNames: List<String>,
    val ageMin: Int?,
    val ageMax: Int?,
)

// What find_candidates returns for one person: signals only, no names or places.
data class Signals(
    val personId: UUID,
    val nameSimilarity: Double,
    val wordsSoundAlike: Int,
    val nicknameMatch: Boolean,
    val ageClose: Boolean?, // null = no age on one side
    val minor: Boolean,
)

// Who a suggestion is about, read from the search_candidates view (names and age only).
data class CandidateName(
    val preferredName: String,
    val approxAge: Int?,
    val ageIsEstimate: Boolean,
    val minor: Boolean,
)

@Repository
class SearchRepo(
    private val jdbc: JdbcClient,
) {
    // true if new; a re-sent form (same id) stores nothing.
    fun insert(
        r: StartSearchRequest,
        shelterId: UUID,
        staffId: UUID,
    ): Boolean =
        jdbc
            .sql(
                """
                INSERT INTO searches (id, shelter_id, searcher_person_id, sought_names, sought_age_min, sought_age_max,
                                      relationship, last_known_place, entered_by)
                VALUES (:id, :shelterId, :searcher, :names, :ageMin, :ageMax, :relationship, :place, :staffId)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
            ).param("id", r.id)
            .param("shelterId", shelterId)
            .param("searcher", r.searcherPersonId)
            .param("names", r.soughtNames!!.map { it.trim() }.toTypedArray()) // !!: validated as not empty
            .param("ageMin", r.ageMin)
            .param("ageMax", r.ageMax)
            .param("relationship", r.relationship?.trim()?.ifEmpty { null })
            .param("place", r.lastKnownPlace?.trim()?.ifEmpty { null })
            .param("staffId", staffId)
            .update() == 1

    fun find(id: UUID): StoredSearch? =
        jdbc
            .sql(
                "SELECT id, shelter_id, searcher_person_id, sought_names, sought_age_min, sought_age_max FROM searches WHERE id = :id",
            ).param("id", id)
            .query { rs, _ ->
                StoredSearch(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("shelter_id", UUID::class.java),
                    rs.getObject("searcher_person_id", UUID::class.java),
                    rs.strings("sought_names"),
                    rs.getObject("sought_age_min") as Int?,
                    rs.getObject("sought_age_max") as Int?,
                )
            }.optional()
            .orElse(null)

    fun signals(
        name: String,
        ageMin: Int?,
        ageMax: Int?,
    ): List<Signals> =
        jdbc
            .sql("SELECT * FROM find_candidates(:name, :ageMin, :ageMax)")
            .param("name", name)
            .param("ageMin", ageMin)
            .param("ageMax", ageMax)
            .query { rs, _ ->
                Signals(
                    rs.getObject("person_id", UUID::class.java),
                    rs.getDouble("name_similarity"),
                    rs.getInt("words_sound_alike"),
                    rs.getBoolean("nickname_match"),
                    rs.getObject("age_close") as Boolean?,
                    rs.getBoolean("minor"),
                )
            }.list()

    fun names(ids: Collection<UUID>): Map<UUID, CandidateName> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .sql("SELECT id, preferred_name, approx_age, age_is_estimate, minor FROM search_candidates WHERE id = ANY(:ids)")
                .param("ids", ids.toTypedArray())
                .query { rs, _ ->
                    rs.getObject("id", UUID::class.java) to
                        CandidateName(
                            rs.getString("preferred_name"),
                            rs.getObject("approx_age") as Int?,
                            rs.getBoolean("age_is_estimate"),
                            rs.getBoolean("minor"),
                        )
                }.list()
                .toMap()
        }

    // Never suggests the same person twice for one search (UNIQUE); an earlier suggestion keeps its decision.
    fun suggest(
        searchId: UUID,
        personId: UUID,
        score: Double,
        reasons: List<String>,
    ) {
        jdbc
            .sql(
                """
                INSERT INTO match_candidates (search_id, person_id, score, reasons)
                VALUES (:searchId, :personId, :score, to_jsonb(CAST(:reasons AS text[])))
                ON CONFLICT (search_id, person_id) DO NOTHING
                """.trimIndent(),
            ).param("searchId", searchId)
            .param("personId", personId)
            .param("score", score)
            .param("reasons", reasons.toTypedArray())
            .update()
    }

    // The cards, read THROUGH the view: someone hidden or protected since they were suggested drops out.
    fun cards(searchId: UUID): List<MatchCard> =
        jdbc
            .sql(
                """
                SELECT c.id, c.preferred_name, c.approx_age, c.age_is_estimate, c.minor, mc.score,
                       ARRAY(SELECT jsonb_array_elements_text(mc.reasons)) AS reasons,
                       EXISTS (SELECT 1 FROM contact_requests cr WHERE cr.search_id = mc.search_id AND cr.person_id = c.id) AS requested
                FROM match_candidates mc
                JOIN search_candidates c ON c.id = mc.person_id
                WHERE mc.search_id = :searchId AND mc.decision <> 'rejected'
                ORDER BY mc.score DESC, c.preferred_name
                """.trimIndent(),
            ).param("searchId", searchId)
            .query { rs, _ ->
                MatchCard(
                    rs.getObject("id", UUID::class.java),
                    rs.getString("preferred_name"),
                    rs.getObject("approx_age") as Int?,
                    rs.getBoolean("age_is_estimate"),
                    rs.getBoolean("minor"),
                    rs.getDouble("score"),
                    rs.strings("reasons"),
                    rs.getBoolean("requested"),
                )
            }.list()

    fun isVisibleSuggestion(
        searchId: UUID,
        personId: UUID,
    ): Boolean =
        jdbc
            .sql(
                """
                SELECT EXISTS (SELECT 1 FROM match_candidates mc JOIN search_candidates c ON c.id = mc.person_id
                               WHERE mc.search_id = :searchId AND mc.person_id = :personId AND mc.decision <> 'rejected')
                """.trimIndent(),
            ).param("searchId", searchId)
            .param("personId", personId)
            .query(Boolean::class.java)
            .single()

    // A re-sent request (same search and person) returns the request that already exists.
    fun request(
        searchId: UUID,
        personId: UUID,
        staffId: UUID,
        message: String?,
    ): UUID {
        jdbc
            .sql(
                "UPDATE match_candidates SET decision = 'confirmed', reviewed_by = :staffId WHERE search_id = :searchId AND person_id = :personId",
            ).param("staffId", staffId)
            .param("searchId", searchId)
            .param("personId", personId)
            .update()
        if (message != null) {
            jdbc
                .sql("UPDATE searches SET message = :message WHERE id = :searchId")
                .param("message", message)
                .param("searchId", searchId)
                .update()
        }
        jdbc
            .sql(
                "INSERT INTO contact_requests (search_id, person_id) VALUES (:searchId, :personId) ON CONFLICT (search_id, person_id) DO NOTHING",
            ).param("searchId", searchId)
            .param("personId", personId)
            .update()
        return jdbc
            .sql("SELECT id FROM contact_requests WHERE search_id = :searchId AND person_id = :personId")
            .param("searchId", searchId)
            .param("personId", personId)
            .query(UUID::class.java)
            .single()
    }

    fun personShelter(personId: UUID): UUID? =
        jdbc
            .sql("SELECT shelter_id FROM persons WHERE id = :id")
            .param("id", personId)
            .query(UUID::class.java)
            .optional()
            .orElse(null)
}

private fun ResultSet.strings(column: String): List<String> = (getArray(column)?.array as Array<*>?)?.map { it as String } ?: emptyList()
