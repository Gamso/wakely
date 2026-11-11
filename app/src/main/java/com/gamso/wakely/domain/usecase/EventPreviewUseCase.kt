package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarEvent
import com.gamso.wakely.domain.model.parseKeywords
import com.gamso.wakely.domain.repository.CalendarRepository
import java.time.LocalDateTime

/** A timed event of a calendar, as shown in the preview. */
data class EventPreviewItem(val title: String, val start: LocalDateTime, val end: LocalDateTime)

/** The upcoming events the alarms see in a calendar: the ones its keywords accept, and the ones they leave out. */
data class EventPreview(val counted: List<EventPreviewItem>, val ignored: List<EventPreviewItem>)

/**
 * Shows, for one calendar and the keywords as typed, which upcoming events the event alarms see, so the filter can be
 * checked by eye. Only events with a time count (all-day events belong to the leave calendars), with the same keyword
 * rule as the alarms ([CalendarLogic.matches]) and the same horizon.
 */
class GetEventPreviewUseCase(private val calendarRepository: CalendarRepository) {

    suspend operator fun invoke(
        calendarKey: String,
        keywords: String,
        after: LocalDateTime,
        days: Int = GetRuleEventsUseCase.EVENT_HORIZON_DAYS,
    ): EventPreview {
        val from = after.toLocalDate()
        val events = runCatching {
            calendarRepository.getEvents(setOf(calendarKey), from, from.plusDays(days.toLong()))
        }
            .getOrDefault(emptyList())
            .filter { !it.allDay && it.start.isAfter(after) }
            .sortedBy { it.start }
        val words = parseKeywords(keywords)
        val (counted, ignored) = events.partition { CalendarLogic.matches(it, words) }
        fun item(e: CalendarEvent) = EventPreviewItem(e.title, e.start, e.end)
        return EventPreview(counted.map(::item).distinct(), ignored.map(::item).distinct())
    }
}
