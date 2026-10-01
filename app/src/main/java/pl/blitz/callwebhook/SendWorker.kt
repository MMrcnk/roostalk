package pl.blitz.callwebhook

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class SendWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val c = applicationContext
        val url = inputData.getString("url") ?: return Result.failure()
        val label = inputData.getString("label") ?: ""
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 20000; instanceFollowRedirects = true
            }
            val code = conn.responseCode
            try { conn.inputStream.use { it.readBytes() } } catch (_: Throwable) { }
            conn.disconnect()
            if (code in 200..399) { Prefs.log(c, "✔ $label"); Result.success() }
            else fail(c, label, "HTTP $code")
        } catch (e: Throwable) {
            fail(c, label, e.javaClass.simpleName)
        }
    }

    private fun fail(c: Context, label: String, why: String): Result =
        if (runAttemptCount < 10) Result.retry()
        else { Prefs.log(c, "✖ $label ($why)"); Result.failure() }
}
