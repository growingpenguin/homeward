package app.homeward.security

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginThrottleTest {
    private class MovableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant() = now
    }

    @Test
    fun `after a lock expires, the count starts over (one typo doesn't lock again)`() {
        val clock = MovableClock(Instant.parse("2026-10-20T12:00:00Z"))
        val throttle = LoginThrottle(clock)
        repeat(5) { throttle.recordFailure("k") }
        assertTrue(throttle.isLocked("k"))
        clock.now = clock.now.plus(Duration.ofMinutes(16))
        assertFalse(throttle.isLocked("k"))
        throttle.recordFailure("k")
        assertFalse(throttle.isLocked("k"))
    }
}
