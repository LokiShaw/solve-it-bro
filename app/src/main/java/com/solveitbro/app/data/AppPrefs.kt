package com.solveitbro.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale
import java.util.UUID

enum class Language(val code: String) {
    EN("en"),
    HI("hi");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: deviceDefault()

        fun deviceDefault() = if (Locale.getDefault().language == "hi") HI else EN
    }
}

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** Random per-install id the backend uses for the free daily limit. */
    val deviceId: String
        get() = prefs.getString("device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString("device_id", it) } }

    /** Language the explanation is written in. */
    var language: Language
        get() = Language.fromCode(prefs.getString("language", null))
        set(value) = prefs.edit { putString("language", value.code) }
}
