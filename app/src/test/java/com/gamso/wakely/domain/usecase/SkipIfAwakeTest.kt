package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.AwakeSensitivity
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
import com.gamso.wakely.domain.model.SkippedOccurrence
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

    private val minSteps = AwakeSensitivity.NORMAL.minSteps

    @Test
    fun `skips when enough steps were taken since the baseline`() {
        assertTrue(AwakeCheck.shouldSkip(baseline(), 1_000 + minSteps, ringAt, minSteps))
    }

    @Test
    fun `rings when too few steps`() {
        assertFalse(AwakeCheck.shouldSkip(baseline(), 1_000 + minSteps - 1, ringAt, minSteps))
    }

    @Test
    fun `each level has its own threshold, normal by default`() {
        assertEquals(25L, AwakeSensitivity.SENSITIVE.minSteps)
        assertEquals(100L, AwakeSensitivity.NORMAL.minSteps)
        assertEquals(250L, AwakeSensitivity.STRICT.minSteps)
        assertEquals(AwakeSensitivity.NORMAL, runBlocking { FakeSettings().getAwakeSensitivity() })
    }

    @Test
    fun `rings without a baseline or without a reading`() {
        assertFalse(AwakeCheck.shouldSkip(null, 5_000, ringAt, minSteps))
        assertFalse(AwakeCheck.shouldSkip(baseline(), null, ringAt, minSteps))
    }

    @Test
    fun `rings when the counter went down, after a reboot`() {
        assertFalse(AwakeCheck.shouldSkip(baseline(steps = 90_000), 400, ringAt, minSteps))
    }

    @Test
    fun `rings when the baseline is from another day or after the ring`() {
        assertFalse(AwakeCheck.shouldSkip(StepBaseline(0, ringAt.minusDays(1)), 5_000, ringAt, minSteps))
        assertFalse(AwakeCheck.shouldSkip(StepBaseline(0, ringAt.plusMinutes(1)), 5_000, ringAt, minSteps))
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
    fun `no step check when the last one is already past`() = runBlocking {
        val g = Graph(alarm, ringAt.minusMinutes(4))
        g.reschedule()
        assertNull(g.scheduler.preChecks[key])
        assertNotNull(g.scheduler.rings[key])
    }

    @Test
    fun `the checks go on from the next one of the grid in the middle of the window`() = runBlocking {
        val g = Graph(alarm, ringAt.minusMinutes(12))
        g.reschedule()
        assertEquals(ringAt.minusMinutes(10), g.scheduler.preChecks[key])
        assertEquals(ringAt, g.scheduler.preCheckRings[key])
    }

    @Test
    fun `the checks are every 5 minutes, the last one 5 minutes before the ring`() {
        fun next(minutesBefore: Long) = AwakeCheck.nextCheck(ringAt, ringAt.minusMinutes(minutesBefore))
        assertEquals(ringAt.minusMinutes(30), next(120))
        assertEquals(ringAt.minusMinutes(25), next(30))
        assertEquals(ringAt.minusMinutes(5), next(6))
        assertNull(next(5))
        assertNull(next(0))
    }

    // ---- the step check

    @Test
    fun `the first step check records the count, or nothing without permission`() = runBlocking {
        val g = Graph(alarm, ringAt.minusMinutes(30))
        g.sensor.steps = 1_000
        g.stepCheck(key, ringAt)
        assertEquals(StepBaseline(1_000, ringAt.minusMinutes(30)), g.store.map[key])
        assertEquals(ringAt.minusMinutes(25), g.scheduler.preChecks[key])

        g.sensor.permission = false
        g.stepCheck(key, ringAt)
        assertNull(g.store.map[key])
    }

    private fun check(g: Graph, baselineSteps: Long, currentSteps: Long?, minutesBefore: Long = 15) = runBlocking {
        g.store.save(key, StepBaseline(baselineSteps, ringAt.minusMinutes(30)))
        g.sensor.steps = currentSteps
        g.now = ringAt.minusMinutes(minutesBefore)
        g.stepCheck(key, ringAt)
    }

    @Test
    fun `a check that sees enough steps skips the ring and notifies at once`() {
        val g = Graph(alarm.copy(label = "Work"), ringAt.minusMinutes(30))
        check(g, 1_000, 1_100)
        assertEquals(listOf(FakeAwakeSkipNotifier.Skip("Work", ringAt, 100)), g.awakeSkips.skips)
        assertEquals(ringAt.plusDays(1), g.scheduler.rings[key])
        assertNull(g.store.map[key])
        assertTrue(g.alarms.alarms.single().enabled)
        // The ring time comes: nothing rings, and nothing is notified a second time.
        g.now = ringAt
        assertNull(runBlocking { g.handle(key) })
        assertEquals(1, g.awakeSkips.skips.size)
    }

    @Test
    fun `a check that sees too few steps keeps the reference and plans the next check`() {
        val g = Graph(alarm, ringAt.minusMinutes(30))
        check(g, 1_000, 1_050)
        assertTrue(g.awakeSkips.skips.isEmpty())
        assertTrue(runBlocking { g.skips.get() }.isEmpty())
        assertNotNull(g.store.map[key])
        assertEquals(ringAt.minusMinutes(10), g.scheduler.preChecks[key])
    }

    @Test
    fun `a check never skips without a valid reading or after a reboot`() {
        val noReading = Graph(alarm, ringAt)
        check(noReading, 1_000, null)
        val rebooted = Graph(alarm, ringAt)
        check(rebooted, 90_000, 400)
        listOf(noReading, rebooted).forEach {
            assertTrue(it.awakeSkips.skips.isEmpty())
            assertTrue(runBlocking { it.skips.get() }.isEmpty())
        }
        // After the reboot, the count taken then is the new reference.
        assertEquals(400L, rebooted.store.map[key]?.steps)
    }

    @Test
    fun `a one-shot alarm skipped by a check is turned off`() {
        val g = Graph(alarm.copy(days = emptySet()), ringAt)
        check(g, 1_000, 1_200)
        assertFalse(g.alarms.alarms.single().enabled)
        assertEquals(1, g.awakeSkips.skips.size)
    }

    @Test
    fun `a check does nothing for an alarm turned off, edited or already skipped`() {
        val off = Graph(alarm.copy(skipIfAwake = SkipIfAwake.OFF), ringAt)
        check(off, 1_000, 5_000)
        val disabled = Graph(alarm.copy(enabled = false), ringAt)
        check(disabled, 1_000, 5_000)
        val handSkipped = Graph(alarm, ringAt)
        runBlocking { handSkipped.skips.add(SkippedOccurrence(key, ringAt)) }
        check(handSkipped, 1_000, 5_000)
        listOf(off, disabled, handSkipped).forEach {
            assertTrue(it.awakeSkips.skips.isEmpty())
            assertNull(it.store.map[key])
        }
    }

    @Test
    fun `a check uses the level chosen in the settings`() {
        val strict = graphAt(AwakeSensitivity.STRICT)
        check(strict, 1_000, 1_100)
        assertTrue(strict.awakeSkips.skips.isEmpty())
        check(strict, 1_000, 1_250)
        assertEquals(1, strict.awakeSkips.skips.size)
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
        val g = Graph(alarm, ringAt)
        assertNotNull(ring(g, 1_000, 1_005))
        assertTrue(g.awakeSkips.skips.isEmpty())
    }

    @Test
    fun `a ring skipped because the user walked is notified with its steps`() {
        val g = Graph(alarm.copy(label = "Work"), ringAt)
        assertNull(ring(g, 1_000, 1_142))
        assertEquals(listOf(FakeAwakeSkipNotifier.Skip("Work", ringAt, 142)), g.awakeSkips.skips)
    }

    @Test
    fun `a ring that does not sound for another reason is not notified`() {
        // Option off: the alarm rings, nothing to notify.
        val off = Graph(alarm.copy(skipIfAwake = SkipIfAwake.OFF), ringAt)
        ring(off, 1_000, 9_000)
        assertTrue(off.awakeSkips.skips.isEmpty())
        // Disabled alarm: nothing rings, but not because the user was up.
        val disabled = Graph(alarm.copy(enabled = false), ringAt)
        assertNull(ring(disabled, 1_000, 9_000))
        assertTrue(disabled.awakeSkips.skips.isEmpty())
    }

    @Test
    fun `rings without a baseline, without permission, without a reading`() {
        assertNotNull(ring(Graph(alarm, ringAt), null, 5_000))
        val noPermission = Graph(alarm, ringAt).also { it.sensor.permission = false }
        assertNotNull(ring(noPermission, 1_000, 5_000))
        assertNotNull(ring(Graph(alarm, ringAt), 1_000, null))
    }

    @Test
    fun `the chosen level decides how many steps skip the ring`() {
        // 40 steps: enough when sensitive, not by default.
        assertNull(ring(graphAt(AwakeSensitivity.SENSITIVE), 1_000, 1_040))
        assertNotNull(ring(Graph(alarm, ringAt), 1_000, 1_040))
        // 200 steps: enough by default, not when strict; 250 are.
        assertNull(ring(Graph(alarm, ringAt), 1_000, 1_200))
        assertNotNull(ring(graphAt(AwakeSensitivity.STRICT), 1_000, 1_200))
        assertNull(ring(graphAt(AwakeSensitivity.STRICT), 1_000, 1_250))
    }

    private fun graphAt(sensitivity: AwakeSensitivity) = Graph(alarm, ringAt).also {
        runBlocking { it.settings.setAwakeSensitivity(sensitivity) }
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
        g.stepCheck(RingKey(RingKind.EVENT, 1), ringAt)
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
