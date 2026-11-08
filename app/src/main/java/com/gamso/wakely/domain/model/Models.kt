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
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
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

/** Identifies one occurrence of a calendar event, for alarms bound to a single event. */
data class EventRef(val sourceKey: String, val title: String, val start: LocalDateTime)

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
    /** Only the first matching event of each day triggers a ring. */
    val firstEventOfDayOnly: Boolean = true,
    /** When set, only this event occurrence triggers the alarm (one-shot). */
    val singleEvent: EventRef? = null,
    val sound: AlarmSound? = null,
    val snoozeMinutes: Int = Alarm.DEFAULT_SNOOZE_MINUTES,
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
    /** Public holidays: days off. */
    HOLIDAYS,

    /** Leave, vacations, days not worked: days off. */
    LEAVE,

    /** Events that can trigger "minutes before" alarms. */
    EVENTS,

    ;

    val isDayOff: Boolean get() = this != EVENTS
}

/** A calendar synced on the device (Google, Outlook/Exchange, Samsung... any Android account). */
data class DeviceCalendar(val calendarId: Long, val name: String, val account: String, val color: Int) {
    val key: String get() = keyOf(calendarId)

    companion object {
        fun keyOf(calendarId: Long) = "device:$calendarId"
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

data class DayOff(val date: LocalDate, val reason: String, val category: CalendarCategory = CalendarCategory.HOLIDAYS)

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

/** Identifies what a system alarm belongs to. */
data class RingKey(val kind: RingKind, val id: Long)

/** Everything the ringing screen needs. */
data class RingInfo(val key: RingKey, val label: String, val sound: AlarmSound?, val snoozeMinutes: Int)

/** Evening reminder of the next day's rings, shared by every alarm. */
data class SummarySettings(val enabled: Boolean = true, val time: LocalTime = LocalTime.of(21, 0))

data class PlannedRing(val at: LocalDateTime, val title: String, val detail: String? = null)

data class DayPlan(
    val date: LocalDate,
    val rings: List<PlannedRing>,
    /** Human-readable notes on alarms that will not ring that day. */
    val skipped: List<String>,
)

data class AppInfo(val versionName: String)

enum class LicenseId { MIT, APACHE_2 }
