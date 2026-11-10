package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.repository.CalendarRepository
import java.time.LocalDate

/** An event of a leave calendar, from [first] to [last] (inclusive), and whether it gives days off. */
data class LeaveItem(val title: String, val first: LocalDate, val last: LocalDate)

/** What the leave calendars give: the events that count, the ones the keywords left out, and the days counted. */
data class LeavePreview(val counted: List<LeaveItem>, val ignored: List<LeaveItem>, val days: Int)

/**
 * Shows, for the leave calendars and their keywords as typed, which events become days off, so a mixed calendar
 * can be tuned by eye. It applies the same rule as the real days off ([CalendarLogic.countedDays]).
 */
class GetLeavePreviewUseCase(private val calendarRepository: CalendarRepository) {

    /** [filters] are the calendar filters as being edited; only the day-off categories are looked at. */
    suspend operator fun invoke(
        filters: List<CalendarFilter>,
        from: LocalDate,
        days: Int = HORIZON_DAYS,
    ): LeavePreview {
        val leave = filters.filter { it.category.isDayOff }
        if (leave.isEmpty()) return LeavePreview(emptyList(), emptyList(), 0)
        val to = from.plusDays(days.toLong())
        val events = runCatching { calendarRepository.getEvents(leave.map { it.sourceKey }.toSet(), from, to) }
            .getOrDefault(emptyList())
        val bySource = leave.groupBy { it.sourceKey }
        val counted = mutableListOf<LeaveItem>()
        val ignored = mutableListOf<LeaveItem>()
        val countedDays = sortedSetOf<LocalDate>()
        for (event in events.sortedBy { it.start }) {
            val covered = CalendarLogic.countedDays(event, bySource[event.sourceKey].orEmpty()).filter {
                it in from..to
            }
            if (covered.isNotEmpty()) {
                counted += LeaveItem(event.title, covered.min(), covered.max())
                countedDays += covered
            } else {
                val last = if (event.allDay) event.end.toLocalDate().minusDays(1) else event.end.toLocalDate()
                val first = event.start.toLocalDate()
                ignored += LeaveItem(event.title, first, maxOf(first, last))
            }
        }
        return LeavePreview(counted.distinct(), ignored.distinct(), countedDays.size)
    }

    companion object {
        const val HORIZON_DAYS = 365
    }
}
