package app.homeward.consent

import app.homeward.audit.AuditService
import app.homeward.consent.dto.searcher.SearcherStatus
import app.homeward.security.CurrentStaff
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.Serializable
import java.time.Instant
import java.util.UUID

// Set in the session for 10 minutes after someone's own slip (code + PIN) was checked at this desk,
// so their slip works at ANY desk: they may see their own searches' answers and their own requests.
data class VerifiedPerson(
    val personId: UUID,
    val until: Instant,
) : Serializable {
    companion object {
        const val SESSION_KEY = "homeward.verifiedPerson"

        fun from(session: HttpSession?): UUID? =
            (session?.getAttribute(SESSION_KEY) as? VerifiedPerson)?.takeIf { it.until.isAfter(Instant.now()) }?.personId
    }
}

// The single place that decides what a searcher may see. Controllers never filter location fields
// themselves; they call this.
@Service
class VisibilityService(
    private val requests: ConsentRepo,
    private val audit: AuditService,
    private val currentStaff: CurrentStaff,
) {
    @Transactional
    fun statusForSearcher(
        searchId: UUID,
        verifiedPersonId: UUID?,
    ): SearcherStatus {
        val staff = currentStaff.get()
        val (shelterId, searcherId) = requests.searchShelterAndSearcher(searchId) ?: return SearcherStatus.Active
        // Allowed at the desk where the search was made, or anywhere with the searcher's own slip.
        val allowed = shelterId == staff.shelterId || (verifiedPersonId != null && searcherId == verifiedPersonId)
        if (!allowed) return SearcherStatus.Active // same reply as everything else

        val request = requests.bestFor(searchId) ?: return SearcherStatus.Active // no match looks like everything else

        // Safety flags beat everything, even a "Connect" given earlier:
        // a flag on the person, on this search, or on the searcher (same rule as the database guard).
        if (requests.isFlagged(request.personId, searchId, request.searcherPersonId)) return SearcherStatus.Active

        // Every state is listed: a new state later forces a decision about what the searcher sees.
        return when (request.state) {
            "connected" -> {
                audit.log(staff, "REVEALED_CONTACT", request.id)
                SearcherStatus.Connected(checkNotNull(request.contactPlan) { "Connected request ${request.id} has no contact plan" })
            }
            "safe_only" -> {
                audit.log(staff, "REVEALED_SAFE", request.id)
                SearcherStatus.Safe
            }
            "pending", "declined", "blocked" -> SearcherStatus.Active // hidden states collapse into one reply
            else -> error("Unknown request state ${request.state}")
        }
    }
}
