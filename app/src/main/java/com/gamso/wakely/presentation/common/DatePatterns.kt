package com.gamso.wakely.presentation.common

import android.content.Context
import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date and time formats that follow the conventions of the phone's language (day/month order, separators...) and its
 * 12/24-hour setting.
 */
object DatePatterns {

    /**
     * Formatter for a date [skeleton] such as "EEEdMMM" (weekday, day, short month), ordered the way
     * the current language writes it. Falls back to [fallback] if the platform pattern is not supported.
     */
    fun formatter(skeleton: String, fallback: String): DateTimeFormatter {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return runCatching { DateTimeFormatter.ofPattern(pattern, locale) }
            .getOrElse { DateTimeFormatter.ofPattern(fallback, locale) }
    }

    /** Hours and minutes: "19:30" when the phone is set to 24-hour format, "7:30 PM" otherwise. */
    fun timeFormatter(context: Context): DateTimeFormatter = if (DateFormat.is24HourFormat(context)) {
        DateTimeFormatter.ofPattern(TIME_24)
    } else {
        formatter("hmm", TIME_12)
    }

    private const val TIME_24 = "HH:mm"
    private const val TIME_12 = "h:mm a"
}
