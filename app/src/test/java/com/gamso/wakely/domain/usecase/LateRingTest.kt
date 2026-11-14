package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** An alarm the system delivers late (no exact alarm permission) still rings, within a limit. */
class LateRingTest {

    private val ringAt = LocalDateTime.of(2026, 11, 9, 7, 0) // a Monday
    private val key = RingKey(RingKind.ALARM, 1)
    private val alarm = Alarm(id = 1, time = LocalTime.of(7, 0))

    private fun fire(at: LocalDateTime): Pair<Graph, Boolean> {
        val g = Graph(alarm, at)
        return g to (runBlocking { g.handle(key) } != null)
    }

    @Test
    fun `an alarm delivered 20 minutes late rings, then the next day is scheduled`() {
        val (g, rang) = fire(ringAt.plusMinutes(20))
        assertEquals(true, rang)
        assertEquals(ringAt.plusDays(1), g.scheduler.rings[key])
    }

    @Test
    fun `an alarm delivered more than 30 minutes late does not ring`() {
        val (g, rang) = fire(ringAt.plusMinutes(31))
        assertEquals(false, rang)
        assertEquals(ringAt.plusDays(1), g.scheduler.rings[key])
    }

    @Test
    fun `an alarm delivered a little early still rings, but not too early`() {
        assertEquals(true, fire(ringAt.minusMinutes(4)).second)
        assertEquals(false, fire(ringAt.minusMinutes(6)).second)
    }

    @Test
    fun `a late one-shot alarm rings once`() = runBlocking {
        val g = Graph(alarm.copy(days = emptySet()), ringAt.plusMinutes(10))
        assertNotNull(g.handle(key))
        assertNull(g.handle(key))
    }

    @Test
    fun `an event rule rings for the latest due event, not for one that already rang`() = runBlocking {
        val first = CalendarEvent("e", "First", "", ringAt.plusMinutes(60), ringAt.plusMinutes(90), false)
        val second = CalendarEvent("e", "Second", "", ringAt.plusMinutes(80), ringAt.plusMinutes(100), false)
        val rule = EventAlarm(id = 2, minutesBefore = 60)
        val ruleKey = RingKey(RingKind.EVENT, 2)
        val now = ringAt.plusMinutes(20) // the second ring is due, the first one rang at 07:00
        val calendar = FakeCalendar(listOf(first, second))
        val settings = FakeSettings(listOf(CalendarFilter("e", CalendarCategory.EVENTS)))
        val rules = FakeEventAlarms(rule)
        val time = TimeProvider { now }
        val getDaysOff =
            GetDaysOffUseCase(calendar, NoPublicHolidays, DayOffSources(settings, NoRegion, NoPublicHolidays))
        val getRuleEvents = GetRuleEventsUseCase(calendar, settings)
        val compute = ComputeNextRingUseCase()
        val scheduler = FakeScheduler()
        val skips = FakeSkips()
        val reschedule = RescheduleAlarmsUseCase(
            FakeAlarms(emptyList()),
            rules,
            settings,
            getDaysOff,
            getRuleEvents,
            compute,
            scheduler,
            time,
            skips,
        )
        val handle = HandleRingFiredUseCase(
            FakeAlarms(emptyList()),
            rules,
            getDaysOff,
            getRuleEvents,
            compute,
            reschedule,
            time,
            FakeSensor(),
            FakeStore(),
            skips,
            settings,
        )
        assertEquals("Second", handle(ruleKey)?.label)
        // No event left after the second one.
        assertNull(scheduler.rings[ruleKey])
    }
}
