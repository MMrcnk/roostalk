package pl.blitz.callwebhook

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    @Volatile private var migrated = false

    /** Czy telefon został odblokowany od restartu (wcześniej Android blokuje zwykłą pamięć aplikacji). */
    fun isUnlocked(c: Context): Boolean =
        if (Build.VERSION.SDK_INT < 24) true
        else try { (c.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked } catch (_: Throwable) { true }

    /**
     * Ustawienia trzymane w pamięci dostępnej od razu po restarcie (przed wpisaniem PIN-u).
     * Przy pierwszym uruchomieniu nowej wersji przenosi stare ustawienia – link i SIM zostają.
     */
    private fun sp(c: Context): SharedPreferences {
        if (Build.VERSION.SDK_INT < 24) return c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val app = c.applicationContext ?: c
        val dp = app.createDeviceProtectedStorageContext()
        if (!migrated && isUnlocked(app)) {
            try { dp.moveSharedPreferencesFrom(app, "cfg") } catch (_: Throwable) { }
            migrated = true
        }
        return dp.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    }

    /** Przyjmuje pełny link albo samo ID wdrożenia (AKfycb…). */
    fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty() || s.startsWith("http")) return s
        return "https://script.google.com/macros/s/$s/exec"
    }

    fun rawUrl(c: Context): String = sp(c).getString("url", "") ?: ""
    fun url(c: Context): String = normalize(rawUrl(c))
    fun setUrl(c: Context, v: String) = sp(c).edit().putString("url", v).apply()

    /** 0 = SIM1, 1 = SIM2, -1 = nie wybrano */
    fun slot(c: Context) = sp(c).getInt("slot", -1)
    fun setSlot(c: Context, v: Int) = sp(c).edit().putInt("slot", v).apply()

    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun askedBattery(c: Context) = sp(c).getBoolean("asked_battery", false)
    fun setAskedBattery(c: Context) = sp(c).edit().putBoolean("asked_battery", true).apply()

    fun askedOverlay(c: Context) = sp(c).getBoolean("asked_overlay", false)
    fun setAskedOverlay(c: Context) = sp(c).edit().putBoolean("asked_overlay", true).apply()

    fun askedAutostart(c: Context) = sp(c).getBoolean("asked_autostart", false)
    fun setAskedAutostart(c: Context) = sp(c).edit().putBoolean("asked_autostart", true).apply()

    fun lastCallId(c: Context) = sp(c).getLong("last_call_id", -1L)
    fun setLastCallId(c: Context, id: Long) { sp(c).edit().putLong("last_call_id", id).commit() }

    /** Ostatnie dzwonienie: numer, czas, karta (-1 = nieznana), czy już wysłane (tryb przed odblokowaniem). */
    data class Ringing(val number: String, val ts: Long, val slot: Int, val sent: Boolean)

    fun lastRinging(c: Context): Ringing {
        val p = (sp(c).getString("last_ringing", "") ?: "").split("|")
        return Ringing(
            p.getOrNull(0) ?: "",
            p.getOrNull(1)?.toLongOrNull() ?: 0L,
            p.getOrNull(2)?.toIntOrNull() ?: -1,
            p.getOrNull(3) == "1"
        )
    }
    fun setLastRinging(c: Context, r: Ringing) {
        sp(c).edit().putString("last_ringing", "${r.number}|${r.ts}|${r.slot}|${if (r.sent) 1 else 0}").commit()
    }

    private val LOG_LOCK = Any()
    fun log(c: Context, msg: String) = synchronized(LOG_LOCK) {
        val ts = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date())
        val lines = (listOf("$ts  $msg") + logText(c).lines().filter { it.isNotBlank() }).take(30)
        sp(c).edit().putString("log", lines.joinToString("\n")).apply()
    }
    fun logText(c: Context): String = sp(c).getString("log", "") ?: ""
}
