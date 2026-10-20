package app.homeward.security

import java.io.Serializable
import java.util.UUID

// Who is logged in. Stored in the session; every service reads the shelter and role from here,
// never from a request body.
data class StaffPrincipal(
    val staffId: UUID,
    val shelterId: UUID,
    val displayName: String,
    val role: String, // volunteer | lead | coordinator
) : Serializable
