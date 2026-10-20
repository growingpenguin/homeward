package app.homeward.consent

import app.homeward.audit.AuditService
import app.homeward.security.CurrentStaff
import app.homeward.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Flags, for the lead page. A flag is about one person or one search at the lead's own shelter,
// and blocks anything already shared about them at once (V4 trigger).
@Service
class LeadService(
    private val requests: ConsentRepo,
    private val audit: AuditService,
    private val currentStaff: CurrentStaff,
    private val jdbc: JdbcClient,
) {
    @Transactional
    fun addFlag(body: FlagIn): UUID {
        val lead = currentStaff.get()
        if ((body.personId == null) == (body.searchId == null)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "A flag is about one person or one search.")
        }
        val shelter =
            jdbc
                .sql("SELECT shelter_id FROM persons WHERE id = :p UNION ALL SELECT shelter_id FROM searches WHERE id = :s")
                .param("p", body.personId)
                .param("s", body.searchId)
                .query(UUID::class.java)
                .optional()
                .orElse(null)
        if (shelter != lead.shelterId) throw ApiException(HttpStatus.NOT_FOUND, "Not found at this shelter.")
        val note = body.note?.trim()?.ifEmpty { null }
        val id = requests.addFlag(body.personId, body.searchId, body.kind!!, note, lead.staffId) // !!: validated
        audit.log(lead, "FLAG_ADDED", id)
        return id
    }

    @Transactional
    fun clearFlag(flagId: UUID) {
        val lead = currentStaff.get()
        if (!requests.clearFlag(flagId, lead.shelterId, lead.staffId)) throw ApiException(HttpStatus.NOT_FOUND, "Flag not found.")
        audit.log(lead, "FLAG_CLEARED", flagId)
    }
}
