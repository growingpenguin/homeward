package app.homeward.security

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// 5 wrong PINs for one login name (or one unlock) → locked for 15 minutes. In memory (one instance).
@Component
class LoginThrottle(
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Attempts(
        val failures: Int,
        val lockedUntil: Instant?,
    )

    private val attempts = ConcurrentHashMap<String, Attempts>()

    fun isLocked(key: String): Boolean = attempts[key]?.lockedUntil?.isAfter(clock.instant()) == true

    fun recordFailure(key: String) {
        attempts.compute(key) { _, old ->
            val failures = (old?.failures ?: 0) + 1
            Attempts(failures, if (failures >= MAX_FAILURES) clock.instant().plus(LOCK_FOR) else null)
        }
    }

    fun recordSuccess(key: String) {
        attempts.remove(key)
    }

    fun clear() = attempts.clear() // tests only

    companion object {
        const val MAX_FAILURES = 5
        val LOCK_FOR: Duration = Duration.ofMinutes(15)

        fun loginKey(
            shelterId: Any,
            name: String,
        ) = "login:$shelterId:${name.trim().lowercase()}"

        fun unlockKey(staffId: Any) = "unlock:$staffId"
    }
}
