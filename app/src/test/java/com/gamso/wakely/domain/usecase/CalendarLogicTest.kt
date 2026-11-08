package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.EventRef
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarLogicTest {

    private fun event(
        src: String,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Boolean = false,
        desc: String = "",
    ) = CalendarEvent(src, title, desc, start, end, allDay)

    private val nov10 = LocalDate.of(2026, 11, 10)

    @Test
    fun `days off per category with keyword filter`() {
        val events = listOf(
            event(
                "h",
                "Armistice",
                nov10.plusDays(1).atStartOfDay(),
                nov10.plusDays(2).atStartOfDay(),
                true,
                "Jour férié",
            ),
            event(
                "h",
                "Fête des pères",
                nov10.plusDays(8).atStartOfDay(),
                nov10.plusDays(9).atStartOfDay(),
                true,
                "Fête",
            ),
            event("l", "Vacances", nov10.plusDays(20).atStartOfDay(), nov10.plusDays(23).atStartOfDay(), true),
        )
        val filters = listOf(
            CalendarFilter("h", CalendarCategory.HOLIDAYS, "ferie"),
            CalendarFilter("l", CalendarCategory.LEAVE),
        )
        val off = CalendarLogic.daysOff(events, filters, nov10, nov10.plusDays(60))
        assertEquals(
            listOf(nov10.plusDays(1), nov10.plusDays(20), nov10.plusDays(21), nov10.plusDays(22)),
            off.keys.toList(),
        )
        assertEquals(CalendarCategory.LEAVE, off[nov10.plusDays(20)]!!.category)
    }

    @Test
    fun `event rule rings minutes before first event of the day`() {
        val events = listOf(
            event("w", "Réunion", nov10.atTime(9, 0), nov10.atTime(10, 0)),
            event("w", "Déjeuner", nov10.atTime(12, 0), nov10.atTime(13, 0)),
            event("w", "Client", nov10.plusDays(1).atTime(8, 30), nov10.plusDays(1).atTime(9, 0)),
            event("w", "Congés", nov10.plusDays(2).atStartOfDay(), nov10.plusDays(3).atStartOfDay(), allDay = true),
        )
        val filters = listOf(CalendarFilter("w", CalendarCategory.EVENTS))
        val rule = EventAlarm(id = 1, minutesBefore = 90)
        val rings = CalendarLogic.upcomingEventRings(rule, nov10.atStartOfDay(), events, filters)
        assertEquals(listOf(nov10.atTime(7, 30), nov10.plusDays(1).atTime(7, 0)), rings.map { it.at })
    }

    @Test
    fun `rule keywords and single event`() {
        val events = listOf(
            event("w", "Réunion équipe", nov10.atTime(9, 0), nov10.atTime(10, 0)),
            event("w", "Dentiste", nov10.atTime(14, 0), nov10.atTime(15, 0)),
            event("w", "Dentiste", nov10.plusDays(7).atTime(14, 0), nov10.plusDays(7).atTime(15, 0)),
        )
        val filters = listOf(CalendarFilter("w", CalendarCategory.EVENTS))
        val byKeyword = EventAlarm(id = 2, minutesBefore = 30, keywords = "dentiste", firstEventOfDayOnly = false)
        assertEquals(
            listOf(nov10.atTime(13, 30), nov10.plusDays(7).atTime(13, 30)),
            CalendarLogic.upcomingEventRings(byKeyword, nov10.atStartOfDay(), events, filters).map { it.at },
        )
        val single = byKeyword.copy(
            keywords = "",
            singleEvent = EventRef("w", "Dentiste", nov10.plusDays(7).atTime(14, 0)),
        )
        assertEquals(
            listOf(nov10.plusDays(7).atTime(13, 30)),
            CalendarLogic.upcomingEventRings(single, nov10.atStartOfDay(), events, filters).map { it.at },
        )
        assertNull(CalendarLogic.nextEventRing(single, nov10.plusDays(8).atStartOfDay(), events, filters))
    }
}
