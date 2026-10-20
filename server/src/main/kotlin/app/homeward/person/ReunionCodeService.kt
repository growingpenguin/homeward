package app.homeward.person

import app.homeward.audit.AuditService
import app.homeward.security.StaffPrincipal
import app.homeward.web.ApiException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
            // ON CONFLICT on the code: a taken random code inserts nothing (and doesn't abort the
            // transaction, which a failed INSERT would in PostgreSQL), so we simply try another.
            val inserted =
                try {
                    jdbc
                        .sql(
                            "INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES (:code, :person, :hash) ON CONFLICT (code) DO NOTHING",
                        ).param("code", code)
                        .param("person", personId)
                        .param("hash", hash)
                        .update()
                } catch (e: DuplicateKeyException) {
                    // one_active_code_per_person: another request gave this person a slip at the same moment.
                    throw alreadyHasSlip()
                }
            if (inserted == 1) {
                audit.log(staff, "CODE_ISSUED", personId)
                return code to pin
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

    // Someone with no phone shows their slip at ANY desk; a volunteer types the code and PIN.
    // Returns whose slip it is, or null. A wrong code, a locked code and a wrong PIN all return null
    // after the same BCrypt work, and nothing is thrown, so the failure count is saved.
    // @Transactional: the row lock (FOR UPDATE) holds until the count is saved, so parallel guesses wait in line.
    @Transactional
    fun lookup(
        code: String,
        pin: String,
        staff: StaffPrincipal,
    ): UUID? {
        // FOR UPDATE: guesses sent at the same time are counted one by one, so none slip past the limit.
        // Only working codes: an old (voided) slip or a blank pre-printed slip never opens anything.
        val rc =
            jdbc
                .sql(
                    """
                    SELECT person_id, pin_hash, locked_until > now() AS locked FROM reunion_codes
                    WHERE code = :code AND voided_at IS NULL AND person_id IS NOT NULL
                    FOR UPDATE
                    """.trimIndent(),
                ).param("code", normalize(code))
                .query { rs, _ -> Triple(rs.getObject("person_id", UUID::class.java), rs.getString("pin_hash"), rs.getBoolean("locked")) }
                .optional()
                .orElse(null)
        if (rc == null || rc.third) {
            encoder.matches(pin, DUMMY_HASH) // same work as a real check, so timing gives nothing away
            audit.log(staff, "CODE_LOOKUP_FAILED", rc?.first)
            return null
        }
        val (personId, hash, _) = rc
        if (!encoder.matches(pin.trim(), hash)) {
            // The 5th wrong PIN locks the slip for 15 minutes (and starts counting again after that).
            jdbc
                .sql(
                    """
                    UPDATE reunion_codes
                    SET failed_attempts = CASE WHEN failed_attempts + 1 >= :max THEN 0 ELSE failed_attempts + 1 END,
                        locked_until = CASE WHEN failed_attempts + 1 >= :max THEN now() + interval '15 minutes' ELSE locked_until END
                    WHERE code = :code
                    """.trimIndent(),
                ).param("max", MAX_TRIES)
                .param("code", normalize(code))
                .update()
            audit.log(staff, "CODE_LOOKUP_FAILED", personId)
            return null
        }
        jdbc
            .sql(
                "UPDATE reunion_codes SET failed_attempts = 0, locked_until = NULL WHERE code = :code",
            ).param("code", normalize(code))
            .update()
        audit.log(staff, "CODE_LOOKUP", personId)
        return personId
    }

    // A lost slip (lead only): the old code stops working and a new one is printed, in one transaction.
    fun reissue(
        personId: UUID,
        staff: StaffPrincipal,
    ): Pair<String, String> {
        jdbc.sql("UPDATE reunion_codes SET voided_at = now() WHERE person_id = :p AND voided_at IS NULL").param("p", personId).update()
        audit.log(staff, "CODE_REISSUED", personId)
        return issueNew(personId, staff)
    }

    // "hw-7k3p", " HW-7K3P " and "7K3P" are all the slip HW-7K3P.
    private fun normalize(code: String): String {
        val c = code.trim().uppercase()
        return if (c.startsWith("HW-")) c else "HW-$c"
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
        const val MAX_TRIES = 5

        // A real BCrypt hash of a throwaway PIN, only used to spend the same time on a miss.
        private const val DUMMY_HASH = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}
