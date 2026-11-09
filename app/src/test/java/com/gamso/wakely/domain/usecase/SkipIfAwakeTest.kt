package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.HolidaySettings
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkipIfAwake
import com.gamso.wakely.domain.model.StepBaseline
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.DeviceRegion
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.HolidayRepository
import com.gamso.wakely.domain.repository.PublicHoliday
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.StepBaselineStore
import com.gamso.wakely.domain.repository.StepSensor
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decision to skip an alarm because the user is already up, and its safety nets. */
class SkipIfAwakeTest {

    private val ringAt = LocalDateTime.of(2026, 11, 9, 7, 0) // a Monday
    private val key = RingKey(RingKind.ALARM, 1)

    private fun baseline(steps: Long = 1_000, minutesBefore: Long = 30) =
        StepBaseline(steps, ringAt.minusMinutes(minutesBefore))

    // ---- pure decision

    @Test
    fun `skips when enough steps were taken since the baseline`() {
        assertTrue(AwakeCheck.shouldSkip(baseline(), 1_000 + AwakeCheck.MIN_STEPS, ringAt))
    }

    @Test
    fun `rings when too few steps`() {
        assertFalse(AwakeCheck.shouldSkip(baseline(), 1_000 + AwakeCheck.MIN_STEPS - 1, ringAt))
    }

    @Test
    fun `rings without a baseline or without a reading`() {
        assertFalse(AwakeCheck.shouldSkip(null, 5_000, ringAt))
        assertFalse(AwakeCheck.shouldSkip(baseline(), null, ringAt))
    }

    @Test
    fun `rings when the counter went down, after a reboot`() {
        assertFalse(AwakeCheck.shouldSkip(baseline(steps = 90_000), 400, ringAt))
    }

    @Test
    fun `rings when the baseline is from another day or after the ring`() {
        assertFalse(AwakeCheck.shouldSkip(StepBaseline(0, ringAt.minusDays(1)), 5_000, ringAt))
        assertFalse(AwakeCheck.shouldSkip(StepBaseline(0, ringAt.plusMinutes(1)), 5_000, ringAt))
    }

    // ---- fakes

    private class FakeAlarms(var alarms: List<Alarm>) : AlarmRepository {
        override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms)
        override suspend fun getAlarms() = alarms
        override suspend fun getAlarm(id: Long) = alarms.find { it.id == id }
        override suspend fun save(alarm: Alarm): Alarm {
            alarms = alarms.filterNot { it.id == alarm.id } + alarm
            return alarm
        }

        override suspend fun delete(id: Long) {
            alarms = alarms.filterNot { it.id == id }
        }
    }

    private object NoEvents : EventAlarmRepository {
        override fun observeEventAlarms(): Flow<List<EventAlarm>> = flowOf(emptyList())
        override suspend fun getEventAlarms() = emptyList<EventAlarm>()
        override suspend fun getEventAlarm(id: Long): EventAlarm? = null
        override suspend fun save(alarm: EventAlarm) = alarm
        override suspend fun delete(id: Long) = Unit
    }

    private class FakeSettings(initial: List<CalendarFilter> = emptyList()) : SettingsRepository {
        private val filters = MutableStateFlow(initial)
        private val summary = MutableStateFlow(SummarySettings(enabled = false))
        override fun observeCalendarFilters(): Flow<List<CalendarFilter>> = filters
        override suspend fun getCalendarFilters() = filters.value
        override suspend fun setCalendarFilters(filters: List<CalendarFilter>) = Unit
        override fun observeSummarySettings(): Flow<SummarySettings> = summary
        override suspend fun getSummarySettings() = summary.value
        override suspend fun setSummarySettings(settings: SummarySettings) = Unit
        override fun observeHolidaySettings(): Flow<HolidaySettings> = flowOf(HolidaySettings())
        override suspend fun getHolidaySettings() = HolidaySettings()
        override suspend fun setHolidaySettings(settings: HolidaySettings) = Unit
    }

    /** No public holidays: the days off of these tests come from a leave calendar. */
    private object NoPublicHolidays : HolidayRepository {
        override fun countries() = emptyList<String>()
        override fun regions(country: String) = emptyList<HolidayRegion>()
        override fun holidays(country: String, region: String?, from: LocalDate, to: LocalDate) =
            emptyList<PublicHoliday>()
    }

    private object NoRegion : DeviceRegion {
        override fun countryCandidates() = emptyList<String>()
    }

    private class FakeCalendar(private val events: List<CalendarEvent> = emptyList()) : CalendarRepository {
        override fun hasPermission() = true
        override suspend fun getCalendars() = emptyList<DeviceCalendar>()
        override suspend fun getEvents(sourceKeys: Set<String>, from: LocalDate, to: LocalDate) = events
    }

    private class FakeScheduler : AlarmScheduler {
        val rings = mutableMapOf<RingKey, LocalDateTime>()
        val preChecks = mutableMapOf<RingKey, LocalDateTime>()
        override fun schedule(key: RingKey, at: LocalDateTime) {
            rings[key] = at
        }

        override fun scheduleSnooze(key: RingKey, at: LocalDateTime) = Unit
        override fun cancel(key: RingKey) {
            rings.remove(key)
        }

        override fun schedulePreCheck(key: RingKey, at: LocalDateTime) {
            preChecks[key] = at
        }

        override fun cancelPreCheck(key: RingKey) {
            preChecks.remove(key)
        }

        override fun scheduleSummary(at: LocalDateTime?) = Unit
        override fun canScheduleExactAlarms() = true
    }

    private class FakeSensor(var permission: Boolean = true, var steps: Long? = 0) : StepSensor {
        override fun isAvailable() = true
        override fun hasPermission() = permission
        override suspend fun readSteps() = steps
    }

    private class FakeStore : StepBaselineStore {
        val map = mutableMapOf<RingKey, StepBaseline>()
        override fun save(key: RingKey, baseline: StepBaseline) {
            map[key] = baseline
        }

        override fun get(key: RingKey) = map[key]
        override fun clear(key: RingKey) {
            map.remove(key)
        }
    }

    private class Graph(alarm: Alarm, now: LocalDateTime, leaveDay: LocalDate? = null) {
        val alarms = FakeAlarms(listOf(alarm))
        val scheduler = FakeScheduler()
        val sensor = FakeSensor()
        val store = FakeStore()
        var now: LocalDateTime = now
        private val time = TimeProvider { this.now }
        private val leave = leaveDay?.let {
            CalendarEvent("l", "Congé", "", it.atStartOfDay(), it.plusDays(1).atStartOfDay(), true)
        }
        private val calendar = FakeCalendar(listOfNotNull(leave))
        private val settings = FakeSettings(listOf(CalendarFilter("l", CalendarCategory.LEAVE)))
        private val getDaysOff =
            GetDaysOffUseCase(calendar, NoPublicHolidays, DayOffSources(settings, NoRegion, NoPublicHolidays))
        private val getRuleEvents = GetRuleEventsUseCase(calendar, settings)
        private val compute = ComputeNextRingUseCase()
        val reschedule = RescheduleAlarmsUseCase(
            alarms,
            NoEvents,
            settings,
            getDaysOff,
            getRuleEvents,
            compute,
            scheduler,
            time,
        )
        val handle = HandleRingFiredUseCase(
            alarms,
            NoEvents,
            getDaysOff,
            getRuleEvents,
            compute,
            reschedule,
            time,
            sensor,
            store,
        )
        val record = RecordStepBaselineUseCase(alarms, sensor, store, time)
    }

    private val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), skipIfAwake = SkipIfAwake.ALWAYS)

    // ---- scheduling

    @Test
    fun `a step check is scheduled half an hour before an opted-in alarm`() = runBlocking {
        val g = Graph(alarm, ringAt.minusHours(8))
        g.reschedule()
        assertEquals(ringAt, g.scheduler.rings[key])
        assertEquals(ringAt.minusMinutes(30), g.scheduler.preChecks[key])
    }

    @Test
    fun `no step check without the option`() = runBlocking {
        val g = Graph(alarm.copy(skipIfAwake = SkipIfAwake.OFF), ringAt.minusHours(8))
        g.reschedule()
        assertNull(g.scheduler.preChecks[key])
    }

    @Test
    fun `no step check when it would already be in the past`() = runBlocking {
        val g = Graph(alarm, ringAt.minusMinutes(10))
        g.reschedule()
        assertNull(g.scheduler.preChecks[key])
        assertNotNull(g.scheduler.rings[key])
    }

    // ---- the step check

    @Test
    fun `the step check records the count, or nothing without permission`() = runBlocking {
        val g = Graph(alarm, ringAt.minusMinutes(30))
        g.sensor.steps = 1_000
        g.record(key)
        assertEquals(StepBaseline(1_000, ringAt.minusMinutes(30)), g.store.map[key])

        g.sensor.permission = false
        g.record(key)
        assertNull(g.store.map[key])
    }

    // ---- the ring

    private fun ring(g: Graph, baselineSteps: Long?, currentSteps: Long?, at: LocalDateTime = ringAt) = runBlocking {
        baselineSteps?.let { g.store.save(key, StepBaseline(it, at.minusMinutes(30))) }
        g.sensor.steps = currentSteps
        g.now = at
        g.handle(key)
    }

    @Test
    fun `skips the ring when the user walked, this occurrence only`() {
        val g = Graph(alarm, ringAt)
        assertNull(ring(g, 1_000, 1_100))
        // Repeating alarm: the next day is scheduled and the baseline is gone.
        assertEquals(ringAt.plusDays(1), g.scheduler.rings[key])
        assertNull(g.store.map[key])
        assertTrue(g.alarms.alarms.single().enabled)
    }

    @Test
    fun `rings when the user did not walk`() {
        assertNotNull(ring(Graph(alarm, ringAt), 1_000, 1_005))
    }

    @Test
    fun `rings without a baseline, without permission, without a reading`() {
        assertNotNull(ring(Graph(alarm, ringAt), null, 5_000))
        val noPermission = Graph(alarm, ringAt).also { it.sensor.permission = false }
        assertNotNull(ring(noPermission, 1_000, 5_000))
        assertNotNull(ring(Graph(alarm, ringAt), 1_000, null))
    }

    @Test
    fun `rings when the option is off even with many steps`() {
        assertNotNull(ring(Graph(alarm.copy(skipIfAwake = SkipIfAwake.OFF), ringAt), 1_000, 9_000))
    }

    @Test
    fun `a skipped one-shot alarm is consumed`() {
        val g = Graph(alarm.copy(days = emptySet()), ringAt)
        assertNull(ring(g, 1_000, 1_100))
        assertFalse(g.alarms.alarms.single().enabled)
    }

    @Test
    fun `event alarms are never concerned`() = runBlocking {
        val g = Graph(alarm, ringAt)
        g.sensor.steps = 5_000
        g.record(RingKey(RingKind.EVENT, 1))
        assertTrue(g.store.map.isEmpty())
    }

    // ---- scope: only on holidays and days off

    private val holiday = ringAt.toLocalDate()
    private val holidayRing = ringAt.withHour(9)
    private val worker = alarm.copy(
        skipIfAwake = SkipIfAwake.DAYS_OFF_ONLY,
        dayOffMode = DayOffMode.OTHER_TIME,
        dayOffTime = LocalTime.of(9, 0),
    )

    @Test
    fun `days-off-only skips the holiday ring when the user walked`() {
        val g = Graph(worker, ringAt.minusHours(8), leaveDay = holiday)
        assertNull(ring(g, 1_000, 1_100, at = holidayRing))
    }

    @Test
    fun `days-off-only never skips the ring of an ordinary day`() {
        val g = Graph(worker, ringAt, leaveDay = null)
        assertNotNull(ring(g, 1_000, 9_000))
    }

    @Test
    fun `days-off-only schedules a step check only for the holiday ring`() = runBlocking {
        val holidayGraph = Graph(worker, ringAt.minusHours(8), leaveDay = holiday)
        holidayGraph.reschedule()
        assertEquals(holidayRing, holidayGraph.scheduler.rings[key])
        assertEquals(holidayRing.minusMinutes(30), holidayGraph.scheduler.preChecks[key])

        val ordinary = Graph(worker, ringAt.minusHours(8))
        ordinary.reschedule()
        assertEquals(ringAt, ordinary.scheduler.rings[key])
        assertNull(ordinary.scheduler.preChecks[key])
    }

    @Test
    fun `days-off-only also covers an alarm that ignores the calendar`() {
        val ignoring = worker.copy(dayOffMode = DayOffMode.IGNORE)
        val g = Graph(ignoring, ringAt, leaveDay = holiday)
        assertNull(ring(g, 1_000, 1_100))
    }
}
