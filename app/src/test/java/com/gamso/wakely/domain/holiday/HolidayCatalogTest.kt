package com.gamso.wakely.domain.holiday

import java.io.File
import java.io.InputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the real Jollyday files that the app embeds. */
class HolidayCatalogTest {

    private object FileSource : HolidayDataSource {
        private val dir = File("src/main/assets/holidays")
        private val name = Regex("Holidays_([a-z]{2})\\.xml")

        override fun countries(): List<String> =
            dir.listFiles().orEmpty().mapNotNull { name.matchEntire(it.name)?.groupValues?.get(1)?.uppercase() }

        override fun open(country: String): InputStream? =
            File(dir, "Holidays_${country.lowercase()}.xml").takeIf { it.exists() }?.inputStream()
    }

    private val catalog = HolidayCatalog(FileSource)

    private fun year(country: String, year: Int, region: String? = null): Map<String, LocalDate> =
        catalog.holidays(country, region, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            .associate { it.id to it.date }

    @Test
    fun `easter sunday, western and orthodox`() {
        assertEquals(LocalDate.of(2024, 3, 31), HolidayEvaluator.gregorianEaster(2024))
        assertEquals(LocalDate.of(2025, 4, 20), HolidayEvaluator.gregorianEaster(2025))
        assertEquals(LocalDate.of(2026, 4, 5), HolidayEvaluator.gregorianEaster(2026))
        assertEquals(LocalDate.of(2027, 3, 28), HolidayEvaluator.gregorianEaster(2027))
        assertEquals(LocalDate.of(2024, 5, 5), HolidayEvaluator.julianEaster(2024))
        assertEquals(LocalDate.of(2025, 4, 20), HolidayEvaluator.julianEaster(2025))
        assertEquals(LocalDate.of(2026, 4, 12), HolidayEvaluator.julianEaster(2026))
        assertEquals(LocalDate.of(2027, 5, 2), HolidayEvaluator.julianEaster(2027))
    }

    @Test
    fun `france 2026 national holidays`() {
        assertEquals(
            mapOf(
                "NEW_YEAR" to LocalDate.of(2026, 1, 1),
                "christian.EASTER_MONDAY" to LocalDate.of(2026, 4, 6),
                "LABOUR_DAY" to LocalDate.of(2026, 5, 1),
                "VICTORY_DAY" to LocalDate.of(2026, 5, 8),
                "christian.ASCENSION_DAY" to LocalDate.of(2026, 5, 14),
                "christian.WHIT_MONDAY" to LocalDate.of(2026, 5, 25),
                "NATIONAL_DAY" to LocalDate.of(2026, 7, 14),
                "ASSUMPTION_MARY" to LocalDate.of(2026, 8, 15),
                "ALL_SAINTS" to LocalDate.of(2026, 11, 1),
                "ARMISTICE" to LocalDate.of(2026, 11, 11),
                "CHRISTMAS" to LocalDate.of(2026, 12, 25),
            ),
            year("FR", 2026),
        )
    }

    @Test
    fun `alsace-moselle has two more holidays`() {
        val alsace = year("FR", 2026, region = "67")
        assertEquals(LocalDate.of(2026, 4, 3), alsace["christian.GOOD_FRIDAY"])
        assertEquals(LocalDate.of(2026, 12, 26), alsace["SECOND_CHRISTMAS_DAY"])
        assertEquals(13, alsace.size)
        assertFalse("christian.GOOD_FRIDAY" in year("FR", 2026))
    }

    @Test
    fun `only the regions that add holidays are offered`() {
        assertEquals(listOf("Bas-Rhin", "Haut-Rhin", "Moselle"), catalog.regions("FR").map { it.name })
        assertEquals(listOf("67", "68", "57"), catalog.regions("FR").map { it.path })
    }

    @Test
    fun `an unknown region gives the national holidays`() {
        assertEquals(year("FR", 2026), year("FR", 2026, region = "nope"))
    }

    @Test
    fun `united states observed days, across new year too`() {
        val us = year("US", 2026)
        // 4 July 2026 is a Saturday: observed on Friday 3 July.
        assertEquals(LocalDate.of(2026, 7, 3), us["INDEPENDENCE_DAY"])
        assertEquals(LocalDate.of(2026, 11, 26), us["THANKSGIVING"])
        // 1 January 2022 is a Saturday: observed on Friday 31 December 2021.
        val newYear2022 = catalog.holidays("US", null, LocalDate.of(2021, 12, 31), LocalDate.of(2021, 12, 31))
        assertEquals(listOf("NEW_YEAR"), newYear2022.map { it.id })
    }

    @Test
    fun `germany has regional holidays`() {
        val national = year("DE", 2026)
        assertEquals(LocalDate.of(2026, 10, 3), national["UNIFICATION_GERMANY"])
        assertFalse("EPIPHANY" in national)
        assertEquals(LocalDate.of(2026, 1, 6), year("DE", 2026, region = "by")["EPIPHANY"])
        assertTrue(catalog.regions("DE").any { it.path == "by" })
    }

    @Test
    fun `united kingdom england and wales has the bank holidays`() {
        val england = year("GB", 2026, region = "eng")
        assertEquals(LocalDate.of(2026, 5, 4), england["EARLY_MAY_BANK_HOLIDAY"])
        assertEquals(LocalDate.of(2026, 5, 25), england["SPRING_BANK_HOLIDAY"])
        assertEquals(LocalDate.of(2026, 8, 31), england["SUMMER_BANK_HOLIDAY"])
        // Boxing Day on a Saturday is observed on Monday 28.
        assertEquals(LocalDate.of(2026, 12, 28), england["BOXING_DAY"])
    }

    @Test
    fun `observances are not days off`() {
        // Argentina lists commemorations next to its public holidays.
        val ids = year("AR", 2026).keys
        assertFalse("NATIONAL_DAY_OF_MOURNING_KIRCHNER" in ids)
    }

    @Test
    fun `every embedded country can be read and computed`() {
        val countries = catalog.countries()
        assertTrue("expected about 150 countries, got ${countries.size}", countries.size > 140)
        for (country in countries) {
            val holidays = catalog.holidays(country, null, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31))
            assertTrue("$country has no holiday in 2026-2027", holidays.isNotEmpty())
            // Regions must be readable too.
            catalog.regions(country).forEach { region ->
                catalog.holidays(country, region.path, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
            }
        }
    }

    @Test
    fun `countries without computable rules are not embedded`() {
        // The national holidays of Morocco follow the Islamic calendar.
        assertFalse(catalog.isSupported("MA"))
        assertTrue(catalog.isSupported("fr"))
    }
}
