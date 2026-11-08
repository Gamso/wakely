package com.gamso.wakely.presentation.common

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date formats that follow the conventions of the phone's language (day/month order, separators...). */
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
}
