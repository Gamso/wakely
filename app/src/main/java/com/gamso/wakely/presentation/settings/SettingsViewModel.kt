package com.gamso.wakely.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.usecase.UpdateCalendarFiltersUseCase
import com.gamso.wakely.domain.usecase.UpdateSummarySettingsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A calendar assigned to a category, with its keyword filter. */
data class AssignedCalendar(val calendar: DeviceCalendar, val keywords: String)

data class SettingsUiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = true,
    val summary: SummarySettings = SummarySettings(),
    val calendars: List<DeviceCalendar> = emptyList(),
    val byCategory: Map<CalendarCategory, List<AssignedCalendar>> = emptyMap(),
) {
    /** Calendars not yet in [category], for the "add" dialog. */
    fun available(category: CalendarCategory): List<DeviceCalendar> {
        val assigned = byCategory[category].orEmpty().map { it.calendar.key }.toSet()
        return calendars.filterNot { it.key in assigned }
    }
}

class SettingsViewModel(
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    private val updateCalendarFilters: UpdateCalendarFiltersUseCase,
    private val updateSummarySettings: UpdateSummarySettingsUseCase,
    private val applicationScope: CoroutineScope,
    /** Keyword pre-filled for public-holiday calendars, in the language of the phone. */
    private val holidayKeyword: () -> String,
) : ViewModel() {

    private val calendars = MutableStateFlow<List<DeviceCalendar>?>(null)
    private val draft = MutableStateFlow<List<CalendarFilter>?>(null)
    private var persistJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        calendars,
        draft,
        settingsRepository.observeSummarySettings(),
    ) { cals, filters, summary ->
        if (cals == null || filters == null) return@combine SettingsUiState(summary = summary)
        val byKey = cals.associateBy { it.key }
        SettingsUiState(
            loading = false,
            hasPermission = calendarRepository.hasPermission(),
            summary = summary,
            calendars = cals,
            byCategory = CalendarCategory.entries.associateWith { category ->
                filters.filter { it.category == category }
                    .mapNotNull { f -> byKey[f.sourceKey]?.let { AssignedCalendar(it, f.keywords) } }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun reload() {
        viewModelScope.launch {
            draft.value = settingsRepository.getCalendarFilters()
            calendars.value = calendarRepository.getCalendars()
        }
    }

    fun setSummaryEnabled(enabled: Boolean) = updateSummary { it.copy(enabled = enabled) }

    fun setSummaryTime(time: java.time.LocalTime) = updateSummary { it.copy(time = time) }

    private fun updateSummary(block: (SummarySettings) -> SummarySettings) {
        applicationScope.launch {
            updateSummarySettings(block(settingsRepository.getSummarySettings()))
        }
    }

    fun addCalendar(calendar: DeviceCalendar, category: CalendarCategory) = editFilters { filters ->
        filters + CalendarFilter(calendar.key, category, defaultKeywords(calendar, category))
    }

    /**
     * Google's "Holidays and observances" calendars also contain days that are not public
     * holidays (Mother's Day, Valentine's Day...). Their public holidays say "Public holiday"
     * (or "Jour férié"...) in the description, so the keyword is pre-filled; the user can still edit it.
     */
    private fun defaultKeywords(calendar: DeviceCalendar, category: CalendarCategory): String {
        if (category != CalendarCategory.HOLIDAYS) return ""
        val name = com.gamso.wakely.domain.usecase.CalendarLogic.normalize(calendar.name)
        return if ("ferie" in name || "holiday" in name) holidayKeyword() else ""
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
