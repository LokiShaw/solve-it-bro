package com.solveitbro.app.monetization

import java.util.TimeZone

/** Persists the quota counter. Backed by SharedPreferences in the app and a map in tests. */
interface QuotaStore {
    var dayKey: Long
    var used: Int
}

/** Whether the student has an active subscription. Play Billing plugs in here later. */
fun interface Entitlements {
    fun isSubscribed(): Boolean
}

/**
 * Free tier: [freeDailyLimit] solves per local calendar day, unlimited for subscribers.
 *
 * This is a client-side hint for the UI only; the backend enforces the real limit.
 * Rewarded ads (to earn extra solves) will call [grantBonus].
 */
class SolveQuota(
    private val store: QuotaStore,
    private val entitlements: Entitlements,
    private val freeDailyLimit: Int,
    private val today: () -> Long = ::localEpochDay,
) {
    sealed interface Status {
        data object Unlimited : Status
        data class Remaining(val count: Int) : Status
    }

    fun status(): Status {
        if (entitlements.isSubscribed()) return Status.Unlimited
        rollOverIfNewDay()
        return Status.Remaining((freeDailyLimit - store.used).coerceAtLeast(0))
    }

    /** Records one solve. Returns false (and records nothing) when the free limit is used up. */
    fun tryConsume(): Boolean {
        if (entitlements.isSubscribed()) return true
        rollOverIfNewDay()
        if (store.used >= freeDailyLimit) return false
        store.used += 1
        return true
    }

    /** Gives back [solves] for today, e.g. after a rewarded ad. */
    fun grantBonus(solves: Int = 1) {
        rollOverIfNewDay()
        store.used -= solves
    }

    private fun rollOverIfNewDay() {
        val day = today()
        if (store.dayKey != day) {
            store.dayKey = day
            store.used = 0
        }
    }
}

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

/** Days since epoch in the device's time zone, so the quota resets at local midnight. */
fun localEpochDay(nowMillis: Long = System.currentTimeMillis()): Long {
    val offset = TimeZone.getDefault().getOffset(nowMillis)
    return Math.floorDiv(nowMillis + offset, MILLIS_PER_DAY)
}
