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
    private val locks = ConcurrentHashMap<String, Any>()

    // The whole check → verify PIN → count runs under one lock per name, so parallel guesses
    // are counted one by one and can't all slip past the limit.
    fun <T> guarded(
        key: String,
        block: () -> T,
    ): T = synchronized(locks.computeIfAbsent(key) { Any() }) { block() }

    fun isLocked(key: String): Boolean = current(key)?.lockedUntil != null

    fun recordFailure(key: String) {
        purgeIfLarge()
        val failures = (current(key)?.failures ?: 0) + 1
        attempts[key] = Attempts(failures, if (failures >= MAX_FAILURES) clock.instant().plus(LOCK_FOR) else null)
    }

    fun recordSuccess(key: String) {
        attempts.remove(key)
    }

    fun clear() = attempts.clear() // tests only

    // An expired lock starts over from zero (one typo after waiting must not lock again).
    private fun current(key: String): Attempts? {
        val a = attempts[key] ?: return null
        if (a.lockedUntil != null && !a.lockedUntil.isAfter(clock.instant())) {
            attempts.remove(key)
            return null
        }
        return a
    }

    // Wrong logins with made-up names must not grow memory forever on a 512 MB server.
    private fun purgeIfLarge() {
        if (attempts.size < MAX_ENTRIES) return
        val now = clock.instant()
        attempts.entries.removeIf { it.value.lockedUntil == null || !it.value.lockedUntil!!.isAfter(now) } // !!: null checked first
        if (attempts.size >= MAX_ENTRIES) attempts.clear()
        locks.keys.retainAll(attempts.keys)
    }

    companion object {
        const val MAX_FAILURES = 5
        const val MAX_ENTRIES = 10_000
        val LOCK_FOR: Duration = Duration.ofMinutes(15)

        fun loginKey(
            shelterId: Any,
            name: String,
        ) = "login:$shelterId:${name.trim().lowercase()}"

        fun unlockKey(staffId: Any) = "unlock:$staffId"
    }
}
