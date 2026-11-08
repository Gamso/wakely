package com.gamso.wakely.framework

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_SUMMARY = "summary"

    fun createChannels(context: Context) {
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            "Réveils en cours",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Affiche le réveil qui sonne"
            setSound(null, null) // sound is played by AlarmService
            enableVibration(false)
        }
        val summary = NotificationChannel(
            CHANNEL_SUMMARY,
            "Réveils du lendemain",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Rappel, la veille au soir, des réveils prévus le lendemain"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(alarm, summary))
    }
}
