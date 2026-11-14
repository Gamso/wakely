package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A pending snooze is cancelled with its alarm, and survives a reboot. */
class PendingSnoozeTest {

    private val now = LocalDateTime.of(2026, 11, 9, 7, 0) // a Monday
    private val alarm = Alarm(id = 1, time = LocalTime.of(7, 0))
    private val key = RingKey(RingKind.ALARM, 1)
    private val snoozeAt = now.plusMinutes(10)

    private fun snoozed(): Graph = Graph(alarm, now).also {
        SnoozeAlarmUseCase(it.snoozes, TimeProvider { now })(key, 10)
        assertEquals(snoozeAt, it.scheduler.snoozes[key])
        assertEquals(snoozeAt, it.pendingSnoozes.map[key])
    }

    private fun assertNoSnooze(g: Graph) {
        assertNull(g.scheduler.snoozes[key])
        assertTrue(g.pendingSnoozes.map.isEmpty())
    }

    @Test
    fun `turning the alarm off cancels its snooze`() = runBlocking {
        val g = snoozed()
        SetAlarmEnabledUseCase(g.alarms, g.reschedule, g.snoozes)(alarm, false)
        assertNoSnooze(g)
    }

    @Test
    fun `turning the alarm on keeps its snooze`() = runBlocking {
        val g = snoozed()
        SetAlarmEnabledUseCase(g.alarms, g.reschedule, g.snoozes)(alarm, true)
        assertEquals(snoozeAt, g.scheduler.snoozes[key])
    }

    @Test
    fun `editing the alarm cancels its snooze`() = runBlocking {
        val g = snoozed()
        SaveAlarmUseCase(g.alarms, g.reschedule, g.skips, g.snoozes)(alarm.copy(label = "Work"))
        assertNoSnooze(g)
    }

    @Test
    fun `deleting the alarm cancels its snooze`() = runBlocking {
        val g = snoozed()
        DeleteAlarmUseCase(g.alarms, g.scheduler, g.skips, g.snoozes)(alarm.id)
        assertNoSnooze(g)
    }

    @Test
    fun `a fired snooze is no longer pending`() = runBlocking {
        val g = snoozed()
        val fired =
            HandleSnoozeFiredUseCase(GetRingInfoUseCase(g.alarms, NoEvents), TimeProvider { snoozeAt }, g.snoozes)
        assertNotNull(fired(key, snoozeAt))
        assertTrue(g.pendingSnoozes.map.isEmpty())
    }

    @Test
    fun `after a reboot, a pending snooze is scheduled again`() = runBlocking {
        val g = snoozed()
        g.scheduler.snoozes.clear() // the system forgot it
        RestoreSnoozesUseCase(g.snoozes, g.alarms, NoEvents, TimeProvider { now.plusMinutes(5) })()
        assertEquals(snoozeAt, g.scheduler.snoozes[key])
    }

    @Test
    fun `after a long time off, a snooze too late is dropped`() = runBlocking {
        val g = snoozed()
        g.scheduler.snoozes.clear()
        RestoreSnoozesUseCase(g.snoozes, g.alarms, NoEvents, TimeProvider { snoozeAt.plusMinutes(31) })()
        assertNoSnooze(g)
    }

    @Test
    fun `the snooze of a deleted alarm is dropped`() = runBlocking {
        val g = snoozed()
        g.alarms.alarms = emptyList()
        RestoreSnoozesUseCase(g.snoozes, g.alarms, NoEvents, TimeProvider { now })()
        assertNoSnooze(g)
    }
}
