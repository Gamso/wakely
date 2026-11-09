package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.HolidayRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import java.time.LocalDate

/**
 * Loads days off: the public holidays of the country and region (minus the ones the user works on), and the leave
 * calendars. On error or missing permission, a source gives nothing: an alarm that rings is better than a missed one.
 */
class GetDaysOffUseCase(
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository,
    private val dayOffSources: DayOffSources,
) {
    suspend operator fun invoke(
        from: LocalDate,
        days: Int = ComputeNextRingUseCase.HORIZON_DAYS,
        filters: List<CalendarFilter>? = null,
    ): Map<LocalDate, DayOff> {
        val resolved = dayOffSources.resolve(filters)
        val to = from.plusDays(days.toLong())
        val result = sortedMapOf<LocalDate, DayOff>()

        resolved.country?.let { country ->
            runCatching { holidayRepository.holidays(country, resolved.region, from, to) }
                .getOrDefault(emptyList())
                .filter { it.id !in resolved.excludedIds }
                .forEach { result.putIfAbsent(it.date, DayOff(it.date, it.name)) }
        }

        val leave = resolved.filters.filter { it.category.isDayOff }
        if (leave.isNotEmpty()) {
            val events = runCatching {
                calendarRepository.getEvents(leave.map { it.sourceKey }.toSet(), from, to)
            }.getOrDefault(emptyList())
            CalendarLogic.daysOff(events, leave, from, to).forEach { (date, off) -> result.putIfAbsent(date, off) }
        }
        return result
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
