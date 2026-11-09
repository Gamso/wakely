package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The pause of every alarm until a date. */
class PauseTest {

    private val key = RingKey(RingKind.ALARM, 1)

    // Monday 9 November 2026, 20:00. The alarm rings every weekday at 07:00.
    private val now = LocalDateTime.of(2026, 11, 9, 20, 0)
    private val alarm = Alarm(id = 1, time = LocalTime.of(7, 0))
    private val thursday = LocalDate.of(2026, 11, 12)

    @Test
    fun `nothing rings up to the last paused day, then alarms ring again`() = runBlocking {
        val g = Graph(alarm, now)
        g.setPause(thursday)
        // Tuesday, Wednesday and Thursday are paused: the next ring is Friday.
        assertEquals(LocalDateTime.of(2026, 11, 13, 7, 0), g.scheduler.rings[key])
    }

    @Test
    fun `resuming brings the next ring back at once`() = runBlocking {
        val g = Graph(alarm, now)
        g.setPause(thursday)
        g.setPause(null)
        assertEquals(LocalDateTime.of(2026, 11, 10, 7, 0), g.scheduler.rings[key])
    }

    @Test
    fun `a ring scheduled before the pause does not sound during it`() = runBlocking {
        val g = Graph(alarm, now)
        g.reschedule()
        g.settings.setPause(thursday)
        g.now = LocalDateTime.of(2026, 11, 10, 7, 0)
        assertNull(g.handle(key))
    }

    @Test
    fun `the first ring after the pause sounds`() = runBlocking {
        val g = Graph(alarm, now)
        g.setPause(thursday)
        g.now = LocalDateTime.of(2026, 11, 13, 7, 0)
        assertNotNull(g.handle(key))
    }

    @Test
    fun `the alarms and their settings are untouched`() = runBlocking {
        val g = Graph(alarm, now)
        g.setPause(thursday)
        assertEquals(alarm, g.alarms.alarms.single())
    }

    @Test
    fun `a pause that is over is no pause`() {
        assertNull(Pause.active(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9)))
        assertEquals(thursday, Pause.active(thursday, LocalDate.of(2026, 11, 9)))
        // The last day still counts.
        assertEquals(thursday, Pause.active(thursday, thursday))
    }

    @Test
    fun `clamping moves a time to the end of the pause only when needed`() {
        val during = LocalDateTime.of(2026, 11, 10, 7, 0)
        val after = LocalDateTime.of(2026, 11, 14, 7, 0)
        assertEquals(thursday.plusDays(1).atStartOfDay().minusNanos(1), Pause.clamp(during, thursday))
        assertEquals(after, Pause.clamp(after, thursday))
        assertEquals(during, Pause.clamp(during, null))
    }

    @Test
    fun `an expired pause saved long ago changes nothing`() = runBlocking {
        val g = Graph(alarm, now)
        g.settings.setPause(LocalDate.of(2026, 1, 1))
        g.reschedule()
        assertEquals(LocalDateTime.of(2026, 11, 10, 7, 0), g.scheduler.rings[key])
    }
}
