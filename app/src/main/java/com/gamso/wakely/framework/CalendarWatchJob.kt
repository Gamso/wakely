package com.gamso.wakely.framework

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import com.gamso.wakely.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Triggered when calendar content changes (Google sync, new day off...)
 * and reschedules alarms accordingly.
 */
class CalendarWatchJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                applicationContext.appContainer.rescheduleAlarms()
            } finally {
                jobFinished(params, false)
                // A content-trigger job must be rescheduled after each run.
                schedule(applicationContext, force = true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 4242

        fun schedule(context: Context, force: Boolean = false) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!force && scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarWatchJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        CalendarContract.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    ),
                )
                .setTriggerContentUpdateDelay(5_000)
                .setTriggerContentMaxDelay(60_000)
                .build()
            runCatching { scheduler.schedule(job) }
        }
    }
}
