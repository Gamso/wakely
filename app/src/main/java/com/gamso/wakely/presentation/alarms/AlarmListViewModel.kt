package com.gamso.wakely.presentation.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.wakely.di.AppContainer
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.EventRing
import com.gamso.wakely.domain.model.NextRing
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkippedOccurrence
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.SkippedOccurrences
import com.gamso.wakely.domain.repository.TimeProvider
import com.gamso.wakely.domain.usecase.CalendarLogic
import com.gamso.wakely.domain.usecase.ComputeNextRingUseCase
import com.gamso.wakely.domain.usecase.GetDaysOffUseCase
import com.gamso.wakely.domain.usecase.GetRuleEventsUseCase
import com.gamso.wakely.domain.usecase.RescheduleAlarmsUseCase
import com.gamso.wakely.domain.usecase.SetAlarmEnabledUseCase
import com.gamso.wakely.domain.usecase.SetEventAlarmEnabledUseCase
import com.gamso.wakely.domain.usecase.SkipNextRingUseCase
import com.gamso.wakely.domain.usecase.UndoSkipUseCase
import com.gamso.wakely.domain.usecase.timesOf
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmItemUi(
    val alarm: Alarm,
    val next: NextRing?,
    /** The ring the user skipped (the earliest one still to come), if any. */
    val skippedAt: LocalDateTime? = null,
) {
    /** One-shot alarms have no ring after the skipped one: switching them off does the same. */
    val canSkip: Boolean get() = alarm.enabled && alarm.isRepeating
}

data class EventAlarmItemUi(
    val rule: EventAlarm,
    val next: EventRing?,
    /** Names of the watched calendars. */
    val calendarNames: List<String>,
    val skippedAt: LocalDateTime? = null,
) {
    val canSkip: Boolean get() = rule.enabled && rule.singleEvent == null
}

data class AlarmListUiState(
    val items: List<AlarmItemUi> = emptyList(),
    val eventItems: List<EventAlarmItemUi> = emptyList(),
    val hasCalendarPermission: Boolean = true,
    val hasCalendarSelected: Boolean = true,
    /** The "Skip" buttons are on (Settings). A skip already made can always be undone. */
    val showSkipButtons: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val loading: Boolean = true,
)

/** Builds the view model from the application graph. */
fun AppContainer.alarmListViewModel() = AlarmListViewModel(
    alarmRepository, eventAlarmRepository, settingsRepository, calendarRepository, alarmScheduler, getDaysOff,
    getRuleEvents, computeNextRing, setAlarmEnabled, setEventAlarmEnabled, rescheduleAlarms, timeProvider,
    skippedOccurrences, skipNextRing, undoSkip,
)

private data class Listing(
    val alarms: List<Alarm>,
    val rules: List<EventAlarm>,
    val filters: List<CalendarFilter>,
    val skipped: Set<SkippedOccurrence>,
    val skipButtons: Boolean,
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
    skippedOccurrences: SkippedOccurrences,
    private val skipNextRing: SkipNextRingUseCase,
    private val undoSkip: UndoSkipUseCase,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<AlarmListUiState> = combine(
        alarmRepository.observeAlarms(),
        eventAlarmRepository.observeEventAlarms(),
        settingsRepository.observeCalendarFilters(),
        // Changing the country or the holidays the user works on changes the next rings.
        settingsRepository.observeHolidaySettings(),
        // A skipped ring changes the next ones, like a refresh does.
        combine(refreshTrigger, skippedOccurrences.observe(), settingsRepository.observeSkipButtons()) {
                _,
                skipped,
                buttons,
            ->
            skipped to buttons
        },
    ) { alarms, rules, filters, _, (skipped, buttons) -> Listing(alarms, rules, filters, skipped, buttons) }
        .mapLatest { listing ->
            val (alarms, rules, filters) = listing
            val skipped = listing.skipped
            val now = time.now()
            fun skippedAt(key: RingKey) = skipped.filter { it.key == key && it.at.isAfter(now) }.minOfOrNull { it.at }
            val daysOff = getDaysOff(now.toLocalDate(), filters = filters)
            val ruleData = getRuleEvents(now.toLocalDate(), extraSourceKeys = rules.flatMap { it.sourceKeys }.toSet())
            val calendars: Map<String, DeviceCalendar> =
                runCatching { calendarRepository.getCalendars() }.getOrDefault(emptyList()).associateBy { it.key }
            AlarmListUiState(
                items = alarms.map { alarm ->
                    val key = RingKey(RingKind.ALARM, alarm.id)
                    val next = if (alarm.enabled) {
                        computeNextRing(alarm, now, daysOff, skipped = skipped.timesOf(key))
                    } else {
                        null
                    }
                    AlarmItemUi(alarm, next, skippedAt(key))
                },
                eventItems = rules.map { rule ->
                    val key = RingKey(RingKind.EVENT, rule.id)
                    EventAlarmItemUi(
                        rule = rule,
                        next = if (rule.enabled) {
                            CalendarLogic.nextEventRing(
                                rule,
                                now,
                                ruleData.events,
                                ruleData.eventFilters,
                                skipped.timesOf(key),
                            )
                        } else {
                            null
                        },
                        calendarNames = CalendarLogic.ruleSources(rule, ruleData.eventFilters)
                            .mapNotNull { calendars[it]?.name },
                        skippedAt = skippedAt(key),
                    )
                },
                hasCalendarPermission = calendarRepository.hasPermission(),
                hasCalendarSelected = filters.any { it.category.isDayOff },
                showSkipButtons = listing.skipButtons,
                canScheduleExactAlarms = scheduler.canScheduleExactAlarms(),
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    /** Skips the next ring of an alarm or event rule, only that one. */
    fun skip(key: RingKey) {
        viewModelScope.launch { skipNextRing(key) }
    }

    fun undoSkip(key: RingKey) {
        viewModelScope.launch { undoSkip.invoke(key) }
    }

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
