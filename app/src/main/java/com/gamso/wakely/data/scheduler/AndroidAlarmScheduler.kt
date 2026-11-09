package com.gamso.wakely.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gamso.wakely.domain.model.RingKey
import com.gamso.wakely.domain.model.RingKind
import com.gamso.wakely.domain.repository.AlarmScheduler
import com.gamso.wakely.framework.AlarmReceiver
import com.gamso.wakely.presentation.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId

/** [AlarmScheduler] backed by AlarmManager.setAlarmClock (exact, even in Doze). */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(key: RingKey, at: LocalDateTime) = setAlarmClock(at, firePendingIntent(key, snooze = false))

    override fun scheduleSnooze(key: RingKey, at: LocalDateTime) =
        setAlarmClock(at, firePendingIntent(key, snooze = true, scheduledAt = at.toEpochMs()))

    override fun cancel(key: RingKey) {
        alarmManager.cancel(firePendingIntent(key, snooze = false))
    }

    override fun schedulePreCheck(key: RingKey, at: LocalDateTime) {
        val operation = preCheckPendingIntent(key)
        val triggerMs = at.toEpochMs()
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
        }
    }

    override fun cancelPreCheck(key: RingKey) {
        alarmManager.cancel(preCheckPendingIntent(key))
    }

    override fun scheduleSummary(at: LocalDateTime?) {
        val operation = PendingIntent.getBroadcast(
            context,
            SUMMARY_REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SUMMARY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            alarmManager.cancel(operation)
            return
        }
        // A reminder does not need alarm-clock priority (no status-bar alarm icon).
        val triggerMs = at.toEpochMs()
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
        }
    }

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setAlarmClock(at: LocalDateTime, operation: PendingIntent) {
        val triggerMs = at.toEpochMs()
        try {
            if (canScheduleExactAlarms()) {
                val show = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMs, show), operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
        }
    }

    private fun LocalDateTime.toEpochMs(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun preCheckPendingIntent(key: RingKey): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_PRECHECK)
            .putExtra(AlarmReceiver.EXTRA_KIND, key.kind.name)
            .putExtra(AlarmReceiver.EXTRA_ID, key.id)
        // Negative codes never meet the ring and snooze ones.
        val kindOffset = if (key.kind == RingKind.ALARM) 0 else 1
        return PendingIntent.getBroadcast(
            context,
            -(key.id * 2 + kindOffset).toInt() - 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun firePendingIntent(key: RingKey, snooze: Boolean, scheduledAt: Long = -1L): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(if (snooze) AlarmReceiver.ACTION_SNOOZE_FIRED else AlarmReceiver.ACTION_FIRED)
            .putExtra(AlarmReceiver.EXTRA_KIND, key.kind.name)
            .putExtra(AlarmReceiver.EXTRA_ID, key.id)
            .putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, scheduledAt)
        // 4 codes per id: (alarm | event) x (ring | snooze).
        val kindOffset = if (key.kind == RingKind.ALARM) 0 else 2
        val requestCode = (key.id * 4 + kindOffset + if (snooze) 1 else 0).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val SUMMARY_REQUEST_CODE = Int.MAX_VALUE - 1
    }
}
