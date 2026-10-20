package app.homeward.person

import app.homeward.security.CurrentStaff
import app.homeward.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class Registered(
    val person: PersonView,
    val created: Boolean,
)

@Service
class PersonService(
    private val people: PersonRepo,
    private val codes: ReunionCodeService,
    private val currentStaff: CurrentStaff,
    private val jdbc: JdbcClient,
) {
    @Transactional
    fun register(r: RegisterPersonRequest): Registered {
        val staff = currentStaff.get()
        if (r.source == "paper" && r.recordedAt == null) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Write the date on the paper. If there isn't one, write the day the forms were collected.",
            )
        }
        val created = people.insert(r, staff.shelterId, staff.staffId)
        val stored = people.find(r.id!!)!! // !!: id validated as present; the row exists after insert or conflict
        // Same id from another shelter: never reveal or touch someone else's record.
        if (stored.shelterId !=
            staff.shelterId
        ) {
            throw ApiException(HttpStatus.CONFLICT, "Something went wrong with this form. Please start it again.")
        }
        if (created && r.slipCode != null) codes.attachPrePrinted(r.slipCode, r.id, staff)
        return Registered(people.find(r.id)!!.view, created) // !!: just checked it exists
    }

    // Volunteers only see people registered at their own shelter; anything else looks like "not found".
    fun get(id: UUID): PersonView = ownShelter(id).view

    @Transactional
    fun edit(
        id: UUID,
        e: EditPersonRequest,
    ): PersonView {
        ownShelter(id)
        people.update(id, e, currentStaff.get().staffId)
        return ownShelter(id).view
    }

    @Transactional
    fun issueSlip(id: UUID): SlipResponse {
        val staff = currentStaff.get()
        ownShelter(id)
        val (code, pin) = codes.issueNew(id, staff)
        return slip(code, pin, staff.shelterId)
    }

    private fun ownShelter(id: UUID): StoredPerson {
        val stored = people.find(id)
        if (stored == null ||
            stored.shelterId != currentStaff.get().shelterId
        ) {
            throw ApiException(HttpStatus.NOT_FOUND, "Person not found.")
        }
        return stored
    }

    private fun slip(
        code: String,
        pin: String?,
        shelterId: UUID,
    ): SlipResponse =
        jdbc
            .sql("SELECT name, desk_phone FROM shelters WHERE id = :id")
            .param("id", shelterId)
            .query { rs, _ -> SlipResponse(code, pin, rs.getString("name"), rs.getString("desk_phone")) }
            .single()
}
