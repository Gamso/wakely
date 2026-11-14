package com.gamso.wakely.framework

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.gamso.wakely.R

object Notifications {
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_SUMMARY = "summary"
    const val CHANNEL_MISSED = "missed"

    fun createChannels(context: Context) {
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.notif_channel_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_alarm_desc)
            setSound(null, null) // sound is played by AlarmService
            enableVibration(false)
        }
        val summary = NotificationChannel(
            CHANNEL_SUMMARY,
            context.getString(R.string.notif_channel_summary),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_summary_desc)
        }
        val missed = NotificationChannel(
            CHANNEL_MISSED,
            context.getString(R.string.notif_channel_missed),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_missed_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(alarm, summary, missed))
    }
}
