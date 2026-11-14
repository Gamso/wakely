package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeAlarmUseCaseTest {

    private class FakeScheduler : AlarmScheduler {
        val snoozes = mutableListOf<Pair<RingKey, LocalDateTime>>()
        override fun schedule(key: RingKey, at: LocalDateTime) = Unit
        override fun scheduleSnooze(key: RingKey, at: LocalDateTime) {
            snoozes += key to at
        }
        override fun cancel(key: RingKey) = Unit
        override fun cancelSnooze(key: RingKey) = Unit
        override fun schedulePreCheck(key: RingKey, at: LocalDateTime) = Unit
        override fun cancelPreCheck(key: RingKey) = Unit
        override fun scheduleSummary(at: LocalDateTime?) = Unit
        override fun canScheduleExactAlarms() = true
    }

    private val now = LocalDateTime.of(2026, 11, 9, 7, 0)
    private val scheduler = FakeScheduler()
    private val snooze = SnoozeAlarmUseCase(Snoozes(FakePendingSnoozes(), scheduler), TimeProvider { now })
    private val key = RingKey(RingKind.EVENT, 3)

    @Test
    fun `snooze uses configured minutes`() {
        snooze(key, minutes = 15)
        assertEquals(listOf(key to now.plusMinutes(15)), scheduler.snoozes)
    }

    @Test
    fun `disabled snooze schedules nothing`() {
        snooze(key, minutes = 0)
        assertTrue(scheduler.snoozes.isEmpty())
    }
}
