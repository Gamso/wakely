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
    fun `quick buttons summary lists the buttons that are on`() {
        assertEquals(listOf(QuickButton.SKIP, QuickButton.PAUSE), quickButtonsOn(skip = true, pause = true))
        assertEquals(listOf(QuickButton.PAUSE), quickButtonsOn(skip = false, pause = true))
        assertEquals(emptyList<QuickButton>(), quickButtonsOn(skip = false, pause = false))
    }
}
