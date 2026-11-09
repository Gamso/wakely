package com.gamso.wakely.di

import android.content.Context
import com.gamso.wakely.data.about.AppInfoRepositoryImpl
import com.gamso.wakely.data.calendar.CalendarRepositoryImpl
import com.gamso.wakely.data.holiday.AssetHolidayDataSource
import com.gamso.wakely.data.holiday.HolidayRepositoryImpl
import com.gamso.wakely.data.local.AlarmRepositoryImpl
import com.gamso.wakely.data.local.EventAlarmRepositoryImpl
import com.gamso.wakely.data.local.SettingsRepositoryImpl
import com.gamso.wakely.data.region.AndroidDeviceRegion
import com.gamso.wakely.data.scheduler.AndroidAlarmScheduler
import com.gamso.wakely.data.sensor.StepBaselinePrefs
import com.gamso.wakely.data.sensor.StepCounterSensor
import com.gamso.wakely.data.sound.SoundRepositoryImpl
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.AppInfoRepository
import com.gamso.wakely.domain.repository.CalendarRepository
import com.gamso.wakely.domain.repository.DeviceRegion
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.HolidayRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.SoundPreviewer
import com.gamso.wakely.domain.repository.SoundRepository
import com.gamso.wakely.domain.repository.StepBaselineStore
import com.gamso.wakely.domain.repository.StepSensor
import com.gamso.wakely.domain.repository.TimeProvider
import com.gamso.wakely.domain.usecase.ComputeNextRingUseCase
import com.gamso.wakely.domain.usecase.DayOffSources
import com.gamso.wakely.domain.usecase.DeleteAlarmUseCase
import com.gamso.wakely.domain.usecase.DeleteEventAlarmUseCase
import com.gamso.wakely.domain.usecase.GetDayPlanUseCase
import com.gamso.wakely.domain.usecase.GetDaysOffUseCase
import com.gamso.wakely.domain.usecase.GetRingInfoUseCase
import com.gamso.wakely.domain.usecase.GetRuleEventsUseCase
import com.gamso.wakely.domain.usecase.GetUpcomingHolidaysUseCase
import com.gamso.wakely.domain.usecase.HandleRingFiredUseCase
import com.gamso.wakely.domain.usecase.HandleSnoozeFiredUseCase
import com.gamso.wakely.domain.usecase.RecordStepBaselineUseCase
import com.gamso.wakely.domain.usecase.RescheduleAlarmsUseCase
import com.gamso.wakely.domain.usecase.SaveAlarmUseCase
import com.gamso.wakely.domain.usecase.SaveEventAlarmUseCase
import com.gamso.wakely.domain.usecase.SetAlarmEnabledUseCase
import com.gamso.wakely.domain.usecase.SetEventAlarmEnabledUseCase
import com.gamso.wakely.domain.usecase.SnoozeAlarmUseCase
import com.gamso.wakely.domain.usecase.UpdateCalendarFiltersUseCase
import com.gamso.wakely.domain.usecase.UpdateHolidaySettingsUseCase
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
    val deviceRegion: DeviceRegion = AndroidDeviceRegion(appContext)
    val calendarRepository: CalendarRepository = CalendarRepositoryImpl(appContext)
    val holidayRepository: HolidayRepository = HolidayRepositoryImpl(AssetHolidayDataSource(appContext))
    val alarmScheduler: AlarmScheduler = AndroidAlarmScheduler(appContext)
    val stepSensor: StepSensor = StepCounterSensor(appContext)
    val stepBaselines: StepBaselineStore = StepBaselinePrefs(appContext)
    val soundRepository: SoundRepository = SoundRepositoryImpl(appContext)
    val soundPreviewer: SoundPreviewer = MediaSoundPreviewer(appContext)
    val ringingState = RingingStateHolder()
    val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl(appContext)

    // Domain
    val computeNextRing = ComputeNextRingUseCase()
    val dayOffSources = DayOffSources(settingsRepository, deviceRegion, holidayRepository)
    val getDaysOff = GetDaysOffUseCase(calendarRepository, holidayRepository, dayOffSources)
    val getUpcomingHolidays = GetUpcomingHolidaysUseCase(holidayRepository, dayOffSources)
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
    val updateHolidaySettings = UpdateHolidaySettingsUseCase(settingsRepository, rescheduleAlarms)
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
        stepSensor,
        stepBaselines,
    )
    val recordStepBaseline = RecordStepBaselineUseCase(alarmRepository, stepSensor, stepBaselines, timeProvider)
    val getDayPlan = GetDayPlanUseCase(
        alarmRepository,
        eventAlarmRepository,
        getDaysOff,
        getRuleEvents,
        computeNextRing,
        timeProvider,
    )
}
