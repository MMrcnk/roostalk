package pl.blitz.callwebhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Restart telefonu / aktualizacja aplikacji:
 * 1) od razu (nawet przed wpisaniem PIN-u) uruchamia usługę nasłuchującą połączeń,
 * 2) po odblokowaniu – kolejkę, skan rejestru i (jeśli jest zgoda) na chwilę otwiera Roostalk.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        try { MonitorService.ensureRunning(c) } catch (_: Throwable) { }
        if (!Prefs.isUnlocked(c)) return

        try { Work.schedulePeriodic(c) } catch (_: Throwable) { }
        try { Work.scanSoon(c) } catch (_: Throwable) { }

        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (boot) openApp(c)
    }

    private fun openApp(c: Context) {
        if (!Prefs.enabled(c)) return
        // Android 10+ pozwala otworzyć aplikację z tła tylko ze zgodą „wyświetlanie nad innymi aplikacjami”
        if (Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(c)) return
        try {
            c.startActivity(Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_FROM_BOOT, true))
        } catch (_: Throwable) { }
    }
}
