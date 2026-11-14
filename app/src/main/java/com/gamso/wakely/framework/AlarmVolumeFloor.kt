package com.gamso.wakely.framework

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * Keeps the alarm stream audible while an alarm rings: a volume set very low or muted is raised to a minimum, and
 * turning it down again during the ring is undone. When the ring ends, a volume that was raised is given back.
 */
internal class AlarmVolumeFloor(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)

    /** The alarm volume before the ring, or null when no ring is running. */
    private var before: Int? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = raise()
    }

    /** Does nothing when a ring is already running, so the volume of before the first one is kept. */
    fun start() {
        if (before != null) return
        before = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        raise()
        // Volume changes are saved in the system settings.
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    fun stop() {
        val volume = before ?: return
        before = null
        context.contentResolver.unregisterContentObserver(observer)
        if (volume < floor()) set(volume)
    }

    private fun raise() {
        val floor = floor()
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < floor) set(floor)
    }

    private fun floor(): Int =
        (audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * MIN_SHARE).roundToInt().coerceAtLeast(1)

    // A fixed volume or a Do Not Disturb policy can refuse the change: the alarm then rings at the volume it has.
    private fun set(volume: Int) {
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0) }
    }

    private companion object {
        /** Share of the maximum alarm volume an alarm always rings at, at least. */
        const val MIN_SHARE = 0.3f
    }
}
