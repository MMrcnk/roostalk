package pl.blitz.callwebhook

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wspólna logika: czy wysłać i co wysłać (numer + data/godzina). */
object Sender {

    fun digits(n: String) = n.filter { it.isDigit() }.takeLast(9)

    fun send(c: Context, number: String, ts: Long, id: String) {
        val url = Prefs.url(c)
        if (url.isBlank()) return
        Work.send(c, url, linkedMapOf(
            "caller_id" to number.ifBlank { "ukryty" },
            "data" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts)),
            "ts" to ts.toString(),
            "id" to id
        ), number)
    }

    /** Wywoływane w chwili dzwonienia. slot = -1 gdy telefon nie podał karty. */
    fun onRinging(c: Context, number: String, slot: Int) {
        if (!Prefs.enabled(c) || slot < 0 || slot != Prefs.slot(c)) return
        val now = System.currentTimeMillis()
        val (lastNum, lastTs) = Prefs.lastRinging(c)
        if (digits(lastNum) == digits(number) && now - lastTs < 60_000) return  // podwójny broadcast
        Prefs.setLastRinging(c, number, now)
        send(c, number, now, "r$now")
    }

    /** Czy połączenie z rejestru zostało już wysłane podczas dzwonienia. */
    fun alreadySent(c: Context, number: String, callTs: Long): Boolean {
        val (lastNum, lastTs) = Prefs.lastRinging(c)
        return lastNum.isNotBlank() && digits(lastNum) == digits(number) && Math.abs(callTs - lastTs) < 180_000
    }
}
