package com.gamso.wakely.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPageTest {

    @Test
    fun `every page has its own route and is found again from it`() {
        assertEquals(SettingsPage.entries.size, SettingsPage.entries.map { it.route }.toSet().size)
        SettingsPage.entries.forEach { assertEquals(it, SettingsPage.fromRoute(it.route)) }
    }

    @Test
    fun `the menu keeps the route the navigation already used`() {
        assertEquals("settings", SettingsPage.MENU.route)
    }

    @Test
    fun `routes of other screens are not settings pages`() {
        assertNull(SettingsPage.fromRoute("list"))
        assertNull(SettingsPage.fromRoute("about"))
        assertNull(SettingsPage.fromRoute("edit/3/123"))
        assertNull(SettingsPage.fromRoute("settings/unknown"))
    }

    @Test
    fun `a calendar has a route of its own, and goes back to the list of calendars`() {
        val route = SettingsPage.calendarRoute("device:2")
        assertEquals(SettingsPage.CALENDAR, SettingsPage.fromRoute(route))
        assertEquals("device:2", SettingsPage.calendarKeyOf(route))
        assertEquals(SettingsPage.CALENDARS.route, SettingsPage.parentOf(route))
        assertEquals(SettingsPage.MENU.route, SettingsPage.parentOf(SettingsPage.CALENDARS.route))
        assertEquals(SettingsPage.MENU.route, SettingsPage.parentOf(SettingsPage.HOLIDAYS.route))
    }

    @Test
    fun `a calendar route without a key is not a calendar`() {
        assertNull(SettingsPage.calendarKeyOf(SettingsPage.CALENDAR_PREFIX))
        assertNull(SettingsPage.calendarKeyOf("settings/holidays"))
    }

    @Test
    fun `quick buttons summary lists the buttons that are on`() {
        assertEquals(listOf(QuickButton.SKIP, QuickButton.PAUSE), quickButtonsOn(skip = true, pause = true))
        assertEquals(listOf(QuickButton.PAUSE), quickButtonsOn(skip = false, pause = true))
        assertEquals(emptyList<QuickButton>(), quickButtonsOn(skip = false, pause = false))
    }
}
