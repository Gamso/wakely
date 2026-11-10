package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkipIfAwake
import com.gamso.wakely.domain.model.TimeRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A leave entered with a time replaces the rings it covers, alarm by alarm. */
class HalfDayLeaveTest {

    // Tuesday 10 November 2026. The evening before, 20:00.
    private val day = LocalDate.of(2026, 11, 10)
    private val evening = LocalDateTime.of(2026, 11, 9, 20, 0)
    private val compute = ComputeNextRingUseCase()

    private fun leave(startHour: Int, endHour: Int, title: String = "Conge") = CalendarEvent(
        "l",
        title,
        "",
        day.atTime(startHour, 0),
        day.atTime(endHour, 0),
        false,
    )

    private fun filters(keywords: String) = listOf(CalendarFilter("l", CalendarCategory.LEAVE, keywords))

    private fun daysOff(event: CalendarEvent, keywords: String = "conge") =
        CalendarLogic.daysOff(listOf(event), filters(keywords), day.minusDays(2), day.plusDays(2))

    // Working-day alarm at 07:30, ringing at 08:30 on days off.
    private val other = Alarm(
        id = 1,
        time = LocalTime.of(7, 30),
        dayOffMode = DayOffMode.OTHER_TIME,
        dayOffTime = LocalTime.of(8, 30),
    )
    private val silent = Alarm(id = 2, time = LocalTime.of(7, 30), dayOffMode = DayOffMode.SKIP)

    private fun nextOf(alarm: Alarm, event: CalendarEvent, keywords: String = "conge") =
        compute(alarm, evening, daysOff(event, keywords))!!.at

    @Test
    fun `a morning off replaces the working alarm by the days-off one`() {
        assertEquals(day.atTime(8, 30), nextOf(other, leave(7, 12)))
    }

    @Test
    fun `a morning off silences an alarm that does not ring on days off`() {
        assertEquals(day.plusDays(1).atTime(7, 30), nextOf(silent, leave(7, 12)))
    }

    @Test
    fun `an afternoon off leaves the morning alarm as usual`() {
        assertEquals(day.atTime(7, 30), nextOf(other, leave(13, 17)))
    }

    @Test
    fun `a leave that has not started at the alarm time does not replace it`() {
        assertEquals(day.atTime(7, 30), nextOf(other, leave(8, 12)))
    }

    @Test
    fun `the ring of the working day and the one of the day off are decided per alarm`() {
        val event = leave(7, 12)
        val early = other.copy(id = 3, time = LocalTime.of(6, 0))
        // 06:00 is before the leave: it rings. 07:30 is inside it: replaced by 08:30.
        assertEquals(day.atTime(6, 0), nextOf(early, event))
        assertEquals(day.atTime(8, 30), nextOf(other, event))
    }

    @Test
    fun `an alarm that ignores the calendar rings whatever the leave`() {
        assertEquals(day.atTime(7, 30), nextOf(other.copy(dayOffMode = DayOffMode.IGNORE), leave(7, 12)))
    }

    @Test
    fun `a timed event without keywords never cancels an alarm`() {
        // Calendar without keyword, and a meeting from 07:00 to 08:30.
        assertEquals(day.atTime(7, 30), nextOf(other, leave(7, 9, "Reunion"), keywords = ""))
        assertTrue(daysOff(leave(7, 9, "Reunion"), keywords = "").isEmpty())
    }

    @Test
    fun `a timed event that does not match the keywords never cancels an alarm`() {
        assertEquals(day.atTime(7, 30), nextOf(other, leave(7, 9, "Reunion"), keywords = "conge, rtt"))
    }

    @Test
    fun `whole-day leave behaves as before`() {
        val allDay = CalendarEvent("l", "Conge", "", day.atStartOfDay(), day.plusDays(1).atStartOfDay(), true)
        assertEquals(day.atTime(8, 30), nextOf(other, allDay, keywords = ""))
        assertEquals(day.plusDays(1).atTime(7, 30), nextOf(silent, allDay, keywords = ""))
    }

    @Test
    fun `a leave from midnight to midnight is a whole day with or without keywords`() {
        val midnight = CalendarEvent("l", "Conge", "", day.atStartOfDay(), day.plusDays(1).atStartOfDay(), false)
        assertTrue(daysOff(midnight, keywords = "").getValue(day).ranges.isEmpty())
    }

    @Test
    fun `a leave over several days gives part of the first and last days and whole days between`() {
        val event = CalendarEvent("l", "Conge", "", day.atTime(14, 0), day.plusDays(2).atTime(12, 0), false)
        val result = daysOff(event)
        assertEquals(listOf(TimeRange(LocalTime.of(14, 0), LocalTime.MAX)), result.getValue(day).ranges)
        // The day in between is covered entirely: a whole day off.
        assertTrue(result.getValue(day.plusDays(1)).ranges.isEmpty())
        assertEquals(listOf(TimeRange(LocalTime.MIN, LocalTime.of(12, 0))), result.getValue(day.plusDays(2)).ranges)
    }

    @Test
    fun `a whole day wins over a partial one on the same date`() {
        val partial = leave(7, 12)
        val whole = CalendarEvent("l", "Vacances", "", day.atStartOfDay(), day.plusDays(1).atStartOfDay(), true)
        val result = CalendarLogic.daysOff(listOf(partial, whole), filters(""), day.minusDays(1), day.plusDays(1))
        assertTrue(result.getValue(day).ranges.isEmpty())
    }

    @Test
    fun `partial ranges of the same day add up`() {
        val result = CalendarLogic.daysOff(
            listOf(leave(7, 9), leave(13, 17)),
            filters("conge"),
            day.minusDays(1),
            day.plusDays(1),
        )
        assertEquals(2, result.getValue(day).ranges.size)
    }

    @Test
    fun `covers is true all day without ranges and only inside a range otherwise`() {
        val whole = DayOff(day, "x")
        assertTrue(whole.covers(LocalTime.of(3, 0)))
        val morning = DayOff(day, "x", listOf(TimeRange(LocalTime.of(7, 0), LocalTime.of(12, 0))))
        assertTrue(morning.covers(LocalTime.of(7, 0)))
        assertFalse(morning.covers(LocalTime.of(12, 0)))
        assertFalse(morning.covers(LocalTime.of(6, 59)))
    }

    @Test
    fun `the preview shows a partial leave with its hours and counts its day`() = runBlocking {
        val result = GetLeavePreviewUseCase(FakeCalendar(listOf(leave(7, 12))))(filters("conge"), day.minusDays(1))
        assertEquals(1, result.days)
        val item = result.counted.single()
        assertEquals(LocalTime.of(7, 0), item.from)
        assertEquals(LocalTime.of(12, 0), item.to)
    }

    @Test
    fun `the preview ignores a timed event without keywords`() = runBlocking {
        val result = GetLeavePreviewUseCase(FakeCalendar(listOf(leave(7, 9, "Reunion"))))(filters(""), day.minusDays(1))
        assertEquals(0, result.days)
        assertEquals(1, result.ignored.size)
    }

    @Test
    fun `the skip-if-awake scope follows the partial day off`() {
        val alarm = other.copy(skipIfAwake = SkipIfAwake.DAYS_OFF_ONLY)
        val morning = daysOff(leave(7, 12))
        assertTrue(AwakeCheck.appliesTo(alarm, day, morning))
        assertFalse(AwakeCheck.appliesTo(alarm, day, daysOff(leave(13, 17))))
        assertEquals(RingKey(RingKind.ALARM, 1), RingKey(RingKind.ALARM, alarm.id))
    }
}
