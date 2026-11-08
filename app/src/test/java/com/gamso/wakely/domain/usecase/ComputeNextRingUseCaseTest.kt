package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DayOff
import com.gamso.wakely.domain.model.DayOffMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComputeNextRingUseCaseTest {

    private val compute = ComputeNextRingUseCase()

    // Monday 10 November 2025 20:00; Tuesday 11 November = Armistice Day
    private val now = LocalDateTime.of(2025, 11, 10, 20, 0)
    private val armistice = LocalDate.of(2025, 11, 11)
    private val daysOff = mapOf(armistice to DayOff(armistice, "Armistice"))
    private val alarm = Alarm(id = 1, time = LocalTime.of(7, 0))

    @Test
    fun `holiday skipped - rings next day`() {
        val next = compute(alarm, now, daysOff)!!
        assertEquals(LocalDateTime.of(2025, 11, 12, 7, 0), next.at)
        assertEquals(listOf(DayOff(armistice, "Armistice")), next.skipped)
        assertNull(next.dayOffReason)
    }

    @Test
    fun `holiday - alternative time`() {
        val a = alarm.copy(dayOffMode = DayOffMode.OTHER_TIME, dayOffTime = LocalTime.of(9, 30))
        val next = compute(a, now, daysOff)!!
        assertEquals(LocalDateTime.of(2025, 11, 11, 9, 30), next.at)
        assertEquals("Armistice", next.dayOffReason)
    }

    @Test
    fun `calendar ignored`() {
        val next = compute(alarm.copy(dayOffMode = DayOffMode.IGNORE), now, daysOff)!!
        assertEquals(LocalDateTime.of(2025, 11, 11, 7, 0), next.at)
    }

    @Test
    fun `weekend skipped for weekday alarm`() {
        val friday = LocalDateTime.of(2025, 11, 14, 8, 0)
        val next = compute(alarm, friday, emptyMap())!!
        assertEquals(LocalDateTime.of(2025, 11, 17, 7, 0), next.at)
    }

    @Test
    fun `one-shot alarm skips day off`() {
        val once = alarm.copy(days = emptySet())
        val next = compute(once, now, daysOff)!!
        assertEquals(LocalDateTime.of(2025, 11, 12, 7, 0), next.at)
    }
}
