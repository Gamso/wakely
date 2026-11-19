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
import com.gamso.wakely.domain.repository.AwakeSkipNotifier
import com.gamso.wakely.presentation.MainActivity
import com.gamso.wakely.presentation.common.DatePatterns
import java.time.LocalDateTime

/** Posts "Alarm not rung" when the step counter showed the user was already up. */
class AwakeSkipNotifierImpl(private val context: Context) : AwakeSkipNotifier {
    override fun notifySkipped(label: String, ringAt: LocalDateTime, steps: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val name = label.ifBlank { context.getString(R.string.ring_default_label) }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val count = steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_AWAKE_SKIP)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(
                context.getString(
                    R.string.notif_awake_skip_title,
                    ringAt.toLocalTime().format(DatePatterns.timeFormatter(context)),
                ),
            )
            .setContentText(context.resources.getQuantityString(R.plurals.notif_awake_skip_text, count, name, count))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val NOTIFICATION_ID = 4
    }
}
