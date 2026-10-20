package app.homeward.consent

import app.homeward.consent.dto.searcher.SearchAnswer
import app.homeward.consent.dto.searcher.SearcherStatus
import app.homeward.person.ReunionCodeService
import app.homeward.security.CurrentStaff
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class LookupIn(
    @field:NotBlank(message = "Please type the code from the slip.") @field:Size(max = 12) val code: String?,
    @field:NotBlank(message = "Please type the PIN from the slip.") @field:Size(max = 12) val pin: String?,
)

// The same reply for a wrong code, a locked code and a wrong PIN: found = false and one sentence.
data class LookupOut(
    val found: Boolean,
    val message: String?,
    val personName: String?, // the slip holder's own name, to greet them
    val searches: List<SearchAnswer>, // their own searches, each answer from VisibilityService
    val answersWaiting: Int, // requests from people looking for THEM, to answer now
)

// Two desks acting on the same people at the same moment can deadlock in the database; PostgreSQL
// then cancels one transaction (nothing of it is saved), so running it once more is safe.
fun <T> retryOnce(block: () -> T): T =
    try {
        block()
    } catch (e: PessimisticLockingFailureException) {
        block()
    }

@RestController
class ConsentController(
    private val visibility: VisibilityService,
    private val consent: ConsentService,
    private val requests: ConsentRepo,
    private val codes: ReunionCodeService,
    private val currentStaff: CurrentStaff,
) {
    // The searcher's answer at the desk where the search was made (or anywhere after their slip was checked).
    @GetMapping("/api/searches/{id}/status")
    fun status(
        @PathVariable id: UUID,
        request: HttpServletRequest,
    ): SearcherStatus = visibility.statusForSearcher(id, VerifiedPerson.from(request.getSession(false)))

    @PostMapping("/api/lookup")
    fun lookup(
        @Valid @RequestBody body: LookupIn,
        request: HttpServletRequest,
    ): LookupOut {
        // Every new check starts clean: the previous slip holder is forgotten, even if this one fails.
        request.getSession(false)?.removeAttribute(VerifiedPerson.SESSION_KEY)
        val personId =
            codes.lookup(body.code!!, body.pin!!, currentStaff.get()) // !!: validated as not blank
                ?: return LookupOut(false, "That code and PIN didn't match. Please check the slip and try again.", null, emptyList(), 0)
        request.session.setAttribute(VerifiedPerson.SESSION_KEY, VerifiedPerson(personId, Instant.now().plusSeconds(600)))
        val searches =
            requests.searchesBy(personId).map { (id, names) ->
                SearchAnswer(id, names, visibility.statusForSearcher(id, personId))
            }
        return LookupOut(true, null, requests.personName(personId), searches, requests.waiting(null, personId).size)
    }

    // Done with this person ("Give the screen back"): their slip no longer opens anything here.
    @PostMapping("/api/lookup/end")
    fun endLookup(request: HttpServletRequest): ResponseEntity<Void> {
        request.getSession(false)?.removeAttribute(VerifiedPerson.SESSION_KEY)
        return ResponseEntity.noContent().build()
    }

    // Requests waiting for an answer: people at this desk, plus the person whose slip was just checked.
    @GetMapping("/api/answers")
    fun answers(request: HttpServletRequest): List<WaitingRequest> =
        requests.waiting(currentStaff.get().shelterId, VerifiedPerson.from(request.getSession(false)))

    @PostMapping("/api/requests/{id}/decision")
    fun decide(
        @PathVariable id: UUID,
        @Valid @RequestBody body: DecisionIn,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        retryOnce { consent.decide(id, body, VerifiedPerson.from(request.getSession(false))) }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/api/requests/{id}/connect-findable")
    fun connectFindable(
        @PathVariable id: UUID,
        @Valid @RequestBody body: ConnectIn,
    ): ResponseEntity<Void> {
        retryOnce { consent.connectFindable(id, body) }
        return ResponseEntity.noContent().build()
    }
}
