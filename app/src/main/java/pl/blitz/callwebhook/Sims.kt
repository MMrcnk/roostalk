package pl.blitz.callwebhook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object Sims {
    data class Sim(val subId: Int, val slot: Int, val carrier: String, val number: String) {
        val label get() = "SIM ${slot + 1} · $carrier" + if (number.isNotBlank()) " · $number" else ""
    }

    private fun sm(c: Context) =
        c.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun list(c: Context): List<Sim> {
        return try {
            val sm = sm(c) ?: return emptyList()
            (sm.activeSubscriptionInfoList ?: emptyList()).map {
                val num = try {
                    if (Build.VERSION.SDK_INT >= 33) sm.getPhoneNumber(it.subscriptionId) else it.number ?: ""
                } catch (e: Throwable) { "" }
                Sim(it.subscriptionId, it.simSlotIndex, (it.carrierName ?: it.displayName ?: "").toString(), num)
            }.sortedBy { it.slot }
        } catch (e: Throwable) { emptyList() }
    }

    /** Mapuje PHONE_ACCOUNT_ID z rejestru połączeń na kartę SIM. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun subIdForAccount(c: Context, accountId: String?): Int? {
        val sims = list(c)
        if (sims.size == 1) return sims[0].subId
        if (accountId.isNullOrBlank()) return null

        if (Build.VERSION.SDK_INT >= 30) try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val tel = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            for (h in tm.callCapablePhoneAccounts) if (h.id == accountId) {
                val id = tel.getSubscriptionId(h)
                if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return id
            }
        } catch (e: Throwable) { }

        accountId.toIntOrNull()?.let { n -> sims.firstOrNull { it.subId == n }?.let { return it.subId } }

        try {
            sm(c)?.activeSubscriptionInfoList?.forEach {
                val icc = it.iccId ?: ""
                if (icc.isNotBlank() && (accountId.startsWith(icc) || icc.startsWith(accountId))) return it.subscriptionId
            }
        } catch (e: Throwable) { }
        return null
    }
}
