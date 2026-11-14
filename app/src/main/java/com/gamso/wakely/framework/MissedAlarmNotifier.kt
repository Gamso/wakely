package com.gamso.wakely.framework

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gamso.wakely.R
import com.gamso.wakely.presentation.MainActivity
import com.gamso.wakely.presentation.common.DatePatterns
import java.time.LocalTime

/** Tells the user an alarm stopped by itself, without being stopped or snoozed. */
object MissedAlarmNotifier {
    private const val NOTIFICATION_ID = 3

    fun show(context: Context, ringing: RingingAlarm, rangAt: LocalTime) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val name = ringing.label.ifBlank { context.getString(R.string.ring_default_label) }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(
                context.getString(R.string.notif_missed_title, rangAt.format(DatePatterns.timeFormatter(context))),
            )
            .setContentText(context.getString(R.string.notif_missed_text, name))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
