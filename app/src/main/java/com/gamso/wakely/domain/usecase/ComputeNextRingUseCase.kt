package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.NextRing
import java.time.LocalDate
import java.time.LocalDateTime

/** Pure logic: computes an alarm's next ring, taking days off into account. */
class ComputeNextRingUseCase {

    operator fun invoke(
        alarm: Alarm,
        after: LocalDateTime,
        daysOff: Map<LocalDate, DayOff>,
        horizonDays: Int = HORIZON_DAYS,
        /** Ring times the user skipped: they are passed over. */
        skipped: Set<LocalDateTime> = emptySet(),
    ): NextRing? {
        val skippedDaysOff = mutableListOf<DayOff>()
        var date = after.toLocalDate()
        repeat(horizonDays) {
            if (!alarm.isRepeating || date.dayOfWeek in alarm.days) {
                // A partial day off only replaces the ring when it is already running at the alarm's normal time.
                val off = if (alarm.dayOffMode == DayOffMode.IGNORE) {
                    null
                } else {
                    daysOff[date]?.takeIf { it.covers(alarm.time) }
                }
                when {
                    off == null -> {
                        val at = date.atTime(alarm.time)
                        if (at.isAfter(after) && at !in skipped) return NextRing(alarm.id, at, null, skippedDaysOff)
                    }

                    alarm.dayOffMode == DayOffMode.SKIP -> {
                        if (date.atTime(alarm.time).isAfter(after)) skippedDaysOff += off
                    }

                    else -> {
                        val at = date.atTime(alarm.dayOffTime)
                        if (at.isAfter(after) && at !in skipped) {
                            return NextRing(alarm.id, at, off.reason, skippedDaysOff)
                        }
                    }
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    companion object {
        const val HORIZON_DAYS = 400
    }
}
