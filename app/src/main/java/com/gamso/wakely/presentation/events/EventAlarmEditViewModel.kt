package com.gamso.wakely.presentation.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.EventRef
import com.gamso.wakely.domain.model.EventRing
import com.gamso.wakely.domain.model.parseKeywords
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.TimeProvider
import com.gamso.wakely.domain.usecase.CalendarLogic
import com.gamso.wakely.domain.usecase.DeleteEventAlarmUseCase
import com.gamso.wakely.domain.usecase.GetRuleEventsUseCase
import com.gamso.wakely.domain.usecase.SaveEventAlarmUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventAlarmEditUiState(
    val alarm: EventAlarm = EventAlarm(),
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val done: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    /** Calendars of the "events" category, used when the rule selects none. */
    val eventCategoryKeys: Set<String> = emptySet(),
    /** Next rings of the rule as currently edited. */
    val upcoming: List<EventRing> = emptyList(),
    /** Upcoming timed events the user can bind the rule to. */
    val candidates: List<CalendarEvent> = emptyList(),
) {
    fun calendarName(key: String) = calendars.find { it.key == key }?.name.orEmpty()
}

class EventAlarmEditViewModel(
    private val alarmId: Long?,
    private val repository: EventAlarmRepository,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    private val saveEventAlarm: SaveEventAlarmUseCase,
    private val deleteEventAlarm: DeleteEventAlarmUseCase,
    private val time: TimeProvider,
    /** Sound used when the rule has none selected. */
    val defaultSound: AlarmSound,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventAlarmEditUiState())
    val uiState: StateFlow<EventAlarmEditUiState> = _uiState.asStateFlow()

    private var events: List<CalendarEvent> = emptyList()
    private var eventFilters: List<CalendarFilter> = emptyList()

    init {
        viewModelScope.launch {
            val alarm = alarmId?.let { repository.getEventAlarm(it) }
            val calendars = calendarRepository.getCalendars()
            eventFilters = settingsRepository.getCalendarFilters().filter { it.category == CalendarCategory.EVENTS }
            events = runCatching {
                val today = time.now().toLocalDate()
                calendarRepository.getEvents(
                    calendars.map { it.key }.toSet(),
                    today,
                    today.plusDays(GetRuleEventsUseCase.EVENT_HORIZON_DAYS.toLong()),
                )
            }.getOrDefault(emptyList())
            _uiState.value = EventAlarmEditUiState(
                alarm = alarm ?: EventAlarm(),
                isNew = alarm == null,
                loaded = true,
                calendars = calendars,
                eventCategoryKeys = eventFilters.map { it.sourceKey }.toSet(),
            )
            recompute()
        }
    }

    private fun edit(block: (EventAlarm) -> EventAlarm) {
        _uiState.update { it.copy(alarm = block(it.alarm)) }
        recompute()
    }

    private fun recompute() {
        val rule = _uiState.value.alarm
        val now = time.now()
        val sources = CalendarLogic.ruleSources(rule, eventFilters)
        val keywords = parseKeywords(rule.keywords)
        _uiState.update {
            it.copy(
                upcoming = CalendarLogic.upcomingEventRings(rule, now, events, eventFilters).take(PREVIEW_COUNT),
                candidates = events.filter { e ->
                    !e.allDay && e.sourceKey in sources && e.start.isAfter(now) && CalendarLogic.matches(e, keywords)
                }.take(MAX_CANDIDATES),
            )
        }
    }

    fun setLabel(label: String) = edit { it.copy(label = label) }
    fun setMinutesBefore(minutes: Int) = edit { it.copy(minutesBefore = minutes.coerceIn(0, MAX_MINUTES)) }
    fun toggleSource(key: String) = edit {
        it.copy(sourceKeys = if (key in it.sourceKeys) it.sourceKeys - key else it.sourceKeys + key)
    }
    fun setKeywords(keywords: String) = edit { it.copy(keywords = keywords) }
    fun setFirstEventOfDayOnly(value: Boolean) = edit { it.copy(firstEventOfDayOnly = value) }
    fun setSingleEvent(event: CalendarEvent?) = edit {
        it.copy(singleEvent = event?.let { e -> EventRef(e.sourceKey, e.title, e.start) })
    }
    fun setSound(sound: AlarmSound) = edit { it.copy(sound = sound) }
    fun setSnoozeMinutes(minutes: Int) = edit { it.copy(snoozeMinutes = minutes) }

    fun save() = viewModelScope.launch {
        saveEventAlarm(_uiState.value.alarm.copy(enabled = true))
        _uiState.update { it.copy(done = true) }
    }

    fun delete() = viewModelScope.launch {
        if (alarmId != null) deleteEventAlarm(alarmId)
        _uiState.update { it.copy(done = true) }
    }

    private companion object {
        const val PREVIEW_COUNT = 5
        const val MAX_CANDIDATES = 100
        const val MAX_MINUTES = 24 * 60
    }
}
