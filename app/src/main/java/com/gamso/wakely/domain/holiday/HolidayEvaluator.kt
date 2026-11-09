package com.gamso.wakely.domain.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Computes the date of a holiday rule in a given year, following the rules of Jollyday: validity limits and cycles,
 * Easter-based holidays (Gregorian and Julian), weekday-based holidays and moving ("observed") conditions.
 */
object HolidayEvaluator {

    private const val WEEK = 7

    /** The day off the rule gives in [year] (the observed date when a moving condition applies), or null. */
    fun dateIn(rule: HolidayRule, year: Int): LocalDate? {
        if (!applies(rule.limits, year)) return null
        return when (rule) {
            is FixedRule -> if (rule.monthDay.isValidYear(year)) {
                moved(rule.monthDay.atYear(year), rule.moving)
            } else {
                null
            }

            is ChristianRule -> {
                val easter = if (rule.julian) julianEaster(year) else gregorianEaster(year)
                moved(easter.plusDays(rule.type.daysFromEaster), rule.moving)
            }

            is FixedWeekdayRule -> moved(weekdayInMonth(rule, year), rule.moving)

            is WeekdayBetweenFixedRule -> firstWeekdayBetween(
                rule.weekday,
                rule.from.atYear(year),
                rule.to.atYear(year),
            )

            is WeekdayRelativeToFixedRule -> weekdayRelativeToFixed(rule, year)

            is RelativeToFixedRule -> relativeToFixed(rule, year)

            is RelativeToWeekdayInMonthRule ->
                moveToWeekday(weekdayInMonth(rule.base, year), rule.weekday, rule.relation == Relation.BEFORE)

            is RelativeToEasterRule -> {
                val easter = if (rule.julian) julianEaster(year) else gregorianEaster(year)
                easter.plusDays(rule.days)
            }

            is UnsupportedRule -> null
        }
    }

    /** Validity period and cycle ("every 4 years" counts from `validFrom`, or back from `validTo`). */
    private fun applies(limits: Limits, year: Int): Boolean {
        val from = limits.validFrom
        val to = limits.validTo
        if (from != null && year < from) return false
        if (to != null && year > to) return false
        return when (limits.cycle) {
            Cycle.EVERY_YEAR -> true

            Cycle.ODD_YEARS -> year % 2 != 0

            Cycle.EVEN_YEARS -> year % 2 == 0

            else -> when {
                from != null -> (year - from) % limits.cycle.years == 0
                to != null -> (to - year) % limits.cycle.years == 0
                else -> false
            }
        }
    }

    private fun moved(date: LocalDate, conditions: List<MovingCondition>): LocalDate {
        val condition = conditions.firstOrNull { it.substitute == date.dayOfWeek } ?: return date
        return date.with(
            if (condition.next) {
                TemporalAdjusters.nextOrSame(condition.weekday)
            } else {
                TemporalAdjusters.previousOrSame(condition.weekday)
            },
        )
    }

    private fun weekdayInMonth(rule: FixedWeekdayRule, year: Int): LocalDate {
        val first = LocalDate.of(year, rule.month, 1)
        return if (rule.which == Which.LAST) {
            first.with(TemporalAdjusters.lastInMonth(rule.weekday))
        } else {
            first.with(TemporalAdjusters.dayOfWeekInMonth(rule.which.ordinal + 1, rule.weekday))
        }
    }

    private fun firstWeekdayBetween(weekday: DayOfWeek, from: LocalDate, to: LocalDate): LocalDate? =
        generateSequence(from) { it.plusDays(1) }.take((ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(0))
            .firstOrNull { it.dayOfWeek == weekday }

    private fun weekdayRelativeToFixed(rule: WeekdayRelativeToFixedRule, year: Int): LocalDate {
        val day = rule.day.atYear(year)
        val first = when (rule.relation) {
            Relation.AFTER -> day.with(TemporalAdjusters.next(rule.weekday))
            Relation.BEFORE -> day.with(TemporalAdjusters.previous(rule.weekday))
            Relation.CLOSEST -> closest(day, rule.weekday)
        }
        if (rule.relation == Relation.CLOSEST) return first
        val weeks = when (rule.which) {
            Which.SECOND -> 1L
            Which.THIRD -> 2L
            Which.FOURTH -> 3L
            else -> 0L
        }
        return if (rule.relation == Relation.AFTER) first.plusWeeks(weeks) else first.minusWeeks(weeks)
    }

    private fun closest(date: LocalDate, weekday: DayOfWeek): LocalDate {
        val previous = date.with(TemporalAdjusters.previousOrSame(weekday))
        val next = date.with(TemporalAdjusters.nextOrSame(weekday))
        val previousDays = ChronoUnit.DAYS.between(previous, date)
        val nextDays = ChronoUnit.DAYS.between(date, next)
        return if (previousDays <= nextDays) previous else next
    }

    private fun relativeToFixed(rule: RelativeToFixedRule, year: Int): LocalDate {
        val date = rule.date.atYear(year)
        val before = rule.relation == Relation.BEFORE
        return when {
            rule.weekday != null -> moveToWeekday(date, rule.weekday, before)
            rule.days != null -> if (before) date.minusDays(rule.days.toLong()) else date.plusDays(rule.days.toLong())
            else -> date
        }
    }

    /** The first [target] weekday strictly before/after [date]. */
    private fun moveToWeekday(date: LocalDate, target: DayOfWeek, before: Boolean): LocalDate {
        var difference = target.value - date.dayOfWeek.value
        if (before && difference >= 0) difference -= WEEK
        if (!before && difference <= 0) difference += WEEK
        return date.plusDays(difference.toLong())
    }

    /** Easter Sunday in the Gregorian calendar (Meeus/Jones/Butcher). */
    fun gregorianEaster(year: Int): LocalDate {
        val goldenNumber = year % 19
        val century = year / 100
        val yearInCentury = year % 100
        val centuryCorrection = (century + 8) / 25
        val epact = (19 * goldenNumber + century - century / 4 - (century - centuryCorrection + 1) / 3 + 15) % 30
        val weekdayOffset = (32 + 2 * (century % 4) + 2 * (yearInCentury / 4) - epact - yearInCentury % 4) % 7
        val monthCorrection = (goldenNumber + 11 * epact + 22 * weekdayOffset) / 451
        val daysAfterFebruary = epact + weekdayOffset - 7 * monthCorrection + 114
        return LocalDate.of(year, if (daysAfterFebruary / 31 == 3) 3 else 4, daysAfterFebruary % 31 + 1)
    }

    /** Orthodox Easter Sunday, expressed in the Gregorian calendar. */
    fun julianEaster(year: Int): LocalDate {
        val lunarAge = (19 * (year % 19) + 15) % 30
        val weekdayCorrection = (2 * (year % 4) + 4 * (year % 7) - lunarAge + 34) % 7
        val dayOffset = lunarAge + weekdayCorrection + 114
        val month = if (dayOffset / 31 == 3) 3 else 4
        return julianToGregorian(year, month, dayOffset % 31 + 1)
    }

    /** Julian calendar date to Gregorian date, through the Julian day number. */
    private fun julianToGregorian(year: Int, month: Int, day: Int): LocalDate {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        val julianDay = day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return LocalDate.ofEpochDay(julianDay - JULIAN_DAY_OF_EPOCH)
    }

    /** Julian day number of 1970-01-01. */
    private const val JULIAN_DAY_OF_EPOCH = 2_440_588L
}
