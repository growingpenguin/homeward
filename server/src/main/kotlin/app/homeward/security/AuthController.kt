package app.homeward.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class LoginRequest(
    val shelterId: UUID,
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotBlank @field:Size(max = 12) val pin: String,
)

data class UnlockRequest(
    @field:NotBlank @field:Size(max = 12) val pin: String,
)

data class Me(
    val staffId: UUID,
    val shelterId: UUID,
    val displayName: String,
    val role: String,
)

data class Problem(
    val message: String,
)

@RestController
class AuthController(
    private val staff: StaffRepo,
    private val encoder: PasswordEncoder,
    private val throttle: LoginThrottle,
    private val contextRepository: SecurityContextRepository,
) {
    @GetMapping("/api/shelters")
    fun shelters() = staff.shelters()

    // The React app calls this once before the first POST. It sets the XSRF-TOKEN cookie; the app sends
    // that cookie's value back in the X-XSRF-TOKEN header (csrf.spa()). The masked value of
    // `token.token` would be rejected, so it's deliberately not returned.
    @GetMapping("/api/csrf")
    fun csrf(token: CsrfToken): Map<String, String> {
        token.token // reading it makes Spring write the cookie
        return mapOf("headerName" to "X-XSRF-TOKEN", "cookieName" to "XSRF-TOKEN") // the names csrf.spa() uses
    }

    @PostMapping("/api/login")
    fun login(
        @Valid @RequestBody body: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> {
        val key = LoginThrottle.loginKey(body.shelterId, body.name)
        if (throttle.isLocked(key)) return tooManyTries()

        val found = staff.findForLogin(body.shelterId, body.name)
        // Same BCrypt work and the same answer whether the name is unknown or the PIN is wrong.
        val ok = encoder.matches(body.pin, found?.pinHash ?: DUMMY_HASH) && found != null
        if (!ok) {
            throttle.recordFailure(key)
            return if (throttle.isLocked(key)) tooManyTries() else wrongLogin()
        }
        throttle.recordSuccess(key)

        val principal = StaffPrincipal(found.staffId, found.shelterId, found.displayName, found.role)
        val auth = UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${found.role.uppercase()}")))
        val context = SecurityContextHolder.createEmptyContext().apply { authentication = auth }
        SecurityContextHolder.setContext(context)
        request.getSession(true) // session fixation: Spring changes the id on login
        request.changeSessionId()
        request.session.maxInactiveInterval = SHIFT.seconds.toInt()
        request.session.setAttribute(IdleLockFilter.LAST_ACTIVITY, Instant.now())
        contextRepository.saveContext(context, request, response)
        return ResponseEntity.ok(principal.toMe())
    }

    @GetMapping("/api/me")
    fun me(): Me = (SecurityContextHolder.getContext().authentication!!.principal as StaffPrincipal).toMe() // !!: route requires login

    @PostMapping("/api/unlock")
    fun unlock(
        @Valid @RequestBody body: UnlockRequest,
        request: HttpServletRequest,
    ): ResponseEntity<*> {
        val principal = SecurityContextHolder.getContext().authentication!!.principal as StaffPrincipal // !!: route requires login
        val key = LoginThrottle.unlockKey(principal.staffId)
        if (throttle.isLocked(key)) return tooManyTries()
        if (!encoder.matches(body.pin, staff.pinHash(principal.staffId) ?: DUMMY_HASH)) {
            throttle.recordFailure(key)
            return if (throttle.isLocked(
                    key,
                )
            ) {
                tooManyTries()
            } else {
                ResponseEntity
                    .status(
                        HttpStatus.UNAUTHORIZED,
                    ).body(Problem("That PIN didn't work. Please try again."))
            }
        }
        throttle.recordSuccess(key)
        request.session.setAttribute(IdleLockFilter.LAST_ACTIVITY, Instant.now())
        return ResponseEntity.ok(principal.toMe())
    }

    @PostMapping("/api/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }

    private fun StaffPrincipal.toMe() = Me(staffId, shelterId, displayName, role)

    private fun wrongLogin() =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Problem("That didn't work. Check the shelter, your name and your PIN."))

    private fun tooManyTries() =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Problem("Too many tries. Please wait 15 minutes or ask a lead."))

    companion object {
        val SHIFT: Duration = Duration.ofHours(12)

        // A real BCrypt hash of a throwaway PIN: spends the same time when the name is unknown.
        private const val DUMMY_HASH = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}
