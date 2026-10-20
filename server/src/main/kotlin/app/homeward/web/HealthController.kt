package app.homeward.web

import app.homeward.db.SchemaVersionCheck
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// Used by Render's health check and by the frontend's "Connecting. Please wait" banner.
// Public on purpose: it says nothing about people.
@RestController
class HealthController(
    private val schema: SchemaVersionCheck,
) {
    @GetMapping("/api/health")
    fun health(): Map<String, Any> = mapOf("status" to "ok", "schemaVersion" to schema.databaseVersion())
}
