package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.repository.SettingsRepository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The pause of every alarm, until a date. It ends by itself: rings are computed after the end of the pause, so
 * nothing has to run when it is over, and a date in the past is simply no pause.
 */
object Pause {

    /** [after], moved to the end of the last paused day when the pause is still running. */
    fun clamp(after: LocalDateTime, until: LocalDate?): LocalDateTime {
        if (until == null) return after
        val end = until.plusDays(1).atStartOfDay().minusNanos(1)
        return maxOf(after, end)
    }

    /** The pause as the user should see it: null once its last day is over. */
    fun active(until: LocalDate?, today: LocalDate): LocalDate? = until?.takeIf { !it.isBefore(today) }
}

/** Pauses every alarm up to [until] (inclusive), or resumes them with null. The alarms themselves are not touched. */
class SetPauseUseCase(
    private val settingsRepository: SettingsRepository,
    private val reschedule: RescheduleAlarmsUseCase,
) {
    suspend operator fun invoke(until: LocalDate?) {
        settingsRepository.setPause(until)
        reschedule()
    }
}
