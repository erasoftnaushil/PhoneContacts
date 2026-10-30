package com.contactsapp2025.universal.permissionApi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.webkit.WebSettings
import androidx.core.content.ContextCompat
import com.contactsapp2025.universal.permissionApi.database.PermissionStore
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.time.TrustedTime
import com.google.android.gms.time.TrustedTimeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URL
import java.util.Locale

object PermissionUtil {

    private val TAG = PermissionUtil::class.java.simpleName

    // ─── Prefs ────────────────────────────────────────────────────────────────
    private object Prefs {
        private const val PREF_FILE = "sp"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

        fun getInt(context: Context, key: String, default: Int = 0): Int =
            prefs(context).getInt(key, default)

        fun setInt(context: Context, key: String, value: Int) =
            prefs(context).edit().putInt(key, value).apply()

        fun getLong(context: Context, key: String, default: Long = 0L): Long =
            prefs(context).getLong(key, default)

        fun setLong(context: Context, key: String, value: Long) =
            prefs(context).edit().putLong(key, value).apply()

        fun getString(context: Context, key: String, default: String = ""): String =
            prefs(context).getString(key, default) ?: default

        fun setString(context: Context, key: String, value: String) =
            prefs(context).edit().putString(key, value).apply()
    }

    // ─── Keys ────────────────────────────────────────────────────────────────
    private const val PREF_NOTIFICATION = "pref_notification"
    private const val PREF_PHONECALL    = "pref_phone"
    private const val PREF_OVERLAY      = "pref_overlay"
    private const val PREF_LOCATION     = "pref_location"
    private const val PREF_LAST_PAYLOAD = "pref_payload"
    private const val PREF_UNIQUE       = "pref_unique"

    // ─── State ───────────────────────────────────────────────────────────────
    var uniqueID = ""
        private set

    var trustedTimeClient: TrustedTimeClient? = null
        private set

    lateinit var userInfoManager: UserInfoManager
        private set

    private val _isInitCompleted = MutableStateFlow(false)
    val isInitCompleted = _isInitCompleted.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbMutex = Mutex()
    private val sendMutex = Mutex()

    // ─── Init ─────────────────────────────────────────────────────────────────
    fun init(context: Context) {
        initTrustedTime(context)
        if (!::userInfoManager.isInitialized) {
            userInfoManager = UserInfoManager(context.applicationContext)
        }
        checkAndSyncPermissions(context)
    }

    fun onAppOpen(context: Context) {
        scope.launch {
            uniqueID = ensureBasePayload(context)
            upsertDB(mapOf("cdo_open" to 1))
            checkAndSyncPermissions(context)
        }
    }

    // ─── Permission: Ask ──────────────────────────────────────────────────────
    fun onAskLocation(activity: Activity, source: String = "") =
        onAsk(activity, PREF_LOCATION, "p_location", source)

    fun onAskNotification(activity: Activity, source: String = "") =
        onAsk(activity, PREF_NOTIFICATION, "p_notification", source)

    fun onAskPhoneCall(activity: Activity, source: String = "") =
        onAsk(activity, PREF_PHONECALL, "p_phone_call", source)

    fun onAskOverlay(activity: Activity, source: String = "") =
        onAsk(activity, PREF_OVERLAY, "p_overlay", source, includeOpenAt = false)

    private fun onAsk(
        activity: Activity,
        prefKey: String,
        dbPrefix: String,
        source: String,
        includeOpenAt: Boolean = true
    ) {
        if (Prefs.getInt(activity, prefKey, 0) != 0) return
        val now = getTrustedUtcMillis()
        val fields = mutableMapOf<String, Any>(
            "${dbPrefix}_dialogue_ask_at" to now,
            "${dbPrefix}_source" to source.ifEmpty { activity::class.java.simpleName }
        )
        if (includeOpenAt) fields["${dbPrefix}_dialogue_open_at"] = now
        upsertDB(fields)
    }

    // ─── Permission: Result ───────────────────────────────────────────────────
    fun onLocationResult(context: Context, granted: Boolean) =
        onResult(context, PREF_LOCATION, "p_location", granted)

    fun onNotificationResult(context: Context, granted: Boolean) =
        onResult(context, PREF_NOTIFICATION, "p_notification", granted)

    fun onPhoneCallResult(context: Context, granted: Boolean) =
        onResult(context, PREF_PHONECALL, "p_phone_call", granted)

    fun onOverlayResult(context: Context, granted: Boolean) =
        onResult(context, PREF_OVERLAY, "p_overlay", granted)

    private fun onResult(
        context: Context,
        prefKey: String,
        dbPrefix: String,
        granted: Boolean
    ) {
        if (Prefs.getInt(context, prefKey, 0) != 0) return
        upsertDB(mapOf(
            "${dbPrefix}_dialogue_close_at" to getTrustedUtcMillis(),
            "${dbPrefix}_status" to if (granted) 1 else 2
        ))
        if (granted) Prefs.setInt(context, prefKey, 1)
    }

    // ─── Sync Permissions & Send ──────────────────────────────────────────────
    fun checkAndSyncPermissions(context: Context) {
        scope.launch {
            uniqueID = ensureBasePayload(context)
            syncIfGrantedFromSettings(context)
            val payload = PermissionStore.repo.getPayload(uniqueID) ?: return@launch
            sendToServer(context, payload)
            _isInitCompleted.value = true
        }
    }

    private suspend fun syncIfGrantedFromSettings(context: Context) {
        data class PermSync(
            val prefKey: String,
            val dbPrefix: String,
            val isGranted: Boolean,
            val hasOpenAt: Boolean = true
        )

        val checks = listOf(
            PermSync(PREF_PHONECALL,     "p_phone_call",   context.hasPermission(Manifest.permission.READ_PHONE_STATE)),
            PermSync(PREF_OVERLAY,       "p_overlay",      Settings.canDrawOverlays(context), hasOpenAt = false),
            PermSync(PREF_NOTIFICATION,  "p_notification", hasPostNotificationPermission(context)),
            PermSync(PREF_LOCATION,      "p_location",     context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        )

        checks.forEach { (prefKey, dbPrefix, isGranted, hasOpenAt) ->
            if (Prefs.getInt(context, prefKey, 0) == 0 && isGranted) {
                val fields = mutableMapOf<String, Any>(
                    "${dbPrefix}_dialogue_ask_at"   to 0,
                    "${dbPrefix}_dialogue_close_at" to 0,
                    "${dbPrefix}_source"            to "App Settings",
                    "${dbPrefix}_status"            to 1
                )
                if (hasOpenAt) fields["${dbPrefix}_dialogue_open_at"] = 0
                upsertPermissionFieldsSuspend(uniqueID, fields)
                Prefs.setInt(context, prefKey, 1)
            }
        }
    }

    fun updateCommonFields(context: Context) {
        scope.launch {
            uniqueID = ensureBasePayload(context)
            val (installDate, updateDate) = getInstallAndUpdateDates(context)
            val ipAddress = fetchIpAddress(context)
            val versionName = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: ""
            upsertPermissionFieldsSuspend(uniqueID, mapOf(
                "install_date" to installDate,
                "update_date"  to updateDate,
                "ip_address"   to ipAddress,
                "ad_id"        to getAdID(context),
                "app_version"  to versionName
            ))
        }
    }

    // ─── DB Helpers ───────────────────────────────────────────────────────────
    private fun upsertDB(fields: Map<String, Any>) {
        scope.launch {
            upsertPermissionFieldsSuspend(uniqueID, fields)
        }
    }

    suspend fun upsertPermissionFieldsSuspend(id: String, fields: Map<String, Any>) {
        if (id.isBlank()) return
        dbMutex.withLock {
            Log.d(TAG, "DB update: $fields")
            PermissionStore.repo.upsert(id, fields)
        }
    }

    // ─── Base Payload ─────────────────────────────────────────────────────────
    suspend fun ensureBasePayload(context: Context): String {
        PermissionStore.init(context)
        val savedId = Prefs.getString(context, PREF_UNIQUE, "")
        val uniqueId = savedId.ifEmpty {
            val generated = if (isNetworkAvailable(context)) getAppSetIdSuspend(context) else "unknown"
            Prefs.setString(context, PREF_UNIQUE, generated)
            generated
        }
        // Return early if record already exists
        if (PermissionStore.repo.getPayload(uniqueId) != null) return uniqueId

        val (installDate, updateDate) = getInstallAndUpdateDates(context)
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

        PermissionStore.repo.upsert(uniqueId, mapOf(
            "unique_user_id"  to uniqueId,
            "ad_id"           to getAdID(context),
            "package_name"    to context.packageName,
            "app_version"     to versionName,
            "android_os"      to Build.VERSION.RELEASE.trim(),
            "device_brand"    to Build.MANUFACTURER,
            "device_model"    to Build.MODEL,
            "device_id"       to getDeviceId(context),
            "ip_address"      to fetchIpAddress(context),
            "install_date"    to installDate,
            "update_date"     to updateDate,
            "country"         to context.getCountryName(),
            "iso_country_code" to getIsoCountryCode(context),
            "user_agent"      to getSafeUserAgent(context),
            // defaults
            "set_as_default_source"     to "", "set_as_default_status" to 0, "set_as_default_updated_at" to 0,
            "p_phone_call_dialogue_ask_at" to 0, "p_phone_call_dialogue_open_at" to 0,
            "p_phone_call_dialogue_close_at" to 0, "p_phone_call_source" to "", "p_phone_call_status" to 0,
            "p_overlay_dialogue_ask_at" to 0, "p_overlay_dialogue_close_at" to 0,
            "p_overlay_source" to "", "p_overlay_status" to 0,
            "p_location_dialogue_ask_at" to 0, "p_location_dialogue_open_at" to 0,
            "p_location_dialogue_close_at" to 0, "p_location_source" to "", "p_location_status" to 0,
            "p_notification_dialogue_ask_at" to 0, "p_notification_dialogue_open_at" to 0,
            "p_notification_dialogue_close_at" to 0, "p_notification_source" to "", "p_notification_status" to 0,
            "cdo_open" to 0
        ))
        Log.d(TAG, "Base payload created for: $uniqueId")
        return uniqueId
    }

    // ─── Server ───────────────────────────────────────────────────────────────
    suspend fun sendToServer(context: Context, payloadJson: JSONObject) {
        sendMutex.withLock {
            val normalized = payloadJson.toString()
            val lastPayload = Prefs.getString(context, PREF_LAST_PAYLOAD, "")
            if (normalized == lastPayload) {
                Log.d(TAG, "Payload unchanged, skipping.")
                return
            }
            if (!isNetworkAvailable(context)) {
                Log.d(TAG, "No internet, skipping.")
                return
            }
            val currentUniqueId = Prefs.getString(context, PREF_UNIQUE, "")
            if (currentUniqueId == "unknown") {
                // Regenerate ID then resend
                val newId = getAppSetIdSuspend(context)
                upsertPermissionFieldsSuspend(uniqueID, mapOf("unique_user_id" to newId))
                delay(500)
                val newPayload = PermissionStore.repo.getPayload(uniqueID) ?: return
                userInfoManager.sendPayload(jsonToMap(newPayload))
            } else {
                userInfoManager.sendPayload(jsonToMap(payloadJson))
            }
            Prefs.setString(context, PREF_LAST_PAYLOAD, normalized)
            Log.d(TAG, "Payload sent successfully.")
        }
    }

    // ─── Utils ────────────────────────────────────────────────────────────────
    fun getTrustedUtcMillis(): Long =
        trustedTimeClient?.computeCurrentUnixEpochMillis() ?: System.currentTimeMillis()

    fun hasPostNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        else true

    fun Context.hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun initTrustedTime(context: Context) {
        try {
            TrustedTime.createClient(context).addOnCompleteListener { task ->
                trustedTimeClient = task.result.takeIf { task.isSuccessful }
            }
        } catch (e: Exception) {
            trustedTimeClient = null
        }
    }

    private suspend fun fetchIpAddress(context: Context): String =
        try {
            if (isNetworkAvailable(context)) URL("https://api.ipify.org").readText() else ""
        } catch (e: Exception) { "" }

    fun getInstallAndUpdateDates(context: Context): Pair<Long, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.firstInstallTime to info.lastUpdateTime
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    suspend fun getAppSetIdSuspend(context: Context): String =
        suspendCancellableCoroutine { cont ->
            AppSet.getClient(context).appSetIdInfo
                .addOnSuccessListener { cont.resume(it.id, null) }
                .addOnFailureListener { cont.resume("unknown", null) }
        }

    fun getSafeUserAgent(context: Context): String =
        try { WebSettings.getDefaultUserAgent(context) }
        catch (_: Throwable) { "Android/${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL}" }

    fun getAdID(context: Context): String =
        if (isNetworkAvailable(context))
            AdvertisingIdClient.getAdvertisingIdInfo(context)?.id ?: "unknown_gaid"
        else "unknown_gaid"

    fun getIsoCountryCode(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when {
            !tm.simCountryIso.isNullOrBlank()     -> tm.simCountryIso.uppercase()
            !tm.networkCountryIso.isNullOrBlank() -> tm.networkCountryIso.uppercase()
            else -> Locale.getDefault().country
        }
    }

    fun isNetworkAvailable(context: Context): Boolean =
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetworkInfo?.isConnected == true
        } catch (e: Exception) { false }

    fun jsonToMap(json: JSONObject): Map<String, Any> =
        json.keys().asSequence().associateWith { key ->
            json.get(key).let { if (it is JSONObject) jsonToMap(it) else it }
        }
}
