package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkipIfAwake
import com.gamso.wakely.domain.model.StepBaseline
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.StepBaselineStore
import com.gamso.wakely.domain.repository.StepSensor
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Decides whether the user is already up before an alarm. The alarm must never be skipped by mistake:
 * every doubt (no baseline, a baseline from another time, a counter that went down after a reboot, no reading)
 * means it rings.
 */
object AwakeCheck {
    /** The steps are counted over this time before the ring. */
    val WINDOW: Duration = Duration.ofMinutes(30)

    /** Steps in the window from which the user is considered up. */
    const val MIN_STEPS = 25L

    private val CLOCK_MARGIN: Duration = Duration.ofMinutes(5)

    /** Whether the option covers the ring of [alarm] on [ringDate]; [daysOff] are holidays and leave days. */
    fun appliesTo(alarm: Alarm, ringDate: LocalDate, daysOff: Map<LocalDate, DayOff>): Boolean =
        when (alarm.skipIfAwake) {
            SkipIfAwake.OFF -> false
            SkipIfAwake.ALWAYS -> true
            SkipIfAwake.DAYS_OFF_ONLY -> ringDate in daysOff
        }

    fun shouldSkip(baseline: StepBaseline?, current: Long?, ringAt: LocalDateTime): Boolean {
        if (baseline == null || current == null) return false
        // The baseline must have been taken in the window before this ring, not for another day.
        if (baseline.takenAt.isBefore(ringAt.minus(WINDOW).minus(CLOCK_MARGIN)) || baseline.takenAt.isAfter(ringAt)) {
            return false
        }
        // A smaller counter means the phone rebooted since: the baseline is meaningless.
        if (current < baseline.steps) return false
        return current - baseline.steps >= MIN_STEPS
    }
}

/** Runs [AwakeCheck.WINDOW] before an alarm that can be skipped: remembers the step count. */
class RecordStepBaselineUseCase(
    private val alarmRepository: AlarmRepository,
    private val sensor: StepSensor,
    private val store: StepBaselineStore,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(key: RingKey) {
        if (key.kind != RingKind.ALARM) return
        val alarm = alarmRepository.getAlarm(key.id)?.takeIf { it.enabled && it.skipIfAwake != SkipIfAwake.OFF }
        if (alarm == null) {
            store.clear(key)
            return
        }
        val steps = if (sensor.hasPermission()) runCatching { sensor.readSteps() }.getOrNull() else null
        if (steps != null) store.save(key, StepBaseline(steps, time.now())) else store.clear(key)
    }
}
