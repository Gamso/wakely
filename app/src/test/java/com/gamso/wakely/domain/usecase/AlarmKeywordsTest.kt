package com.gamso.wakely.domain.usecase

import com.gamso.wakely.domain.model.CalendarCategory
import com.gamso.wakely.domain.model.CalendarFilter
import com.gamso.wakely.domain.model.EventAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmKeywordsTest {

    private fun events(key: String, keywords: String) = CalendarFilter(key, CalendarCategory.EVENTS, keywords)

    private val filters = listOf(events("work", "Vermifuge, Veto"), events("perso", "veto, Anti puce"))

    @Test
    fun `choices are the keywords of the calendars, once each, in order`() {
        assertEquals(listOf("Vermifuge", "Veto", "Anti puce"), AlarmKeywords.choices(EventAlarm(), filters))
    }

    @Test
    fun `choices only come from the calendars the alarm watches`() {
        val rule = EventAlarm(sourceKeys = setOf("perso"))
        assertEquals(listOf("veto", "Anti puce"), AlarmKeywords.choices(rule, filters))
    }

    @Test
    fun `a calendar without keywords gives no choice`() {
        assertTrue(AlarmKeywords.choices(EventAlarm(), listOf(events("work", ""))).isEmpty())
    }

    @Test
    fun `ticking adds the keyword to the saved text, unticking removes it`() {
        assertEquals("Veto", AlarmKeywords.toggle("", "Veto"))
        assertEquals("Veto, Anti puce", AlarmKeywords.toggle("Veto", "Anti puce"))
        assertEquals("Anti puce", AlarmKeywords.toggle("Veto, Anti puce", "Veto"))
        assertEquals("", AlarmKeywords.toggle("Veto", "Veto"))
    }

    @Test
    fun `case and accents do not matter`() {
        assertTrue(AlarmKeywords.isSelected("vétérinaire, dentiste", "Veterinaire"))
        assertFalse(AlarmKeywords.isSelected("dentiste", "Veterinaire"))
        assertEquals("dentiste", AlarmKeywords.toggle("vétérinaire, dentiste", "VETERINAIRE"))
    }

    @Test
    fun `custom keywords that are not choices are kept when ticking`() {
        assertEquals("rdv, Veto", AlarmKeywords.toggle("rdv", "Veto"))
        assertEquals("rdv", AlarmKeywords.toggle("rdv, Veto", "Veto"))
    }

    @Test
    fun `options add the alarm's own keywords that no calendar has, so they can be unticked`() {
        val rule = EventAlarm(keywords = "rdv, veto")
        // "veto" is a calendar keyword (ignoring case): not repeated. "rdv" is not: kept, last.
        assertEquals(listOf("Vermifuge", "Veto", "Anti puce", "rdv"), AlarmKeywords.options(rule, filters))
    }

    @Test
    fun `options are the choices when the alarm has no keyword of its own`() {
        assertEquals(AlarmKeywords.choices(EventAlarm(), filters), AlarmKeywords.options(EventAlarm(), filters))
    }

    @Test
    fun `an alarm with a keyword but calendars without any still lists it`() {
        val rule = EventAlarm(keywords = "rdv")
        assertEquals(listOf("rdv"), AlarmKeywords.options(rule, listOf(events("work", ""))))
    }
}
