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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skipping the next ring of an alarm, and only that one. */
class SkipNextRingTest {

    private val key = RingKey(RingKind.ALARM, 1)

    // Monday 9 November 2026, 20:00: the next ring is Tuesday 07:00.
    private val now = LocalDateTime.of(2026, 11, 9, 20, 0)
    private val tuesday = LocalDateTime.of(2026, 11, 10, 7, 0)
    private val wednesday = LocalDateTime.of(2026, 11, 11, 7, 0)
    private val alarm = Alarm(id = 1, time = LocalTime.of(7, 0))

    @Test
    fun `skips the next ring only, the one after rings as usual`() = runBlocking {
        val g = Graph(alarm, now)
        g.reschedule()
        assertEquals(tuesday, g.scheduler.rings[key])

        g.skipNext(key)

        assertEquals(wednesday, g.scheduler.rings[key])
        // The alarm itself is untouched.
        assertEquals(alarm, g.alarms.alarms.single())
    }

    @Test
    fun `undo brings the ring back at once`() = runBlocking {
        val g = Graph(alarm, now)
        g.skipNext(key)
        g.undoSkip(key)
        assertEquals(tuesday, g.scheduler.rings[key])
        assertTrue(g.skips.get().isEmpty())
    }

    @Test
    fun `a skipped ring that still fires does not sound`() = runBlocking {
        val g = Graph(alarm, now)
        g.skipNext(key)
        g.now = tuesday
        assertNull(g.handle(key))
        // The ring after it is scheduled.
        assertEquals(wednesday, g.scheduler.rings[key])
    }

    @Test
    fun `the next ring after a skip sounds`() = runBlocking {
        val g = Graph(alarm, now)
        g.skipNext(key)
        g.now = wednesday
        assertNotNull(g.handle(key))
    }

    @Test
    fun `skipping a second time skips the following ring`() = runBlocking {
        val g = Graph(alarm, now)
        g.skipNext(key)
        g.skipNext(key)
        assertEquals(LocalDateTime.of(2026, 11, 12, 7, 0), g.scheduler.rings[key])
    }

    @Test
    fun `on a day off it targets the next ring that would sound`() = runBlocking {
        // Tuesday is a leave day: the alarm does not ring then, so the next real ring is Wednesday.
        val g = Graph(alarm, now, leaveDay = LocalDate.of(2026, 11, 10))
        g.skipNext(key)
        assertEquals(LocalDateTime.of(2026, 11, 12, 7, 0), g.scheduler.rings[key])
    }

    @Test
    fun `one-shot and disabled alarms cannot be skipped`() = runBlocking {
        val once = Graph(alarm.copy(days = emptySet()), now)
        once.skipNext(key)
        assertTrue(once.skips.get().isEmpty())

        val off = Graph(alarm.copy(enabled = false), now)
        off.skipNext(key)
        assertTrue(off.skips.get().isEmpty())
    }

    @Test
    fun `a skip from a notification targets the given ring`() = runBlocking {
        val g = Graph(alarm, now)
        g.skipNext(key, at = wednesday)
        // Tuesday still rings; Wednesday is passed over.
        assertEquals(tuesday, g.scheduler.rings[key])
        g.now = tuesday
        assertNotNull(g.handle(key))
        assertEquals(LocalDateTime.of(2026, 11, 12, 7, 0), g.scheduler.rings[key])
    }

    @Test
    fun `editing the alarm forgets its skip`() = runBlocking {
        val g = Graph(alarm, now)
        g.skipNext(key)
        SaveAlarmUseCase(g.alarms, g.reschedule, g.skips)(alarm.copy(label = "Work"))
        assertTrue(g.skips.get().isEmpty())
        assertEquals(tuesday, g.scheduler.rings[key])
    }

    @Test
    fun `old skips are forgotten`() = runBlocking {
        val g = Graph(alarm, now)
        g.skipNext(key)
        g.now = now.plusDays(5)
        g.reschedule()
        assertTrue(g.skips.get().isEmpty())
    }
}
