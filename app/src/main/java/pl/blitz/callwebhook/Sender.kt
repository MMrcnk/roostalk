package pl.blitz.callwebhook

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Co i kiedy wysyłamy: numer + data/godzina + status (odebrane / nieodebrane / odrzucone).
 * Status znany jest dopiero po zakończeniu połączenia, więc wysyłka idzie z rejestru połączeń
 * (ScanWorker) kilka sekund po rozłączeniu. W chwili dzwonienia tylko zapamiętujemy numer i kartę SIM.
 */
object Sender {

    fun digits(n: String) = n.filter { it.isDigit() }.takeLast(9)

    fun send(c: Context, number: String, ts: Long, status: String, id: String) {
        val url = Prefs.url(c)
        if (url.isBlank()) return
        Work.send(c, url, linkedMapOf(
            "caller_id" to number.ifBlank { "ukryty" },
            "data" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts)),
            "ts" to ts.toString(),
            "status" to status,
            "id" to id
        ), "$number · $status")
    }

    /** Chwila dzwonienia. slot = -1 gdy telefon nie podał karty. */
    fun onRinging(c: Context, number: String, slot: Int) {
        if (!Prefs.enabled(c)) return
        val now = System.currentTimeMillis()
        val last = Prefs.lastRinging(c)
        if (digits(last.number) == digits(number) && now - last.ts < 60_000) {
            // drugi broadcast tego samego dzwonienia – uzupełnij tylko kartę, jeśli teraz ją znamy
            if (last.slot < 0 && slot >= 0) Prefs.setLastRinging(c, last.copy(slot = slot))
            return
        }
        // Telefon zablokowany po restarcie: rejestru połączeń nie da się odczytać,
        // więc wysyłamy od razu (bez statusu), żeby połączenie nie przepadło.
        val sendNow = !Prefs.isUnlocked(c) && slot >= 0 && slot == Prefs.slot(c)
        Prefs.setLastRinging(c, Prefs.Ringing(number, now, slot, sendNow))
        if (sendNow) send(c, number, now, "nieznany", "r$now")
    }

    /** Dane z chwili dzwonienia pasujące do połączenia z rejestru (ten sam numer, ±3 min). */
    fun ringingFor(c: Context, number: String, callTs: Long): Prefs.Ringing? {
        val r = Prefs.lastRinging(c)
        return if (r.number.isNotBlank() && digits(r.number) == digits(number) &&
            Math.abs(callTs - r.ts) < 180_000) r else null
    }
}
