package app.homeward.consent

import app.homeward.audit.AuditService
import app.homeward.security.CurrentStaff
import app.homeward.web.ApiException
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

enum class Choice { CONNECT, SAFE, NOT_NOW, DECLINE }

data class DecisionIn(
    @field:NotNull(message = "Please choose an answer.") val choice: Choice?,
    // How they'll meet, e.g. "Call the Eastside desk and ask for Rosa". Only for CONNECT.
    @field:Size(max = 300, message = "Please shorten how they will meet.") val contactPlan: String? = null,
)

data class ConnectIn(
    @field:Size(max = 300, message = "Please shorten how they will meet.") val contactPlan: String? = null,
)

// What a lead fills in before releasing a child.
data class MinorReleaseIn(
    val guardianChecked: Boolean = false, // e.g. the child recognised them, details match
    @field:Size(max = 500, message = "Please shorten the note.") val note: String? = null, // how they checked
    @field:Size(max = 300, message = "Please shorten how they will meet.") val contactPlan: String? = null,
)

// The moment two people are actually connected. Every rule here is also enforced by the database
// guard (V4), so a bug here still can't release a child or get past a safety flag.
@Service
class ConsentService(
    private val requests: ConsentRepo,
    private val audit: AuditService,
    private val currentStaff: CurrentStaff,
) {
    // The found person answers, at their own desk or anywhere after their slip was checked.
    @Transactional
    fun decide(
        requestId: UUID,
        body: DecisionIn,
        verifiedPersonId: UUID?,
    ) {
        val staff = currentStaff.get()
        val r = requests.facts(requestId)
        if (r == null || (r.personShelterId != staff.shelterId && r.personId != verifiedPersonId)) throw notFound()
        when (body.choice!!) { // !!: validated as present
            Choice.NOT_NOW -> {} // nothing changes; they can answer later
            Choice.DECLINE -> change(r, "declined", null)
            Choice.SAFE -> {
                // "Safe" also tells the other side a child is alive and registered: a lead decides that too.
                if (r.childInvolved) throw ApiException(HttpStatus.CONFLICT, NEEDS_LEAD)
                change(r, "safe_only", null)
            }
            Choice.CONNECT -> {
                if (r.childInvolved) throw ApiException(HttpStatus.CONFLICT, NEEDS_LEAD)
                change(r, "connected", plan(body.contactPlan))
            }
        }
        audit.log(staff, "DECISION", requestId)
    }

    // "Tell them where I am" = consent given in advance: a volunteer at either desk can connect
    // without finding the person first. Never for a child, never past a flag, only while waiting.
    @Transactional
    fun connectFindable(
        requestId: UUID,
        body: ConnectIn,
    ) {
        val staff = currentStaff.get()
        val r = requests.facts(requestId)
        if (r == null || (staff.shelterId != r.searchShelterId && staff.shelterId != r.personShelterId)) throw notFound()
        if (r.personVisibility != "findable") throw ApiException(HttpStatus.CONFLICT, "This person asked to be asked first.")
        if (r.childInvolved) throw ApiException(HttpStatus.CONFLICT, NEEDS_LEAD)
        change(r, "connected", plan(body.contactPlan))
        audit.log(staff, "DECISION", requestId)
    }

    // Only a lead or coordinator (the /api/lead/** route), only for a request with a child on either side,
    // and only at the FOUND person's desk: where the child actually is (and can be seen), or, when the
    // child is the one searching, where the adult being asked says yes in person. Never the asking side alone.
    // The approver is whoever is logged in, never someone named in the request.
    @Transactional
    fun releaseMinor(
        requestId: UUID,
        body: MinorReleaseIn,
    ) {
        val lead = currentStaff.get()
        val r = requests.facts(requestId)
        if (r == null || lead.shelterId != r.personShelterId) throw notFound()
        if (!r.childInvolved) throw ApiException(HttpStatus.CONFLICT, "No child is involved: the person answers this themselves.")
        if (!body.guardianChecked) throw ApiException(HttpStatus.BAD_REQUEST, "Please confirm you checked the guardian first.")
        val note =
            body.note?.trim()?.ifEmpty { null }
                ?: throw ApiException(HttpStatus.BAD_REQUEST, "Please write a short note on how you checked.")
        change(r, "connected", plan(body.contactPlan), approvedBy = lead.staffId, guardianChecked = true, note = note)
        audit.log(lead, "MINOR_RELEASED", requestId)
    }

    private fun change(
        r: RequestFacts,
        state: String,
        contactPlan: String?,
        approvedBy: UUID? = null,
        guardianChecked: Boolean = false,
        note: String? = null,
    ) {
        // Checked here for a plain answer; the database guard refuses it anyway.
        if (requests.isFlagged(r.personId, r.searchId, r.searcherPersonId)) throw ApiException(HttpStatus.CONFLICT, CANT_SHARE)
        val changed =
            try {
                requests.decideIfPending(r.id, state, contactPlan, approvedBy, guardianChecked, note)
            } catch (e: DataIntegrityViolationException) {
                // The guard refused it (e.g. a flag added a moment ago): never shared.
                throw ApiException(HttpStatus.CONFLICT, CANT_SHARE)
            }
        if (!changed) throw ApiException(HttpStatus.CONFLICT, "This person has already answered. Please ask them again.")
    }

    private fun plan(contactPlan: String?) =
        contactPlan?.trim()?.ifEmpty { null }
            ?: throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Please write how they will meet, e.g. \"Call the Eastside desk and ask for Rosa\".",
            )

    private fun notFound() = ApiException(HttpStatus.NOT_FOUND, "Request not found.")

    companion object {
        const val NEEDS_LEAD = "A child is involved, so a lead has to approve this. Please ask a lead."
        const val CANT_SHARE = "This can't be shared right now. Please ask a lead."
    }
}
