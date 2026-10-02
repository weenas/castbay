package com.weenas.castbay.receiver

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.weenas.castbay.util.Diagnostics

/**
 * Runs every 15 minutes or so while the device has a network, and starts the receiver if the
 * system stopped it: a car that sleeps when switched off (rather than shutting down) kills
 * apps and sends no boot broadcast on waking, but jobs carry on once its network is back.
 */
class WakeJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Diagnostics.init(this)
        ReceiverStarter.start(this, "network job")
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 7000

        /** Schedules the job once; later calls leave it be. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, WakeJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                // Survives a reboot too (RECEIVE_BOOT_COMPLETED).
                .setPersisted(true)
                .build()
            val result = scheduler.schedule(job)
            Diagnostics.record("start", if (result == JobScheduler.RESULT_SUCCESS) "Network job scheduled" else "Network job not scheduled")
        }
    }
}
