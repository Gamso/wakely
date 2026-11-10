package com.gamso.wakely.presentation.settings

import androidx.annotation.StringRes
import com.gamso.wakely.R

/** The screens of Settings: a short menu, and one screen per topic. [route] is what the navigation stores. */
enum class SettingsPage(val route: String, @StringRes val title: Int) {
    MENU("settings", R.string.settings_title),
    NOTIFICATION("settings/notification", R.string.summary_title),
    QUICK("settings/quick", R.string.quick_buttons_title),
    HOLIDAYS("settings/holidays", R.string.holidays_title),
    LEAVE("settings/leave", R.string.cat_leave_title),
    EVENTS("settings/events", R.string.cat_events_title),
    ;

    companion object {
        /** The page of a navigation route, or null when the route is not a Settings one. */
        fun fromRoute(route: String): SettingsPage? = entries.firstOrNull { it.route == route }
    }
}

/** The quick buttons that are turned on, for the summary line of the menu. */
enum class QuickButton { SKIP, PAUSE }

fun quickButtonsOn(skip: Boolean, pause: Boolean): List<QuickButton> =
    listOfNotNull(QuickButton.SKIP.takeIf { skip }, QuickButton.PAUSE.takeIf { pause })
