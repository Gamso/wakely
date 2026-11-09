package com.gamso.wakely.presentation.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.EventRing
import com.gamso.wakely.domain.model.NextRing
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.TimeProvider
import com.gamso.wakely.domain.usecase.CalendarLogic
import com.gamso.wakely.domain.usecase.ComputeNextRingUseCase
import com.gamso.wakely.domain.usecase.GetDaysOffUseCase
import com.gamso.wakely.domain.usecase.GetRuleEventsUseCase
import com.gamso.wakely.domain.usecase.RescheduleAlarmsUseCase
import com.gamso.wakely.domain.usecase.SetAlarmEnabledUseCase
import com.gamso.wakely.domain.usecase.SetEventAlarmEnabledUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmItemUi(val alarm: Alarm, val next: NextRing?)

data class EventAlarmItemUi(
    val rule: EventAlarm,
    val next: EventRing?,
    /** Names of the watched calendars. */
    val calendarNames: List<String>,
)

data class AlarmListUiState(
    val items: List<AlarmItemUi> = emptyList(),
    val eventItems: List<EventAlarmItemUi> = emptyList(),
    val hasCalendarPermission: Boolean = true,
    val hasCalendarSelected: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModel(
    alarmRepository: AlarmRepository,
    eventAlarmRepository: EventAlarmRepository,
    settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
    private val scheduler: AlarmScheduler,
    private val getDaysOff: GetDaysOffUseCase,
    private val getRuleEvents: GetRuleEventsUseCase,
    private val computeNextRing: ComputeNextRingUseCase,
    private val setAlarmEnabled: SetAlarmEnabledUseCase,
    private val setEventAlarmEnabled: SetEventAlarmEnabledUseCase,
    private val rescheduleAlarms: RescheduleAlarmsUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<AlarmListUiState> = combine(
        alarmRepository.observeAlarms(),
        eventAlarmRepository.observeEventAlarms(),
        settingsRepository.observeCalendarFilters(),
        // Changing the country or the holidays the user works on changes the next rings.
        settingsRepository.observeHolidaySettings(),
        refreshTrigger,
    ) { alarms, rules, filters, _, _ -> Triple(alarms, rules, filters) }
        .mapLatest { (alarms, rules, filters) ->
            val now = time.now()
            val daysOff = getDaysOff(now.toLocalDate(), filters = filters)
            val ruleData = getRuleEvents(now.toLocalDate(), extraSourceKeys = rules.flatMap { it.sourceKeys }.toSet())
            val calendars: Map<String, DeviceCalendar> =
                runCatching { calendarRepository.getCalendars() }.getOrDefault(emptyList()).associateBy { it.key }
            AlarmListUiState(
                items = alarms.map { alarm ->
                    AlarmItemUi(alarm, if (alarm.enabled) computeNextRing(alarm, now, daysOff) else null)
                },
                eventItems = rules.map { rule ->
                    EventAlarmItemUi(
                        rule = rule,
                        next = if (rule.enabled) {
                            CalendarLogic.nextEventRing(rule, now, ruleData.events, ruleData.eventFilters)
                        } else {
                            null
                        },
                        calendarNames = CalendarLogic.ruleSources(rule, ruleData.eventFilters)
                            .mapNotNull { calendars[it]?.name },
                    )
                },
                hasCalendarPermission = calendarRepository.hasPermission(),
                hasCalendarSelected = filters.any { it.category.isDayOff },
                canScheduleExactAlarms = scheduler.canScheduleExactAlarms(),
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    fun onToggle(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch { setAlarmEnabled(alarm, enabled) }
    }

    fun onToggle(rule: EventAlarm, enabled: Boolean) {
        viewModelScope.launch { setEventAlarmEnabled(rule, enabled) }
    }

    /** Call when returning to the app (permissions or calendar may have changed). */
    fun refresh() {
        refreshTrigger.update { it + 1 }
        viewModelScope.launch { rescheduleAlarms() }
    }
}
