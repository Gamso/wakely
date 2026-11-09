package com.gamso.wakely.domain.holiday

import com.gamso.wakely.domain.model.HolidaySettings

/** Decides which country's public holidays apply. */
object HolidayConfig {

    /**
     * The user's choice when there is one (null = no public holidays), otherwise the first of the device's
     * [detectedCountries] that is among the [supported] ones. Nothing is assumed for an unsupported country.
     */
    fun effectiveCountry(
        settings: HolidaySettings,
        detectedCountries: List<String>,
        supported: Collection<String>,
    ): String? {
        val known = supported.map { it.uppercase() }.toSet()
        return if (settings.countryChosen) {
            settings.country?.uppercase()?.takeIf { it in known }
        } else {
            detectedCountries.map { it.uppercase() }.firstOrNull { it in known }
        }
    }
}
