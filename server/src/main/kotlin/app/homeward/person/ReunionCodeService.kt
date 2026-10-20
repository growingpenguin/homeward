package app.homeward.person

import app.homeward.audit.AuditService
import app.homeward.security.StaffPrincipal
import app.homeward.web.ApiException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.UUID

// Printed slips for people with no phone: a code (HW- + 4 characters, no 0/O/1/I) and a 4-digit PIN.
// Only the PIN's BCrypt hash is stored; the PIN itself is shown once, on the slip.
@Service
class ReunionCodeService(
    private val jdbc: JdbcClient,
    private val encoder: PasswordEncoder,
    private val audit: AuditService,
) {
    private val random = SecureRandom()

    fun issueNew(
        personId: UUID,
        staff: StaffPrincipal,
    ): Pair<String, String> {
        if (hasWorkingSlip(personId)) throw alreadyHasSlip()
        val pin = (0 until 4).joinToString("") { random.nextInt(10).toString() }
        val hash = encoder.encode(pin)!! // !!: BCryptPasswordEncoder never returns null for a non-null PIN
        repeat(10) {
            val code = "HW-" + (0 until 4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            try {
                jdbc
                    .sql("INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES (:code, :person, :hash)")
                    .param("code", code)
                    .param("person", personId)
                    .param("hash", hash)
                    .update()
                audit.log(staff, "CODE_ISSUED", personId)
                return code to pin
            } catch (e: DuplicateKeyException) {
                // Another request gave this person a slip at the same moment: never make a second one.
                if (e.message.orEmpty().contains("one_active_code_per_person")) throw alreadyHasSlip()
                // Otherwise the random code was taken: try another.
            }
        }
        throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't make a new slip right now. Please try again.")
    }

    // A pre-printed slip (handed out with a paper form) is attached only while it's free and working.
    fun attachPrePrinted(
        code: String,
        personId: UUID,
        staff: StaffPrincipal,
    ) {
        if (hasWorkingSlip(personId)) throw alreadyHasSlip()
        val attached =
            jdbc
                .sql(
                    """
                    UPDATE reunion_codes SET person_id = :person
                    WHERE code = :code AND person_id IS NULL AND voided_at IS NULL
                    """.trimIndent(),
                ).param("person", personId)
                .param("code", code.trim().uppercase())
                .update()
        if (attached !=
            1
        ) {
            throw ApiException(HttpStatus.CONFLICT, "That slip is already in use or not valid. Please check the code, or ask a lead.")
        }
        audit.log(staff, "CODE_ISSUED", personId)
    }

    private fun hasWorkingSlip(personId: UUID) =
        jdbc
            .sql("SELECT EXISTS (SELECT 1 FROM reunion_codes WHERE person_id = :p AND voided_at IS NULL)")
            .param("p", personId)
            .query(Boolean::class.java)
            .single()

    private fun alreadyHasSlip() =
        ApiException(HttpStatus.CONFLICT, "This person already has a slip. If it's lost, ask a lead to reprint it.")

    companion object {
        const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // same as the database: no 0/O, no 1/I
    }
}
