package com.gamso.wakely.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamso.wakely.R
import com.gamso.wakely.WakelyApp
import com.gamso.wakely.di.AppContainer
import com.gamso.wakely.domain.model.Alarm
import com.gamso.wakely.domain.model.DayOffMode
import com.gamso.wakely.domain.model.NextRing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** ViewModel factory wired to the dependency container. */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create((this[APPLICATION_KEY] as WakelyApp).container) }
}

/** Text shown for a day off whose calendar event has no title. */
@Composable
fun dayOffReasonText(reason: String): String = reason.ifBlank { stringResource(R.string.day_off_default) }

/** Dates and texts follow the language of the phone; times are always shown in 24-hour format. */
object Format {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private const val DAYS_IN_WEEK = 7
    private const val MINUTES_PER_HOUR = 60

    val WEEK: List<DayOfWeek> = DayOfWeek.entries

    fun time(t: LocalTime): String = t.format(timeFmt)

    fun dayDate(d: LocalDate): String = d.format(DatePatterns.formatter("EEEdMMM", "EEE d MMM"))

    /** Weekday, day, month and year, e.g. "Mon 25 May 2026". */
    fun fullDate(d: LocalDate): String = d.format(DatePatterns.formatter("EEEdMMMy", "EEE d MMM y"))

    fun dayLetter(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        .uppercase(Locale.getDefault())

    @Composable
    fun daysSummary(days: Set<DayOfWeek>): String = when {
        days.isEmpty() -> stringResource(R.string.summary_once)

        days.size == DAYS_IN_WEEK -> stringResource(R.string.summary_every_day)

        days == Alarm.WORKDAYS -> stringResource(R.string.summary_weekdays)

        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> stringResource(R.string.summary_weekend)

        else -> WEEK.filter { it in days }.joinToString(", ") {
            it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    /** 45 → "45 min", 60 → "1 h", 90 → "1 h 30". */
    fun duration(minutes: Int): String = when {
        minutes < MINUTES_PER_HOUR -> "$minutes min"
        minutes % MINUTES_PER_HOUR == 0 -> "${minutes / MINUTES_PER_HOUR} h"
        else -> "${minutes / MINUTES_PER_HOUR} h ${(minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
    }

    @Composable
    fun dayOffSummary(alarm: Alarm): String = when (alarm.dayOffMode) {
        DayOffMode.SKIP -> stringResource(R.string.days_off_skip)
        DayOffMode.OTHER_TIME -> stringResource(R.string.days_off_other_time, time(alarm.dayOffTime))
        DayOffMode.IGNORE -> stringResource(R.string.days_off_ignore)
    }

    @Composable
    fun nextRing(next: NextRing, now: LocalDateTime = LocalDateTime.now()): String = nextRing(next.at, now)

    @Composable
    fun nextRing(at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String =
        stringResource(R.string.next_ring, dayWord(at, now), time(at.toLocalTime()))

    /** "today", "tomorrow", or the weekday and date. */
    @Composable
    fun dayWord(at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
        val date = at.toLocalDate()
        return when (date) {
            now.toLocalDate() -> stringResource(R.string.today)
            now.toLocalDate().plusDays(1) -> stringResource(R.string.tomorrow)
            else -> dayDate(date)
        }
    }
}
