package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.PendingSnoozes
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.Duration
import java.time.LocalDateTime

/** The snoozes waiting to ring: scheduled with the system and remembered, so they can be cancelled and restored. */
class Snoozes(private val store: PendingSnoozes, private val scheduler: AlarmScheduler) {

    fun schedule(key: RingKey, at: LocalDateTime) {
        store.save(key, at)
        scheduler.scheduleSnooze(key, at)
    }

    /** The user turned the alarm off, edited or deleted it: its snooze no longer rings. */
    fun cancel(key: RingKey) {
        store.clear(key)
        scheduler.cancelSnooze(key)
    }

    /** The snooze rang (or was dropped as too late): nothing is waiting any more. */
    fun fired(key: RingKey) = store.clear(key)

    fun pending(): Map<RingKey, LocalDateTime> = store.getAll()

    /** The system forgot the snooze (reboot): it is scheduled again, at the same time. */
    fun restore(key: RingKey, at: LocalDateTime) = scheduler.scheduleSnooze(key, at)
}

/**
 * After a reboot, an app update or a clock change, schedules the pending snoozes again. A snooze whose alarm was
 * deleted, or too late to ring (see [HandleSnoozeFiredUseCase.MAX_DELAY]), is dropped.
 */
class RestoreSnoozesUseCase(
    private val snoozes: Snoozes,
    private val alarmRepository: AlarmRepository,
    private val eventAlarmRepository: EventAlarmRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke() {
        val now = time.now()
        snoozes.pending().forEach { (key, at) ->
            val exists = when (key.kind) {
                RingKind.ALARM -> alarmRepository.getAlarm(key.id) != null
                RingKind.EVENT -> eventAlarmRepository.getEventAlarm(key.id) != null
            }
            if (exists && Duration.between(at, now) <= HandleSnoozeFiredUseCase.MAX_DELAY) {
                snoozes.restore(key, at)
            } else {
                snoozes.cancel(key)
            }
        }
    }
}
