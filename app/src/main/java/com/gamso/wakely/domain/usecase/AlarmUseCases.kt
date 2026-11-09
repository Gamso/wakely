package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.DayPlan
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.PlannedRing
import com.gamso.wakely.domain.model.RingInfo
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkippedRing
import com.gamso.wakely.domain.model.SummarySettings
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.SkippedOccurrences
import com.gamso.wakely.domain.repository.StepBaselineStore
import com.gamso.wakely.domain.repository.StepSensor
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** (Re)schedules every enabled alarm, event rule and the evening summary with the system. */
class RescheduleAlarmsUseCase(
    private val alarmRepository: AlarmRepository,
    private val eventAlarmRepository: EventAlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val getDaysOff: GetDaysOffUseCase,
    private val getRuleEvents: GetRuleEventsUseCase,
    private val computeNextRing: ComputeNextRingUseCase,
    private val scheduler: AlarmScheduler,
    private val time: TimeProvider,
    private val skips: SkippedOccurrences,
) {
    /**
     * @param fired ring that just fired: it is rescheduled after [firedAt]
     * so the same occurrence does not ring twice.
     */
    suspend operator fun invoke(fired: RingKey? = null, firedAt: LocalDateTime? = null) {
        val now = time.now()
        fun afterFor(key: RingKey) = if (key == fired && firedAt != null && firedAt > now) firedAt else now

        skips.prune(now.minusDays(1))
        val skipped = skips.get()
        val daysOff = getDaysOff(now.toLocalDate())
        alarmRepository.getAlarms().forEach { alarm ->
            val key = RingKey(RingKind.ALARM, alarm.id)
            val next = if (alarm.enabled) {
                computeNextRing(alarm, afterFor(key), daysOff, skipped = skipped.timesOf(key))
            } else {
                null
            }
            if (next != null) scheduler.schedule(key, next.at) else scheduler.cancel(key)
            val checkAt = next?.takeIf { AwakeCheck.appliesTo(alarm, it.at.toLocalDate(), daysOff) }
                ?.at?.minus(AwakeCheck.WINDOW)
            if (checkAt != null && checkAt.isAfter(now)) {
                scheduler.schedulePreCheck(key, checkAt)
            } else {
                scheduler.cancelPreCheck(key)
            }
        }

        val rules = eventAlarmRepository.getEventAlarms()
        val ruleEvents = getRuleEvents(now.toLocalDate(), extraSourceKeys = rules.flatMap { it.sourceKeys }.toSet())
        rules.forEach { rule ->
            val key = RingKey(RingKind.EVENT, rule.id)
            val next = if (rule.enabled) {
                CalendarLogic.nextEventRing(
                    rule,
                    afterFor(key),
                    ruleEvents.events,
                    ruleEvents.eventFilters,
                    skipped.timesOf(key),
                )
            } else {
                null
            }
            if (next != null) scheduler.schedule(key, next.at) else scheduler.cancel(key)
        }

        val summary = settingsRepository.getSummarySettings()
        scheduler.scheduleSummary(
            if (summary.enabled) {
                val today = now.toLocalDate().atTime(summary.time)
                if (today.isAfter(now)) today else today.plusDays(1)
            } else {
                null
            },
        )
    }
}

class SaveAlarmUseCase(
    private val alarmRepository: AlarmRepository,
    private val reschedule: RescheduleAlarmsUseCase,
    private val skips: SkippedOccurrences,
) {
    /** Editing an alarm forgets its skipped ring: the user decided on the old settings. */
    suspend operator fun invoke(alarm: Alarm): Alarm = alarmRepository.save(alarm).also {
        skips.clear(RingKey(RingKind.ALARM, it.id))
        reschedule()
    }
}

class SetAlarmEnabledUseCase(
    private val alarmRepository: AlarmRepository,
    private val reschedule: RescheduleAlarmsUseCase,
) {
    suspend operator fun invoke(alarm: Alarm, enabled: Boolean) {
        alarmRepository.save(alarm.copy(enabled = enabled))
        reschedule()
    }
}

class DeleteAlarmUseCase(
    private val alarmRepository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val skips: SkippedOccurrences,
) {
    suspend operator fun invoke(id: Long) {
        scheduler.cancel(RingKey(RingKind.ALARM, id))
        skips.clear(RingKey(RingKind.ALARM, id))
        alarmRepository.delete(id)
    }
}

class SaveEventAlarmUseCase(
    private val repository: EventAlarmRepository,
    private val reschedule: RescheduleAlarmsUseCase,
    private val skips: SkippedOccurrences,
) {
    suspend operator fun invoke(alarm: EventAlarm): EventAlarm = repository.save(alarm).also {
        skips.clear(RingKey(RingKind.EVENT, it.id))
        reschedule()
    }
}

class SetEventAlarmEnabledUseCase(
    private val repository: EventAlarmRepository,
    private val reschedule: RescheduleAlarmsUseCase,
) {
    suspend operator fun invoke(alarm: EventAlarm, enabled: Boolean) {
        repository.save(alarm.copy(enabled = enabled))
        reschedule()
    }
}

class DeleteEventAlarmUseCase(
    private val repository: EventAlarmRepository,
    private val scheduler: AlarmScheduler,
    private val skips: SkippedOccurrences,
) {
    suspend operator fun invoke(id: Long) {
        scheduler.cancel(RingKey(RingKind.EVENT, id))
        skips.clear(RingKey(RingKind.EVENT, id))
        repository.delete(id)
    }
}

class UpdateCalendarFiltersUseCase(
    private val settingsRepository: SettingsRepository,
    private val reschedule: RescheduleAlarmsUseCase,
) {
    suspend operator fun invoke(filters: List<CalendarFilter>) {
        settingsRepository.setCalendarFilters(filters)
        reschedule()
    }
}

class UpdateSummarySettingsUseCase(
    private val settingsRepository: SettingsRepository,
    private val reschedule: RescheduleAlarmsUseCase,
) {
    suspend operator fun invoke(settings: SummarySettings) {
        settingsRepository.setSummarySettings(settings)
        reschedule()
    }
}

class SnoozeAlarmUseCase(private val scheduler: AlarmScheduler, private val time: TimeProvider) {
    /** Schedules a snooze; does nothing when [minutes] is 0 (snooze disabled). */
    operator fun invoke(key: RingKey, minutes: Int) {
        if (minutes <= 0) return
        scheduler.scheduleSnooze(key, time.now().plusMinutes(minutes.toLong()))
    }
}

/** Builds the ringing info for an alarm or an event rule. */
class GetRingInfoUseCase(
    private val alarmRepository: AlarmRepository,
    private val eventAlarmRepository: EventAlarmRepository,
) {
    suspend operator fun invoke(key: RingKey, eventLabel: String? = null): RingInfo? = when (key.kind) {
        RingKind.ALARM -> alarmRepository.getAlarm(key.id)?.let {
            RingInfo(key, it.label, it.sound, it.snoozeMinutes)
        }

        RingKind.EVENT -> eventAlarmRepository.getEventAlarm(key.id)?.let {
            RingInfo(key, eventLabel ?: it.label, it.sound, it.snoozeMinutes)
        }
    }
}

/**
 * Called when a snooze fires. A snooze that is far too late (device off, clock change)
 * is dropped instead of ringing hours or days later.
 */
class HandleSnoozeFiredUseCase(private val getRingInfo: GetRingInfoUseCase, private val time: TimeProvider) {
    suspend operator fun invoke(key: RingKey, scheduledAt: LocalDateTime?): RingInfo? {
        if (scheduledAt != null && Duration.between(scheduledAt, time.now()) > MAX_DELAY) return null
        return getRingInfo(key)
    }

    companion object {
        private val MAX_DELAY: Duration = Duration.ofMinutes(30)
    }
}

/**
 * Called when a system alarm fires. Re-checks the calendar (it may have
 * changed since scheduling) and decides whether to ring.
 * @return what to ring, or null.
 */
class HandleRingFiredUseCase(
    private val alarmRepository: AlarmRepository,
    private val eventAlarmRepository: EventAlarmRepository,
    private val getDaysOff: GetDaysOffUseCase,
    private val getRuleEvents: GetRuleEventsUseCase,
    private val computeNextRing: ComputeNextRingUseCase,
    private val reschedule: RescheduleAlarmsUseCase,
    private val time: TimeProvider,
    private val stepSensor: StepSensor,
    private val stepBaselines: StepBaselineStore,
    private val skips: SkippedOccurrences,
) {
    suspend operator fun invoke(key: RingKey): RingInfo? {
        val now = time.now()
        val windowStart = now.minus(TOLERANCE)
        // A ring the user skipped is passed over, even if the system still fires it.
        val skipped = skips.get().timesOf(key)
        var expectedAt: LocalDateTime? = null
        val info: RingInfo? = when (key.kind) {
            RingKind.ALARM -> {
                val alarm = alarmRepository.getAlarm(key.id)?.takeIf { it.enabled }
                val daysOff = getDaysOff(now.toLocalDate().minusDays(1))
                val expected = alarm?.let { computeNextRing(it, windowStart, daysOff, skipped = skipped) }
                if (alarm != null && expected != null && isDue(now, expected.at)) {
                    expectedAt = expected.at
                    if (!alarm.isRepeating) alarmRepository.save(alarm.copy(enabled = false))
                    if (alreadyUp(alarm, key, expected.at, daysOff)) {
                        null
                    } else {
                        RingInfo(key, alarm.label, alarm.sound, alarm.snoozeMinutes)
                    }
                } else {
                    null
                }
            }

            RingKind.EVENT -> {
                val rule = eventAlarmRepository.getEventAlarm(key.id)?.takeIf { it.enabled }
                val expected = rule?.let {
                    val data = getRuleEvents(now.toLocalDate().minusDays(1), extraSourceKeys = it.sourceKeys)
                    CalendarLogic.nextEventRing(it, windowStart, data.events, data.eventFilters, skipped)
                }
                if (rule != null && expected != null && isDue(now, expected.at)) {
                    expectedAt = expected.at
                    if (rule.singleEvent != null) eventAlarmRepository.save(rule.copy(enabled = false))
                    val title = rule.label.ifBlank { expected.event.title }
                    RingInfo(
                        key,
                        title,
                        rule.sound,
                        rule.snoozeMinutes,
                        eventTime = expected.event.start.toLocalTime(),
                    )
                } else {
                    null
                }
            }
        }
        reschedule(key, expectedAt)
        return info
    }

    /** True when the option covers this ring and the user walked enough since the step check. */
    private suspend fun alreadyUp(
        alarm: Alarm,
        key: RingKey,
        ringAt: LocalDateTime,
        daysOff: Map<LocalDate, DayOff>,
    ): Boolean {
        val baseline = stepBaselines.get(key)
        stepBaselines.clear(key)
        if (!AwakeCheck.appliesTo(alarm, ringAt.toLocalDate(), daysOff)) return false
        val current = if (baseline != null && stepSensor.hasPermission()) {
            runCatching { stepSensor.readSteps() }.getOrNull()
        } else {
            null
        }
        return AwakeCheck.shouldSkip(baseline, current, ringAt)
    }

    private fun isDue(now: LocalDateTime, at: LocalDateTime) = Duration.between(now, at).abs() <= TOLERANCE

    companion object {
        private val TOLERANCE: Duration = Duration.ofMinutes(5)
    }
}

/** Lists what will ring on a given day (used by the evening summary). */
class GetDayPlanUseCase(
    private val alarmRepository: AlarmRepository,
    private val eventAlarmRepository: EventAlarmRepository,
    private val getDaysOff: GetDaysOffUseCase,
    private val getRuleEvents: GetRuleEventsUseCase,
    private val computeNextRing: ComputeNextRingUseCase,
    private val time: TimeProvider,
    private val skips: SkippedOccurrences,
) {
    suspend operator fun invoke(date: LocalDate): DayPlan {
        val now = time.now()
        val dayStart = date.atStartOfDay().minusNanos(1)
        val daysOff = getDaysOff(now.toLocalDate())
        val skippedByUser = skips.get()
        val rings = mutableListOf<PlannedRing>()
        val skipped = mutableListOf<SkippedRing>()

        alarmRepository.getAlarms().filter { it.enabled }.forEach { alarm ->
            val after = if (alarm.isRepeating) maxOf(dayStart, now) else now
            val key = RingKey(RingKind.ALARM, alarm.id)
            val next = computeNextRing(alarm, after, daysOff, skipped = skippedByUser.timesOf(key))
            if (next != null && next.at.toLocalDate() == date) {
                rings += PlannedRing(next.at, alarm.label, dayOffReason = next.dayOffReason, key = key)
            } else {
                val off = daysOff[date]
                val normallyRings = !alarm.isRepeating || date.dayOfWeek in alarm.days
                if (off != null && normallyRings && alarm.dayOffMode == DayOffMode.SKIP) {
                    skipped += SkippedRing(alarm.label, alarm.time, off.reason)
                }
            }
        }

        val rules = eventAlarmRepository.getEventAlarms().filter { it.enabled }
        if (rules.isNotEmpty()) {
            val data = getRuleEvents(now.toLocalDate(), extraSourceKeys = rules.flatMap { it.sourceKeys }.toSet())
            rules.forEach { rule ->
                val key = RingKey(RingKind.EVENT, rule.id)
                CalendarLogic.upcomingEventRings(
                    rule,
                    maxOf(dayStart, now),
                    data.events,
                    data.eventFilters,
                    skippedByUser.timesOf(key),
                )
                    .filter { it.at.toLocalDate() == date }
                    .forEach { ring -> rings += PlannedRing(ring.at, rule.label, event = ring.event, key = key) }
            }
        }
        return DayPlan(date, rings.sortedBy { it.at }, skipped)
    }
}
