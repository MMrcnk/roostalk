package pl.blitz.callwebhook

import android.content.Context
import androidx.work.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Work {
    fun scanSoon(c: Context) {
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniqueWork("scan", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay(5, TimeUnit.SECONDS).build())
        wm.enqueueUniqueWork("scan_late", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay(60, TimeUnit.SECONDS).build())
    }

    fun schedulePeriodic(c: Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "scan_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val q = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        return base + (if (base.contains("?")) "&" else "?") + q
    }

    fun send(c: Context, base: String, params: Map<String, String>, label: String) {
        WorkManager.getInstance(c).enqueue(
            OneTimeWorkRequestBuilder<SendWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf("url" to buildUrl(base, params), "label" to label))
                .build()
        )
    }
}
