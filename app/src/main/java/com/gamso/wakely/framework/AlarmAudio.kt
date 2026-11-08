package com.gamso.wakely.framework

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import com.gamso.wakely.domain.repository.SoundPreviewer

internal object AlarmAudio {

    /**
     * Starts looping playback on the alarm stream. Tries each candidate in order so the
     * alarm still rings if the chosen file was deleted or its permission revoked.
     */
    fun play(context: Context, candidates: List<Uri?>, wakeLock: Boolean): MediaPlayer? {
        val fallbacks = candidates + listOf(
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in fallbacks.filterNotNull().distinct()) {
            create(context, uri, wakeLock)?.let { return it }
        }
        return null
    }

    private fun create(context: Context, uri: Uri, wakeLock: Boolean): MediaPlayer? {
        val player = MediaPlayer()
        return runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            if (wakeLock) player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            player.setDataSource(context, uri)
            player.isLooping = true
            player.prepare()
            player.start()
            player
        }.getOrElse {
            player.release()
            null
        }
    }
}

/** [SoundPreviewer] playing at alarm volume so the user hears the real loudness. */
class MediaSoundPreviewer(private val context: Context) : SoundPreviewer {
    private var player: MediaPlayer? = null

    override fun play(uri: String) {
        stop()
        player = AlarmAudio.play(context, listOf(Uri.parse(uri)), wakeLock = false)
    }

    override fun stop() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
    }
}
