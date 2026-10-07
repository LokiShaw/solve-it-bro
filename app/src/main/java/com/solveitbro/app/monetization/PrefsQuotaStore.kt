package com.solveitbro.app.monetization

import android.content.Context
import androidx.core.content.edit

class PrefsQuotaStore(context: Context) : QuotaStore {
    private val prefs = context.getSharedPreferences("solve_quota", Context.MODE_PRIVATE)

    override var dayKey: Long
        get() = prefs.getLong("day", Long.MIN_VALUE)
        set(value) = prefs.edit { putLong("day", value) }

    override var used: Int
        get() = prefs.getInt("used", 0)
        set(value) = prefs.edit { putInt("used", value) }
}

/** Placeholder until Play Billing is wired up: nobody is subscribed yet. */
object NoSubscription : Entitlements {
    override fun isSubscribed() = false
}
