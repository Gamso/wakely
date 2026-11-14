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
import android.os.SystemClock
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
import com.gamso.wakely.domain.usecase.VolumeRamp
import com.gamso.wakely.presentation.common.DatePatterns
import com.gamso.wakely.presentation.ring.RingActivity
import java.time.LocalTime
import kotlinx.coroutines.runBlocking

/** Foreground service that plays the alarm sound and vibrates. */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRinging() }
    private var rampStartedAt = 0L
    private var rampSeconds = 0
    private val rampStep = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.elapsedRealtime() - rampStartedAt
            val level = VolumeRamp.level(elapsed, rampSeconds)
            // Any failure leaves the volume where it is: the next step or the end of the ramp sets it again.
            player?.runCatching { setVolume(level, level) }
            if (level < 1f) handler.postDelayed(this, RAMP_STEP_MS)
        }
    }

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
                    eventTime = intent.getStringExtra(EXTRA_EVENT_TIME)?.let(LocalTime::parse),
                ),
                soundUri = intent.getStringExtra(EXTRA_SOUND_URI),
                rampSeconds = intent.getIntExtra(EXTRA_RAMP_SECONDS, 0),
                vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true),
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

    private fun startRinging(ringing: RingingAlarm, soundUri: String?, rampSeconds: Int, vibrate: Boolean) {
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
        playSound(soundUri, rampSeconds)
        if (vibrate) vibrate()
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

    private fun playSound(soundUri: String?, rampSeconds: Int) {
        // Chosen sound first, then the app default, then system defaults (see AlarmAudio).
        val default = appContainer.soundRepository.defaultSound().uri
        player = AlarmAudio.play(
            this,
            listOf(soundUri?.let(Uri::parse), Uri.parse(default)),
            wakeLock = true,
            initialVolume = VolumeRamp.level(0, rampSeconds),
        )
        this.rampSeconds = rampSeconds
        rampStartedAt = SystemClock.elapsedRealtime()
        if (rampSeconds > 0) handler.post(rampStep)
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
        handler.removeCallbacks(rampStep)
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
        val time = LocalTime.now().format(DatePatterns.timeFormatter(this))
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(getString(R.string.notif_ring_title, time))
            .setContentText(contentText(ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
        // With a pattern asked, the notification has no buttons: they would get around it, so stopping goes through
        // the ring screen.
        if (runBlocking { appContainer.settingsRepository.getUnlockPattern() } != null) return builder.build()
        if (ringing.snoozeMinutes > 0) {
            builder.addAction(
                0,
                getString(R.string.notif_snooze, ringing.snoozeMinutes),
                servicePendingIntent(ACTION_SNOOZE, 1),
            )
        }
        return builder
            .addAction(0, getString(R.string.action_stop), servicePendingIntent(ACTION_STOP, 2))
            .build()
    }

    private fun contentText(ringing: RingingAlarm): String {
        val name = ringing.label.ifBlank { getString(R.string.notif_ring_text_default) }
        val eventTime = ringing.eventTime ?: return name
        return getString(R.string.at_time, name, eventTime.format(DatePatterns.timeFormatter(this)))
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
        private const val EXTRA_EVENT_TIME = "event_time"
        private const val EXTRA_RAMP_SECONDS = "ramp_seconds"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val RAMP_STEP_MS = 100L
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
                    .putExtra(EXTRA_EVENT_TIME, info.eventTime?.toString())
                    .putExtra(EXTRA_SOUND_URI, info.sound?.uri)
                    .putExtra(EXTRA_SNOOZE_MINUTES, info.snoozeMinutes)
                    .putExtra(EXTRA_RAMP_SECONDS, info.volumeRampSeconds)
                    .putExtra(EXTRA_VIBRATE, info.vibrate),
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
