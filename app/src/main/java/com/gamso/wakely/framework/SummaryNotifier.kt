package com.gamso.wakely.framework

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gamso.wakely.R
import com.gamso.wakely.domain.model.DayPlan
import com.gamso.wakely.presentation.MainActivity
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Posts the evening notification describing the next day's rings. */
object SummaryNotifier {
    private const val NOTIFICATION_ID = 2
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun show(context: Context, plan: DayPlan) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val lines = plan.rings.map { ring ->
            val detail = ring.detail?.let { " ($it)" }.orEmpty()
            "${ring.at.format(timeFormat)} · ${ring.title}$detail"
        } + plan.skipped
        val title = "Demain, ${plan.date.format(dateFormat)}"
        val summary = when {
            plan.rings.isEmpty() && plan.skipped.isEmpty() -> "Aucun réveil prévu"
            plan.rings.isEmpty() -> "Aucun réveil ne sonnera"
            plan.rings.size == 1 -> "Réveil à ${plan.rings[0].at.format(timeFormat)}"
            else -> "${plan.rings.size} réveils, le premier à ${plan.rings[0].at.format(timeFormat)}"
        }
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { style.addLine(it) }
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(if (lines.isEmpty()) null else style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
