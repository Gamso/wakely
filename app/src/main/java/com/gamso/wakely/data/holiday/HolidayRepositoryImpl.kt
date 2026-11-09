package com.gamso.wakely.data.holiday

import android.content.Context
import com.gamso.wakely.domain.holiday.HolidayCatalog
import com.gamso.wakely.domain.holiday.HolidayDataSource
import com.gamso.wakely.domain.holiday.HolidayRegion
import com.gamso.wakely.domain.repository.HolidayRepository
import com.gamso.wakely.domain.repository.PublicHoliday
import java.io.InputStream
import java.time.LocalDate
import java.util.Locale
import java.util.Properties

/** Reads the Jollyday files embedded in the assets (see assets/holidays/SOURCE.txt). */
class AssetHolidayDataSource(private val context: Context) : HolidayDataSource {

    private val countries by lazy {
        context.assets.list(DIR).orEmpty()
            .mapNotNull { FILE.matchEntire(it)?.groupValues?.get(1)?.uppercase() }
    }

    override fun countries(): List<String> = countries

    override fun open(country: String): InputStream? =
        runCatching { context.assets.open("$DIR/Holidays_${country.lowercase()}.xml") }.getOrNull()

    fun descriptions(language: String): Properties = Properties().also { properties ->
        // English first, then the language of the phone on top of it.
        listOf("holiday_descriptions.properties", "holiday_descriptions_$language.properties").forEach { name ->
            runCatching {
                context.assets.open("$DIR/$name").reader(Charsets.UTF_8).use(properties::load)
            }
        }
    }

    private companion object {
        const val DIR = "holidays"
        val FILE = Regex("Holidays_([a-z]{2})\\.xml")
    }
}

/** Holidays from the embedded data, named in the language of the phone (English when a translation is missing). */
class HolidayRepositoryImpl(private val source: AssetHolidayDataSource) : HolidayRepository {

    private val catalog = HolidayCatalog(source)
    private val namesByLanguage = mutableMapOf<String, Properties>()

    /** Read for the language in use now: the language of the app can change while the process lives. */
    @Synchronized
    private fun names(): Properties {
        val language = Locale.getDefault().language
        return namesByLanguage.getOrPut(language) { source.descriptions(language) }
    }

    override fun countries(): List<String> = catalog.countries()

    override fun regions(country: String): List<HolidayRegion> = catalog.regions(country)

    override fun holidays(country: String, region: String?, from: LocalDate, to: LocalDate): List<PublicHoliday> =
        catalog.holidays(country, region, from, to).map { PublicHoliday(it.id, nameOf(it.id), it.date) }

    private fun nameOf(id: String): String =
        names().getProperty("holiday.description.$id")?.trim()?.takeIf { it.isNotEmpty() } ?: prettify(id)

    /** Last resort for a holiday without a description: "BOXING_DAY" becomes "Boxing day". */
    private fun prettify(id: String): String =
        id.substringAfterLast('.').replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
