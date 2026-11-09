package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.model.SkippedOccurrence
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.SettingsRepository
import com.gamso.wakely.domain.repository.SkippedOccurrences
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.LocalDateTime

/** The ring times of [key] that the user skipped. */
fun Set<SkippedOccurrence>.timesOf(key: RingKey): Set<LocalDateTime> = filter { it.key == key }.map { it.at }.toSet()

/**
 * Skips the next ring of a repeating alarm or of an event rule, and only that one: the settings, the days and the
 * enabled switch are not touched, and the ring after it sounds as usual. One-shot alarms and single-event rules have
 * nothing after the skipped ring: switching them off does the same.
 */
class SkipNextRingUseCase(
    private val alarmRepository: AlarmRepository,
    private val eventAlarmRepository: EventAlarmRepository,
    private val getDaysOff: GetDaysOffUseCase,
    private val getRuleEvents: GetRuleEventsUseCase,
    private val computeNextRing: ComputeNextRingUseCase,
    private val skips: SkippedOccurrences,
    private val reschedule: RescheduleAlarmsUseCase,
    private val time: TimeProvider,
    private val settingsRepository: SettingsRepository,
) {
    /** [at] is the ring to skip (from a notification); by default the next one that would sound. */
    suspend operator fun invoke(key: RingKey, at: LocalDateTime? = null) {
        val now = time.now()
        val target = at ?: nextRing(key, now) ?: return
        if (!target.isAfter(now) || !canSkip(key)) return
        skips.add(SkippedOccurrence(key, target))
        reschedule()
    }

    private suspend fun canSkip(key: RingKey): Boolean = when (key.kind) {
        RingKind.ALARM -> alarmRepository.getAlarm(key.id)?.let { it.enabled && it.isRepeating } == true

        RingKind.EVENT -> eventAlarmRepository.getEventAlarm(key.id)?.let { it.enabled && it.singleEvent == null } ==
            true
    }

    private suspend fun nextRing(key: RingKey, now: LocalDateTime): LocalDateTime? {
        val skipped = skips.get().timesOf(key)
        // During a pause, the next ring that would sound is the first one after it.
        val from = Pause.clamp(now, settingsRepository.getPause())
        return when (key.kind) {
            RingKind.ALARM -> alarmRepository.getAlarm(key.id)?.let {
                computeNextRing(it, from, getDaysOff(now.toLocalDate()), skipped = skipped)?.at
            }

            RingKind.EVENT -> eventAlarmRepository.getEventAlarm(key.id)?.let {
                val data = getRuleEvents(now.toLocalDate(), extraSourceKeys = it.sourceKeys)
                CalendarLogic.nextEventRing(it, from, data.events, data.eventFilters, skipped)?.at
            }
        }
    }
}

/** Brings back the ring that was skipped (the earliest one still to come). */
class UndoSkipUseCase(
    private val skips: SkippedOccurrences,
    private val reschedule: RescheduleAlarmsUseCase,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(key: RingKey) {
        val now = time.now()
        val next = skips.get().filter { it.key == key && it.at.isAfter(now) }.minByOrNull { it.at } ?: return
        skips.remove(next)
        reschedule()
    }
}
