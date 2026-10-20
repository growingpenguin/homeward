package app.homeward.audit

import app.homeward.security.StaffPrincipal
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.util.UUID

// Called from the service method that does the action, in the same transaction.
// The app may only add staff_id, action and target (V1's column grant); the time comes from the database.
// Never a PIN, a message or a location: only IDs and an action from the fixed list in V1.
@Service
class AuditService(
    private val jdbc: JdbcClient,
) {
    fun log(
        actor: StaffPrincipal,
        action: String,
        targetId: UUID?,
    ) {
        jdbc
            .sql("INSERT INTO audit_log (staff_id, action, target) VALUES (:staff, :action, :target)")
            .param("staff", actor.staffId)
            .param("action", action)
            .param("target", targetId)
            .update()
    }
}
