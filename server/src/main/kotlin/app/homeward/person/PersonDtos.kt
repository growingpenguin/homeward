package app.homeward.person

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// What the Register form sends. Only a name (or nickname) is required; "unknown" is always allowed.
// Deliberately NO fields for: legal ID, date of birth, SSN, immigration status, photo. Unknown JSON
// fields are refused (application.yml), so they can't sneak in either.
// The shelter and the volunteer come from the login, never from here.
data class RegisterPersonRequest(
    @field:NotNull(message = "Something went wrong with this form. Please start it again.")
    val id: UUID?, // made by the browser; a re-send uses the same id
    @field:NotBlank(message = "Please write a name or nickname.")
    @field:Size(max = 100, message = "Please use a shorter name.")
    val preferredName: String?,
    val otherNames: List<String> = emptyList(),
    @field:Min(0, message = "Please check the age.") @field:Max(120, message = "Please check the age.")
    val approxAge: Int? = null, // null = unknown (0 is a real age: a baby)
    val ageIsEstimate: Boolean = true,
    @JsonProperty("isMinor")
    val isMinor: Boolean? = null, // null = not sure
    val unaccompanied: Boolean = false,
    @field:Size(max = 200) val homeArea: String? = null,
    @field:Size(max = 500) val description: String? = null,
    @field:Size(max = 40) val phone: String? = null,
    @field:Pattern(regexp = "findable|ask|hidden", message = "Please choose one of the three options.")
    val visibility: String = "ask", // safe default, also the database's
    @field:Pattern(regexp = "app|paper") val source: String = "app",
    @field:Size(max = 100) val paperRef: String? = null, // "Sheet 4, line 12"
    val recordedAt: Instant? = null, // the date written on the paper
    @field:Pattern(
        regexp = "^HW-[2-9A-HJ-NP-Z]{4}$",
        message = "That doesn't look like a slip code. It looks like HW- and 4 letters or numbers.",
    )
    val slipCode: String? = null, // a pre-printed slip to attach
)

// Editing a saved person (the Edit button). Same rules; where they came from can't change.
data class EditPersonRequest(
    @field:NotBlank(message = "Please write a name or nickname.")
    @field:Size(max = 100, message = "Please use a shorter name.")
    val preferredName: String?,
    val otherNames: List<String> = emptyList(),
    @field:Min(0, message = "Please check the age.") @field:Max(120, message = "Please check the age.")
    val approxAge: Int? = null,
    val ageIsEstimate: Boolean = true,
    @JsonProperty("isMinor")
    val isMinor: Boolean? = null,
    val unaccompanied: Boolean = false,
    @field:Size(max = 200) val homeArea: String? = null,
    @field:Size(max = 500) val description: String? = null,
    @field:Size(max = 40) val phone: String? = null,
    @field:Pattern(regexp = "findable|ask|hidden", message = "Please choose one of the three options.")
    val visibility: String = "ask",
)

// Staff-only view of a person at their own shelter (never shown to a searcher).
data class PersonView(
    val id: UUID,
    val preferredName: String,
    val otherNames: List<String>,
    val approxAge: Int?,
    val ageIsEstimate: Boolean,
    @JsonProperty("isMinor")
    val isMinor: Boolean?,
    val treatedAsMinor: Boolean,
    val unaccompanied: Boolean,
    val homeArea: String?,
    val description: String?,
    val phone: String?,
    val visibility: String,
    val source: String,
    val paperRef: String?,
    val recordedAt: Instant,
    val hasSlip: Boolean,
)

// Printed on the slip. The PIN is shown once here and never again (only its BCrypt hash is stored).
data class SlipResponse(
    val code: String,
    val pin: String?, // null when a pre-printed slip was attached (its PIN is already on the paper)
    val shelterName: String,
    val deskPhone: String?,
)
