package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.SettingsRepository

/**
 * Takes a calendar out of the event alarms that chose it. An alarm left with no calendar of its own goes back to the
 * default: every calendar of the pool.
 */
class ForgetEventCalendarUseCase(private val eventAlarmRepository: EventAlarmRepository) {
    suspend operator fun invoke(key: String) {
        eventAlarmRepository.getEventAlarms().filter { key in it.sourceKeys }.forEach {
            eventAlarmRepository.save(it.copy(sourceKeys = it.sourceKeys - key))
        }
    }
}

/**
 * Event alarms used to be able to watch calendars outside the "events" category. That category is now the pool they
 * choose from, so such calendars are added to it (without keywords, like before): nothing stops ringing.
 */
class MigrateEventCalendarsUseCase(
    private val settingsRepository: SettingsRepository,
    private val eventAlarmRepository: EventAlarmRepository,
) {
    suspend operator fun invoke() {
        val filters = settingsRepository.getCalendarFilters()
        val pool = filters.filter { it.category == CalendarCategory.EVENTS }.map { it.sourceKey }.toSet()
        val missing = eventAlarmRepository.getEventAlarms().flatMap { it.sourceKeys }.toSet() - pool
        if (missing.isEmpty()) return
        settingsRepository.setCalendarFilters(
            filters + missing.sorted().map {
                CalendarFilter(it, CalendarCategory.EVENTS)
            },
        )
    }
}
