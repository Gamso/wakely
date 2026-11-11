package com.gamso.wakely.domain.repository

import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.model.AppInfo
import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DeviceCalendar
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.HolidaySettings
import com.gamso.wakely.domain.model.LicenseId
import com.gamso.wakely.domain.model.PatternHash
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.SkippedOccurrence
import com.gamso.wakely.domain.model.StepBaseline
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

    /** Whether the "Pause alarms" button is offered in the alarm list. On by default. */
    fun observePauseButton(): Flow<Boolean>
    suspend fun setPauseButton(enabled: Boolean)

    /** Whether the "Skip" buttons are offered on the alarms and in the evening notification. On by default. */
    fun observeSkipButtons(): Flow<Boolean>
    suspend fun getSkipButtons(): Boolean
    suspend fun setSkipButtons(enabled: Boolean)

    /** The pattern to draw to stop or snooze a ringing alarm, or null when no pattern is asked. */
    fun observeUnlockPattern(): Flow<PatternHash?>
    suspend fun getUnlockPattern(): PatternHash?
    suspend fun setUnlockPattern(pattern: PatternHash?)

    /** Last day of the pause of every alarm (nothing rings up to and including it), or null. */
    fun observePause(): Flow<LocalDate?>
    suspend fun getPause(): LocalDate?
    suspend fun setPause(until: LocalDate?)

    fun observeHolidaySettings(): Flow<HolidaySettings>
    suspend fun getHolidaySettings(): HolidaySettings
    suspend fun setHolidaySettings(settings: HolidaySettings)
}

/** A public holiday with its name in the language of the phone. [id] is stable across years and languages. */
data class PublicHoliday(val id: String, val name: String, val date: LocalDate)

/** Public holidays by country and region, computed offline from the embedded Jollyday data. */
interface HolidayRepository {
    /** ISO 3166-1 alpha-2 codes of the supported countries. */
    fun countries(): List<String>

    /** Regions of [country] that have holidays of their own. */
    fun regions(country: String): List<HolidayRegion>

    fun holidays(country: String, region: String?, from: LocalDate, to: LocalDate): List<PublicHoliday>
}

/** Where the phone is, as far as holidays are concerned. No location permission is involved. */
interface DeviceRegion {
    /** ISO 3166-1 alpha-2 codes, most reliable first (e.g. the SIM country, then the locale's country). */
    fun countryCandidates(): List<String>
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

    /** Schedules the step check that precedes a ring of an alarm that can be skipped. */
    fun schedulePreCheck(key: RingKey, at: LocalDateTime)
    fun cancelPreCheck(key: RingKey)

    /** Schedules the evening summary; null cancels it. */
    fun scheduleSummary(at: LocalDateTime?)
    fun canScheduleExactAlarms(): Boolean
}

/** The occurrences the user skipped. They are forgotten once their time has passed, or when the alarm is edited. */
interface SkippedOccurrences {
    fun observe(): Flow<Set<SkippedOccurrence>>
    suspend fun get(): Set<SkippedOccurrence>
    suspend fun add(occurrence: SkippedOccurrence)
    suspend fun remove(occurrence: SkippedOccurrence)

    /** Forgets every skipped occurrence of [key]. */
    suspend fun clear(key: RingKey)

    /** Forgets the occurrences before [limit]. */
    suspend fun prune(limit: LocalDateTime)
}

/** The step counter of the phone. Everything is null or false when it cannot be read: the alarm then rings. */
interface StepSensor {
    fun isAvailable(): Boolean
    fun hasPermission(): Boolean

    /** Steps since the phone booted, or null (no sensor, no permission, no answer in time). */
    suspend fun readSteps(): Long?
}

/** Remembers the step count taken before each alarm. Only compared, never kept after the ring. */
interface StepBaselineStore {
    fun save(key: RingKey, baseline: StepBaseline)
    fun get(key: RingKey): StepBaseline?
    fun clear(key: RingKey)
}

interface AppInfoRepository {
    fun getAppInfo(): AppInfo
    suspend fun getLicenseText(id: LicenseId): String
}

fun interface TimeProvider {
    fun now(): LocalDateTime
}
