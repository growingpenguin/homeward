package app.homeward.consent

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

// One contact request with what the consent rules need to know about both sides.
data class RequestFacts(
    val id: UUID,
    val searchId: UUID,
    val personId: UUID,
    val state: String, // pending | connected | safe_only | declined | blocked
    val contactPlan: String?,
    val personShelterId: UUID,
    val personVisibility: String,
    val searchShelterId: UUID,
    val searcherPersonId: UUID?,
    val childInvolved: Boolean, // the found person or the searcher counts as a child
)

// What a volunteer reads out to the found person (staff only; never shown to the searcher).
data class WaitingRequest(
    val requestId: UUID,
    val personId: UUID,
    val personName: String,
    val searcherName: String?,
    val relationship: String?,
    val message: String?,
    val childInvolved: Boolean,
    val personVisibility: String,
)

data class ActiveFlag(
    val flagId: UUID,
    val kind: String,
    val about: String, // the person's name, or "a search for …"
    val note: String?,
)

data class ChildAtShelter(
    val personId: UUID,
    val preferredName: String,
    val approxAge: Int?,
)

private const val FACTS = """
    SELECT cr.id, cr.search_id, cr.person_id, cr.state, cr.contact_plan,
           p.shelter_id AS person_shelter, p.visibility, s.shelter_id AS search_shelter, s.searcher_person_id,
           treated_as_minor(p) OR (sp.id IS NOT NULL AND treated_as_minor(sp)) AS child_involved
    FROM contact_requests cr
    JOIN persons p ON p.id = cr.person_id
    JOIN searches s ON s.id = cr.search_id
    LEFT JOIN persons sp ON sp.id = s.searcher_person_id
"""

@Repository
class ConsentRepo(
    private val jdbc: JdbcClient,
) {
    fun facts(requestId: UUID): RequestFacts? =
        jdbc
            .sql("$FACTS WHERE cr.id = :id")
            .param("id", requestId)
            .query { rs, _ ->
                RequestFacts(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("search_id", UUID::class.java),
                    rs.getObject("person_id", UUID::class.java),
                    rs.getString("state"),
                    rs.getString("contact_plan"),
                    rs.getObject("person_shelter", UUID::class.java),
                    rs.getString("visibility"),
                    rs.getObject("search_shelter", UUID::class.java),
                    rs.getObject("searcher_person_id", UUID::class.java),
                    rs.getBoolean("child_involved"),
                )
            }.optional()
            .orElse(null)

    // If several people were asked for one search: "connected" beats "safe only" beats everything else.
    fun bestFor(searchId: UUID): RequestFacts? =
        jdbc
            .sql(
                """
                $FACTS WHERE cr.search_id = :searchId
                ORDER BY CASE cr.state WHEN 'connected' THEN 0 WHEN 'safe_only' THEN 1 ELSE 2 END, cr.created_at
                LIMIT 1
                """.trimIndent(),
            ).param("searchId", searchId)
            .query { rs, _ ->
                RequestFacts(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("search_id", UUID::class.java),
                    rs.getObject("person_id", UUID::class.java),
                    rs.getString("state"),
                    rs.getString("contact_plan"),
                    rs.getObject("person_shelter", UUID::class.java),
                    rs.getString("visibility"),
                    rs.getObject("search_shelter", UUID::class.java),
                    rs.getObject("searcher_person_id", UUID::class.java),
                    rs.getBoolean("child_involved"),
                )
            }.optional()
            .orElse(null)

    // The same rule as the database guard: a flag on the person, on the search, or on the searcher.
    fun isFlagged(
        personId: UUID,
        searchId: UUID,
        searcherPersonId: UUID?,
    ): Boolean =
        jdbc
            .sql(
                """
                SELECT EXISTS (SELECT 1 FROM safety_flags f WHERE f.cleared_at IS NULL
                               AND (f.person_id = :personId OR f.search_id = :searchId OR f.person_id = :searcher))
                """.trimIndent(),
            ).param("personId", personId)
            .param("searchId", searchId)
            .param("searcher", searcherPersonId)
            .query(Boolean::class.java)
            .single()

    // Requests waiting for an answer from people at this shelter, or from one person (checked by their slip).
    fun waiting(
        shelterId: UUID?,
        personId: UUID?,
    ): List<WaitingRequest> =
        jdbc
            .sql(
                """
                SELECT cr.id, p.id AS person_id, p.preferred_name, sp.preferred_name AS searcher_name, s.relationship, s.message,
                       treated_as_minor(p) OR (sp.id IS NOT NULL AND treated_as_minor(sp)) AS child_involved, p.visibility
                FROM contact_requests cr
                JOIN persons p ON p.id = cr.person_id
                JOIN searches s ON s.id = cr.search_id
                LEFT JOIN persons sp ON sp.id = s.searcher_person_id
                WHERE cr.state = 'pending' AND (p.shelter_id = :shelterId OR p.id = :personId)
                ORDER BY cr.created_at
                """.trimIndent(),
            ).param("shelterId", shelterId)
            .param("personId", personId)
            .query { rs, _ ->
                WaitingRequest(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("person_id", UUID::class.java),
                    rs.getString("preferred_name"),
                    rs.getString("searcher_name"),
                    rs.getString("relationship"),
                    rs.getString("message"),
                    rs.getBoolean("child_involved"),
                    rs.getString("visibility"),
                )
            }.list()

    // Every decision only applies while the request is still waiting (the check is inside the UPDATE),
    // so an answer given a second earlier is never overwritten. The database guard checks flags and children.
    fun decideIfPending(
        requestId: UUID,
        state: String,
        contactPlan: String?,
        approvedBy: UUID?,
        guardianChecked: Boolean,
        note: String?,
    ): Boolean =
        jdbc
            .sql(
                """
                UPDATE contact_requests
                SET state = :state, contact_plan = :plan, approved_by = :approvedBy,
                    guardian_checked = :guardian, note = :note, decided_at = now()
                WHERE id = :id AND state = 'pending'
                """.trimIndent(),
            ).param("id", requestId)
            .param("state", state)
            .param("plan", contactPlan)
            .param("approvedBy", approvedBy)
            .param("guardian", guardianChecked)
            .param("note", note)
            .update() == 1

    fun searchesBy(personId: UUID): List<Pair<UUID, List<String>>> =
        jdbc
            .sql("SELECT id, sought_names FROM searches WHERE searcher_person_id = :p ORDER BY created_at")
            .param("p", personId)
            .query { rs, _ ->
                rs.getObject("id", UUID::class.java) to
                    (rs.getArray("sought_names").array as Array<*>).map { it as String }
            }.list()

    fun personName(personId: UUID): String? =
        jdbc
            .sql("SELECT preferred_name FROM persons WHERE id = :id")
            .param("id", personId)
            .query(String::class.java)
            .optional()
            .orElse(null)

    fun searchShelterAndSearcher(searchId: UUID): Pair<UUID, UUID?>? =
        jdbc
            .sql("SELECT shelter_id, searcher_person_id FROM searches WHERE id = :id")
            .param("id", searchId)
            .query { rs, _ -> rs.getObject("shelter_id", UUID::class.java) to rs.getObject("searcher_person_id", UUID::class.java) }
            .optional()
            .orElse(null)

    // ---------- lead page ----------
    fun childrenWaiting(shelterId: UUID): List<WaitingRequest> =
        waiting(shelterId, null).filter { it.childInvolved } +
            jdbc
                .sql(
                    """
                    SELECT cr.id, p.id AS person_id, p.preferred_name, sp.preferred_name AS searcher_name, s.relationship, s.message, p.visibility
                    FROM contact_requests cr
                    JOIN persons p ON p.id = cr.person_id
                    JOIN searches s ON s.id = cr.search_id
                    JOIN persons sp ON sp.id = s.searcher_person_id
                    WHERE cr.state = 'pending' AND s.shelter_id = :shelterId AND p.shelter_id <> :shelterId AND sp.id IS NOT NULL AND treated_as_minor(sp)
                    """.trimIndent(),
                ).param("shelterId", shelterId)
                .query { rs, _ ->
                    WaitingRequest(
                        rs.getObject("id", UUID::class.java),
                        rs.getObject("person_id", UUID::class.java),
                        rs.getString("preferred_name"),
                        rs.getString("searcher_name"),
                        rs.getString("relationship"),
                        rs.getString("message"),
                        true,
                        rs.getString("visibility"),
                    )
                }.list()

    fun unaccompaniedChildren(shelterId: UUID): List<ChildAtShelter> =
        jdbc
            .sql(
                """
                SELECT id, preferred_name, approx_age FROM persons p
                WHERE shelter_id = :shelterId AND unaccompanied AND treated_as_minor(p) AND status = 'present'
                ORDER BY created_at
                """.trimIndent(),
            ).param("shelterId", shelterId)
            .query { rs, _ ->
                ChildAtShelter(rs.getObject("id", UUID::class.java), rs.getString("preferred_name"), rs.getObject("approx_age") as Int?)
            }.list()

    fun activeFlags(shelterId: UUID): List<ActiveFlag> =
        jdbc
            .sql(
                """
                SELECT f.id, f.kind, f.note,
                       coalesce(p.preferred_name, 'a search for ' || array_to_string(s.sought_names, ', ')) AS about
                FROM safety_flags f
                LEFT JOIN persons p ON p.id = f.person_id
                LEFT JOIN searches s ON s.id = f.search_id
                WHERE f.cleared_at IS NULL AND (p.shelter_id = :shelterId OR s.shelter_id = :shelterId)
                ORDER BY f.created_at
                """.trimIndent(),
            ).param("shelterId", shelterId)
            .query { rs, _ ->
                ActiveFlag(rs.getObject("id", UUID::class.java), rs.getString("kind"), rs.getString("about"), rs.getString("note"))
            }.list()

    fun addFlag(
        personId: UUID?,
        searchId: UUID?,
        kind: String,
        note: String?,
        staffId: UUID,
    ): UUID =
        jdbc
            .sql(
                """
                INSERT INTO safety_flags (person_id, search_id, kind, note, created_by)
                VALUES (:personId, :searchId, :kind, :note, :staffId) RETURNING id
                """.trimIndent(),
            ).param("personId", personId)
            .param("searchId", searchId)
            .param("kind", kind)
            .param("note", note)
            .param("staffId", staffId)
            .query(UUID::class.java)
            .single()

    // Only a flag about someone (or a search) at this shelter, and only once.
    fun clearFlag(
        flagId: UUID,
        shelterId: UUID,
        staffId: UUID,
    ): Boolean =
        jdbc
            .sql(
                """
                UPDATE safety_flags f SET cleared_at = now(), cleared_by = :staffId
                WHERE f.id = :id AND f.cleared_at IS NULL
                  AND (EXISTS (SELECT 1 FROM persons p WHERE p.id = f.person_id AND p.shelter_id = :shelterId)
                       OR EXISTS (SELECT 1 FROM searches s WHERE s.id = f.search_id AND s.shelter_id = :shelterId))
                """.trimIndent(),
            ).param("id", flagId)
            .param("shelterId", shelterId)
            .param("staffId", staffId)
            .update() == 1
}
