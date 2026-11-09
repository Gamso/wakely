package com.gamso.wakely.domain.holiday

import java.time.DayOfWeek
import java.time.Month
import java.time.MonthDay

/** What a holiday is, as Jollyday classifies it. Only public holidays are days off. */
enum class HolidayKind { PUBLIC_HOLIDAY, BANK_HOLIDAY, OBSERVANCE }

/** How often a rule applies. The "N years" cycles count from [Limits.validFrom] (or back from `validTo`). */
enum class Cycle(val years: Int) {
    EVERY_YEAR(1),
    ODD_YEARS(2),
    EVEN_YEARS(2),
    TWO_YEARS(2),
    THREE_YEARS(3),
    FOUR_YEARS(4),
    FIVE_YEARS(5),
    SIX_YEARS(6),
}

enum class Relation { BEFORE, AFTER, CLOSEST }

enum class Which { FIRST, SECOND, THIRD, FOURTH, LAST }

/** Christian holidays, all defined relative to Easter Sunday. */
enum class ChristianType(val daysFromEaster: Long) {
    CLEAN_MONDAY(-48),
    SHROVE_MONDAY(-48),
    MARDI_GRAS(-47),
    CARNIVAL(-47),
    ASH_WEDNESDAY(-46),
    PALM_SUNDAY(-7),
    MAUNDY_THURSDAY(-3),
    GOOD_FRIDAY(-2),
    EASTER_SATURDAY(-1),
    EASTER(0),
    EASTER_MONDAY(1),
    EASTER_TUESDAY(2),
    GENERAL_PRAYER_DAY(26),
    ASCENSION_DAY(39),
    PENTECOST(49),
    WHIT_SUNDAY(49),
    WHIT_MONDAY(50),
    PENTECOST_MONDAY(50),
    CORPUS_CHRISTI(60),
    SACRED_HEART(68),
}

/** "When the holiday falls on [substitute], observe it on the next/previous [weekday]". */
data class MovingCondition(val substitute: DayOfWeek, val next: Boolean, val weekday: DayOfWeek)

data class Limits(val validFrom: Int? = null, val validTo: Int? = null, val cycle: Cycle = Cycle.EVERY_YEAR)

/** A rule that yields at most one date per year. [key] identifies the holiday (and its translated name). */
sealed interface HolidayRule {
    val key: String
    val limits: Limits
    val kind: HolidayKind
}

data class FixedRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val monthDay: MonthDay,
    val moving: List<MovingCondition>,
) : HolidayRule

data class ChristianRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val type: ChristianType,
    val julian: Boolean,
    val moving: List<MovingCondition>,
) : HolidayRule

data class FixedWeekdayRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val which: Which,
    val weekday: DayOfWeek,
    val month: Month,
    val moving: List<MovingCondition>,
) : HolidayRule

data class WeekdayBetweenFixedRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val weekday: DayOfWeek,
    val from: MonthDay,
    val to: MonthDay,
) : HolidayRule

data class WeekdayRelativeToFixedRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val which: Which,
    val weekday: DayOfWeek,
    val relation: Relation,
    val day: MonthDay,
) : HolidayRule

data class RelativeToFixedRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val date: MonthDay,
    val weekday: DayOfWeek?,
    val days: Int?,
    val relation: Relation,
) : HolidayRule

data class RelativeToWeekdayInMonthRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val base: FixedWeekdayRule,
    val weekday: DayOfWeek,
    val relation: Relation,
) : HolidayRule

data class RelativeToEasterRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val julian: Boolean,
    val days: Long,
) : HolidayRule

/** A rule Wakely cannot compute exactly (Islamic and Ethiopian calendars). Never evaluated. */
data class UnsupportedRule(
    override val key: String,
    override val limits: Limits,
    override val kind: HolidayKind,
    val name: String,
) : HolidayRule

/**
 * The holidays of a country or of one of its regions. A region adds its own [rules] to those of its parents.
 * [hierarchy] is the region's code within its parent (e.g. "67"), [description] its name.
 */
data class HolidayConfiguration(
    val hierarchy: String,
    val description: String,
    val rules: List<HolidayRule>,
    val subConfigurations: List<HolidayConfiguration>,
)
