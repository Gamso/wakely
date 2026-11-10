package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeavePreviewTest {

    private val from = LocalDate.of(2026, 11, 9)

    private fun allDay(title: String, first: LocalDate, last: LocalDate = first, source: String = "l") =
        CalendarEvent(source, title, "", first.atStartOfDay(), last.plusDays(1).atStartOfDay(), true)

    private fun timed(title: String, start: LocalDateTime, end: LocalDateTime) =
        CalendarEvent("l", title, "", start, end, false)

    private fun preview(events: List<CalendarEvent>, keywords: String = "") = runBlocking {
        GetLeavePreviewUseCase(
            FakeCalendar(events),
        )(listOf(CalendarFilter("l", CalendarCategory.LEAVE, keywords)), from)
    }

    private val leave = allDay("Congé annuel", LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27))
    private val birthday = allDay("Anniversaire de Paul", LocalDate.of(2026, 12, 2))

    @Test
    fun `without keywords every all-day event counts, for each day it covers`() {
        val result = preview(listOf(leave, birthday))
        assertEquals(listOf("Congé annuel", "Anniversaire de Paul"), result.counted.map { it.title })
        assertEquals(6, result.days)
        assertEquals(LocalDate.of(2026, 11, 27), result.counted.first().last)
        assertTrue(result.ignored.isEmpty())
    }

    @Test
    fun `keywords keep the leave and show what was left out`() {
        val result = preview(listOf(leave, birthday), keywords = "congé, rtt")
        assertEquals(listOf("Congé annuel"), result.counted.map { it.title })
        assertEquals(listOf("Anniversaire de Paul"), result.ignored.map { it.title })
        assertEquals(5, result.days)
    }

    @Test
    fun `keywords ignore case and accents`() {
        assertEquals(1, preview(listOf(leave), keywords = "CONGE").counted.size)
    }

    @Test
    fun `a meeting with a time does not give a day off`() {
        val meeting =
            timed("Réunion budget", LocalDateTime.of(2026, 11, 26, 14, 0), LocalDateTime.of(2026, 11, 26, 15, 0))
        val result = preview(listOf(meeting))
        assertTrue(result.counted.isEmpty())
        assertEquals(listOf("Réunion budget"), result.ignored.map { it.title })
        assertEquals(0, result.days)
    }

    @Test
    fun `a leave entered from midnight to midnight counts`() {
        val day = timed("Congé", LocalDateTime.of(2026, 11, 26, 0, 0), LocalDateTime.of(2026, 11, 27, 0, 0))
        assertEquals(1, preview(listOf(day)).days)
    }

    @Test
    fun `it counts the same days as the real days off`() {
        val events = listOf(leave, birthday)
        val filters = listOf(CalendarFilter("l", CalendarCategory.LEAVE, "congé"))
        val real = CalendarLogic.daysOff(events, filters, from, from.plusDays(365)).size
        assertEquals(real, preview(events, keywords = "congé").days)
    }

    @Test
    fun `no leave calendar gives nothing`() = runBlocking {
        val result = GetLeavePreviewUseCase(FakeCalendar(listOf(leave)))(
            listOf(CalendarFilter("l", CalendarCategory.EVENTS)),
            from,
        )
        assertEquals(LeavePreview(emptyList(), emptyList(), 0), result)
    }

    @Test
    fun `events before today are not shown`() {
        val old = allDay("Congé passé", LocalDate.of(2026, 1, 5))
        assertEquals(0, preview(listOf(old)).days)
    }
}
