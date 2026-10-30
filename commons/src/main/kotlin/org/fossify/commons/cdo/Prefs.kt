package org.fossify.commons.cdo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Prefs(context: Context) {

    private var APP_PREF_INT_EXAMPLE = "contacts_app_2025_pref"

    val preferences: SharedPreferences =
        context.getSharedPreferences(APP_PREF_INT_EXAMPLE, Context.MODE_PRIVATE)

    fun getInstallTime(): Long {
        return preferences.getLong("install_time", -1L)
    }

    fun setInstallTime(time: Long) {
        preferences.edit { putLong("install_time", time) }
    }
}
