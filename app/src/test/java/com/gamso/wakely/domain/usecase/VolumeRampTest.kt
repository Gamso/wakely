package com.gamso.wakely.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeRampTest {

    @Test
    fun `without a ramp the volume is full at once`() {
        assertEquals(1f, VolumeRamp.level(0, 0), 0f)
        assertEquals(1f, VolumeRamp.level(5_000, 0), 0f)
    }

    @Test
    fun `starts in silence and ends at full volume`() {
        assertEquals(0f, VolumeRamp.level(0, 30), 0f)
        assertEquals(1f, VolumeRamp.level(30_000, 30), 0f)
        assertEquals(1f, VolumeRamp.level(120_000, 30), 0f)
    }

    @Test
    fun `rises without ever going down`() {
        var previous = -1f
        for (ms in 0..60_000 step 250) {
            val level = VolumeRamp.level(ms.toLong(), 60)
            assertTrue("volume went down at $ms ms", level >= previous)
            assertTrue(level in 0f..1f)
            previous = level
        }
    }

    @Test
    fun `is quieter than a straight line at the start`() {
        assertEquals(0.25f, VolumeRamp.level(5_000, 10), 0.0001f)
    }

    @Test
    fun `a clock that goes backwards gives full volume`() {
        assertEquals(1f, VolumeRamp.level(-1, 30), 0f)
    }
}
