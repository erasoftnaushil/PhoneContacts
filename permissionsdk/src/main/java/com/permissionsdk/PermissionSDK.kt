package com.permissionsdk

import android.content.Context
import android.os.Looper
import com.google.android.gms.time.TrustedTime
import com.google.android.gms.time.TrustedTimeClient
import com.permissionsdk.api.RetrofitClient
import com.permissionsdk.db.PermissionDatabase
import com.permissionsdk.model.PermissionEntity
import com.permissionsdk.p_utils.*
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.permissionsdk.db.PermissionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.app.Application
import android.app.Activity
import android.os.Bundle

object PermissionSDK {

    private lateinit var appContext: Context
    private var secretKey: String = ""
    private var overlayActivityName: String = ""
    private val sdkScope = CoroutineScope(Dispatchers.IO)
    private val dataMutex = Mutex()
    var trustedTimeClient: TrustedTimeClient? = null
    private var initJob: Job? = null
    private var isLifecycleRegistered = false

    // ─── Overlay Attempt Tracking ─────────────────────────────────────────
    // Track when user explicitly attempts overlay permission (calls notifyOpeningOverlaySettings)
    @Volatile private var overlayAttemptPending = false

    private var pausedActivityName = ""

    val dao: PermissionDao by lazy { PermissionDatabase.getDatabase(appContext).permissionDao() }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    fun with(context: Context): PermissionSDK {
        appContext = context.applicationContext
        initTrustedTime()
        return this
    }

    fun setSecretKey(secret: String): PermissionSDK {
        this.secretKey = secret
        return this
    }

    fun setOverlayActivity(activityName: String): PermissionSDK {
        this.overlayActivityName = activityName
        return this
    }

    /**
     * ⚠️ CALL THIS before starting overlay settings intent!
     *
     * This tells SDK: "User is about to attempt overlay permission"
     * Without this, we can't distinguish between:
     * - User attempting overlay permission dialog
     * - App checking overlay state (could be 0 or 2 for other reasons)
     *
     * Example:
     * PermissionSDK.notifyOpeningOverlaySettings()
     * startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, ...))
     */
    fun notifyOpeningOverlaySettings() {
        overlayAttemptPending = true
        logW("🔔 notifyOpeningOverlaySettings() — User will attempt overlay permission. Flag set.")
    }

    // ─────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────

    private fun initTrustedTime() {
        try {
            TrustedTime.createClient(appContext).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    trustedTimeClient = task.result
                    logW("✅ Trusted Time is ready!")
                } else {
                    trustedTimeClient = null
                    logW("⚠️ Could not get Trusted Time: ${task.exception?.message}. Using system time.")
                }
            }
        } catch (e: Exception) {
            trustedTimeClient = null
            logE("TrustedTime init exception: ${e.message}")
        }
    }

    private fun enforceMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException(
                """
            ❌ PermissionSDK Error
            
            It must be called from the Main (UI) thread.
            """.trimIndent()
            )
        }
    }

    fun initialize() {
        enforceMainThread()

        if (!isLifecycleRegistered) {
            (appContext as? Application)?.registerActivityLifecycleCallbacks(object :
                Application.ActivityLifecycleCallbacks {

                override fun onActivityPaused(activity: Activity) {
                    pausedActivityName = activity.javaClass.simpleName
                }

                override fun onActivityResumed(activity: Activity) {
                    val activityName = activity.javaClass.simpleName
                    val isCdoActivity = activityName.contains(ContextCompat.getString(appContext, R.string.permission_sdk_cdo_activity))

                    logW("▶️ Resumed: $activityName | isCDO=$isCdoActivity | overlayAttemptPending=$overlayAttemptPending")

                    checkPermissions(activity, pausedActivityName, isCdoActivity)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
            isLifecycleRegistered = true
            logW("✅ Auto-permission tracking enabled.")
        }

        logW("🚀 Starting PermissionSDK...")

        initJob = sdkScope.launch {
            dataMutex.withLock {
                val existing = dao.getPermissionData()

                if (existing == null) {
                    // No database data yet - TYPE A or TYPE B user
                    var updated = getOrCreateEntity()

                    if (updated.installDate == updated.updateDate) {
                        // TYPE A: New User
                        logW("🆕 Type A: New user detected. Checking system permissions...")

                        updated = updated.copy(
                            pPhoneCallStatus = checkNewUserPermission(Manifest.permission.READ_PHONE_STATE),
                            pOverlayStatus = checkNewUserPermissionOverlay(),
                            pLocationStatus = checkNewUserPermissionLocation(),
                            pNotificationStatus = if (isTiramisuPlus) checkNewUserPermission(Manifest.permission.POST_NOTIFICATIONS) else 1,
                            setAsDefaultStatus = 0,
                            cdoOpen = checkCdoPermission()
                        )
                    } else {
                        // TYPE B: Old User Without SDK
                        logW("👤 Type B: Old user (upgraded from version without SDK). Checking system permissions...")

                        updated = updated.copy(
                            pPhoneCallStatus = checkOldUserPermission(Manifest.permission.READ_PHONE_STATE),
                            pOverlayStatus = checkOldUserPermissionOverlay(),
                            pLocationStatus = checkOldUserPermissionLocation(),
                            pNotificationStatus = if (isTiramisuPlus) checkOldUserPermission(Manifest.permission.POST_NOTIFICATIONS) else 1,
                            setAsDefaultStatus = 0,
                            cdoOpen = checkCdoPermission()
                        )
                    }

                    dao.insertOrUpdate(updated)
                    syncWithBackend(updated)
                    logW("✨ Initial sync completed")

                } else {
                    // TYPE C: Old User With SDK
                    logW("📊 Type C: Old user with SDK detected. Skipping initialization.")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Permission Check Functions - New User (Type A)
    // ─────────────────────────────────────────────────────────────────────
    // For NEW users: Only return 0 (never attempted) or 1 (granted)
    // NEVER return 2 (denied) because new user hasn't been through dialog yet
    // If system says "not granted", it just means Android hasn't granted it,
    // NOT that user explicitly denied it.

    private fun checkNewUserPermission(permission: String?): Int {
        return if (permission != null) {
            val isGranted = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
            // For new user: 1 (granted) or 0 (not granted, not attempted)
            if (isGranted) 1 else 0
        } else {
            0
        }
    }

    private fun checkNewUserPermissionOverlay(): Int {
        // For new user: Only return 0 or 1
        // 0→2 transition only if user explicitly attempted via notifyOpeningOverlaySettings()
        return when {
            Settings.canDrawOverlays(appContext) -> 1
            else -> 0  // New user: not granted doesn't mean denied, keep as 0
        }
    }

    private fun checkNewUserPermissionLocation(): Int {
        val isFineGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isCoarseGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // For new user: 1 (granted) or 0 (not granted, not attempted)
        return when {
            isFineGranted || isCoarseGranted -> 1
            else -> 0
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Permission Check Functions - Old User
    // ─────────────────────────────────────────────────────────────────────

    private fun checkOldUserPermission(permission: String?): Int {
        return if (permission != null) {
            val isGranted = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
            when {
                isGranted -> 1
                else -> 2  // Old user: never 0, treat as denied
            }
        } else {
            2
        }
    }

    private fun checkOldUserPermissionOverlay(): Int {
        return when {
            Settings.canDrawOverlays(appContext) -> 1
            else -> 2  // Old user: never 0, treat as denied
        }
    }

    private fun checkOldUserPermissionLocation(): Int {
        val isFineGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val isCoarseGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        return when {
            isFineGranted || isCoarseGranted -> 1
            else -> 2  // Old user: never 0, treat as denied
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CDO Check Function
    // ─────────────────────────────────────────────────────────────────────

    private fun checkCdoPermission(): Int {
        return try {
            val tm = appContext.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            if (tm?.defaultDialerPackage == appContext.packageName) 1 else 0
        } catch (e: Exception) {
            logE("Error checking CDO permission: ${e.message}")
            0
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Core permission check
    // ─────────────────────────────────────────────────────────────────────

    fun checkPermissions(activity: Activity? = null, sourceActivity: String = "", isCdoActivity: Boolean = false) {
        logW("🔍 Checking permissions... (source=$sourceActivity isCDO=$isCdoActivity overlayAttempt=$overlayAttemptPending)")

        updateData(activity, sourceActivity, isCdoActivity)
    }

    // ─────────────────────────────────────────────────────────────────────
    // State Validation & Resolution
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Smart merge: Once 1 (granted), never downgrade. Can upgrade 2→1.
     */
    private fun resolvePermissionStatus(current: Int, existing: Int): Int = when {
        existing == 1 -> 1              // Already granted = LOCKED
        current == 1 -> 1               // Now granted = LOCK
        else -> current                 // Otherwise use current
    }

    /**
     * Validate permission transition before accepting
     */
    private fun validatePermissionTransition(old: Int, new: Int, permName: String): Int {
        return when {
            old == 1 && new != 1 -> {
                logW("❌ Permission $permName: Attempted downgrade from 1 to $new. Rejected. Keeping locked state at 1.")
                1
            }
            old == 2 && new == 0 -> {
                logW("❌ Permission $permName: Attempted impossible transition 2→0. Rejected. Keeping state at 2.")
                2
            }
            else -> new
        }
    }

    /**
     * Validate overlay transition with attempt flag check
     */
    private fun validateOverlayTransition(old: Int, new: Int): Int {
        return when {
            old == 1 && new != 1 -> {
                logW("❌ Overlay: Attempted downgrade from 1 to $new. Rejected. Keeping locked state at 1.")
                1
            }
            old == 2 && new == 0 -> {
                logW("❌ Overlay: Attempted impossible transition 2→0. Rejected. Keeping state at 2.")
                2
            }
            // ✅ NEW: If transitioning from 0 to 2, only allow if user explicitly attempted
            old == 0 && new == 2 && !overlayAttemptPending -> {
//                logW("⚠️ Overlay: Would change 0→2, but overlayAttemptPending=false. User didn't explicitly attempt. Keeping at 0.")
                0  // Don't record the denial, user never attempted
            }
            else -> new
        }
    }

    /**
     * Validate CDO transition before accepting
     */
//    private fun validateCdoTransition(old: Int, new: Int): Int {
//        return when {
//            old == 1 && new == 0 -> {
//                logW("❌ CDO: Attempted to close from 1 to 0. Not possible. Keeping locked state at 1.")
//                1
//            }
//            else -> new
//        }
//    }

    /**
     * Source is written ONCE when permission first transitions
     */
    private fun resolveSourceOnce(justDecided: Boolean, existingSource: String, newSource: String): String =
        if (justDecided && existingSource.isEmpty()) newSource else existingSource

    // ─────────────────────────────────────────────────────────────────────
    // Entity creation / update
    // ─────────────────────────────────────────────────────────────────────

    private suspend fun getOrCreateEntity(): PermissionEntity {
        val db = PermissionDatabase.getDatabase(appContext)
        val dao = db.permissionDao()
        val existing = dao.getPermissionData()

        var uniqueUserId = SDKPreferenceManager.getUniqueUserId(appContext)
        if (uniqueUserId.isNullOrEmpty()) {
            uniqueUserId = appContext.getAppSetId() ?: ""
            if (uniqueUserId.isNotEmpty()) SDKPreferenceManager.saveUniqueUserId(appContext, uniqueUserId)
        }

        var adId = SDKPreferenceManager.getAdId(appContext)
        if (adId.isNullOrEmpty()) {
            adId = try {
                AdvertisingIdClient.getAdvertisingIdInfo(appContext).id ?: ""
            } catch (e: Exception) {
                logE("⚠️ Could not find Advertising ID: ${e.message}")
                ""
            }
            if (adId.isNotBlank()) SDKPreferenceManager.saveAdId(appContext, adId)
        }

        var deviceId = SDKPreferenceManager.getDeviceId(appContext)
        if (deviceId.isNullOrEmpty()) {
            deviceId = appContext.findDeviceId()
            if (deviceId.isNotBlank()) SDKPreferenceManager.saveDeviceId(appContext, deviceId)
        }

        if (uniqueUserId.isEmpty()) uniqueUserId = "generated_${System.currentTimeMillis()}"
        if (adId.isEmpty()) adId = SDKPreferenceManager.getAdId(appContext) ?: "no_ad_id"
        if (deviceId.isEmpty()) deviceId = SDKPreferenceManager.getDeviceId(appContext) ?: "no_device_id"

        return if (existing == null) {
            val pkgInfo = appContext.permissionPkgInfo()
            PermissionEntity(
                uniqueUserId = uniqueUserId,
                adId = adId,
                packageName = appContext.packageName,
                appVersion = appContext.getAppVersion(),
                androidOs = appContext.getOsVersion(),
                deviceBrand = appContext.getBrand(),
                deviceModel = appContext.getModel(),
                deviceId = deviceId,
                ipAddress = getIpAddress(),
                installDate = pkgInfo.firstInstallTime,
                updateDate = pkgInfo.lastUpdateTime,
                country = appContext.getCountryName(),
                isoCountryCode = appContext.getCountryCode(),
                userAgent = appContext.getUserAgent()
            )
        } else {
            val shouldRefreshCountry = existing.country.isEmpty() || existing.country == "Unknown"
            val pkgInfo = appContext.permissionPkgInfo()
            existing.copy(
                adId = if (adId != "no_ad_id") adId else existing.adId,
                deviceId = if (deviceId != "no_device_id") deviceId else existing.deviceId,
                country = if (shouldRefreshCountry) appContext.getCountryName() else existing.country,
                isoCountryCode = if (shouldRefreshCountry) appContext.getCountryCode() else existing.isoCountryCode,
                ipAddress = getIpAddress(),
                updateDate = pkgInfo.lastUpdateTime
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Change detection
    // ─────────────────────────────────────────────────────────────────────

    private fun hasPermissionStatusChanged(old: PermissionEntity, new: PermissionEntity): Boolean {
        var changed = false

        if (old.pPhoneCallStatus != new.pPhoneCallStatus) {
            logW("📞 PhoneCall permission changed: ${old.pPhoneCallStatus} -> ${new.pPhoneCallStatus}")
            changed = true
        }

        if (old.pOverlayStatus != new.pOverlayStatus) {
            logW("🪟 Overlay permission changed: ${old.pOverlayStatus} -> ${new.pOverlayStatus}")
            changed = true
        }

        if (old.pLocationStatus != new.pLocationStatus) {
            logW("📍 Location permission changed: ${old.pLocationStatus} -> ${new.pLocationStatus}")
            changed = true
        }

        if (old.pNotificationStatus != new.pNotificationStatus) {
            logW("🔔 Notification permission changed: ${old.pNotificationStatus} -> ${new.pNotificationStatus}")
            changed = true
        }

        return changed
    }

    private fun hasCdoStatusChanged(old: PermissionEntity, new: PermissionEntity): Boolean {
        return if (old.cdoOpen != new.cdoOpen) {
            logW("🟢 CDO status changed: ${old.cdoOpen} -> ${new.cdoOpen}")
            true
        } else {
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Backend sync
    // ─────────────────────────────────────────────────────────────────────

    private suspend fun syncWithBackend(data: PermissionEntity) {
        try {
            logW("📤 Sending updated information to server...")
            logW("🔍 Data Snapshot: ${data.toString().prettyJson()}")
            val response = RetrofitClient.getInstance(appContext, secretKey).updatePermissions(data)
            logW("response: ${response.code()} - ${response.message()}")
            if (response.isSuccessful) {
                if (response.body()?.ok == true) {
                    logW("✅ Successfully updated on the server!")
                    dao.insertOrUpdate(data.copy(isSynced = true))
                } else {
                    logE("❌ Server returned success but 'ok' is false.")
                }
            } else {
                logE("❌ Server update failed (Error ${response.code()}). Will retry on next startup.")
            }
        } catch (e: Exception) {
            logE("❌ Connection error: ${e.message}. Will retry on next startup.")
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal update helper
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Update permission data and sync only if meaningful changes detected.
     */
    private fun updateData(activity: Activity? = null, sourceActivity: String = "", isCdoActivity: Boolean = false) {
        sdkScope.launch {
            dataMutex.withLock {
                initJob?.join()

                val entity = getOrCreateEntity()
                var updated = entity.copy(isSynced = false)

                // ── 1. Phone ──────────────────────────────────────────────────────
                val isPhoneGranted = ContextCompat.checkSelfPermission(
                    appContext, Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
                val isPhoneDenied = activity != null &&
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)
                val phoneLive = when {
                    isPhoneGranted -> 1
                    isPhoneDenied  -> 2
                    else           -> 0
                }

                val phoneResolved = resolvePermissionStatus(phoneLive, entity.pPhoneCallStatus)
                val phoneValidated = validatePermissionTransition(entity.pPhoneCallStatus, phoneResolved, "Phone")
                val phoneJustDecided = entity.pPhoneCallStatus == 0 && phoneValidated != 0
                val phoneSource = resolveSourceOnce(phoneJustDecided, entity.pPhoneCallSource, sourceActivity)

                // ── 2. Overlay ────────────────────────────────────────────
                val canOverlay = Settings.canDrawOverlays(appContext)
                val overlayLive = when {
                    canOverlay -> 1
                    else -> 2  // System says user denied
                }


                val overlayResolved = resolvePermissionStatus(overlayLive, entity.pOverlayStatus)
                val overlayValidated = validateOverlayTransition(entity.pOverlayStatus, overlayResolved)
                val overlayJustDecided = entity.pOverlayStatus == 0 && overlayValidated != 0
                val overlaySource = resolveSourceOnce(overlayJustDecided, entity.pOverlaySource, overlayActivityName)

                // Clear overlay attempt flag after checking
                if (overlayAttemptPending) {
                    overlayAttemptPending = false
                    logW("✓ Overlay attempt flag cleared after permission check.")
                }

                // ── 3. Location ───────────────────────────────────────────
                val isFineGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val isCoarseGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val isLocationDenied = activity != null && (
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                val locationLive = when {
                    isFineGranted || isCoarseGranted -> 1
                    isLocationDenied -> 2
                    else -> 0
                }
                val locationResolved = resolvePermissionStatus(locationLive, entity.pLocationStatus)
                val locationValidated = validatePermissionTransition(entity.pLocationStatus, locationResolved, "Location")
                val locationJustDecided = entity.pLocationStatus == 0 && locationValidated != 0
                val locationSource = resolveSourceOnce(locationJustDecided, entity.pLocationSource, sourceActivity)

                // ── 4. Notification ───────────────────────────────────────
                val isNotifGranted = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
                val isNotifDenied  = if (android.os.Build.VERSION.SDK_INT >= 33 && activity != null) {
                    activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                } else false
                val notifLive = when {
                    isNotifGranted -> 1
                    isNotifDenied -> 2
                    else -> 0
                }
                val notifResolved = resolvePermissionStatus(notifLive, entity.pNotificationStatus)
                val notifValidated = validatePermissionTransition(entity.pNotificationStatus, notifResolved, "Notification")
                val notifJustDecided = entity.pNotificationStatus == 0 && notifValidated != 0
                val notifSource = resolveSourceOnce(notifJustDecided, entity.pNotificationSource, sourceActivity)

                // Update entity with validated states
                updated = updated.copy(
                    pPhoneCallStatus = phoneValidated,
                    pPhoneCallSource = phoneSource,
                    pOverlayStatus = overlayValidated,
                    pOverlaySource = overlaySource,
                    pLocationStatus = locationValidated,
                    pLocationSource = locationSource,
                    pNotificationStatus = notifValidated,
                    pNotificationSource = notifSource,
                    setAsDefaultStatus = 0  // Always 0, don't check
                )

                // ── 5. CDO Status (if CallerIDActivity) ─────────────────
                if (isCdoActivity) {
//                    val cdoValidated = validateCdoTransition(entity.cdoOpen, 1)
                    updated = updated.copy(cdoOpen = 1)
                }

                logW(
                    "📱 Phone=$phoneValidated | " +
                            "🖼️ Overlay=$overlayValidated | " +
                            "📍 Location=$locationValidated | " +
                            "🔔 Notif=$notifValidated | " +
                            "📞 CDO=${updated.cdoOpen}"
                )

                // Check if anything changed
                val permissionChanged = hasPermissionStatusChanged(entity, updated)
                val cdoChanged = hasCdoStatusChanged(entity, updated)
                val isAnySyncPending = dao.getIsSynced() == false

                val shouldSync = permissionChanged || cdoChanged || isAnySyncPending

                if (shouldSync) {
                    logW("🔥 Status change detected (perms=$permissionChanged, cdo=$cdoChanged, isSyncPending=$isAnySyncPending. Firing API...")
                    dao.insertOrUpdate(updated)
                    logW("📤 Updated isSynced=false in local database")
                    syncWithBackend(updated)
                } else {
                    logW("ℹ️ No permission or CDO status changes. Skipping API.")
                }
            }
        }
    }
}