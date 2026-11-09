package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.EventRing
import com.gamso.wakely.domain.model.parseKeywords
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime

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
            for (filter in bySource[event.sourceKey].orEmpty()) {
                if (!matches(event, filter.keywordList())) continue
                // The title may be blank: the UI shows a localized default.
                coveredDates(event).filter { it in from..to }.forEach { date ->
                    result.putIfAbsent(date, DayOff(date, event.title))
                }
            }
        }
        return result
    }

    /** Calendars an event rule watches: its own selection, or every "events" calendar. */
    fun ruleSources(rule: EventAlarm, eventFilters: List<CalendarFilter>): Set<String> =
        rule.sourceKeys.ifEmpty { eventFilters.map { it.sourceKey }.toSet() }

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
    ): EventRing? = upcomingEventRings(rule, after, events, eventFilters).firstOrNull()

    fun upcomingEventRings(
        rule: EventAlarm,
        after: LocalDateTime,
        events: List<CalendarEvent>,
        eventFilters: List<CalendarFilter>,
    ): List<EventRing> = matchingEvents(rule, events, eventFilters)
        .map { EventRing(rule.id, it.start.minusMinutes(rule.minutesBefore.toLong()), it) }
        .filter { it.at.isAfter(after) }
        .sortedBy { it.at }
}
