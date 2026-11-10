package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.EventRing
import com.gamso.wakely.domain.model.TimeRange
import com.gamso.wakely.domain.model.parseKeywords
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure calendar helpers shared by the use cases. */
object CalendarLogic {

    private val ACCENTS = Regex("\\p{Mn}+")

    /** Lowercase without accents: "Férié" == "ferie". */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")

    /** True when [keywords] is empty or one keyword appears in the title or description. */
    fun matches(event: CalendarEvent, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return true
        val haystack = normalize("${event.title} ${event.description}")
        return keywords.any { haystack.contains(normalize(it)) }
    }

    /**
     * Days covered by an event. All-day events cover each day until the exclusive end;
     * timed events only count days they fully cover (e.g. leave entered 00:00 → 00:00).
     */
    fun coveredDates(event: CalendarEvent): List<LocalDate> {
        val first = event.start.toLocalDate()
        if (event.allDay) {
            val endExclusive = event.end.toLocalDate()
            if (!endExclusive.isAfter(first)) return listOf(first)
            return generateSequence(first) { it.plusDays(1) }.takeWhile { it < endExclusive }.toList()
        }
        val last = event.end.toLocalDate()
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { it <= last }
            .filter { !it.atStartOfDay().isBefore(event.start) && !it.plusDays(1).atStartOfDay().isAfter(event.end) }
            .toList()
    }

    /**
     * The days off an event gives, whole or partial. The real days off and the preview in Settings both use this rule.
     *  - An event accepted by the keywords gives every day it covers entirely (all-day events, or a leave entered from
     *    midnight to midnight).
     *  - A timed event that covers only part of a day gives a partial day off (a morning off, 07:00 to 12:00), but only
     *    when a calendar keyword is set and matches: without keywords, a meeting would cancel alarms.
     */
    fun dayOffsOf(event: CalendarEvent, filters: List<CalendarFilter>): List<DayOff> {
        if (filters.none { matches(event, it.keywordList()) }) return emptyList()
        val whole = coveredDates(event)
        val result = whole.map { DayOff(it, event.title) }.toMutableList()
        val explicit = filters.any { it.keywordList().isNotEmpty() && matches(event, it.keywordList()) }
        if (!event.allDay && explicit) {
            val lastDate = event.end.minusNanos(1).toLocalDate()
            generateSequence(event.start.toLocalDate()) { it.plusDays(1) }
                .takeWhile { it <= lastDate }
                .filter { it !in whole }
                .forEach { date ->
                    val start = if (date == event.start.toLocalDate()) event.start.toLocalTime() else LocalTime.MIN
                    val end = if (date == lastDate && event.end.toLocalTime() != LocalTime.MIN) {
                        event.end.toLocalTime()
                    } else {
                        LocalTime.MAX
                    }
                    if (start < end) result += DayOff(date, event.title, listOf(TimeRange(start, end)))
                }
        }
        return result
    }

    /** Days off from events of the day-off calendars ([filters] of the leave category). */
    fun daysOff(
        events: List<CalendarEvent>,
        filters: List<CalendarFilter>,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, DayOff> {
        val bySource = filters.filter { it.category.isDayOff }.groupBy { it.sourceKey }
        val result = sortedMapOf<LocalDate, DayOff>()
        for (event in events.sortedBy { it.start }) {
            // The title may be blank: the UI shows a localized default.
            dayOffsOf(event, bySource[event.sourceKey].orEmpty()).filter { it.date in from..to }.forEach { off ->
                result[off.date] = merge(result[off.date], off)
            }
        }
        return result
    }

    /** A whole day wins over partial ones, partial ranges add up, and the first event gives the reason. */
    private fun merge(existing: DayOff?, added: DayOff): DayOff = when {
        existing == null -> added
        existing.ranges.isEmpty() -> existing
        added.ranges.isEmpty() -> added
        else -> existing.copy(ranges = existing.ranges + added.ranges)
    }

    /**
     * Calendars an event rule watches. The "events" calendars are the pool the rules choose from: the rule's own
     * selection (every calendar of the pool when it has none), limited to the pool.
     */
    fun ruleSources(rule: EventAlarm, eventFilters: List<CalendarFilter>): Set<String> {
        val pool = eventFilters.map { it.sourceKey }.toSet()
        return rule.sourceKeys.ifEmpty { pool }.intersect(pool)
    }

    /** The rules that watch the calendar [key], for "used by N alarms". */
    fun rulesUsing(key: String, rules: List<EventAlarm>, eventFilters: List<CalendarFilter>): List<EventAlarm> =
        rules.filter { key in ruleSources(it, eventFilters) }

    /** Timed events matching the rule (calendar keywords AND rule keywords). */
    fun matchingEvents(
        rule: EventAlarm,
        events: List<CalendarEvent>,
        eventFilters: List<CalendarFilter>,
    ): List<CalendarEvent> {
        val sources = ruleSources(rule, eventFilters)
        val calendarKeywords = eventFilters.associate { it.sourceKey to it.keywordList() }
        val ruleKeywords = parseKeywords(rule.keywords)
        val single = rule.singleEvent
        val candidates = events.filter { e ->
            !e.allDay && e.sourceKey in sources &&
                matches(e, calendarKeywords[e.sourceKey].orEmpty()) &&
                matches(e, ruleKeywords) &&
                (
                    single == null ||
                        (e.sourceKey == single.sourceKey && e.start == single.start && e.title == single.title)
                    )
        }
        val selected = if (rule.firstEventOfDayOnly && single == null) {
            candidates.groupBy { it.start.toLocalDate() }.values.map { day -> day.minBy { it.start } }
        } else {
            candidates
        }
        return selected.sortedBy { it.start }
    }

    /** Next ring of an event rule strictly after [after]. */
    fun nextEventRing(
        rule: EventAlarm,
        after: LocalDateTime,
        events: List<CalendarEvent>,
        eventFilters: List<CalendarFilter>,
        skipped: Set<LocalDateTime> = emptySet(),
    ): EventRing? = upcomingEventRings(rule, after, events, eventFilters, skipped).firstOrNull()

    fun upcomingEventRings(
        rule: EventAlarm,
        after: LocalDateTime,
        events: List<CalendarEvent>,
        eventFilters: List<CalendarFilter>,
        skipped: Set<LocalDateTime> = emptySet(),
    ): List<EventRing> = matchingEvents(rule, events, eventFilters)
        .map { EventRing(rule.id, it.start.minusMinutes(rule.minutesBefore.toLong()), it) }
        .filter { it.at.isAfter(after) && it.at !in skipped }
        .sortedBy { it.at }
}
