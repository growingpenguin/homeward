package app.homeward.consent

import app.homeward.audit.AuditService
import app.homeward.person.PersonService
import app.homeward.person.SlipResponse
import app.homeward.security.CurrentStaff
import app.homeward.web.ApiException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class LeadOverview(
    val childrenWaiting: List<WaitingRequest>, // a child on either side: only a lead can connect
    val unaccompaniedChildren: List<ChildAtShelter>,
    val flags: List<ActiveFlag>,
)

data class FlagIn(
    val personId: UUID? = null,
    val searchId: UUID? = null,
    @field:NotNull(message = "Please choose what kind of flag this is.")
    @field:Pattern(regexp = "do_not_disclose|suspected_abuser|custody_hold", message = "Please choose what kind of flag this is.")
    val kind: String?,
    @field:Size(max = 300, message = "Please shorten the note.") val note: String? = null,
)

// Lead page: everything under /api/lead/** needs a lead or coordinator (SecurityConfig).
@RestController
class LeadController(
    private val requests: ConsentRepo,
    private val consent: ConsentService,
    private val people: PersonService,
    private val audit: AuditService,
    private val currentStaff: CurrentStaff,
    private val jdbc: JdbcClient,
) {
    @GetMapping("/api/lead/overview")
    fun overview(): LeadOverview {
        val shelter = currentStaff.get().shelterId
        return LeadOverview(requests.childrenWaiting(shelter), requests.unaccompaniedChildren(shelter), requests.activeFlags(shelter))
    }

    @PostMapping("/api/lead/requests/{id}/release-minor")
    fun releaseMinor(
        @PathVariable id: UUID,
        @Valid @RequestBody body: MinorReleaseIn,
    ): ResponseEntity<Void> {
        retryOnce { consent.releaseMinor(id, body) }
        return ResponseEntity.noContent().build()
    }

    // A flag is about one person or one search at this shelter. It blocks anything already shared, at once (V4).
    @PostMapping("/api/lead/flags")
    @Transactional
    fun addFlag(
        @Valid @RequestBody body: FlagIn,
    ): ResponseEntity<Map<String, UUID>> {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("flagId" to id))
    }

    @PostMapping("/api/lead/flags/{id}/clear")
    @Transactional
    fun clearFlag(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        val lead = currentStaff.get()
        if (!requests.clearFlag(id, lead.shelterId, lead.staffId)) throw ApiException(HttpStatus.NOT_FOUND, "Flag not found.")
        audit.log(lead, "FLAG_CLEARED", id)
        return ResponseEntity.noContent().build()
    }

    // A lost slip: the old code stops working, a new code and PIN are printed (shown once).
    @PostMapping("/api/lead/persons/{id}/reissue-slip")
    fun reissueSlip(
        @PathVariable id: UUID,
    ): ResponseEntity<SlipResponse> = ResponseEntity.status(HttpStatus.CREATED).body(people.reissueSlip(id))
}
