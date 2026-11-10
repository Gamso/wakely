package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.EventAlarm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Events" calendars are the pool the event alarms choose from. */
class EventCalendarPoolTest {

    private fun events(key: String, keywords: String = "") = CalendarFilter(key, CalendarCategory.EVENTS, keywords)

    private val pool = listOf(events("work"), events("perso"))

    @Test
    fun `an alarm without a choice watches the whole pool`() {
        assertEquals(setOf("work", "perso"), CalendarLogic.ruleSources(EventAlarm(), pool))
    }

    @Test
    fun `an alarm watches its own choice within the pool`() {
        assertEquals(setOf("work"), CalendarLogic.ruleSources(EventAlarm(sourceKeys = setOf("work")), pool))
    }

    @Test
    fun `an alarm never watches a calendar that left the pool`() {
        val rule = EventAlarm(sourceKeys = setOf("work", "old"))
        assertEquals(setOf("work"), CalendarLogic.ruleSources(rule, pool))
        assertTrue(CalendarLogic.ruleSources(EventAlarm(sourceKeys = setOf("old")), pool).isEmpty())
    }

    @Test
    fun `used by counts the alarms that watch the calendar, explicitly or by default`() {
        val rules = listOf(
            EventAlarm(id = 1, sourceKeys = setOf("work")),
            EventAlarm(id = 2),
            EventAlarm(id = 3, sourceKeys = setOf("perso")),
        )
        assertEquals(listOf(1L, 2L), CalendarLogic.rulesUsing("work", rules, pool).map { it.id })
        assertEquals(listOf(2L, 3L), CalendarLogic.rulesUsing("perso", rules, pool).map { it.id })
        assertTrue(CalendarLogic.rulesUsing("other", rules, pool).isEmpty())
    }

    @Test
    fun `forgetting a calendar removes it from the alarms that chose it only`() = runBlocking {
        val repo = FakeEventAlarms(
            EventAlarm(id = 1, sourceKeys = setOf("work", "perso")),
            EventAlarm(id = 2, sourceKeys = setOf("perso")),
            EventAlarm(id = 3),
        )
        ForgetEventCalendarUseCase(repo)("work")
        assertEquals(setOf("perso"), repo.rules.first { it.id == 1L }.sourceKeys)
        assertEquals(setOf("perso"), repo.rules.first { it.id == 2L }.sourceKeys)
        assertTrue(repo.rules.first { it.id == 3L }.sourceKeys.isEmpty())
    }

    @Test
    fun `an alarm left with no calendar of its own goes back to the default`() = runBlocking {
        val repo = FakeEventAlarms(EventAlarm(id = 1, sourceKeys = setOf("work")))
        ForgetEventCalendarUseCase(repo)("work")
        assertTrue(repo.rules.single().sourceKeys.isEmpty())
        assertEquals(setOf("perso"), CalendarLogic.ruleSources(repo.rules.single(), listOf(events("perso"))))
    }

    @Test
    fun `the migration adds to the pool the calendars alarms watch outside it`() = runBlocking {
        val settings =
            FakeSettings(listOf(events("work", "meeting"), CalendarFilter("l", CalendarCategory.LEAVE, "conge")))
        val repo = FakeEventAlarms(EventAlarm(id = 1, sourceKeys = setOf("work", "perso")))
        MigrateEventCalendarsUseCase(settings, repo)()
        val filters = settings.getCalendarFilters()
        assertEquals(CalendarFilter("perso", CalendarCategory.EVENTS), filters.first { it.sourceKey == "perso" })
        // The existing calendars keep their keywords.
        assertEquals("meeting", filters.first { it.sourceKey == "work" }.keywords)
        assertEquals(3, filters.size)
    }

    @Test
    fun `the migration changes nothing when every alarm calendar is in the pool`() = runBlocking {
        val settings = FakeSettings(listOf(events("work")))
        MigrateEventCalendarsUseCase(settings, FakeEventAlarms(EventAlarm(id = 1, sourceKeys = setOf("work"))))()
        assertEquals(listOf(events("work")), settings.getCalendarFilters())
        assertEquals(Alarm.DEFAULT_SNOOZE_MINUTES, EventAlarm().snoozeMinutes)
    }
}
