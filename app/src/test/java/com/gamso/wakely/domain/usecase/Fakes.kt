package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.HolidaySettings
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkipIfAwake
import com.gamso.wakely.domain.model.SkippedOccurrence
import com.gamso.wakely.domain.model.StepBaseline
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.DeviceRegion
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.HolidayRepository
import com.gamso.wakely.domain.repository.PublicHoliday
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.SkippedOccurrences
import com.gamso.wakely.domain.repository.StepBaselineStore
import com.gamso.wakely.domain.repository.StepSensor
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeAlarms(var alarms: List<Alarm>) : AlarmRepository {
    override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms)
    override suspend fun getAlarms() = alarms
    override suspend fun getAlarm(id: Long) = alarms.find { it.id == id }
    override suspend fun save(alarm: Alarm): Alarm {
        alarms = alarms.filterNot { it.id == alarm.id } + alarm
        return alarm
    }

    override suspend fun delete(id: Long) {
        alarms = alarms.filterNot { it.id == id }
    }
}

internal object NoEvents : EventAlarmRepository {
    override fun observeEventAlarms(): Flow<List<EventAlarm>> = flowOf(emptyList())
    override suspend fun getEventAlarms() = emptyList<EventAlarm>()
    override suspend fun getEventAlarm(id: Long): EventAlarm? = null
    override suspend fun save(alarm: EventAlarm) = alarm
    override suspend fun delete(id: Long) = Unit
}

internal class FakeSettings(initial: List<CalendarFilter> = emptyList()) : SettingsRepository {
    private val filters = MutableStateFlow(initial)
    private val summary = MutableStateFlow(SummarySettings(enabled = false))
    override fun observeCalendarFilters(): Flow<List<CalendarFilter>> = filters
    override suspend fun getCalendarFilters() = filters.value
    override suspend fun setCalendarFilters(filters: List<CalendarFilter>) = Unit
    override fun observeSummarySettings(): Flow<SummarySettings> = summary
    override suspend fun getSummarySettings() = summary.value
    override suspend fun setSummarySettings(settings: SummarySettings) = Unit
    override fun observeSkipButtons(): Flow<Boolean> = flowOf(true)
    override suspend fun getSkipButtons() = true
    override suspend fun setSkipButtons(enabled: Boolean) = Unit
    override fun observeHolidaySettings(): Flow<HolidaySettings> = flowOf(HolidaySettings())
    override suspend fun getHolidaySettings() = HolidaySettings()
    override suspend fun setHolidaySettings(settings: HolidaySettings) = Unit
}

/** No public holidays: the days off of these tests come from a leave calendar. */
internal object NoPublicHolidays : HolidayRepository {
    override fun countries() = emptyList<String>()
    override fun regions(country: String) = emptyList<HolidayRegion>()
    override fun holidays(country: String, region: String?, from: LocalDate, to: LocalDate) = emptyList<PublicHoliday>()
}

internal object NoRegion : DeviceRegion {
    override fun countryCandidates() = emptyList<String>()
}

internal class FakeCalendar(private val events: List<CalendarEvent> = emptyList()) : CalendarRepository {
    override fun hasPermission() = true
    override suspend fun getCalendars() = emptyList<DeviceCalendar>()
    override suspend fun getEvents(sourceKeys: Set<String>, from: LocalDate, to: LocalDate) = events
}

internal class FakeScheduler : AlarmScheduler {
    val rings = mutableMapOf<RingKey, LocalDateTime>()
    val preChecks = mutableMapOf<RingKey, LocalDateTime>()
    override fun schedule(key: RingKey, at: LocalDateTime) {
        rings[key] = at
    }

    override fun scheduleSnooze(key: RingKey, at: LocalDateTime) = Unit
    override fun cancel(key: RingKey) {
        rings.remove(key)
    }

    override fun schedulePreCheck(key: RingKey, at: LocalDateTime) {
        preChecks[key] = at
    }

    override fun cancelPreCheck(key: RingKey) {
        preChecks.remove(key)
    }

    override fun scheduleSummary(at: LocalDateTime?) = Unit
    override fun canScheduleExactAlarms() = true
}

internal class FakeSensor(var permission: Boolean = true, var steps: Long? = 0) : StepSensor {
    override fun isAvailable() = true
    override fun hasPermission() = permission
    override suspend fun readSteps() = steps
}

internal class FakeStore : StepBaselineStore {
    val map = mutableMapOf<RingKey, StepBaseline>()
    override fun save(key: RingKey, baseline: StepBaseline) {
        map[key] = baseline
    }

    override fun get(key: RingKey) = map[key]
    override fun clear(key: RingKey) {
        map.remove(key)
    }
}

internal class Graph(alarm: Alarm, now: LocalDateTime, leaveDay: LocalDate? = null) {
    val alarms = FakeAlarms(listOf(alarm))
    val scheduler = FakeScheduler()
    val sensor = FakeSensor()
    val store = FakeStore()
    val skips = FakeSkips()
    var now: LocalDateTime = now
    private val time = TimeProvider { this.now }
    private val leave = leaveDay?.let {
        CalendarEvent("l", "Congé", "", it.atStartOfDay(), it.plusDays(1).atStartOfDay(), true)
    }
    private val calendar = FakeCalendar(listOfNotNull(leave))
    private val settings = FakeSettings(listOf(CalendarFilter("l", CalendarCategory.LEAVE)))
    val getDaysOff =
        GetDaysOffUseCase(calendar, NoPublicHolidays, DayOffSources(settings, NoRegion, NoPublicHolidays))
    private val getRuleEvents = GetRuleEventsUseCase(calendar, settings)
    private val compute = ComputeNextRingUseCase()
    val reschedule = RescheduleAlarmsUseCase(
        alarms,
        NoEvents,
        settings,
        getDaysOff,
        getRuleEvents,
        compute,
        scheduler,
        time,
        skips,
    )
    val handle = HandleRingFiredUseCase(
        alarms,
        NoEvents,
        getDaysOff,
        getRuleEvents,
        compute,
        reschedule,
        time,
        sensor,
        store,
        skips,
    )
    val record = RecordStepBaselineUseCase(alarms, sensor, store, time)
    val skipNext = SkipNextRingUseCase(alarms, NoEvents, getDaysOff, getRuleEvents, compute, skips, reschedule, time)
    val undoSkip = UndoSkipUseCase(skips, reschedule, time)
}

internal class FakeSkips : SkippedOccurrences {
    private val state = MutableStateFlow(emptySet<SkippedOccurrence>())
    override fun observe(): Flow<Set<SkippedOccurrence>> = state
    override suspend fun get() = state.value
    override suspend fun add(occurrence: SkippedOccurrence) {
        state.value = state.value + occurrence
    }

    override suspend fun remove(occurrence: SkippedOccurrence) {
        state.value = state.value - occurrence
    }

    override suspend fun clear(key: RingKey) {
        state.value = state.value.filterNot { it.key == key }.toSet()
    }

    override suspend fun prune(limit: LocalDateTime) {
        state.value = state.value.filter { !it.at.isBefore(limit) }.toSet()
    }
}
