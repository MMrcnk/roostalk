package pl.blitz.callwebhook

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Sprawdza GitHub Releases i instaluje nowszą wersję. */
object Updater {

    fun check(a: Activity, silent: Boolean) {
        val repo = BuildConfig.GH_REPO
        if (repo.isBlank()) { if (!silent) toast(a, "Wersja lokalna – brak repo"); return }
        Thread {
            try {
                val o = JSONObject(get("https://api.github.com/repos/$repo/releases/latest"))
                val ver = o.getString("tag_name").removePrefix("v").toIntOrNull() ?: 0
                if (ver <= BuildConfig.VERSION_CODE) {
                    if (!silent) toast(a, "Masz najnowszą wersję"); return@Thread
                }
                val assets = o.getJSONArray("assets")
                var apk: String? = null
                for (i in 0 until assets.length()) {
                    val x = assets.getJSONObject(i)
                    if (x.getString("name").endsWith(".apk")) apk = x.getString("browser_download_url")
                }
                val url = apk ?: return@Thread
                a.runOnUiThread {
                    if (a.isFinishing) return@runOnUiThread
                    AlertDialog.Builder(a)
                        .setTitle("Nowa wersja ($ver)")
                        .setMessage(o.optString("body").take(500).ifBlank { "Zainstalować aktualizację?" })
                        .setPositiveButton("Aktualizuj") { _, _ -> download(a, url) }
                        .setNegativeButton("Później", null)
                        .show()
                }
            } catch (e: Throwable) {
                if (!silent) toast(a, "Nie udało się sprawdzić aktualizacji")
            }
        }.start()
    }

    private fun download(a: Activity, url: String) {
        toast(a, "Pobieram aktualizację…")
        Thread {
            try {
                val dir = if (Build.VERSION.SDK_INT < 24) a.externalCacheDir ?: a.cacheDir else a.cacheDir
                val f = File(dir, "update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } }
                a.runOnUiThread { install(a, f) }
            } catch (e: Throwable) {
                toast(a, "Błąd pobierania")
            }
        }.start()
    }

    private fun install(a: Activity, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            toast(a, "Zezwól na instalację i kliknij Aktualizuj jeszcze raz")
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        val uri = if (Build.VERSION.SDK_INT >= 24)
            FileProvider.getUriForFile(a, "${a.packageName}.files", f) else Uri.fromFile(f)
        a.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 10000; c.readTimeout = 10000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun toast(a: Activity, s: String) = a.runOnUiThread { Toast.makeText(a, s, Toast.LENGTH_SHORT).show() }
}
