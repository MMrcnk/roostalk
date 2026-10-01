package pl.blitz.callwebhook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/** Odbiornik z manifestu – działa nawet gdy aplikacja jest zamknięta. MonitorService używa tej samej logiki. */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) = handle(c, intent)

    companion object {
        fun handle(c: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            try {
                when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        @Suppress("DEPRECATION")
                        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        if (!number.isNullOrBlank()) Sender.onRinging(c, number, slotFromIntent(c, intent))
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> Work.scanSoon(c)
                }
            } catch (_: Throwable) { }
            MonitorService.ensureRunning(c)
        }

        /** Różni producenci podają kartę SIM pod różnymi kluczami. */
        private fun slotFromIntent(c: Context, i: Intent): Int {
            val sims = Sims.list(c)
            if (sims.size == 1) return sims[0].slot
            for (k in listOf("android.telephony.extra.SUBSCRIPTION_INDEX", "subscription", "subscription_id", "subId")) {
                val sub = readInt(i, k)
                if (sub >= 0) sims.firstOrNull { it.subId == sub }?.let { return it.slot }
            }
            for (k in listOf("android.telephony.extra.SLOT_INDEX", "slot", "slot_id", "simId", "simSlot", "phone")) {
                val s = readInt(i, k)
                if (s in 0..1) return s
            }
            return -1
        }

        private fun readInt(i: Intent, key: String): Int {
            if (!i.hasExtra(key)) return -1
            return try {
                @Suppress("DEPRECATION")
                when (val v = i.extras?.get(key)) {
                    is Int -> v
                    is Long -> v.toInt()
                    is String -> v.toIntOrNull() ?: -1
                    else -> -1
                }
            } catch (_: Throwable) { -1 }
        }
    }
}
