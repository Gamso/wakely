package com.gamso.wakely.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

object Format {
    private val locale = Locale.FRENCH
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dayDateFmt = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val longDateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)

    val WEEK: List<DayOfWeek> = DayOfWeek.entries

    fun time(t: LocalTime): String = t.format(timeFmt)

    fun dayDate(d: LocalDate): String = d.format(dayDateFmt)

    fun longDate(d: LocalDate): String = d.format(longDateFmt).replaceFirstChar { it.titlecase(locale) }

    fun dayLetter(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)

    fun daysSummary(days: Set<DayOfWeek>): String = when {
        days.isEmpty() -> "Une seule fois"

        days.size == 7 -> "Tous les jours"

        days == Alarm.WORKDAYS -> "Du lundi au vendredi"

        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Le week-end"

        else -> WEEK.filter { it in days }.joinToString(", ") {
            it.getDisplayName(TextStyle.SHORT, locale)
        }
    }

    /** 45 → "45 min", 60 → "1 h", 90 → "1 h 30". */
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
    }

    fun dayOffSummary(alarm: Alarm): String = when (alarm.dayOffMode) {
        DayOffMode.SKIP -> "Jours non travaillés : ne sonne pas"
        DayOffMode.OTHER_TIME -> "Jours non travaillés : sonne à ${time(alarm.dayOffTime)}"
        DayOffMode.IGNORE -> "Agenda ignoré"
    }

    fun nextRing(next: NextRing, now: LocalDateTime = LocalDateTime.now()): String = nextRing(next.at, now)

    fun nextRing(at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
        val date = at.toLocalDate()
        val day = when (date) {
            now.toLocalDate() -> "aujourd'hui"
            now.toLocalDate().plusDays(1) -> "demain"
            else -> dayDate(date)
        }
        return "Prochaine sonnerie : $day à ${time(at.toLocalTime())}"
    }
}
