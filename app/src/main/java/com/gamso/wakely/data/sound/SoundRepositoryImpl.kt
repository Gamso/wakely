package com.gamso.wakely.data.sound

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import com.gamso.wakely.R
import com.gamso.wakely.data.local.toAlarmSound
import com.gamso.wakely.data.local.toJson
import com.gamso.wakely.domain.model.AlarmSound
import com.gamso.wakely.domain.model.SoundSource
import com.gamso.wakely.domain.repository.SoundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Lists the three kinds of sounds: bundled with the app, installed on the device,
 * and audio files added by the user (kept through persistable URI permissions).
 */
class SoundRepositoryImpl(private val context: Context) : SoundRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Alarm sounds from the Android Open Source Project (frameworks/base/data/sounds),
     * licensed under Apache 2.0. Keys are res/raw file names.
     */
    private fun bundled(): List<AlarmSound> = listOf(
        "aosp_argon" to "Argon",
        "aosp_analysis" to "Analysis",
        "aosp_barium" to "Barium",
        "aosp_carbon" to "Carbon",
        "aosp_cesium" to "Cesium",
        "aosp_copernicium" to "Copernicium",
        "aosp_curium" to "Curium",
        "aosp_departure" to "Departure",
        "aosp_fermium" to "Fermium",
        "aosp_firedrill" to "Fire Drill",
        "aosp_hassium" to "Hassium",
        "aosp_helium" to "Helium",
        "aosp_krypton" to "Krypton",
        "aosp_neon" to "Neon",
        "aosp_neptunium" to "Neptunium",
        "aosp_nobelium" to "Nobelium",
        "aosp_osmium" to "Osmium",
        "aosp_oxygen" to "Oxygen",
        "aosp_platinum" to "Platinum",
        "aosp_plutonium" to "Plutonium",
        "aosp_promethium" to "Promethium",
        "aosp_scandium" to "Scandium",
        "aosp_timing" to "Timing",
        "aosp_alarm_classic" to context.getString(R.string.sound_classic),
        *(1..BEEP_COUNT).map { "aosp_alarm_beep_0$it" to context.getString(R.string.sound_beep, it) }.toTypedArray(),
        "aosp_alarm_buzzer" to context.getString(R.string.sound_buzzer),
        "aosp_alarm_rooster_01" to context.getString(R.string.sound_rooster, 1),
        "aosp_alarm_rooster_02" to context.getString(R.string.sound_rooster, 2),
    ).map { (res, name) -> AlarmSound(rawUri(res), name, SoundSource.BUNDLED) }

    override fun defaultSound(): AlarmSound = bundled().first()

    override suspend fun getSounds(): List<AlarmSound> = withContext(Dispatchers.IO) {
        // Device sounds that duplicate a bundled one (same AOSP title) are hidden.
        val bundled = bundled()
        val bundledNames = bundled.map { it.name.lowercase() }.toSet()
        bundled + systemSounds().filterNot { it.name.lowercase() in bundledNames } + customSounds()
    }

    override suspend fun addCustomSound(uri: String): AlarmSound? = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                parsed,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = displayName(parsed) ?: return@withContext null
        val sound = AlarmSound(uri, name.substringBeforeLast('.'), SoundSource.CUSTOM)
        saveCustom(customSounds().filterNot { it.uri == uri } + sound)
        sound
    }

    override suspend fun removeCustomSound(uri: String) = withContext(Dispatchers.IO) {
        saveCustom(customSounds().filterNot { it.uri == uri })
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        Unit
    }

    private fun systemSounds(): List<AlarmSound> = runCatching {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = manager.cursor
        buildList {
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX).orEmpty()
                val uri = manager.getRingtoneUri(cursor.position).toString()
                add(AlarmSound(uri, title, SoundSource.SYSTEM))
            }
        }
    }.getOrDefault(emptyList())

    private fun customSounds(): List<AlarmSound> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_CUSTOM, "[]"))
        (0 until arr.length()).mapNotNull { arr.getJSONObject(it).toAlarmSound() }
    }.getOrDefault(emptyList())

    private fun saveCustom(list: List<AlarmSound>) {
        val arr = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).commit()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** Name-based URI: stays valid across builds, unlike numeric resource ids. */
    private fun rawUri(name: String): String =
        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/$name"

    private companion object {
        const val BEEP_COUNT = 3
        const val PREFS = "sounds"
        const val KEY_CUSTOM = "custom_sounds"
    }
}
