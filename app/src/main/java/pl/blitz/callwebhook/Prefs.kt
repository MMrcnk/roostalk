package pl.blitz.callwebhook

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

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

    fun askedAutostart(c: Context) = sp(c).getBoolean("asked_autostart", false)
    fun setAskedAutostart(c: Context) = sp(c).edit().putBoolean("asked_autostart", true).apply()

    fun lastCallId(c: Context) = sp(c).getLong("last_call_id", -1L)
    fun setLastCallId(c: Context, id: Long) { sp(c).edit().putLong("last_call_id", id).commit() }

    /** Ostatni numer wysłany w trakcie dzwonienia – żeby skan rejestru go nie zdublował. */
    fun lastRinging(c: Context): Pair<String, Long> {
        val v = sp(c).getString("last_ringing", "|0") ?: "|0"
        return v.substringBefore("|") to (v.substringAfter("|").toLongOrNull() ?: 0L)
    }
    fun setLastRinging(c: Context, number: String, ts: Long) {
        sp(c).edit().putString("last_ringing", "$number|$ts").commit()
    }

    private val LOG_LOCK = Any()
    fun log(c: Context, msg: String) = synchronized(LOG_LOCK) {
        val ts = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date())
        val lines = (listOf("$ts  $msg") + logText(c).lines().filter { it.isNotBlank() }).take(30)
        sp(c).edit().putString("log", lines.joinToString("\n")).apply()
    }
    fun logText(c: Context): String = sp(c).getString("log", "") ?: ""
}
