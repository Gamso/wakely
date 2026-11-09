package com.gamso.wakely.domain.holiday

import com.gamso.wakely.domain.model.HolidaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HolidayConfigTest {

    private val supported = listOf("FR", "DE", "US")

    @Test
    fun `follows the device when nothing was chosen`() {
        assertEquals("FR", HolidayConfig.effectiveCountry(HolidaySettings(), listOf("fr", "US"), supported))
    }

    @Test
    fun `skips a detected country that is not supported`() {
        assertEquals("US", HolidayConfig.effectiveCountry(HolidaySettings(), listOf("MA", "us"), supported))
        assertNull(HolidayConfig.effectiveCountry(HolidaySettings(), listOf("MA"), supported))
    }

    @Test
    fun `a chosen country wins over the device`() {
        val chosen = HolidaySettings(country = "de", countryChosen = true)
        assertEquals("DE", HolidayConfig.effectiveCountry(chosen, listOf("FR"), supported))
    }

    @Test
    fun `none is a choice too`() {
        val none = HolidaySettings(country = null, countryChosen = true)
        assertNull(HolidayConfig.effectiveCountry(none, listOf("FR"), supported))
    }
}
