package app.homeward.match

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

// "Someone is looking for family". The person searching is registered first with POST /api/persons
// (a name is enough, same rules as anyone else), so they get a slip and can check back at any desk.
data class StartSearchRequest(
    @field:NotNull(message = "Something went wrong with this form. Please start it again.")
    val id: UUID?, // made by the browser; a re-send uses the same id
    @field:NotNull(message = "Please register the person who is looking first.")
    val searcherPersonId: UUID?,
    @field:NotEmpty(message = "Please write the name of the person they are looking for.")
    @field:Size(max = 5, message = "Please keep it to 5 names.")
    val soughtNames: List<
        @NotBlank(message = "Please write the name of the person they are looking for.")
        @Size(max = 100, message = "Please use a shorter name.")
        String,
    >?,
    @field:Min(0, message = "Please check the age.") @field:Max(120, message = "Please check the age.")
    val ageMin: Int? = null,
    @field:Min(0, message = "Please check the age.") @field:Max(120, message = "Please check the age.")
    val ageMax: Int? = null,
    @field:Size(max = 100, message = "Please shorten the relationship.") val relationship: String? = null,
    @field:Size(max = 200, message = "Please shorten the last known place.") val lastKnownPlace: String? = null,
)

// One suggested match, as the volunteer sees it. Deliberately NO shelter, home area, phone,
// description or paper fields: matching never tells anyone where a person is.
data class MatchCard(
    val personId: UUID,
    val preferredName: String,
    val approxAge: Int?,
    val ageIsEstimate: Boolean,
    val child: Boolean, // a lead decides before anything is shared
    val score: Double,
    val reasons: List<String>, // plain words, e.g. "name sounds like Maria Garcia"
    val requested: Boolean, // a contact request was already sent for this person
)

data class SearchResult(
    val searchId: UUID,
    val searcherPersonId: UUID?,
    val soughtNames: List<String>,
    val matches: List<MatchCard>,
)

data class ContactRequestIn(
    @field:NotNull(message = "Please choose who to send the request to.")
    val personId: UUID?,
    // Read out to the person before they decide, so it must not say where the searcher is.
    @field:Size(max = 500, message = "Please shorten the message.") val message: String? = null,
)

data class ContactRequestCreated(
    val requestId: UUID,
)
