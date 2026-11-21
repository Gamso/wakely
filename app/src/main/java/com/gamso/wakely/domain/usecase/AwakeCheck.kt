package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkipIfAwake
import com.gamso.wakely.domain.model.SkippedOccurrence
import com.gamso.wakely.domain.model.StepBaseline
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.AwakeSkipNotifier
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.SkippedOccurrences
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

    /** The steps are checked this often in the window, the last time one interval before the ring. */
    val CHECK_INTERVAL: Duration = Duration.ofMinutes(5)

    private val CLOCK_MARGIN: Duration = Duration.ofMinutes(5)

    /**
     * The next step check after [now] for a ring at [ringAt]: every [CHECK_INTERVAL] from [WINDOW] before the ring
     * to one interval before it, or null when the last one is past. The same times whenever it is asked.
     */
    fun nextCheck(ringAt: LocalDateTime, now: LocalDateTime): LocalDateTime? {
        val first = ringAt.minus(WINDOW)
        val next = if (now.isBefore(first)) {
            first
        } else {
            first.plus(
                CHECK_INTERVAL.multipliedBy(Duration.between(first, now).toMillis() / CHECK_INTERVAL.toMillis() + 1),
            )
        }
        return next.takeIf { !it.isAfter(ringAt.minus(CHECK_INTERVAL)) }
    }

    /** Whether the option covers the ring of [alarm] on [ringDate]; [daysOff] are holidays and leave days. */
    fun appliesTo(alarm: Alarm, ringDate: LocalDate, daysOff: Map<LocalDate, DayOff>): Boolean =
        when (alarm.skipIfAwake) {
            SkipIfAwake.OFF -> false
            SkipIfAwake.ALWAYS -> true
            SkipIfAwake.DAYS_OFF_ONLY -> daysOff[ringDate]?.covers(alarm.time) == true
        }

    /** Whether the user walked at least [minSteps] since [baseline], a valid reference for the ring at [ringAt]. */
    fun shouldSkip(baseline: StepBaseline?, current: Long?, ringAt: LocalDateTime, minSteps: Long): Boolean =
        stepsSince(baseline, current, ringAt)?.let { it >= minSteps } == true

    /** Steps taken since [baseline], or null when it is not a valid reference for the ring at [ringAt]. */
    fun stepsSince(baseline: StepBaseline?, current: Long?, ringAt: LocalDateTime): Long? {
        if (baseline == null || current == null) return null
        // The baseline must have been taken in the window before this ring, not for another day.
        if (baseline.takenAt.isBefore(ringAt.minus(WINDOW).minus(CLOCK_MARGIN)) || baseline.takenAt.isAfter(ringAt)) {
            return null
        }
        // A smaller counter means the phone rebooted since: the baseline is meaningless.
        if (current < baseline.steps) return null
        return current - baseline.steps
    }
}

/**
 * Runs every [AwakeCheck.CHECK_INTERVAL] in the [AwakeCheck.WINDOW] before an alarm that can be skipped.
 * The first one remembers the step count; the next ones skip the ring, and tell the user, as soon as
 * the threshold is reached. The check at the ring time stays as a last net.
 */
class StepCheckUseCase(
    private val alarmRepository: AlarmRepository,
    private val sensor: StepSensor,
    private val store: StepBaselineStore,
    private val time: TimeProvider,
    private val skips: SkippedOccurrences,
    private val getDaysOff: GetDaysOffUseCase,
    private val settingsRepository: SettingsRepository,
    private val reschedule: RescheduleAlarmsUseCase,
    private val scheduler: AlarmScheduler,
    private val notifier: AwakeSkipNotifier,
) {
    /** [ringAt] is the ring this check belongs to. */
    suspend operator fun invoke(key: RingKey, ringAt: LocalDateTime) {
        if (key.kind != RingKind.ALARM) return
        val alarm = alarmRepository.getAlarm(key.id)?.takeIf { it.enabled && it.skipIfAwake != SkipIfAwake.OFF }
        val now = time.now()
        val covered = alarm != null && AwakeCheck.appliesTo(alarm, ringAt.toLocalDate(), getDaysOff(now.toLocalDate()))
        // A ring the user already skipped needs no check either.
        if (alarm == null || !covered || ringAt in skips.get().timesOf(key)) {
            store.clear(key)
            return
        }
        val steps = if (sensor.hasPermission()) runCatching { sensor.readSteps() }.getOrNull() else null
        if (steps == null) {
            store.clear(key)
        } else {
            val walked = AwakeCheck.stepsSince(store.get(key), steps, ringAt)
            when {
                // First check, a stale reference or a counter that went down: a fresh reference.
                walked == null -> store.save(key, StepBaseline(steps, now))

                walked >= settingsRepository.getAwakeSensitivity().minSteps -> {
                    skipEarly(alarm, key, ringAt, walked)
                    return
                }
            }
        }
        AwakeCheck.nextCheck(ringAt, now)?.let { scheduler.schedulePreCheck(key, it, ringAt) }
    }

    private suspend fun skipEarly(alarm: Alarm, key: RingKey, ringAt: LocalDateTime, steps: Long) {
        store.clear(key)
        // Like at the ring time: a repeating alarm loses this ring only, a one-shot one is used up.
        if (alarm.isRepeating) {
            skips.add(SkippedOccurrence(key, ringAt))
        } else {
            alarmRepository.save(alarm.copy(enabled = false))
        }
        reschedule()
        notifier.notifySkipped(alarm.label, ringAt, steps)
    }
}
