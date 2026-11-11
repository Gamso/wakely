package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPreviewTest {

    // Wednesday 11 November 2026, 20:00.
    private val now = LocalDateTime.of(2026, 11, 11, 20, 0)
    private val day = LocalDate.of(2026, 11, 12)

    private fun timed(title: String, hour: Int, date: LocalDate = day) =
        CalendarEvent("c", title, "", date.atTime(hour, 0), date.atTime(hour + 1, 0), false)

    private fun preview(events: List<CalendarEvent>, keywords: String = "") =
        runBlocking { GetEventPreviewUseCase(FakeCalendar(events))("c", keywords, now) }

    private val antiPuce = timed("Anti puce", 17)
    private val meeting = timed("Réunion", 9)

    @Test
    fun `without keywords every timed event counts, in date order`() {
        val result = preview(listOf(antiPuce, meeting))
        assertEquals(listOf("Réunion", "Anti puce"), result.counted.map { it.title })
        assertTrue(result.ignored.isEmpty())
    }

    @Test
    fun `keywords keep the matching events and show the others as ignored`() {
        val result = preview(listOf(antiPuce, meeting), keywords = "vermifuge, anti puce")
        assertEquals(listOf("Anti puce"), result.counted.map { it.title })
        assertEquals(listOf("Réunion"), result.ignored.map { it.title })
    }

    @Test
    fun `keywords ignore case and accents`() {
        assertEquals(1, preview(listOf(timed("Réunion", 9)), keywords = "REUNION").counted.size)
    }

    @Test
    fun `all-day events are never listed`() {
        val allDay = CalendarEvent("c", "Vacances", "", day.atStartOfDay(), day.plusDays(1).atStartOfDay(), true)
        val result = preview(listOf(allDay))
        assertTrue(result.counted.isEmpty() && result.ignored.isEmpty())
    }

    @Test
    fun `events that already started are not upcoming`() {
        val past = timed("Hier", 9, date = LocalDate.of(2026, 11, 10))
        val earlierToday = CalendarEvent("c", "Ce matin", "", now.minusHours(10), now.minusHours(9), false)
        assertTrue(preview(listOf(past, earlierToday)).counted.isEmpty())
    }

    @Test
    fun `no event gives an empty preview`() {
        val result = preview(emptyList())
        assertEquals(EventPreview(emptyList(), emptyList()), result)
    }
}
