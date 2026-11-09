package com.gamso.wakely.domain.holiday

import java.io.InputStream
import java.time.DayOfWeek
import java.time.Month
import java.time.MonthDay
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reads the holiday definitions of Jollyday (https://github.com/focus-shift/jollyday): one XML file per country,
 * with nested `SubConfigurations` for regions. Element names are compared ignoring case because the schema mixes
 * `Date`/`day`/`When`/`days` styles.
 */
object JollydayXmlParser {

    fun parse(input: InputStream): HolidayConfiguration {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return configuration(factory.newDocumentBuilder().parse(input).documentElement)
    }

    private fun configuration(element: Element): HolidayConfiguration = HolidayConfiguration(
        hierarchy = element.getAttribute("hierarchy"),
        description = element.getAttribute("description"),
        rules = children(element, "holidays").flatMap { holidays -> children(holidays).mapNotNull(::rule) },
        subConfigurations = children(element, "subconfigurations").map(::configuration),
    )

    private fun rule(element: Element): HolidayRule? {
        val name = nameOf(element)
        val limits = Limits(
            validFrom = element.getAttribute("validFrom").toIntOrNull(),
            validTo = element.getAttribute("validTo").toIntOrNull(),
            cycle = enumOrNull<Cycle>(element.getAttribute("every")) ?: Cycle.EVERY_YEAR,
        )
        val kind = enumOrNull<HolidayKind>(element.getAttribute("localizedType")) ?: HolidayKind.PUBLIC_HOLIDAY
        val key = element.getAttribute("descriptionPropertiesKey")
        val moving = children(element, "movingcondition").map {
            MovingCondition(
                substitute = DayOfWeek.valueOf(it.getAttribute("substitute")),
                next = it.getAttribute("with") == "NEXT",
                weekday = DayOfWeek.valueOf(it.getAttribute("weekday")),
            )
        }
        return when (name) {
            "fixed" -> FixedRule(key, limits, kind, monthDay(element), moving)

            "christianholiday" -> {
                val type = ChristianType.valueOf(element.getAttribute("type"))
                // Jollyday names these holidays "christian.<KEY or TYPE>".
                val christianKey = "christian." + key.ifEmpty { type.name }
                ChristianRule(christianKey, limits, kind, type, element.getAttribute("chronology") == "JULIAN", moving)
            }

            "fixedweekday" -> fixedWeekday(element, key, limits, kind, moving)

            "fixedweekdaybetweenfixed" -> WeekdayBetweenFixedRule(
                key,
                limits,
                kind,
                weekday = DayOfWeek.valueOf(element.getAttribute("weekday")),
                from = monthDay(child(element, "from")),
                to = monthDay(child(element, "to")),
            )

            "fixedweekdayrelativetofixed" -> WeekdayRelativeToFixedRule(
                key,
                limits,
                kind,
                which = Which.valueOf(element.getAttribute("which")),
                weekday = DayOfWeek.valueOf(element.getAttribute("weekday")),
                relation = Relation.valueOf(element.getAttribute("when")),
                day = monthDay(child(element, "day")),
            )

            "relativetofixed" -> RelativeToFixedRule(
                key,
                limits,
                kind,
                date = monthDay(child(element, "date")),
                weekday = text(element, "weekday")?.let { DayOfWeek.valueOf(it) },
                days = text(element, "days")?.toInt(),
                relation = text(element, "when")?.let { Relation.valueOf(it) } ?: Relation.AFTER,
            )

            "relativetoweekdayinmonth" -> RelativeToWeekdayInMonthRule(
                key,
                limits,
                kind,
                base = fixedWeekday(child(element, "fixedweekday"), key, limits, kind, emptyList()),
                weekday = DayOfWeek.valueOf(element.getAttribute("weekday")),
                relation = Relation.valueOf(element.getAttribute("when")),
            )

            "relativetoeastersunday" -> RelativeToEasterRule(
                key,
                limits,
                kind,
                julian = text(element, "chronology") == "JULIAN",
                days = text(element, "days")?.toLong() ?: 0L,
            )

            else -> UnsupportedRule(key, limits, kind, name)
        }
    }

    private fun fixedWeekday(
        element: Element,
        key: String,
        limits: Limits,
        kind: HolidayKind,
        moving: List<MovingCondition>,
    ) = FixedWeekdayRule(
        key,
        limits,
        kind,
        which = Which.valueOf(element.getAttribute("which")),
        weekday = DayOfWeek.valueOf(element.getAttribute("weekday")),
        month = Month.valueOf(element.getAttribute("month")),
        moving = moving,
    )

    private fun monthDay(element: Element): MonthDay =
        MonthDay.of(Month.valueOf(element.getAttribute("month")), element.getAttribute("day").toInt())

    private fun nameOf(element: Element): String = (element.localName ?: element.nodeName).lowercase()

    private fun children(element: Element, name: String? = null): List<Element> {
        val result = mutableListOf<Element>()
        var node: Node? = element.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE && (name == null || nameOf(node as Element) == name)) {
                result += node as Element
            }
            node = node.nextSibling
        }
        return result
    }

    private fun child(element: Element, name: String): Element =
        children(element, name).firstOrNull() ?: error("Missing <$name> in <${nameOf(element)}>")

    private fun text(element: Element, name: String): String? =
        children(element, name).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private inline fun <reified E : Enum<E>> enumOrNull(value: String): E? =
        if (value.isEmpty()) null else enumValues<E>().firstOrNull { it.name == value }
}
