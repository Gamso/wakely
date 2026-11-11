package com.gamso.wakely.presentation.settings

import androidx.annotation.StringRes
import com.gamso.wakely.R

/** The screens of Settings: a short menu, and one screen per topic. [route] is what the navigation stores. */
enum class SettingsPage(val route: String, @StringRes val title: Int) {
    MENU("settings", R.string.settings_title),
    NOTIFICATION("settings/notification", R.string.summary_title),
    QUICK("settings/quick", R.string.quick_buttons_title),
    RING("settings/ring", R.string.ring_settings_title),
    HOLIDAYS("settings/holidays", R.string.holidays_title),
    CALENDARS("settings/calendars", R.string.calendars_title),

    /** One calendar: its route is [CALENDAR_PREFIX] followed by the calendar key. The title is the calendar name. */
    CALENDAR("settings/calendar", R.string.calendars_title),
    ;

    companion object {
        const val CALENDAR_PREFIX = "settings/calendar/"

        /** The page of a navigation route, or null when the route is not a Settings one. */
        fun fromRoute(route: String): SettingsPage? = entries.firstOrNull { it.route == route }
            ?: CALENDAR.takeIf { route.startsWith(CALENDAR_PREFIX) }

        fun calendarRoute(key: String) = CALENDAR_PREFIX + key

        /** The calendar key of a [CALENDAR] route. */
        fun calendarKeyOf(route: String): String? = route.removePrefix(CALENDAR_PREFIX).takeIf {
            route.startsWith(CALENDAR_PREFIX) && it.isNotEmpty()
        }

        /** Where Back goes from [route]: a calendar to the list of calendars, any other topic to the menu. */
        fun parentOf(route: String): String = if (fromRoute(route) == CALENDAR) CALENDARS.route else MENU.route
    }
}

/** The quick buttons that are turned on, for the summary line of the menu. */
enum class QuickButton { SKIP, PAUSE }

fun quickButtonsOn(skip: Boolean, pause: Boolean): List<QuickButton> =
    listOfNotNull(QuickButton.SKIP.takeIf { skip }, QuickButton.PAUSE.takeIf { pause })
