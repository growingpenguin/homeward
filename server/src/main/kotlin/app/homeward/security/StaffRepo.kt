package app.homeward.security

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

data class StaffLogin(
    val staffId: UUID,
    val shelterId: UUID,
    val displayName: String,
    val role: String,
    val pinHash: String,
)

data class ShelterOption(
    val id: UUID,
    val name: String,
)

@Repository
class StaffRepo(
    private val jdbc: JdbcClient,
) {
    // Names are matched the way a tired volunteer types them: any case, extra spaces trimmed.
    fun findForLogin(
        shelterId: UUID,
        name: String,
    ): StaffLogin? =
        jdbc
            .sql(
                """
                SELECT id, shelter_id, display_name, role, pin_hash FROM staff
                WHERE shelter_id = :shelterId AND lower(btrim(display_name)) = lower(btrim(:name))
                """.trimIndent(),
            ).param("shelterId", shelterId)
            .param("name", name)
            .query { rs, _ ->
                StaffLogin(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("shelter_id", UUID::class.java),
                    rs.getString("display_name"),
                    rs.getString("role"),
                    rs.getString("pin_hash"),
                )
            }.optional()
            .orElse(null)

    fun pinHash(staffId: UUID): String? =
        jdbc
            .sql("SELECT pin_hash FROM staff WHERE id = :id")
            .param("id", staffId)
            .query(String::class.java)
            .optional()
            .orElse(null)

    // Public list for the login dropdown: ids and names only, nothing about people.
    fun shelters(): List<ShelterOption> =
        jdbc
            .sql("SELECT id, name FROM shelters ORDER BY name")
            .query { rs, _ -> ShelterOption(rs.getObject("id", UUID::class.java), rs.getString("name")) }
            .list()
}
