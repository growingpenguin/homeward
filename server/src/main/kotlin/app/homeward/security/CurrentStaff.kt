package app.homeward.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

// The logged-in staff member, taken from the session. Services use this for shelter, role and
// "who did it" (audit, updated_by, approvals), never anything sent in a request body.
@Component
class CurrentStaff {
    fun get(): StaffPrincipal =
        SecurityContextHolder.getContext().authentication?.principal as? StaffPrincipal
            ?: throw IllegalStateException("No staff member is logged in")
}
