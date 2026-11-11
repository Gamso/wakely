package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.parseKeywords

/**
 * The keywords of an event alarm are saved as one comma separated text. These helpers let the editor offer the keywords
 * of the calendars as checkable choices on top of that text, with the same normalisation as [CalendarLogic.matches]:
 * case and accents do not count.
 */
object AlarmKeywords {

    /** The keywords of the calendars the rule watches, without duplicates, in the order of the calendars. */
    fun choices(rule: EventAlarm, eventFilters: List<CalendarFilter>): List<String> {
        val sources = CalendarLogic.ruleSources(rule, eventFilters)
        val seen = mutableSetOf<String>()
        return eventFilters.filter { it.sourceKey in sources }
            .flatMap { it.keywordList() }
            .filter { seen.add(CalendarLogic.normalize(it)) }
    }

    /**
     * What the editor lists: the keywords of the calendars, then the alarm's own keywords that no calendar has (an
     * alarm saved before, or after a calendar lost a keyword), so that every keyword in use can still be unticked.
     */
    fun options(rule: EventAlarm, eventFilters: List<CalendarFilter>): List<String> {
        val choices = choices(rule, eventFilters)
        val known = choices.map { CalendarLogic.normalize(it) }.toSet()
        val own = parseKeywords(rule.keywords).filter { CalendarLogic.normalize(it) !in known }
        return choices + own
    }

    fun isSelected(keywords: String, word: String): Boolean {
        val target = CalendarLogic.normalize(word)
        return parseKeywords(keywords).any { CalendarLogic.normalize(it) == target }
    }

    /** [keywords] with [word] added, or removed when it is already there. */
    fun toggle(keywords: String, word: String): String {
        val target = CalendarLogic.normalize(word)
        val current = parseKeywords(keywords)
        val updated = if (current.any { CalendarLogic.normalize(it) == target }) {
            current.filterNot { CalendarLogic.normalize(it) == target }
        } else {
            current + word
        }
        return updated.joinToString(", ")
    }
}
