package pl.blitz.callwebhook

import android.content.Context
import androidx.work.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Work {
    fun scanSoon(c: Context) {
        if (!Prefs.isUnlocked(c)) return
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniqueWork("scan", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay(5, TimeUnit.SECONDS).build())
        wm.enqueueUniqueWork("scan_late", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay(60, TimeUnit.SECONDS).build())
    }

    fun schedulePeriodic(c: Context) {
        if (!Prefs.isUnlocked(c)) return
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
        val url = buildUrl(base, params)
        if (!Prefs.isUnlocked(c)) { direct(c, url, label); return }
        try {
            WorkManager.getInstance(c).enqueue(
                OneTimeWorkRequestBuilder<SendWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInputData(workDataOf("url" to url, "label" to label))
                    .build()
            )
        } catch (e: Throwable) {
            direct(c, url, label)
        }
    }

    /** Wysyłka bez kolejki – gdy telefon jest jeszcze zablokowany po restarcie. 6 prób co ~20 s. */
    private fun direct(c: Context, url: String, label: String) {
        Thread {
            for (attempt in 1..6) {
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000; readTimeout = 20000; instanceFollowRedirects = true
                    }
                    val code = conn.responseCode
                    try { conn.inputStream.use { it.readBytes() } } catch (_: Throwable) { }
                    conn.disconnect()
                    if (code in 200..399) { Prefs.log(c, "✔ $label"); return@Thread }
                } catch (_: Throwable) { }
                try { Thread.sleep(20_000L * attempt) } catch (_: Throwable) { return@Thread }
            }
        }.start()
    }
}
