package com.permissionsdk.p_utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal object SDKPreferenceManager {
    private const val PREF_NAME = "permission_sdk_prefs"
    private const val KEY_UNIQUE_USER_ID = "unique_user_id"
    private const val KEY_AD_ID = "ad_id"
    private const val KEY_DEVICE_ID = "device_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveUniqueUserId(context: Context, id: String) {
        getPrefs(context).edit { putString(KEY_UNIQUE_USER_ID, id) }
    }

    fun getUniqueUserId(context: Context): String? {
        return getPrefs(context).getString(KEY_UNIQUE_USER_ID, null)
    }

    fun saveAdId(context: Context, id: String) {
        getPrefs(context).edit { putString(KEY_AD_ID, id) }
    }

    fun getAdId(context: Context): String? {
        return getPrefs(context).getString(KEY_AD_ID, null)
    }

    fun saveDeviceId(context: Context, id: String) {
        getPrefs(context).edit { putString(KEY_DEVICE_ID, id) }
    }

    fun getDeviceId(context: Context): String? {
        return getPrefs(context).getString(KEY_DEVICE_ID, null)
    }


}
