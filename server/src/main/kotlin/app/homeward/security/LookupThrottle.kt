package app.homeward.security

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// 10 failed slip-code lookups per login within 15 minutes → every lookup from that login is refused
// (same "didn't match" reply, nothing checked). The per-slip 5-PIN lock can't stop someone trying
// many different codes; this does. In memory (one instance), like LoginThrottle.
@Component
class LookupThrottle(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val failures = ConcurrentHashMap<UUID, List<Instant>>()
    private val locks = ConcurrentHashMap<UUID, Any>()

    // Check → look up → count run under one lock per login, so parallel guesses can't all slip past the limit.
    fun <T> guarded(
        staffId: UUID,
        block: () -> T,
    ): T = synchronized(locks.computeIfAbsent(staffId) { Any() }) { block() }

    fun isBlocked(staffId: UUID): Boolean = recent(staffId).size >= MAX_FAILURES

    fun recordFailure(staffId: UUID) {
        purgeIfLarge()
        failures[staffId] = recent(staffId) + clock.instant()
    }

    fun clear() = failures.clear() // tests only

    private fun recent(staffId: UUID): List<Instant> {
        val cutoff = clock.instant().minus(WINDOW)
        return failures[staffId].orEmpty().filter { it.isAfter(cutoff) }
    }

    private fun purgeIfLarge() {
        if (failures.size < MAX_ENTRIES) return
        failures.keys.removeIf { recent(it).isEmpty() }
        locks.keys.retainAll(failures.keys)
    }

    companion object {
        const val MAX_FAILURES = 10
        const val MAX_ENTRIES = 10_000
        val WINDOW: Duration = Duration.ofMinutes(15)
    }
}
