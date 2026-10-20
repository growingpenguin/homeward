package app.homeward.person

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class StoredPerson(
    val shelterId: UUID,
    val view: PersonView,
)

@Repository
class PersonRepo(
    private val jdbc: JdbcClient,
) {
    // The browser makes the id, so a re-sent form (slow Wi-Fi, double click) inserts nothing.
    fun insert(
        r: RegisterPersonRequest,
        shelterId: UUID,
        staffId: UUID,
    ): Boolean =
        jdbc
            .sql(
                """
                INSERT INTO persons (id, shelter_id, preferred_name, other_names, approx_age, age_is_estimate, is_minor,
                                     unaccompanied, home_area, description, phone, visibility, source, paper_ref,
                                     recorded_at, entered_by)
                VALUES (:id, :shelterId, :name, :otherNames, :age, :estimate, :isMinor, :unaccompanied, :homeArea,
                        :description, :phone, :visibility, :source, :paperRef, coalesce(:recordedAt, now()), :staffId)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
            ).param("id", r.id)
            .param("shelterId", shelterId)
            .param("name", r.preferredName)
            .param(
                "otherNames",
                cleanNames(r.otherNames),
            ).param("age", r.approxAge)
            .param("estimate", r.ageIsEstimate)
            .param("isMinor", r.isMinor)
            .param("unaccompanied", r.unaccompanied)
            .param("homeArea", r.homeArea.blankToNull())
            .param("description", r.description.blankToNull())
            .param("phone", r.phone.blankToNull())
            .param("visibility", r.visibility)
            .param("source", r.source)
            .param("paperRef", r.paperRef.blankToNull())
            .param("recordedAt", r.recordedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) })
            .param("staffId", staffId)
            .update() == 1

    // updated_by comes from the login on EVERY edit, so the history trigger credits the right person.
    fun update(
        id: UUID,
        e: EditPersonRequest,
        staffId: UUID,
    ): Int =
        jdbc
            .sql(
                """
                UPDATE persons SET preferred_name = :name, other_names = :otherNames, approx_age = :age,
                       age_is_estimate = :estimate, is_minor = :isMinor, unaccompanied = :unaccompanied,
                       home_area = :homeArea, description = :description, phone = :phone,
                       visibility = :visibility, updated_by = :staffId
                WHERE id = :id
                """.trimIndent(),
            ).param("id", id)
            .param("name", e.preferredName)
            .param(
                "otherNames",
                cleanNames(e.otherNames),
            ).param("age", e.approxAge)
            .param("estimate", e.ageIsEstimate)
            .param("isMinor", e.isMinor)
            .param("unaccompanied", e.unaccompanied!!) // !!: @NotNull on edit
            .param("homeArea", e.homeArea.blankToNull())
            .param("description", e.description.blankToNull())
            .param("phone", e.phone.blankToNull())
            .param("visibility", e.visibility!!) // !!: @NotNull on edit
            .param("staffId", staffId)
            .update()

    fun find(id: UUID): StoredPerson? =
        jdbc
            .sql(
                """
                SELECT p.*, treated_as_minor(p) AS minor,
                       EXISTS (SELECT 1 FROM reunion_codes c WHERE c.person_id = p.id AND c.voided_at IS NULL) AS has_slip
                FROM persons p WHERE p.id = :id
                """.trimIndent(),
            ).param("id", id)
            .query { rs, _ -> StoredPerson(rs.getObject("shelter_id", UUID::class.java), rs.toView()) }
            .optional()
            .orElse(null)

    private fun ResultSet.toView() =
        PersonView(
            id = getObject("id", UUID::class.java),
            preferredName = getString("preferred_name"),
            otherNames = (getArray("other_names").array as Array<*>).map { it.toString() },
            approxAge = getObject("approx_age") as Int?,
            ageIsEstimate = getBoolean("age_is_estimate"),
            isMinor = getObject("is_minor") as Boolean?,
            treatedAsMinor = getBoolean("minor"),
            unaccompanied = getBoolean("unaccompanied"),
            homeArea = getString("home_area"),
            description = getString("description"),
            phone = getString("phone"),
            visibility = getString("visibility"),
            source = getString("source"),
            paperRef = getString("paper_ref"),
            recordedAt = getObject("recorded_at", OffsetDateTime::class.java).toInstant(),
            hasSlip = getBoolean("has_slip"),
        )
}

private fun String?.blankToNull() = this?.trim()?.takeIf { it.isNotEmpty() }

// Empty entries and missing values are dropped; validation already capped the list and each name's length.
private fun cleanNames(names: List<String?>) = names.mapNotNull { it.blankToNull() }.toTypedArray()
