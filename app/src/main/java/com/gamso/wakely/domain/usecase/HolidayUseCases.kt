package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.holiday.HolidayConfig
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.HolidayItem
import com.gamso.wakely.domain.model.HolidaySettings
import com.gamso.wakely.domain.repository.DeviceRegion
import com.gamso.wakely.domain.repository.HolidayRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import java.time.LocalDate

/** Everything that decides the days off: the leave calendars, and the public holidays the user works on. */
class DayOffSources(
    private val settingsRepository: SettingsRepository,
    private val deviceRegion: DeviceRegion,
    private val holidayRepository: HolidayRepository,
) {
    /** [country] and [region] locate the public holidays; [excludedIds] are the ones the user works on. */
    data class Resolved(
        val filters: List<CalendarFilter>,
        val country: String?,
        val region: String?,
        val excludedIds: Set<String>,
    )

    /** [filters] overrides the saved calendar filters. */
    suspend fun resolve(filters: List<CalendarFilter>? = null): Resolved {
        val holiday = settingsRepository.getHolidaySettings()
        val country = HolidayConfig.effectiveCountry(
            holiday,
            deviceRegion.countryCandidates(),
            holidayRepository.countries(),
        )
        return Resolved(
            filters = filters ?: settingsRepository.getCalendarFilters(),
            country = country,
            region = holiday.region.takeIf { country != null },
            excludedIds = holiday.excludedIds,
        )
    }
}

/** Upcoming public holidays with whether the user works on each, for the Settings screen. */
class GetUpcomingHolidaysUseCase(
    private val holidayRepository: HolidayRepository,
    private val dayOffSources: DayOffSources,
) {
    suspend operator fun invoke(from: LocalDate, days: Int = DEFAULT_DAYS): List<HolidayItem> {
        val resolved = dayOffSources.resolve()
        val country = resolved.country ?: return emptyList()
        return runCatching {
            holidayRepository.holidays(country, resolved.region, from, from.plusDays(days.toLong()))
        }.getOrDefault(emptyList())
            .map { HolidayItem(it.date, it.id, it.name, excluded = it.id in resolved.excludedIds) }
    }

    companion object {
        const val DEFAULT_DAYS = 365
    }
}

class UpdateHolidaySettingsUseCase(
    private val settingsRepository: SettingsRepository,
    private val reschedule: RescheduleAlarmsUseCase,
) {
    suspend operator fun invoke(settings: HolidaySettings) {
        settingsRepository.setHolidaySettings(settings)
        reschedule()
    }
}
