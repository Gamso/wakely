package com.gamso.wakely.di

import android.content.Context
import com.gamso.wakely.data.about.AppInfoRepositoryImpl
import com.gamso.wakely.data.calendar.CalendarRepositoryImpl
import com.gamso.wakely.data.local.AlarmRepositoryImpl
import com.gamso.wakely.data.local.EventAlarmRepositoryImpl
import com.gamso.wakely.data.local.SettingsRepositoryImpl
import com.gamso.wakely.data.scheduler.AndroidAlarmScheduler
import com.gamso.wakely.data.sound.SoundRepositoryImpl
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.AppInfoRepository
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.SoundPreviewer
import com.gamso.wakely.domain.repository.SoundRepository
import com.gamso.wakely.domain.repository.TimeProvider
import com.gamso.wakely.domain.usecase.ComputeNextRingUseCase
import com.gamso.wakely.domain.usecase.DeleteAlarmUseCase
import com.gamso.wakely.domain.usecase.DeleteEventAlarmUseCase
import com.gamso.wakely.domain.usecase.GetDayPlanUseCase
import com.gamso.wakely.domain.usecase.GetDaysOffUseCase
import com.gamso.wakely.domain.usecase.GetRingInfoUseCase
import com.gamso.wakely.domain.usecase.GetRuleEventsUseCase
import com.gamso.wakely.domain.usecase.HandleRingFiredUseCase
import com.gamso.wakely.domain.usecase.HandleSnoozeFiredUseCase
import com.gamso.wakely.domain.usecase.RescheduleAlarmsUseCase
import com.gamso.wakely.domain.usecase.SaveAlarmUseCase
import com.gamso.wakely.domain.usecase.SaveEventAlarmUseCase
import com.gamso.wakely.domain.usecase.SetAlarmEnabledUseCase
import com.gamso.wakely.domain.usecase.SetEventAlarmEnabledUseCase
import com.gamso.wakely.domain.usecase.SnoozeAlarmUseCase
import com.gamso.wakely.domain.usecase.UpdateCalendarFiltersUseCase
import com.gamso.wakely.domain.usecase.UpdateSummarySettingsUseCase
import com.gamso.wakely.framework.MediaSoundPreviewer
import com.gamso.wakely.framework.RingingStateHolder
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection (a single graph for the whole app). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val timeProvider = TimeProvider { LocalDateTime.now() }

    /** Application scope for work that must outlive a screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Data
    val alarmRepository: AlarmRepository = AlarmRepositoryImpl(appContext)
    val eventAlarmRepository: EventAlarmRepository = EventAlarmRepositoryImpl(appContext)
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext)
    val calendarRepository: CalendarRepository = CalendarRepositoryImpl(appContext)
    val alarmScheduler: AlarmScheduler = AndroidAlarmScheduler(appContext)
    val soundRepository: SoundRepository = SoundRepositoryImpl(appContext)
    val soundPreviewer: SoundPreviewer = MediaSoundPreviewer(appContext)
    val ringingState = RingingStateHolder()
    val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl(appContext)

    // Domain
    val computeNextRing = ComputeNextRingUseCase()
    val getDaysOff = GetDaysOffUseCase(calendarRepository, settingsRepository)
    val getRuleEvents = GetRuleEventsUseCase(calendarRepository, settingsRepository)
    val rescheduleAlarms = RescheduleAlarmsUseCase(
        alarmRepository,
        eventAlarmRepository,
        settingsRepository,
        getDaysOff,
        getRuleEvents,
        computeNextRing,
        alarmScheduler,
        timeProvider,
    )
    val saveAlarm = SaveAlarmUseCase(alarmRepository, rescheduleAlarms)
    val setAlarmEnabled = SetAlarmEnabledUseCase(alarmRepository, rescheduleAlarms)
    val deleteAlarm = DeleteAlarmUseCase(alarmRepository, alarmScheduler)
    val saveEventAlarm = SaveEventAlarmUseCase(eventAlarmRepository, rescheduleAlarms)
    val setEventAlarmEnabled = SetEventAlarmEnabledUseCase(eventAlarmRepository, rescheduleAlarms)
    val deleteEventAlarm = DeleteEventAlarmUseCase(eventAlarmRepository, alarmScheduler)
    val updateCalendarFilters = UpdateCalendarFiltersUseCase(settingsRepository, rescheduleAlarms)
    val updateSummarySettings = UpdateSummarySettingsUseCase(settingsRepository, rescheduleAlarms)
    val snoozeAlarm = SnoozeAlarmUseCase(alarmScheduler, timeProvider)
    val getRingInfo = GetRingInfoUseCase(alarmRepository, eventAlarmRepository)
    val handleSnoozeFired = HandleSnoozeFiredUseCase(getRingInfo, timeProvider)
    val handleRingFired = HandleRingFiredUseCase(
        alarmRepository,
        eventAlarmRepository,
        getDaysOff,
        getRuleEvents,
        computeNextRing,
        rescheduleAlarms,
        timeProvider,
    )
    val getDayPlan = GetDayPlanUseCase(
        alarmRepository,
        eventAlarmRepository,
        getDaysOff,
        getRuleEvents,
        computeNextRing,
        timeProvider,
    )
}
