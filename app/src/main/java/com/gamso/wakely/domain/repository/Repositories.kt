package com.gamso.wakely.domain.repository

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.model.AppInfo
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.LicenseId
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.SummarySettings
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    suspend fun getAlarms(): List<Alarm>
    suspend fun getAlarm(id: Long): Alarm?

    /** Creates (id = 0) or updates the alarm and returns it with its id. */
    suspend fun save(alarm: Alarm): Alarm
    suspend fun delete(id: Long)
}

interface EventAlarmRepository {
    fun observeEventAlarms(): Flow<List<EventAlarm>>
    suspend fun getEventAlarms(): List<EventAlarm>
    suspend fun getEventAlarm(id: Long): EventAlarm?

    /** Creates (id = 0) or updates the rule and returns it with its id. */
    suspend fun save(alarm: EventAlarm): EventAlarm
    suspend fun delete(id: Long)
}

interface SettingsRepository {
    fun observeCalendarFilters(): Flow<List<CalendarFilter>>
    suspend fun getCalendarFilters(): List<CalendarFilter>
    suspend fun setCalendarFilters(filters: List<CalendarFilter>)

    fun observeSummarySettings(): Flow<SummarySettings>
    suspend fun getSummarySettings(): SummarySettings
    suspend fun setSummarySettings(settings: SummarySettings)
}

interface CalendarRepository {
    /** Permission to read calendars synced on the device. */
    fun hasPermission(): Boolean
    suspend fun getCalendars(): List<DeviceCalendar>

    /** Event occurrences overlapping [from]..[to] (inclusive) for the given calendar keys. */
    suspend fun getEvents(sourceKeys: Set<String>, from: LocalDate, to: LocalDate): List<CalendarEvent>
}

interface SoundRepository {
    /** The sound used when an alarm has no explicit sound. */
    fun defaultSound(): AlarmSound
    suspend fun getSounds(): List<AlarmSound>

    /** Registers a user audio file; returns null if it cannot be read. */
    suspend fun addCustomSound(uri: String): AlarmSound?
    suspend fun removeCustomSound(uri: String)
}

/** Short sound preview while choosing a ringtone. */
interface SoundPreviewer {
    fun play(uri: String)
    fun stop()
}

/** Abstraction over the system scheduler (AlarmManager). */
interface AlarmScheduler {
    fun schedule(key: RingKey, at: LocalDateTime)
    fun scheduleSnooze(key: RingKey, at: LocalDateTime)
    fun cancel(key: RingKey)

    /** Schedules the evening summary; null cancels it. */
    fun scheduleSummary(at: LocalDateTime?)
    fun canScheduleExactAlarms(): Boolean
}

interface AppInfoRepository {
    fun getAppInfo(): AppInfo
    suspend fun getLicenseText(id: LicenseId): String
}

fun interface TimeProvider {
    fun now(): LocalDateTime
}
