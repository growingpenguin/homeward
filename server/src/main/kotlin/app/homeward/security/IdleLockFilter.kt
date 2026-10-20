package app.homeward.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant

// Laptops get left on desks: after 10 minutes without a PERSON doing something, every request
// gets 401 {"locked": true} until POST /api/unlock with the PIN. Only requests the frontend marks
// with X-Homeward-Activity (clicks, typing) count; background refreshes never keep a session open.
class IdleLockFilter(
    private val idleAfter: Duration = IDLE_AFTER,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val session = request.getSession(false)
        val loggedIn = SecurityContextHolder.getContext().authentication?.principal is StaffPrincipal
        if (session == null || !loggedIn || request.requestURI in ALWAYS_ALLOWED) {
            chain.doFilter(request, response)
            return
        }
        // A shift ends 12 hours after login, even if requests keep coming in the background.
        val loginAt = session.getAttribute(LOGIN_AT) as? Instant ?: Instant.EPOCH
        if (Duration.between(loginAt, Instant.now()) > SHIFT) {
            session.invalidate()
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"expired":true,"message":"Your shift session has ended. Please log in again."}""")
            return
        }
        val last = session.getAttribute(LAST_ACTIVITY) as? Instant ?: Instant.EPOCH
        if (Duration.between(last, Instant.now()) > idleAfter) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"locked":true,"message":"Screen locked. Enter your PIN to carry on."}""")
            return
        }
        if (request.getHeader(ACTIVITY_HEADER) != null) session.setAttribute(LAST_ACTIVITY, Instant.now())
        chain.doFilter(request, response)
    }

    companion object {
        val IDLE_AFTER: Duration = Duration.ofMinutes(10)
        val SHIFT: Duration = Duration.ofHours(12)
        const val LOGIN_AT = "homeward.loginAt"
        const val LAST_ACTIVITY = "homeward.lastActivity"
        const val ACTIVITY_HEADER = "X-Homeward-Activity"
        private val ALWAYS_ALLOWED = setOf("/api/unlock", "/api/logout", "/api/health", "/api/csrf", "/api/shelters", "/api/login")
    }
}
