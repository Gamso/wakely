package com.gamso.wakely.domain.holiday

import java.io.InputStream
import java.time.LocalDate

/** Where the Jollyday definition files come from (the app's assets; plain files in tests). */
interface HolidayDataSource {
    /** ISO 3166-1 alpha-2 codes (upper case) of the countries that have a definition file. */
    fun countries(): List<String>

    fun open(country: String): InputStream?
}

/** A public holiday: its stable [id] (the key of its translated name) and its [date]. */
data class Holiday(val id: String, val date: LocalDate)

/** A region of a country with holidays of its own. [path] identifies it ("67", or "by/ag" for nested ones). */
data class HolidayRegion(val path: String, val name: String)

/**
 * Countries, regions and holidays computed from the Jollyday definitions. Definitions are parsed on demand and kept.
 * Only public holidays count: bank holidays and observances are not days off. Regions that need rules Wakely cannot
 * compute exactly (Islamic and Ethiopian calendars) are not offered.
 */
class HolidayCatalog(private val source: HolidayDataSource) {

    private val parsed = mutableMapOf<String, HolidayConfiguration?>()

    fun countries(): List<String> = source.countries().map { it.uppercase() }.distinct().sorted()

    fun isSupported(country: String): Boolean = country.uppercase() in countries()

    /** The regions of [country] that add holidays to the national ones, by name. */
    fun regions(country: String): List<HolidayRegion> {
        val root = configuration(country) ?: return emptyList()
        val regions = mutableListOf<HolidayRegion>()

        fun walk(parent: HolidayConfiguration, parentPath: String?, parentName: String?) {
            for (sub in parent.subConfigurations) {
                if (sub.rules.any { it is UnsupportedRule }) continue
                val path = listOfNotNull(parentPath, sub.hierarchy).joinToString("/")
                val name = listOfNotNull(parentName, sub.description).joinToString(" › ")
                if (sub.rules.any { it.kind == HolidayKind.PUBLIC_HOLIDAY }) regions += HolidayRegion(path, name)
                walk(sub, path, name)
            }
        }
        if (root.rules.none { it is UnsupportedRule }) walk(root, null, null)
        return regions.sortedBy { it.name }
    }

    /**
     * Holidays of [country] (and of [region], if any) between [from] and [to] inclusive, sorted by date.
     * An unknown country or region gives the national holidays, or nothing.
     */
    fun holidays(country: String, region: String?, from: LocalDate, to: LocalDate): List<Holiday> {
        val root = configuration(country) ?: return emptyList()
        val rules = (listOf(root) + (region?.let { chain(root, it) } ?: emptyList()))
            .flatMap { it.rules }
            .filter { it !is UnsupportedRule && it.kind == HolidayKind.PUBLIC_HOLIDAY }
        // A holiday observed on a weekend can move across New Year: one year of margin on each side.
        return (from.year - 1..to.year + 1)
            .flatMap { year ->
                rules.mapNotNull { rule -> HolidayEvaluator.dateIn(rule, year)?.let { Holiday(rule.key, it) } }
            }
            .filter { it.date in from..to }
            .distinct()
            .sortedWith(compareBy({ it.date }, { it.id }))
    }

    /** The regions from the country down to [path], or empty if the path does not exist. */
    private fun chain(root: HolidayConfiguration, path: String): List<HolidayConfiguration> {
        val result = mutableListOf<HolidayConfiguration>()
        var current = root
        for (code in path.split('/')) {
            current = current.subConfigurations.firstOrNull { it.hierarchy == code } ?: return emptyList()
            result += current
        }
        return result
    }

    @Synchronized
    private fun configuration(country: String): HolidayConfiguration? = parsed.getOrPut(country.uppercase()) {
        runCatching { source.open(country.uppercase())?.use(JollydayXmlParser::parse) }.getOrNull()
    }
}
