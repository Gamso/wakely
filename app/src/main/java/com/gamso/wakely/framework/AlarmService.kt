package com.gamso.wakely.framework

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gamso.wakely.R
import com.gamso.wakely.appContainer
import com.gamso.wakely.domain.model.RingInfo
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.presentation.ring.RingActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Foreground service that plays the alarm sound and vibrates. */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRinging(
                RingingAlarm(
                    key = RingKey(
                        runCatching { RingKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }
                            .getOrDefault(RingKind.ALARM),
                        intent.getLongExtra(EXTRA_ALARM_ID, -1L),
                    ),
                    label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                    snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 0),
                ),
                soundUri = intent.getStringExtra(EXTRA_SOUND_URI),
            )

            ACTION_SNOOZE -> {
                val current = appContainer.ringingState.ringing.value
                if (current != null) appContainer.snoozeAlarm(current.key, current.snoozeMinutes)
                stopRinging()
            }

            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(ringing: RingingAlarm, soundUri: String?) {
        stopPlayback()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(ringing),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
        appContainer.ringingState.set(ringing)
        playSound(soundUri)
        vibrate()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, AUTO_STOP_MS)
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        stopPlayback()
        appContainer.ringingState.set(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playSound(soundUri: String?) {
        // Chosen sound first, then the app default, then system defaults (see AlarmAudio).
        val default = appContainer.soundRepository.defaultSound().uri
        player = AlarmAudio.play(
            this,
            listOf(soundUri?.let(Uri::parse), Uri.parse(default)),
            wakeLock = true,
        )
    }

    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
    }

    private fun stopPlayback() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun buildNotification(ringing: RingingAlarm): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Réveil · $time")
            .setContentText(ringing.label.ifBlank { "C'est l'heure !" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
        if (ringing.snoozeMinutes > 0) {
            builder.addAction(0, "Répéter (${ringing.snoozeMinutes} min)", servicePendingIntent(ACTION_SNOOZE, 1))
        }
        return builder
            .addAction(0, "Arrêter", servicePendingIntent(ACTION_STOP, 2))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopPlayback()
        appContainer.ringingState.set(null)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.gamso.wakely.action.START"
        private const val ACTION_STOP = "com.gamso.wakely.action.STOP"
        private const val ACTION_SNOOZE = "com.gamso.wakely.action.SNOOZE"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_SOUND_URI = "sound_uri"
        private const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        private const val NOTIFICATION_ID = 1
        private const val AUTO_STOP_MS = 10 * 60 * 1000L

        /** Off / on / off durations (ms), repeated from index 0. */
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 800)

        fun start(context: Context, info: RingInfo) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_KIND, info.key.kind.name)
                    .putExtra(EXTRA_ALARM_ID, info.key.id)
                    .putExtra(EXTRA_LABEL, info.label)
                    .putExtra(EXTRA_SOUND_URI, info.sound?.uri)
                    .putExtra(EXTRA_SNOOZE_MINUTES, info.snoozeMinutes),
            )
        }

        fun stop(context: Context) = context.startService(
            Intent(context, AlarmService::class.java).setAction(ACTION_STOP),
        )

        fun snooze(context: Context) = context.startService(
            Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE),
        )
    }
}
