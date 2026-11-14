package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.EventAlarm
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.repository.AlarmRepository
import com.gamso.wakely.domain.repository.EventAlarmRepository
import com.gamso.wakely.domain.repository.TimeProvider
import java.time.LocalDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandleSnoozeFiredUseCaseTest {

    private val alarm = Alarm(id = 7, label = "Matin", snoozeMinutes = 5)
    private val alarms = object : AlarmRepository {
        override fun observeAlarms() = flowOf(listOf(alarm))
        override suspend fun getAlarms() = listOf(alarm)
        override suspend fun getAlarm(id: Long) = alarm.takeIf { it.id == id }
        override suspend fun save(alarm: Alarm) = alarm
        override suspend fun delete(id: Long) = Unit
    }
    private val rules = object : EventAlarmRepository {
        override fun observeEventAlarms() = flowOf(emptyList<EventAlarm>())
        override suspend fun getEventAlarms() = emptyList<EventAlarm>()
        override suspend fun getEventAlarm(id: Long): EventAlarm? = null
        override suspend fun save(alarm: EventAlarm) = alarm
        override suspend fun delete(id: Long) = Unit
    }
    private val key = RingKey(RingKind.ALARM, 7)
    private val scheduled = LocalDateTime.of(2026, 11, 9, 7, 10)

    private fun handler(now: LocalDateTime) = HandleSnoozeFiredUseCase(
        GetRingInfoUseCase(alarms, rules),
        TimeProvider { now },
        Snoozes(FakePendingSnoozes(), FakeScheduler()),
    )

    @Test
    fun `snooze on time rings`() = runBlocking {
        val info = handler(scheduled.plusSeconds(2))(key, scheduled)!!
        assertEquals("Matin", info.label)
        assertEquals(5, info.snoozeMinutes)
    }

    @Test
    fun `stale snooze is dropped`() = runBlocking {
        assertNull(handler(scheduled.plusDays(2))(key, scheduled))
    }
}
