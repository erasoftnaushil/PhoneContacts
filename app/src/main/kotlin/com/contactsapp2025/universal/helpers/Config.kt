package com.contactsapp2025.universal.helpers

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.contactsapp2025.universal.ads.AppInfo
import com.google.gson.Gson
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.SHOW_TABS
import org.fossify.commons.helpers.TAB_CALL_HISTORY

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    /** Favorites tab display mode: VIEW_TYPE_GRID (default) or VIEW_TYPE_LIST */
    var favoritesDisplayMode: Int
        get() = prefs!!.getInt("FAVORITES_DISPLAY_MODE", org.fossify.commons.helpers.VIEW_TYPE_GRID)
        set(value) = prefs!!.edit { putInt("FAVORITES_DISPLAY_MODE", value) }

    fun setPhoneCallPermissionDenyCounter(counter: Int) {
        prefs!!.edit { putInt("PHONE_CALL_PERMISSION_DENY_COUNTER", counter) }
    }

    fun phoneCallPermissionDenyCounter(): Int {
        return prefs!!.getInt("PHONE_CALL_PERMISSION_DENY_COUNTER", 0)
    }

    fun setNotificationPermissionDenyCounter(counter: Int) {
        prefs!!.edit { putInt("NOTIFICATION_PERMISSION_DENY_COUNTER", counter) }
    }

    fun notificationPermissionDenyCounter(): Int {
        return prefs!!.getInt("NOTIFICATION_PERMISSION_DENY_COUNTER", 0)
    }

    var notificationPermissionRequested: Boolean
        get() = prefs!!.getBoolean("NOTIFICATION_PERMISSION_REQUESTED", false)
        set(value) = prefs!!.edit { putBoolean("NOTIFICATION_PERMISSION_REQUESTED", value) }

    fun setLanguage(language: String) {
        prefs!!.edit { putString("SELECTED_LANG", language) }
    }

    fun getLanguage(): String? {
        return prefs!!.getString("SELECTED_LANG", null)
    }

    var lastFetched: Long
        get() = prefs.getLong("LAST_FETCHED", 0)
        set(value) = prefs.edit { putLong("LAST_FETCHED", value) }

    var showTabs: Int
        get() {
            if (!prefs.getBoolean("CALL_HISTORY_TAB_MIGRATED", false)) {
                val current = (prefs.getInt(SHOW_TABS, ALL_TABS_MASK) or TAB_CALL_HISTORY) and ALL_TABS_MASK
                prefs.edit().putInt(SHOW_TABS, current).putBoolean("CALL_HISTORY_TAB_MIGRATED", true).apply()
                return current
            }
            val stored = prefs.getInt(SHOW_TABS, ALL_TABS_MASK) and ALL_TABS_MASK
            return if (stored == 0) ALL_TABS_MASK else stored
        }
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs and ALL_TABS_MASK).putBoolean("CALL_HISTORY_TAB_MIGRATED", true).apply()

    private fun normalizeBlockedKey(number: String): String {
        val digits = number.filter { it.isDigit() || it == '+' }
        return if (digits.length >= 9) digits.takeLast(9) else digits.ifEmpty { number.trim() }
    }

    fun isLocalNumberBlocked(number: String): Boolean {
        if (number.isBlank()) return false
        val key = normalizeBlockedKey(number)
        val set = prefs.getStringSet("LOCAL_BLOCKED_NUMBERS", emptySet()) ?: emptySet()
        return set.contains(key) || set.contains(number.trim())
    }

    fun setLocalNumberBlocked(number: String, blocked: Boolean) {
        if (number.isBlank()) return
        val key = normalizeBlockedKey(number)
        val current = (prefs.getStringSet("LOCAL_BLOCKED_NUMBERS", emptySet()) ?: emptySet()).toMutableSet()
        if (blocked) {
            current.add(key)
            current.add(number.trim())
        } else {
            current.remove(key)
            current.remove(number.trim())
        }
        prefs.edit().remove("LOCAL_BLOCKED_NUMBERS").putStringSet("LOCAL_BLOCKED_NUMBERS", current).apply()
    }

    var autoBackupContactSources: Set<String>
        get() = prefs.getStringSet(AUTO_BACKUP_CONTACT_SOURCES, setOf())!!
        set(autoBackupContactSources) = prefs.edit().remove(AUTO_BACKUP_CONTACT_SOURCES).putStringSet(AUTO_BACKUP_CONTACT_SOURCES, autoBackupContactSources)
            .apply()

    fun saveAppList(appList: String) {
        prefs!!.edit { putString(KEY_APP_LIST, appList)?.apply() }
    }

    fun getAppList(): List<AppInfo> {
        val json = prefs.getString(KEY_APP_LIST, null)
        return if (!json.isNullOrEmpty()) {
            try {
                Gson().fromJson(json, Array<AppInfo>::class.java).toList()
            } catch (e: Exception) {
                Log.e("RemoteConfig", "JSON parse error", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    var hasReachedMainActivity: Boolean
        get() = prefs!!.getBoolean("hasReachedMainActivity", false)
        set(value) = prefs!!.edit { putBoolean("hasReachedMainActivity", value) }

    var isOnboardingCompleted: Boolean
        get() = prefs!!.getBoolean("isOnboardingCompleted", false)
        set(value) = prefs!!.edit { putBoolean("isOnboardingCompleted", value) }

    var onboardingStartedAt: Long
        get() = prefs!!.getLong("onboardingStartedAt", 0L)
        set(value) = prefs!!.edit { putLong("onboardingStartedAt", value) }

    var notificationStage: Int
        get() = prefs!!.getInt("notificationStage", 0)
        set(value) = prefs!!.edit { putInt("notificationStage", value) }

    var notificationCount: Int
        get() = prefs!!.getInt("notificationCount", 0)
        set(value) = prefs!!.edit { putInt("notificationCount", value) }

    var notificationEngineActive: Boolean
        get() = prefs!!.getBoolean("notificationEngineActive", false)
        set(value) = prefs!!.edit { putBoolean("notificationEngineActive", value) }

    var lastNotificationTime: Long
        get() = prefs!!.getLong("lastNotificationTime", 0L)
        set(value) = prefs!!.edit { putLong("lastNotificationTime", value) }

    var contactFeatureNotificationEnabled: Boolean
        get() = prefs!!.getBoolean("contactFeatureNotificationEnabled", true)
        set(value) = prefs!!.edit { putBoolean("contactFeatureNotificationEnabled", value) }

    var lastContactFeatureNotificationTime: Long
        get() = prefs!!.getLong("lastContactFeatureNotificationTime", 0L)
        set(value) = prefs!!.edit { putLong("lastContactFeatureNotificationTime", value) }

    var lastContactFeatureNotificationIndex: Int
        get() = prefs!!.getInt("lastContactFeatureNotificationIndex", -1)
        set(value) = prefs!!.edit { putInt("lastContactFeatureNotificationIndex", value) }

    var nextContactFeatureNotificationTime: Long
        get() = prefs!!.getLong("nextContactFeatureNotificationTime", 0L)
        set(value) = prefs!!.edit { putLong("nextContactFeatureNotificationTime", value) }

    var contactFeatureNotificationStarted: Boolean
        get() = prefs!!.getBoolean("contactFeatureNotificationStarted", false)
        set(value) = prefs!!.edit { putBoolean("contactFeatureNotificationStarted", value) }
        
    var contactFeatureNotificationCount: Int
        get() = prefs!!.getInt("contactFeatureNotificationCount", 0)
        set(value) = prefs!!.edit { putInt("contactFeatureNotificationCount", value) }
}
