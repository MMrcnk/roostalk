package pl.blitz.callwebhook

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Usługa pierwszoplanowa (stałe, ciche powiadomienie – jak w MacroDroid).
 * Trzyma aplikację przy życiu po zamknięciu/wyrzuceniu z ostatnich aplikacji i po restarcie telefonu.
 */
class MonitorService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = CallReceiver.handle(c, i)
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        try {
            ContextCompat.registerReceiver(this, receiver,
                IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            registered = true
        } catch (_: Throwable) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        goForeground()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        ensureRunning(this)
    }

    override fun onDestroy() {
        if (registered) try { unregisterReceiver(receiver) } catch (_: Throwable) { }
        super.onDestroy()
    }

    private fun goForeground() {
        try {
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
        } catch (_: Throwable) { }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Działanie w tle", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                })
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val slot = Prefs.slot(this)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
                else @Suppress("DEPRECATION") Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        return b.setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Roostalk działa")
            .setContentText(if (slot >= 0) "Zapisuję połączenia z SIM${slot + 1}" else "Zapisuję połączenia")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ID = 7
        private const val CHANNEL = "monitor"

        fun ensureRunning(c: Context) {
            if (!Prefs.enabled(c)) return
            try {
                ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
            } catch (_: Throwable) { }   // np. ograniczenia startu w tle – kolejna próba przy następnym zdarzeniu
        }

        fun stop(c: Context) {
            try { c.stopService(Intent(c, MonitorService::class.java)) } catch (_: Throwable) { }
        }
    }
}
