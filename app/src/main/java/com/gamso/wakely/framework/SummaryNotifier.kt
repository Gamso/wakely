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
import com.gamso.wakely.domain.model.PlannedRing
import com.gamso.wakely.domain.model.SkippedRing
import com.gamso.wakely.presentation.MainActivity
import com.gamso.wakely.presentation.common.DatePatterns
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Posts the evening notification describing the next day's rings. */
object SummaryNotifier {
    private const val NOTIFICATION_ID = 2
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun show(context: Context, plan: DayPlan) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val lines = plan.rings.map { ring -> ringLine(context, ring) } + plan.skipped.map { skippedLine(context, it) }
        val dateFormat = DatePatterns.formatter("EEEEdMMMM", "EEEE d MMMM")
        val title = context.getString(R.string.summary_notif_title, plan.date.format(dateFormat))
        val firstTime = plan.rings.firstOrNull()?.at?.format(timeFormat).orEmpty()
        val summary = when {
            plan.rings.isEmpty() && plan.skipped.isEmpty() -> context.getString(R.string.summary_none)
            plan.rings.isEmpty() -> context.getString(R.string.summary_none_ring)
            plan.rings.size == 1 -> context.getString(R.string.summary_one, firstTime)
            else -> context.getString(R.string.summary_many, plan.rings.size, firstTime)
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

    /** "07:00 · Title (detail)". */
    private fun ringLine(context: Context, ring: PlannedRing): String {
        val event = ring.event
        val fallback = context.getString(R.string.ring_default_label)
        val (title, detail) = when {
            event == null -> ring.label.ifBlank { fallback } to ring.dayOffReason?.let {
                context.getString(
                    R.string.summary_detail_adapted,
                    it.ifBlank {
                        context.getString(R.string.day_off_default)
                    },
                )
            }

            ring.label.isBlank() ->
                event.title to
                    context.getString(R.string.summary_detail_event, event.start.format(timeFormat))

            else ->
                ring.label to
                    context.getString(R.string.summary_detail_before, event.title, event.start.format(timeFormat))
        }
        val head = context.getString(R.string.fmt_dot, ring.at.format(timeFormat), title)
        return if (detail == null) head else context.getString(R.string.fmt_paren, head, detail)
    }

    private fun skippedLine(context: Context, skipped: SkippedRing): String = context.getString(
        R.string.summary_skipped,
        skipped.label.ifBlank { context.getString(R.string.ring_default_label) },
        skipped.time.format(timeFormat),
        skipped.reason.ifBlank { context.getString(R.string.day_off_default) },
    )
}
