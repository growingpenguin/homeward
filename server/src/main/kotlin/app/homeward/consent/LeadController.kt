package app.homeward.consent

import app.homeward.person.PersonService
import app.homeward.person.SlipResponse
import app.homeward.security.CurrentStaff
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    private val leads: LeadService,
    private val people: PersonService,
    private val currentStaff: CurrentStaff,
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

    // A flag takes the same row locks as "Connect"; if the two collide, PostgreSQL cancels one and the
    // flag is added once more, so a flag is never lost to a Connect pressed at the same moment.
    @PostMapping("/api/lead/flags")
    fun addFlag(
        @Valid @RequestBody body: FlagIn,
    ): ResponseEntity<Map<String, UUID>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "flagId" to retryOnce { leads.addFlag(body) },
            ),
        )

    @PostMapping("/api/lead/flags/{id}/clear")
    fun clearFlag(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        leads.clearFlag(id)
        return ResponseEntity.noContent().build()
    }

    // A lost slip: the old code stops working, a new code and PIN are printed (shown once).
    @PostMapping("/api/lead/persons/{id}/reissue-slip")
    fun reissueSlip(
        @PathVariable id: UUID,
    ): ResponseEntity<SlipResponse> = ResponseEntity.status(HttpStatus.CREATED).body(people.reissueSlip(id))
}
