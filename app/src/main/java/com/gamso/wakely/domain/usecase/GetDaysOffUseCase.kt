package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import java.time.LocalDate

/**
 * Loads days off. On error or missing permission, returns an empty map:
 * an alarm that rings is better than a missed one.
 */
class GetDaysOffUseCase(
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        from: LocalDate,
        days: Int = ComputeNextRingUseCase.HORIZON_DAYS,
        filters: List<CalendarFilter>? = null,
    ): Map<LocalDate, DayOff> {
        val f = (filters ?: settingsRepository.getCalendarFilters()).filter { it.category.isDayOff }
        if (f.isEmpty()) return emptyMap()
        val to = from.plusDays(days.toLong())
        val events = runCatching {
            calendarRepository.getEvents(f.map { it.sourceKey }.toSet(), from, to)
        }.getOrDefault(emptyList())
        return CalendarLogic.daysOff(events, f, from, to)
    }
}

/** Loads the events watched by event rules ("events" calendars and rule-specific ones). */
class GetRuleEventsUseCase(
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
) {
    data class Result(val events: List<CalendarEvent>, val eventFilters: List<CalendarFilter>)

    suspend operator fun invoke(
        from: LocalDate,
        days: Int = EVENT_HORIZON_DAYS,
        extraSourceKeys: Set<String> = emptySet(),
    ): Result {
        val eventFilters = settingsRepository.getCalendarFilters()
            .filter { it.category == com.gamso.wakely.domain.model.CalendarCategory.EVENTS }
        val keys = eventFilters.map { it.sourceKey }.toSet() + extraSourceKeys
        if (keys.isEmpty()) return Result(emptyList(), eventFilters)
        val events = runCatching {
            calendarRepository.getEvents(keys, from, from.plusDays(days.toLong()))
        }.getOrDefault(emptyList())
        return Result(events, eventFilters)
    }

    companion object {
        const val EVENT_HORIZON_DAYS = 60
    }
}
