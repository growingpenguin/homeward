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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
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
    private val currentStaff: CurrentStaff,
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
        val found =
            throttle.guarded(key) {
                if (throttle.isLocked(key)) return@guarded null to tooManyTries()
                val candidate = staff.findForLogin(body.shelterId, body.name)
                // Same BCrypt work and the same answer whether the name is unknown or the PIN is wrong.
                val ok = encoder.matches(body.pin, candidate?.pinHash ?: DUMMY_HASH) && candidate != null
                if (!ok) {
                    throttle.recordFailure(key)
                    return@guarded null to (if (throttle.isLocked(key)) tooManyTries() else wrongLogin())
                }
                throttle.recordSuccess(key)
                candidate to null
            }
        found.second?.let { return it }
        val login = found.first!! // !!: set whenever there's no error response

        val principal = StaffPrincipal(login.staffId, login.shelterId, login.displayName, login.role)
        val auth = UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${login.role.uppercase()}")))
        val context = SecurityContextHolder.createEmptyContext().apply { authentication = auth }
        SecurityContextHolder.setContext(context)
        request.getSession(true)
        request.changeSessionId() // session fixation: a new session id at login (done here, not by Spring)
        request.session.setAttribute(IdleLockFilter.LOGIN_AT, Instant.now())
        request.session.setAttribute(IdleLockFilter.LAST_ACTIVITY, Instant.now())
        contextRepository.saveContext(context, request, response)
        // A new CSRF token after login: clear the cookie (same name and path csrf.spa() uses);
        // the app calls /api/csrf again. The cookie repository keeps no state, so a local instance is enough.
        CookieCsrfTokenRepository.withHttpOnlyFalse().saveToken(null, request, response)
        return ResponseEntity.ok(principal.toMe())
    }

    @GetMapping("/api/me")
    fun me(): Me = currentStaff.get().toMe()

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

    private fun wrongPin() = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Problem("That PIN didn't work. Please try again."))

    private fun tooManyTries() =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Problem("Too many tries. Please wait 15 minutes or ask a lead."))

    companion object {
        // A real BCrypt hash of a throwaway PIN: spends the same time when the name is unknown.
        private const val DUMMY_HASH = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}
