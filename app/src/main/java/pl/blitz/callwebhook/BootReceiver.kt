package pl.blitz.callwebhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Po restarcie telefonu i po aktualizacji aplikacji – uruchamia wszystko ponownie. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        try {
            Work.schedulePeriodic(c)
            Work.scanSoon(c)
            MonitorService.ensureRunning(c)
        } catch (_: Throwable) { }
    }
}
