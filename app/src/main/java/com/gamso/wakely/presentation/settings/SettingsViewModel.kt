package com.gamso.wakely.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.domain.holiday.HolidayConfig
import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.HolidayItem
import com.gamso.wakely.domain.model.HolidaySettings
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.DeviceRegion
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.HolidayRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.usecase.CalendarLogic
import com.gamso.wakely.domain.usecase.ForgetEventCalendarUseCase
import com.gamso.wakely.domain.usecase.GetLeavePreviewUseCase
import com.gamso.wakely.domain.usecase.GetUpcomingHolidaysUseCase
import com.gamso.wakely.domain.usecase.LeavePreview
import com.gamso.wakely.domain.usecase.UpdateCalendarFiltersUseCase
import com.gamso.wakely.domain.usecase.UpdateHolidaySettingsUseCase
import com.gamso.wakely.domain.usecase.UpdateSummarySettingsUseCase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Settings state combines from several flows beyond the main settings. */
private data class Extras(
    val upcomingHolidays: List<HolidayItem>?,
    val leave: LeavePreview?,
    val calendar: LeavePreview?,
    val rules: List<EventAlarm>,
)

/** A calendar assigned to a category, with its keyword filter. */
data class AssignedCalendar(val calendar: DeviceCalendar, val keywords: String)

data class SettingsUiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = true,
    val summary: SummarySettings = SummarySettings(),
    val skipButtons: Boolean = true,
    /** The days off the leave calendars give with their keywords; null while loading. */
    val leavePreview: LeavePreview? = null,
    val pauseButton: Boolean = true,
    val calendars: List<DeviceCalendar> = emptyList(),
    val byCategory: Map<CalendarCategory, List<AssignedCalendar>> = emptyMap(),
    /** Country whose public holidays apply (chosen, or detected from the device); null = none. */
    val holidayCountry: String? = null,
    /** Region of that country with holidays of its own (e.g. Alsace-Moselle); null = the whole country. */
    val holidayRegion: HolidayRegion? = null,
    /** Regions of the country that have holidays of their own; empty for most countries. */
    val holidayRegions: List<HolidayRegion> = emptyList(),
    /** True while the country follows the device (the user has not chosen one). */
    val holidayCountryAuto: Boolean = true,
    /** Supported country detected from the device, whether or not it is the one in use. */
    val holidayDetectedCountry: String? = null,
    val supportedCountries: List<String> = emptyList(),
    /** Upcoming public holidays; null while loading. */
    val upcomingHolidays: List<HolidayItem>? = null,
    /** Event alarms that use each calendar of the pool, by calendar key. */
    val eventAlarmUse: Map<String, Int> = emptyMap(),
    /** The days off the calendar open in Settings gives with its keywords; null while loading. */
    val calendarPreview: LeavePreview? = null,
) {
    fun usedBy(calendar: DeviceCalendar): Int = eventAlarmUse[calendar.key] ?: 0

    fun isLeave(calendar: DeviceCalendar) = byCategory[CalendarCategory.LEAVE].orEmpty().any { it.calendar == calendar }

    fun isEvents(calendar: DeviceCalendar) = byCategory[CalendarCategory.EVENTS].orEmpty().any {
        it.calendar == calendar
    }

    fun assigned(calendar: DeviceCalendar, category: CalendarCategory): AssignedCalendar? =
        byCategory[category].orEmpty().firstOrNull { it.calendar == calendar }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository,
    private val settingsRepository: SettingsRepository,
    private val updateCalendarFilters: UpdateCalendarFiltersUseCase,
    private val updateSummarySettings: UpdateSummarySettingsUseCase,
    private val getUpcomingHolidays: GetUpcomingHolidaysUseCase,
    private val getLeavePreview: GetLeavePreviewUseCase,
    private val eventAlarmRepository: EventAlarmRepository,
    private val forgetEventCalendar: ForgetEventCalendarUseCase,
    private val updateHolidaySettings: UpdateHolidaySettingsUseCase,
    private val deviceRegion: DeviceRegion,
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val calendars = MutableStateFlow<List<DeviceCalendar>?>(null)
    private val draft = MutableStateFlow<List<CalendarFilter>?>(null)
    private var persistJob: Job? = null

    private val upcoming = settingsRepository.observeHolidaySettings()
        .mapLatest<HolidaySettings, List<HolidayItem>?> { getUpcomingHolidays(LocalDate.now()) }
        .onStart { emit(null) }

    // The preview follows the keywords as they are typed, before they are saved.
    private val openCalendar = MutableStateFlow<String?>(null)

    // The preview of the calendar open on its own screen, with only its own keywords.
    private val calendarPreview = combine(draft.filterNotNull(), openCalendar) { filters, key -> filters to key }
        .debounce(PREVIEW_DEBOUNCE_MS)
        .mapLatest<Pair<List<CalendarFilter>, String?>, LeavePreview?> { (filters, key) ->
            key?.let { getLeavePreview(filters.filter { f -> f.sourceKey == it }, LocalDate.now()) }
        }
        .onStart { emit(null) }

    private val leavePreview = draft.filterNotNull()
        .debounce(PREVIEW_DEBOUNCE_MS)
        .mapLatest<List<CalendarFilter>, LeavePreview?> { getLeavePreview(it, LocalDate.now()) }
        .onStart { emit(null) }

    val uiState: StateFlow<SettingsUiState> = combine(
        calendars,
        draft,
        combine(
            settingsRepository.observeSummarySettings(),
            settingsRepository.observeSkipButtons(),
            settingsRepository.observePauseButton(),
        ) { summary, skip, pause -> Triple(summary, skip, pause) },
        settingsRepository.observeHolidaySettings(),
        combine(upcoming, leavePreview, calendarPreview, eventAlarmRepository.observeEventAlarms()) { h, l, c, r ->
            Extras(h, l, c, r)
        },
    ) { cals, filters, (summary, skipButtons, pauseButton), holiday, extras ->
        if (cals == null ||
            filters == null
        ) {
            return@combine SettingsUiState(summary = summary, skipButtons = skipButtons, pauseButton = pauseButton)
        }
        val byKey = cals.associateBy { it.key }
        val supported = holidayRepository.countries()
        val candidates = deviceRegion.countryCandidates()
        val country = HolidayConfig.effectiveCountry(holiday, candidates, supported)
        val regions = country?.let { holidayRepository.regions(it) }.orEmpty()
        SettingsUiState(
            loading = false,
            hasPermission = calendarRepository.hasPermission(),
            summary = summary,
            skipButtons = skipButtons,
            pauseButton = pauseButton,
            calendars = cals,
            byCategory = CalendarCategory.entries.associateWith { category ->
                filters.filter { it.category == category }
                    .mapNotNull { f -> byKey[f.sourceKey]?.let { AssignedCalendar(it, f.keywords) } }
            },
            holidayCountry = country,
            holidayRegion = regions.firstOrNull { it.path == holiday.region },
            holidayRegions = regions,
            holidayCountryAuto = !holiday.countryChosen,
            holidayDetectedCountry = candidates.map { it.uppercase() }.firstOrNull { it in supported },
            supportedCountries = supported,
            upcomingHolidays = extras.upcomingHolidays,
            leavePreview = extras.leave,
            calendarPreview = extras.calendar,
            eventAlarmUse = filters.filter { it.category == CalendarCategory.EVENTS }.associate { pool ->
                pool.sourceKey to CalendarLogic.rulesUsing(pool.sourceKey, extras.rules, filters).size
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun reload() {
        viewModelScope.launch {
            draft.value = settingsRepository.getCalendarFilters()
            calendars.value = calendarRepository.getCalendars()
        }
    }

    fun setPauseButton(enabled: Boolean) {
        applicationScope.launch { settingsRepository.setPauseButton(enabled) }
    }

    fun setSkipButtons(enabled: Boolean) {
        applicationScope.launch { settingsRepository.setSkipButtons(enabled) }
    }

    fun setSummaryEnabled(enabled: Boolean) = updateSummary { it.copy(enabled = enabled) }

    fun setSummaryTime(time: java.time.LocalTime) = updateSummary { it.copy(time = time) }

    private fun updateSummary(block: (SummarySettings) -> SummarySettings) {
        applicationScope.launch {
            updateSummarySettings(block(settingsRepository.getSummarySettings()))
        }
    }

    /** Chooses the country of the public holidays; null switches them off. The region belongs to a country: reset. */
    fun setHolidayCountry(country: String?) {
        applicationScope.launch {
            updateHolidaySettings(
                settingsRepository.getHolidaySettings().copy(country = country, countryChosen = true, region = null),
            )
        }
    }

    /** Goes back to the country of the device. */
    fun setHolidayCountryAuto() {
        applicationScope.launch {
            updateHolidaySettings(
                settingsRepository.getHolidaySettings().copy(country = null, countryChosen = false, region = null),
            )
        }
    }

    /** Chooses a region with holidays of its own (e.g. "67"); null = the whole country. */
    fun setHolidayRegion(region: String?) {
        applicationScope.launch {
            updateHolidaySettings(settingsRepository.getHolidaySettings().copy(region = region))
        }
    }

    /** Marks a holiday as worked ([excluded] = true) or as a day off again. */
    fun setHolidayExcluded(item: HolidayItem, excluded: Boolean) {
        applicationScope.launch {
            val current = settingsRepository.getHolidaySettings()
            val ids = if (excluded) current.excludedIds + item.id else current.excludedIds - item.id
            updateHolidaySettings(current.copy(excludedIds = ids))
        }
    }

    /** The calendar whose screen is open, so its own preview is computed. */
    fun showCalendar(key: String?) {
        openCalendar.value = key
    }

    fun setLeave(calendar: DeviceCalendar, on: Boolean) =
        if (on) addCalendar(calendar, CalendarCategory.LEAVE) else removeCalendar(calendar, CalendarCategory.LEAVE)

    /**
     * Uses the calendar for the event alarms (it joins their pool), or stops: the alarms that chose it forget it first,
     * then it leaves the pool.
     */
    fun setEvents(calendar: DeviceCalendar, on: Boolean) {
        if (on) {
            addCalendar(calendar, CalendarCategory.EVENTS)
        } else {
            viewModelScope.launch {
                forgetEventCalendar(calendar.key)
                removeCalendar(calendar, CalendarCategory.EVENTS)
            }
        }
    }

    /** Stops every use of the calendar: alarms that watched it forget it. The phone's calendar is not touched. */
    fun removeFromApp(calendar: DeviceCalendar) {
        viewModelScope.launch {
            forgetEventCalendar(calendar.key)
            removeCalendar(calendar, CalendarCategory.LEAVE)
            removeCalendar(calendar, CalendarCategory.EVENTS)
        }
    }

    fun addCalendar(calendar: DeviceCalendar, category: CalendarCategory) = editFilters { filters ->
        filters + CalendarFilter(calendar.key, category)
    }

    fun removeCalendar(calendar: DeviceCalendar, category: CalendarCategory) = editFilters { filters ->
        filters.filterNot { it.sourceKey == calendar.key && it.category == category }
    }

    fun setKeywords(calendar: DeviceCalendar, category: CalendarCategory, keywords: String) = editFilters { filters ->
        filters.map {
            if (it.sourceKey == calendar.key &&
                it.category == category
            ) {
                it.copy(keywords = keywords)
            } else {
                it
            }
        }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 300L
    }

    private fun editFilters(block: (List<CalendarFilter>) -> List<CalendarFilter>) {
        val updated = block(draft.value ?: return)
        draft.value = updated
        // Debounced save + reschedule (typing), outside the screen lifecycle.
        persistJob?.cancel()
        persistJob = applicationScope.launch {
            delay(500)
            updateCalendarFilters(updated)
        }
    }
}
