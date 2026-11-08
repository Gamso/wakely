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
    ): NextRing? {
        val skipped = mutableListOf<DayOff>()
        var date = after.toLocalDate()
        repeat(horizonDays) {
            if (!alarm.isRepeating || date.dayOfWeek in alarm.days) {
                val off = if (alarm.dayOffMode == DayOffMode.IGNORE) null else daysOff[date]
                when {
                    off == null -> {
                        val at = date.atTime(alarm.time)
                        if (at.isAfter(after)) return NextRing(alarm.id, at, null, skipped)
                    }

                    alarm.dayOffMode == DayOffMode.SKIP -> {
                        if (date.atTime(alarm.time).isAfter(after)) skipped += off
                    }

                    else -> {
                        val at = date.atTime(alarm.dayOffTime)
                        if (at.isAfter(after)) return NextRing(alarm.id, at, off.reason, skipped)
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
