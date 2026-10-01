package pl.blitz.callwebhook

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Zabezpieczenie: po każdej rozmowie czyta rejestr i wysyła to, czego nie wysłano podczas dzwonienia. */
class ScanWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val c = applicationContext
        MonitorService.ensureRunning(c)
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            return Result.success()
        try {
            synchronized(LOCK) { initIfNeeded(c); scan(c) }
        } catch (e: Throwable) {
            Prefs.log(c, "błąd skanu: ${e.javaClass.simpleName}")
        }
        return Result.success()
    }

    private fun scan(c: Context) {
        val proj = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
            CallLog.Calls.DATE, CallLog.Calls.PHONE_ACCOUNT_ID
        )
        c.contentResolver.query(
            CallLog.Calls.CONTENT_URI, proj, "${CallLog.Calls._ID} > ?",
            arrayOf(Prefs.lastCallId(c).toString()), "${CallLog.Calls._ID} ASC"
        )?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getLong(0)
                handle(c, id, cur.getString(1) ?: "", cur.getInt(2), cur.getLong(3), cur.getString(4))
                Prefs.setLastCallId(c, id)
            }
        }
    }

    private fun handle(c: Context, id: Long, number: String, type: Int, date: Long, accountId: String?) {
        if (!Prefs.enabled(c)) return
        // tylko przychodzące: 1 odebrane, 3 nieodebrane, 5 odrzucone, 6 zablokowane
        val status = when (type) {
            1 -> "odebrane"
            3 -> "nieodebrane"
            5, 6 -> "odrzucone"
            else -> return
        }
        val ringing = Sender.ringingFor(c, number, date)
        if (ringing?.sent == true) return   // już wysłane w chwili dzwonienia (telefon był zablokowany)

        // karta SIM: z rejestru połączeń, a jeśli telefon jej tam nie zapisał – z chwili dzwonienia
        val subId = Sims.subIdForAccount(c, accountId)
        val slot = Sims.list(c).firstOrNull { it.subId == subId }?.slot
            ?: ringing?.slot?.takeIf { it >= 0 }
        if (slot == null) { Prefs.log(c, "? nie rozpoznano SIM ($accountId)"); return }
        if (slot != Prefs.slot(c)) return
        Sender.send(c, number, date, status, "c$id")
    }

    companion object {
        private val LOCK = Any()

        /** Ustawia punkt startowy – wcześniejsze połączenia nie są wysyłane. */
        fun resetToNow(c: Context) { Prefs.setLastCallId(c, -1); initIfNeeded(c) }

        fun initIfNeeded(c: Context) {
            if (Prefs.lastCallId(c) >= 0) return
            val max = try {
                c.contentResolver.query(
                    CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                    null, null, "${CallLog.Calls._ID} DESC"
                )?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
            } catch (e: Throwable) { return }
            Prefs.setLastCallId(c, max)
        }
    }
}
