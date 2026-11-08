package com.gamso.wakely.framework

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gamso.wakely.appContainer
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Receives system alarms and decides (through the domain) whether to ring. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        if (intent.action == ACTION_SUMMARY) {
            launchAsync {
                val tomorrow = container.timeProvider.now().toLocalDate().plusDays(1)
                SummaryNotifier.show(context, container.getDayPlan(tomorrow))
                container.rescheduleAlarms() // schedules the next summary
            }
            return
        }
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val kind = runCatching { RingKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }
            .getOrDefault(RingKind.ALARM)
        val key = RingKey(kind, id)
        launchAsync {
            val info = when (intent.action) {
                ACTION_SNOOZE_FIRED -> container.handleSnoozeFired(
                    key,
                    intent.getLongExtra(EXTRA_SCHEDULED_AT, -1L).takeIf { it > 0 }?.let {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    },
                )

                else -> container.handleRingFired(key)
            }
            if (info != null) AlarmService.start(context, info)
        }
    }

    companion object {
        const val ACTION_FIRED = "com.gamso.wakely.action.ALARM_FIRED"
        const val ACTION_SNOOZE_FIRED = "com.gamso.wakely.action.SNOOZE_FIRED"
        const val ACTION_SUMMARY = "com.gamso.wakely.action.SUMMARY"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
    }
}

/** Boot, app update, time or time-zone change: reschedule everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CalendarWatchJob.schedule(context)
        launchAsync { context.appContainer.rescheduleAlarms() }
    }
}
