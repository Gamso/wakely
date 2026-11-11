package com.gamso.wakely.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Alarm behaviour when the calendar marks a day off. */
enum class DayOffMode {
    /** The alarm does not ring. */
    SKIP,

    /** The alarm rings at [Alarm.dayOffTime]. */
    OTHER_TIME,

    /** The calendar is ignored for this alarm. */
    IGNORE,
}

/** When an alarm may be skipped because the user is already up. */
enum class SkipIfAwake {
    OFF,

    /** Every ring of the alarm. */
    ALWAYS,

    /** Only the rings that fall on a public holiday or a leave day, never the ring of an ordinary day. */
    DAYS_OFF_ONLY,
}

data class Alarm(
    val id: Long = 0,
    val time: LocalTime = LocalTime.of(7, 0),
    /** Repeat days. Empty = one-shot alarm. */
    val days: Set<DayOfWeek> = WORKDAYS,
    val label: String = "",
    val enabled: Boolean = true,
    val dayOffMode: DayOffMode = DayOffMode.SKIP,
    val dayOffTime: LocalTime = LocalTime.of(9, 0),
    /** Selected ringtone; null = app default sound. */
    val sound: AlarmSound? = null,
    /** Snooze duration in minutes; 0 = snooze disabled. */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** Skip a ring (that occurrence only) when the user is already up and walking. Never for event alarms. */
    val skipIfAwake: SkipIfAwake = SkipIfAwake.OFF,
    /** Seconds over which the volume rises from silence to the alarm volume; 0 = full volume at once. */
    val volumeRampSeconds: Int = 0,
    /** Whether the phone vibrates while the alarm rings. */
    val vibrate: Boolean = true,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        val RAMP_CHOICES = listOf(0, 10, 30, 60)
        val SNOOZE_CHOICES = listOf(0, 5, 10, 15, 20, 30)

        val WORKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}

/** Rings [minutesBefore] minutes before calendar events matching its filters. */
data class EventAlarm(
    val id: Long = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val minutesBefore: Int = DEFAULT_MINUTES_BEFORE,
    /** Calendars to watch ([DeviceCalendar.key]); empty = every "events" calendar. */
    val sourceKeys: Set<String> = emptySet(),
    /** Comma-separated keywords matched against title/description; empty = every event. */
    val keywords: String = "",
    val sound: AlarmSound? = null,
    val snoozeMinutes: Int = Alarm.DEFAULT_SNOOZE_MINUTES,
    val volumeRampSeconds: Int = 0,
    val vibrate: Boolean = true,
) {
    companion object {
        const val DEFAULT_MINUTES_BEFORE = 60
        val MINUTES_CHOICES = listOf(15, 30, 45, 60, 90, 120)
    }
}

enum class SoundSource {
    /** Sounds shipped with the app. */
    BUNDLED,

    /** Alarm sounds installed on the device. */
    SYSTEM,

    /** Audio files added by the user. */
    CUSTOM,
}

data class AlarmSound(val uri: String, val name: String, val source: SoundSource)

/** What a calendar is used for. */
enum class CalendarCategory {
    /** Leave, vacations, days not worked: days off. */
    LEAVE,

    /** Events that can trigger "minutes before" alarms. */
    EVENTS,

    ;

    val isDayOff: Boolean get() = this != EVENTS
}

/** A calendar synced on the device (Google, Outlook/Exchange, Samsung... any Android account). */
data class DeviceCalendar(
    val calendarId: Long,
    val name: String,
    val account: String,
    val color: Int,
    /** A calendar of public holidays (Google's "Holidays in ..."): the app already has them built in. */
    val isHolidays: Boolean = false,
) {
    val key: String get() = keyOf(calendarId)

    companion object {
        fun keyOf(calendarId: Long) = "device:$calendarId"

        /**
         * Google's holiday calendars belong to an account like "en.french#holiday@group.v.calendar.google.com". Only
         * this is looked at, never the name: a calendar called "Holidays" can well be the user's own vacation.
         */
        fun isHolidayOwner(owner: String?): Boolean = owner != null && owner.contains("#holiday@")
    }
}

/**
 * Assigns a calendar to a [category].
 * [keywords]: comma-separated list; empty = every event of the calendar.
 */
data class CalendarFilter(val sourceKey: String, val category: CalendarCategory, val keywords: String = "") {
    fun keywordList(): List<String> = parseKeywords(keywords)
}

fun parseKeywords(keywords: String): List<String> = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** One event occurrence, in local time. All-day events span whole days, end exclusive. */
data class CalendarEvent(
    val sourceKey: String,
    val title: String,
    val description: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
)

/** A part of a day, from [start] (included) to [end] (excluded). */
data class TimeRange(val start: LocalTime, val end: LocalTime)

/**
 * A day off. Without [ranges] it is the whole day. With ranges (a leave entered with a time, such as a morning off) it
 * only concerns the alarms whose normal ring time falls inside one of them, see [covers].
 */
data class DayOff(val date: LocalDate, val reason: String, val ranges: List<TimeRange> = emptyList()) {
    /** True when an alarm that would normally ring at [time] is off: all day, or [time] is inside a range. */
    fun covers(time: LocalTime): Boolean = ranges.isEmpty() || ranges.any { time >= it.start && time < it.end }
}

data class NextRing(
    val alarmId: Long,
    val at: LocalDateTime,
    /** Non-null when the ring falls on a day off (alternative time). */
    val dayOffReason: String?,
    /** Days skipped before the next ring. */
    val skipped: List<DayOff>,
)

data class EventRing(val ruleId: Long, val at: LocalDateTime, val event: CalendarEvent)

enum class RingKind { ALARM, EVENT }

/** Step counter reading taken [takenAt], shortly before an alarm. */
data class StepBaseline(val steps: Long, val takenAt: LocalDateTime)

/** One occurrence of an alarm that the user chose not to hear. The alarm itself is untouched. */
data class SkippedOccurrence(val key: RingKey, val at: LocalDateTime)

/** Identifies what a system alarm belongs to. */
data class RingKey(val kind: RingKind, val id: Long)

/** Everything the ringing screen needs. */
data class RingInfo(
    val key: RingKey,
    /** Alarm label, or the event title for an event rule; may be blank. */
    val label: String,
    val sound: AlarmSound?,
    val snoozeMinutes: Int,
    /** Start time of the event an event rule rings for. */
    val eventTime: LocalTime? = null,
    val volumeRampSeconds: Int = 0,
    val vibrate: Boolean = true,
)

/** Evening reminder of the next day's rings, shared by every alarm. */
data class SummarySettings(val enabled: Boolean = true, val time: LocalTime = LocalTime.of(21, 0))

/**
 * A ring planned for a day. [label] may be blank (the UI shows a default).
 * An alarm may carry the [dayOffReason] that moved it; an event rule carries its [event].
 */
data class PlannedRing(
    val at: LocalDateTime,
    val label: String,
    val dayOffReason: String? = null,
    val event: CalendarEvent? = null,
    /** What the ring belongs to, so it can be skipped from the notification. */
    val key: RingKey? = null,
)

/** An alarm that will not ring on a day off. */
data class SkippedRing(val label: String, val time: LocalTime, val reason: String)

/** The rings of a day. [pausedUntil] is set when the alarms are paused on that day. */
data class DayPlan(
    val date: LocalDate,
    val rings: List<PlannedRing>,
    val skipped: List<SkippedRing>,
    val pausedUntil: LocalDate? = null,
)

data class AppInfo(val versionName: String)

enum class LicenseId { MIT, APACHE_2 }

/**
 * Public holidays, which come from the app itself (Jollyday data), not from calendars.
 * [country] is an ISO 3166-1 alpha-2 code, or null for "no public holidays"; it only counts when [countryChosen] is
 * true, otherwise the country of the device is used. [region] is a region of that country (e.g. "67" for Bas-Rhin).
 * [excludedIds] are the holidays the user works on: they are not days off.
 */
data class HolidaySettings(
    val country: String? = null,
    val countryChosen: Boolean = false,
    val region: String? = null,
    val excludedIds: Set<String> = emptySet(),
)

/** An upcoming public holiday as listed in Settings; [excluded] means the user works that day. */
data class HolidayItem(val date: LocalDate, val id: String, val name: String, val excluded: Boolean)

/** The pattern that must be drawn to stop an alarm: only a salted hash is kept, never the pattern. */
data class PatternHash(val salt: String, val hash: String)
