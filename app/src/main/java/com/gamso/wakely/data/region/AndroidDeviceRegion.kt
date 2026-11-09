package com.gamso.wakely.data.region

import android.content.Context
import android.telephony.TelephonyManager
import com.gamso.wakely.domain.repository.DeviceRegion
import java.util.Locale

/**
 * Candidate countries from the SIM card (the home country of the subscriber) and from the language settings.
 * Neither needs a permission, and nothing leaves the device.
 */
class AndroidDeviceRegion(private val context: Context) : DeviceRegion {

    override fun countryCandidates(): List<String> {
        val sim = runCatching { context.getSystemService(TelephonyManager::class.java)?.simCountryIso }.getOrNull()
        return listOf(sim, Locale.getDefault().country)
            .mapNotNull { it?.trim()?.uppercase() }
            .filter { it.isNotEmpty() }
    }
}
