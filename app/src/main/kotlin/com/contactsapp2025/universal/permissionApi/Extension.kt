package com.contactsapp2025.universal.permissionApi

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.text.format.DateUtils
import android.util.Log
import android.util.LruCache
import android.view.View
import android.view.WindowManager
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.callsdk.CallSDK.trustedTimeClient

import com.callsdk.callmoniter.domain.entity.CallDetails
import com.callsdk.permissions.helper.OEMPermissionHelper.isMi
import com.callsdk.permissions.helper.XiaomiUtilities
import com.callsdk.utils.ACTIVITY_RECREATED
//import com.callsdk.utils.DeviceInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Scanner
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.toString


/**
 * Checks whether the application has been granted READ_CONTACTS permission.
 *
 * This permission is required before accessing the device's contact list
 * through the Contacts Content Provider. Without this permission, reading
 * contacts will throw a SecurityException on Android Marshmallow and above.
 *
 * @return true if READ_CONTACTS permission is granted, otherwise false.
 */
fun Context.hasReadContactPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Checks whether the app has been granted ACCESS_FINE_LOCATION permission.
 *
 * Used for any features requiring precise location such as weather widgets,
 * location-based greeting messages, analytics or device information.
 *
 * @return true if ACCESS_FINE_LOCATION permission is granted.
 */
fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Checks whether the app has been granted READ_PHONE_STATE permission.
 *
 * This permission is necessary when accessing telephony info such as:
 * - SIM state
 * - Network type
 * - IMEI/MEID (Android <10)
 * - Dual SIM info
 *
 * On Android 13+, some telephony operations require additional permissions.
 *
 * @return true if permission is granted.
 */
fun Context.hasPhoneStatePermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Checks whether the app has permission to draw overlays.
 *
 * This is required for SYSTEM_ALERT_WINDOW features like chat heads
 * or floating UI elements.
 *
 * @return true if overlay permission is granted.
 */
fun Context.hasOverlayPermission(): Boolean {
    return Settings.canDrawOverlays(this)
}


/**
 * Determines whether the host application is running in debug mode.
 *
 * Useful for conditional logging, enabling debug UI blocks, or skipping
 * restrictions during internal development builds.
 *
 * @param context Application or Activity context.
 * @return true if the app is debuggable, false otherwise.
 */
fun isHostAppDebug(context: Context): Boolean {
    return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

/**
 * Calculates time difference between two timestamps (in milliseconds)
 * and returns a formatted string.
 *
 * Format:
 * - If hours > 0 → HH:mm:ss
 * - If hours = 0 → mm:ss
 *
 * This method is useful for call duration, timers, reminders, etc.
 *
 * @param startTime Start timestamp in millis.
 * @param endTime End timestamp in millis.
 * @return Formatted time difference or "00:00" if input is null.
 */
fun getTimeDiff(startTime: Long?, endTime: Long?): String {
    if (startTime == null || endTime == null) return "00:00"
    val diff = endTime - startTime
    val diffSeconds = diff / 1000 % 60
    val diffMinutes = diff / (60 * 1000) % 60
    val diffHours = diff / (60 * 60 * 1000) % 24
    var strTime = ""
    strTime = if (diffHours > 0) {
        addExtraZero(diffHours) + ":" + addExtraZero(diffMinutes) + ":" + addExtraZero(
            diffSeconds
        )
    } else {
        addExtraZero(diffMinutes) + ":" + addExtraZero(diffSeconds)
    }
    return strTime
}

/**
 * Pads a single-digit number with a leading zero.
 *
 * Example:
 * - 5 → "05"
 * - 12 → "12"
 *
 * @param time Numeric value to format.
 * @return String with leading zero if needed.
 */
private fun addExtraZero(time: Long): String {
    return if (time >= 10) {
        time.toString()
    } else {
        "0$time"
    }
}

/**
 * Collects a Flow of CallDetails while ensuring the callback
 * always runs on the Main thread.
 *
 * This is especially important for UI updates inside Fragments,
 * Activities or Composables.
 *
 * @param onUpdate Callback invoked when new call details arrive.
 */
suspend fun Flow<CallDetails?>.collectCallDetails(
    onUpdate: (name: String, number: String, details: CallDetails?) -> Unit
) {
    collectLatest { details ->
        val name = details?.contact?.name.orEmpty()
        val number = details?.contact?.number.orEmpty()

        withContext(Dispatchers.Main.immediate) {
            onUpdate(name, number, details)
        }
    }
}

/**
 * Utility property that checks whether the device is running Android 13 or above.
 */
val isAtLeastTiramisu: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Utility property that checks whether the device is running
 * at least Android 4.0.3 Ice Cream Sandwich MR1.
 *
 * Useful for legacy compatibility checks.
 */
val isVersion15: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1

/**
 * Loads the application icon for the given package name
 * into the ImageView.
 *
 * This method uses PackageManager to fetch the app icon drawable.
 * If the package does not exist, it silently fails.
 *
 * @receiver ImageView where the icon will be displayed.
 * @param packageName Package name of the target application.
 */
fun ImageView.getImageFromPackageName(packageName: String) {
    try {
        val icon = context.packageManager.getApplicationIcon(packageName)
        this.setImageDrawable(icon)
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
}

/**
 * Retrieves a human-readable app name from a package name.
 *
 * The method:
 * - Handles empty/invalid package names
 * - Handles security exceptions
 * - Provides fallback text ("this app")
 *
 * @param packageName The package identifier of the application.
 * @return App name or "this app" if lookup fails.
 */
fun Context.getAppNameFromPackageName(packageName: String): String {
    return try {
        if (packageName.isBlank()) {
            return "this app"
        }

        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val appName = packageManager.getApplicationLabel(applicationInfo).toString()

        if (appName.isBlank()) "this app" else appName
    } catch (e: PackageManager.NameNotFoundException) {
        "this app"
    } catch (e: SecurityException) {
        "this app"
    } catch (e: Exception) {
        "this app"
    }
}


/**
 * Formats a timestamp into "hh:mm a" format.
 *
 * Example:
 * - 08:15 PM
 *
 * @return Formatted time string.
 */
fun Long.toTimeFormat(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

/**
 * Retrieves a bitmap from the LruCache by key.
 *
 * @param bitmapCache Cache instance.
 * @param key Identifier for stored bitmap.
 * @return Cached bitmap or null.
 */
fun getBitmapFromCache(bitmapCache: LruCache<String, Bitmap>, key: String): Bitmap? {
    return bitmapCache.get(key)
}

/**
 * Adds a bitmap to cache if the key does not exist.
 *
 * @param bitmapCache LruCache instance.
 * @param key Key for bitmap storage.
 * @param bitmap Bitmap to store.
 */
fun addBitmapToCache(bitmapCache: LruCache<String, Bitmap>, key: String, bitmap: Bitmap) {
    if (getBitmapFromCache(bitmapCache, key) == null) {
        bitmapCache.put(key, bitmap)
    }
}

/**
 * Extracts the first letter of a contact's name.
 *
 * @param contactName Full name of contact.
 * @return Uppercase initial or empty string.
 */
fun getInitials(contactName: String): String {
    return if (contactName.isNotEmpty()) {
        contactName[0].uppercase(Locale.getDefault())
    } else {
        ""
    }
}

/**
 * Converts any Drawable into a Bitmap.
 *
 * Useful when needing to draw a vector or PNG on Canvas.
 *
 * Handles:
 * - BitmapDrawable directly
 * - Vector drawables (via draw())
 * - Unknown-sized drawables (fallback 1×1)
 *
 * @param drawable Drawable to convert.
 * @return Bitmap.
 */
fun drawableToBitmap(drawable: Drawable): Bitmap {
    var bitmap: Bitmap? = null

    if (drawable is BitmapDrawable) {
        val bitmapDrawable = drawable
        if (bitmapDrawable.bitmap != null) {
            return bitmapDrawable.bitmap
        }
    }

    bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        createBitmap(1, 1)
    } else {
        createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
    }

    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

/**
 * Calculates LruCache size based on available RAM.
 *
 * Cache size = 1/8 of total available memory.
 *
 * @return Cache size in KB.
 */
fun calculateCacheSize(): Int {
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    return maxMemory / 8
}


/**
 * Makes the view visible.
 *
 * @receiver Target View.
 */
fun View.beVisible() {
    visibility = View.VISIBLE
}

/**
 * Makes the view gone (invisible and non-occupying layout space).
 *
 * @receiver Target View.
 */
fun View.beGone() {
    visibility = View.GONE
}

/**
 * Makes the view invisible but still occupies layout space.
 *
 * @receiver Target View.
 */
fun View.beInvisible() {
    visibility = View.INVISIBLE
}

/**
 * Sets visibility based on boolean.
 *
 * @receiver View to modify.
 * @param beVisible true → visible, false → gone.
 */
fun View.beVisibleIf(beVisible: Boolean) =
    if (beVisible) beVisible() else beGone()

/**
 * Performs a shake animation on the view with haptic feedback and vibration.
 *
 * @receiver Target View.
 */
fun View.shakeView() {
    // Add vibration feedback
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(150)
    }

    val shake = TranslateAnimation(0f, 10f, 0f, 0f)
    shake.duration = 600
    shake.interpolator = CycleInterpolator(3f)
    this.startAnimation(shake)
}

/**
 * Checks if the device is currently unlocked and interactive.
 *
 * Logic:
 * - Uses PowerManager to check if screen is interactive
 * - Uses KeyguardManager to confirm device is unlocked
 * - Works differently pre/post Android M
 *
 * @return true if device is unlocked and screen is ON.
 */
fun Context.isDeviceUnlocked(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        powerManager.isInteractive &&
            !keyguardManager.isKeyguardLocked &&
            !keyguardManager.isDeviceLocked
    } else {
        powerManager.isInteractive &&
            !keyguardManager.isKeyguardLocked
    }
}



/**
 * Shows a short toast.
 *
 * @receiver Context used to display the toast.
 * @param message Text to show.
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * Hides the soft keyboard for the associated view.
 *
 * @receiver View that currently holds input focus.
 */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

/**
 * Hides the soft keyboard for the associated activity.
 *
 * @receiver Activity that currently holds input focus.
 */
fun Activity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    currentFocus?.let { view ->
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}


/**
 * Hides Android navigation bar with immersive mode.
 *
 * Useful for overlay activities or full-screen caller UI.
 *
 * @receiver Activity.
 */
fun Activity.hideNavigationBar() {
    try {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Shows the navigation bar if previously hidden.
 *
 * @receiver Activity.
 */
fun Activity.showNavigationBar() {
    try {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Retrieves the unique Android device ID (ANDROID_ID).
 *
 * Notes:
 * - ANDROID_ID is stable per app-signature per device.
 * - May change after factory reset.
 * - On some OEM devices this value may be unreliable.
 *
 * @receiver Context to access system content resolver.
 * @return The ANDROID_ID string.
 */
fun Context.findDeviceId(): String {
    return Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
}

/**
 * Checks overlay permission and returns:
 * - 1 if permission granted
 * - 0 if permission denied
 * - -1 on pre-Android M where overlay permission is not required
 *
 * Used by SDK before showing Caller ID overlay windows.
 *
 * @receiver Context.
 */
fun Context.overlayPermissionGranted(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (Settings.canDrawOverlays(this)) 1 else 0
    } else {
        -1
    }
}

/**
 * Returns app visibility state based on process importance.
 *
 * Values:
 * - "foreground" → app is visible and interactive
 * - "background" → app is in background or recents
 * - "" → unknown or crashed
 *
 * @receiver Context.
 */
fun Context.getAppStatus(): String? {
    return try {
        if (isAppInForeground()) {
            "foreground"
        } else if (isAppInRecentOrBackground()) {
            "background"
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

/**
 * Checks if app is currently in the foreground.
 *
 * Criteria:
 * - Uses ActivityManager
 * - Checks runningAppProcesses importance
 *
 * @receiver Context.
 * @return true if app has IMPORTANCE_FOREGROUND.
 */
fun Context.isAppInForeground(): Boolean {
    return try {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = applicationContext.packageName

        appProcesses.firstOrNull { it.processName == packageName }?.importance ==
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (e: Exception) {
        false
    }
}

/**
 * Checks if the app is running in background or in recents.
 *
 * Does NOT verify if app was fully killed.
 *
 * @receiver Context.
 * @return true if app is not in foreground but process exists.
 */
fun Context.isAppInRecentOrBackground(): Boolean {
    return try {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = applicationContext.packageName
        val appProcess = appProcesses.firstOrNull { it.processName == packageName }

        appProcess != null &&
            appProcess.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (e: Exception) {
        false
    }
}

/**
 * Alternative method to determine app state using ActivityManager.getAppTasks().
 *
 * Works on API 21+.
 * Detects:
 * - Foreground (via isAppInForeground())
 * - Background (via presence in recentTasks)
 * - Killed (no tasks)
 *
 * @receiver Context.
 */
fun Context.getAppStatusAlternative(): String? {
    return try {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        if (isAppInForeground()) return "foreground"

        val recentTasks = activityManager.appTasks ?: return null
        val packageName = applicationContext.packageName

        return if (recentTasks.any {
                it.taskInfo?.baseIntent?.component?.packageName == packageName
            }) {
            "background"
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Checks whether the OS has imposed background operation restrictions.
 *
 * On Android P+, OEM may restrict:
 * - background services
 * - background location
 * - implicit broadcasts
 *
 * @receiver Context.
 * @return true if background restricted.
 */
fun Context.isBackgroundRestricted(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isBackgroundRestricted
    }
    return false
}

/**
 * Returns true if device is currently in dark mode.
 *
 * Uses uiMode Night Mask for detection.
 *
 * @param context Context.
 */
fun isDarkModeEnabled(context: Context): Boolean {
    val currentNightMode =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == Configuration.UI_MODE_NIGHT_YES
}

/**
 * Checks if overlay permission is granted using various strategies.
 *
 * For Pre-Oreo MR1:
 * - Attempts to add an invisible overlay window
 * - If adding succeeds → overlay allowed
 *
 * For Android O MR1+:
 * - Uses Settings.canDrawOverlays()
 *
 * This is a robust method for OEM-specific restrictions.
 *
 * @receiver Context.
 * @return true if overlay allowed.
 */
fun Context.isOverlayPermissionGranted(): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        true
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        Settings.canDrawOverlays(this)
    } else {
        if (Settings.canDrawOverlays(this)) return true

        try {
            val mgr = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager ?: return false

            val viewToAdd = View(this)
            val params = WindowManager.LayoutParams(
                0,
                0,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT
            )

            viewToAdd.layoutParams = params
            mgr.addView(viewToAdd, params)
            mgr.removeView(viewToAdd)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * Returns MIUI-specific permission states if the device is Xiaomi.
 *
 * Permissions checked:
 * - Auto-start
 * - Background start activity
 * - Show on lockscreen
 *
 * @receiver Context.
 */
fun Context.getMiUIPermission(): String {
    return if (XiaomiUtilities.isMIUI) {
        val autoStart =
            XiaomiUtilities.isCustomPermissionGranted(this, XiaomiUtilities.OP_AUTO_START)
        val bgStart = XiaomiUtilities.isCustomPermissionGranted(
            this,
            XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY
        )
        val showWhenLocked =
            XiaomiUtilities.isCustomPermissionGranted(this, XiaomiUtilities.OP_SHOW_WHEN_LOCKED)

        " AutoStart:$autoStart | BackgroundStart:$bgStart | ShowOnLockScreen:$showWhenLocked "
    } else {
        ""
    }
}

/**
 * Determines the current network connection type.
 *
 * Return values:
 * - "WiFi"        → Connected via Wi-Fi
 * - "4G", "3G", "5G", etc → Mobile data (based on radio type or bandwidth estimation)
 * - "Ethernet"    → Wired connection
 * - "VPN"         → Under VPN transport
 * - "Unknown"     → Transport detected but classified as unknown
 * - "No Internet" → No active network or no capabilities
 *
 * Behavior:
 * - For cellular, SDK delegates to getCellularNetworkType() which internally
 *   decides whether to use TelephonyManager (requires READ_PHONE_STATE)
 *   or fallback to bandwidth-based estimation.
 *
 * @receiver Context
 * @return A human-readable network type string.
 */
@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
fun Context.getNetworkType(): String {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return "No Internet"
    val caps = cm.getNetworkCapabilities(network) ?: return "No Internet"

    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            getCellularNetworkType(caps)

        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Unknown"
    }
}

/**
 * Internal helper that determines mobile network type.
 *
 * Rules:
 * - If READ_PHONE_STATE permission is granted → use TelephonyManager radio info
 * - If permission is missing → fallback to bandwidth-based estimation
 *
 * Used internally by getNetworkType().
 *
 * @receiver Context
 * @param caps NetworkCapabilities for the active network
 */
@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
private fun Context.getCellularNetworkType(caps: NetworkCapabilities): String {
    return if (hasPhoneStatePermission()) {
        getDetailedMobileNetworkType()
    } else {
        estimateNetworkTypeFromBandwidth(caps)
    }
}

/**
 * Estimates network generation (2G, 3G, 4G, 5G) based on downstream bandwidth.
 *
 * Bandwidth thresholds (approx):
 * - <150 kbps   → 2G
 * - <1 Mbps     → 3G
 * - <15 Mbps    → 4G
 * - <50 Mbps    → 4G+
 * - ≥50 Mbps    → 5G
 *
 * Note:
 * - This is only a fallback when READ_PHONE_STATE is not granted
 * - Not as accurate as TelephonyManager API
 *
 * @receiver Context
 * @param caps NetworkCapabilities of active network
 * @return estimated generation as string
 */
private fun Context.estimateNetworkTypeFromBandwidth(caps: NetworkCapabilities): String {
    val down = caps.linkDownstreamBandwidthKbps

    return when {
        down <= 0 -> "Mobile" // insufficient data
        down < 150 -> "2G"
        down < 1000 -> "3G"
        down < 15000 -> "4G"
        down < 50000 -> "4G+"
        else -> "5G"
    }
}

/**
 * Returns the exact mobile network type using TelephonyManager.
 *
 * Requirements:
 * - READ_PHONE_STATE permission must be granted by the host app.
 *
 * Supported detections:
 * - 5G  → NETWORK_TYPE_NR
 * - 4G  → NETWORK_TYPE_LTE
 * - 3G  → UMTS, HSPA, EVDO, HSDPA, etc.
 * - 2G  → EDGE, GPRS, CDMA, 1xRTT
 *
 * Returns:
 * - "Mobile"      → for unknown or unclassified cases
 * - "Permission denied" → if permission missing (though this should not occur because caller guards it)
 *
 * Telephony API Notes:
 * - On Android N+, uses TelephonyManager.dataNetworkType (more accurate)
 * - On older versions, falls back to networkType (deprecated)
 *
 * @receiver Context
 * @return human-readable mobile network type string
 */
@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
fun Context.getDetailedMobileNetworkType(): String {
    val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    return try {
        val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            telephonyManager.dataNetworkType
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.networkType
        }

        when (networkType) {
            // 5G
            TelephonyManager.NETWORK_TYPE_NR -> "5G"

            // 4G
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"

            // 3G family
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"

            // 2G family
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"

            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"

            else -> "Mobile"
        }
    } catch (e: SecurityException) {
        "Permission denied"
    } catch (e: Exception) {
        "Unknown"
    }
}

/**
 * Collects hardware and software capability information for the device and returns it as JSON.
 *
 * Includes:
 * - Memory info (RAM classification)
 * - Screen density
 * - Screen width / height in pixels
 * - Whether overlays are supported
 * - Whether the device can make phone calls
 *
 * The returned JSON is typically used by the SDK server for diagnostics,
 * device classification, or capability-based feature control.
 *
 * @receiver Context
 * @return JSON string containing device hardware details
 */
//fun Context.getDeviceInfo(): String {
//    val memoryInfo = MemoryIdentifier.getMemoryInfo(this)
//    val screenDensity = MemoryIdentifier.getScreenDensity(this)
//    val screenWidth = MemoryIdentifier.getScreenWidth(this)
//    val screenHeight = MemoryIdentifier.getScreenHeight(this)
//    val supportOverlay = MemoryIdentifier.supportsOverlay(this)
//    val hasPhoneCapability = MemoryIdentifier.hasPhoneCapabilities(this)

//    logE("memoryInfo : $memoryInfo")
//    logE("screenDensity : $screenDensity")
//    logE("screenWidth : $screenWidth")
//    logE("screenHeight : $screenHeight")
//    logE("supportOverlay : $supportOverlay")
//    logE("hasPhoneCapability : $hasPhoneCapability")

//    val deviceInfo = DeviceInfo(
//        memoryInfo = memoryInfo,
//        screenDensity = screenDensity,
//        screenWidth = screenWidth,
//        screenHeight = screenHeight,
//        supportOverlay = supportOverlay,
//        hasPhoneCapability = hasPhoneCapability
//    )

//  /
//}

/**
 * Returns the device manufacturer brand name, such as:
 * - "Samsung"
 * - "Xiaomi"
 * - "Realme"
 * - "Google"
 *
 * Useful for brand-specific permission handling (MIUI, Oppo, Vivo, etc).
 */
fun Context.getBrand(): String {
    return Build.BRAND
}

/**
 * Retrieves the device model name, such as:
 * - "Pixel 7"
 * - "SM-S918B"
 *
 * Useful for debugging and analytics.
 */
fun Context.getModel(): String {
    return Build.MODEL
}

/**
 * Returns the OS version string (e.g., "14", "13", "12").
 *
 * Note: This is not always the API level. It is the release version.
 */
fun Context.getOsVersion(): String {
    return Build.VERSION.RELEASE
}

/**
 * Returns the application versionName from package manager.
 *
 * Safe against NameNotFoundException and returns "Unknown" when missing.
 *
 * @receiver Context
 * @return App versionName as defined in the manifest.
 */
fun Context.getAppVersion(): String {
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }.toString()
}

/**
 * Returns whether the device is currently locked or unlocked.
 *
 * "Locked"   → Keyguard is engaged
 * "Unlocked" → User has authenticated or screen is active without lock
 *
 * @receiver Context
 * @return "Locked" or "Unlocked"
 */
fun Context.getDeviceLockStatus(): String {
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    val isDeviceLocked = keyguardManager.isKeyguardLocked
    return if (isDeviceLocked) "Locked" else "Unlocked"
}

/**
 * Returns a combined permission state:
 *
 * Values:
 * - 0 → Both overlay and phone state permissions granted
 * - 1 → Only PHONE permission granted
 * - 2 → Only overlay permission granted
 * - -1 → No required permissions granted
 *
 * Used by the SDK to determine which permission screen to show next.
 */
fun Context.getPermission(): Int {
    val phonePermission = hasPhoneStatePermission()
    val overlayPermission = isOverlayPermissionGranted()

    return if (overlayPermission && phonePermission) {
        0
    } else if (phonePermission) {
        1
    } else if (overlayPermission) {
        2
    } else {
        -1
    }
}

/**
 * Returns "On" if internet is available, otherwise "Off".
 *
 * This internally checks both:
 * - isInternetAvailable1() → validated capability check
 * - isInternetAvailable2() → fallbacks for older Android
 *
 * @receiver Context
 */
fun Context.getInternet(): String {
    return if (applicationContext.isNetworkAvailable()) "On" else "Off"
}

fun Context.isDeviceLocked(): Boolean {
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    return keyguardManager.isKeyguardLocked
}

/**
 * Determines whether the device has any active internet connection.
 *
 * Combines two methods:
 * - Capability-based (modern)
 * - Transport-based (fallback)
 *
 * Logs merged result for debugging.
 *
 * @receiver Context
 * @return true if internet appears available
 */
fun Context.isNetworkAvailable(): Boolean {
    logW("isInternetAvailable both : ${isInternetAvailable1() || isInternetAvailable2()}")
    return isInternetAvailable1() || isInternetAvailable2()
}

/**
 * Modern internet availability check using:
 * - NET_CAPABILITY_INTERNET
 * - NET_CAPABILITY_VALIDATED
 *
 * This ensures the connection is *actually* usable and validated by OS.
 *
 * @receiver Context
 */
fun Context.isInternetAvailable1(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Fallback internet availability check based on transport types.
 *
 * Works on:
 * - Wi-Fi
 * - Cellular
 * - Ethernet
 * - VPN
 *
 * Provides compatibility with pre-Marshmallow devices.
 *
 * @receiver Context
 */
fun Context.isInternetAvailable2(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        networkInfo != null && networkInfo.isConnected
    }
}

/**
 * Retrieves the current UTC time in milliseconds using the CallSDK TrustedTimeClient.
 *
 * This function attempts to fetch a network-verified Unix timestamp from
 * `trustedTimeClient` (a secure, server-backed time provider used by CallSDK).
 *
 * WHY THIS IS IMPORTANT:
 * - Device clock may be wrong (manually changed by user)
 * - Fraud-prevention & logging features require trusted time
 * - API sync & daily limits rely on accurate timestamps
 * - Scheduled jobs and reminders need consistent UTC time
 *
 * FALLBACK:
 * If `trustedTimeClient` is unavailable or returns null,
 * the function gracefully falls back to:
 *      System.currentTimeMillis()
 *
 * @return UTC timestamp in milliseconds, either trusted or system fallback.
 */
fun getTrustedUtcMillis(): Long {
    val millis = trustedTimeClient?.computeCurrentUnixEpochMillis()
    return millis ?: System.currentTimeMillis()
}


/**
 * Returns current UTC day in the format yyyy-MM-dd.
 *
 * Uses TrustedTime API if available for higher accuracy.
 */
fun getUtcDay(): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(getTrustedUtcMillis()))
}

/**
 * Returns current UTC time in the format yyyy-MM-dd HH:mm:ss.
 *
 * Used for analytics, event timestamping, and logs.
 */
fun getUtcDateTime(): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(getTrustedUtcMillis()))
}


/**
 * Returns current UTC time in the format yyyy-MM-dd HH:mm:ss.
 */

fun Long.toUtcDateTime(): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(this))
}

/**
 * Returns current UTC time in the format yyyy-MM-dd HH:mm:ss.
 */

fun Long.toConvertUtcDate(): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(this))
}

/**
 * Get Package info for install time and update time
 */
@Suppress("DEPRECATION")
private fun Context.pkgInfo(): PackageInfo {
    return if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
}

/**
 * Returns the original installation timestamp of the app in milliseconds.
 *
 * This comes from PackageInfo.firstInstallTime and represents
 * when the app was first installed on the device.
 */
fun Context.installTime(): Long {
    return pkgInfo().firstInstallTime
}

/**
 * Returns the last update timestamp of the app in milliseconds.
 *
 * This comes from PackageInfo.lastUpdateTime and updates each time
 * the APK is replaced (for example through Play Store updates).
 */
fun Context.updateTime(): Long {
    return pkgInfo().lastUpdateTime
}

/**
 * Converts the installation timestamp into a readable UTC date-time
 * string using the format yyyy-MM-dd HH:mm:ss.
 *
 * Example output: "2025-12-22 05:21:19"
 */
fun Context.getInstallTime(): String {
    return installTime().toUtcDateTime()
}

/**
 * Converts the last update timestamp into a readable UTC date-time
 * string using the format yyyy-MM-dd HH:mm:ss.
 *
 * Example output: "2025-12-22 05:21:19"
 */
fun Context.getUpdateTime(): String {
    return updateTime().toUtcDateTime()
}

/**
 * Determines the user's type based on install and update times.
 *
 * Logic:
 * - If firstInstallTime == lastUpdateTime → the app has never been updated.
 *   This means the user is completely new.
 *
 * - If firstInstallTime != lastUpdateTime → the app has been updated at least once.
 *   This means the user has used the app long enough to update it.
 *
 * Returns:
 *   "0" = New user (fresh install, no updates yet)
 *   "1" = Returning/Existing user (has updated the app before)
 */
fun Context.userType(): String {
    val install = installTime()
    val update = updateTime()

    return if (install == update) {
        "0"   // New user
    } else {
        "1"   // Returning/Existing user
    }
}

fun String.prettyJson(): String {
    return try {
        val parsed = JsonParser.parseString(this)
        GsonBuilder().setPrettyPrinting().create().toJson(parsed)
    } catch (e: Exception) {
        this
    }
}

fun JSONObject.prettyJson(): String = toString(2)

fun JSONArray.prettyJson(): String = toString(2)

/**
 * Extension function to get country name from multiple sources
 * Returns country name, never null - tries multiple methods if one fails
 */
fun Context.getCountryName(): String {
    return try {
        getCountryFromTelephonyManager()
            ?: getCountryFromSimState()
            ?: getCountryFromLocale()
            ?: getCountryFromGeoIP()
            ?: "Unknown"
    } catch (e: Exception) {
        logE("Error getting country: ${e.message}")
        "Unknown"
    }
}


/**
 * Method 1: Get country from TelephonyManager (SIM card info)
 * Most reliable method - checks both network and SIM country
 */
private fun Context.getCountryFromTelephonyManager(): String? {
    return try {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val countryCode = tm?.networkCountryIso?.uppercase()
            ?: tm?.simCountryIso?.uppercase()

        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            val locale = Locale("", countryCode)
            val countryName = locale.getDisplayCountry(Locale.ENGLISH)
            logE("Country from TelephonyManager: $countryName ($countryCode)")
            return countryName
        }
        null
    } catch (e: Exception) {
        logE("TelephonyManager method failed: ${e.message}")
        null
    }
}


/**
 * Method 2: Get country from SIM state
 * Alternative TelephonyManager approach
 */
private fun Context.getCountryFromSimState(): String? {
    return try {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simState = tm?.simState

        // If SIM is present
        if (simState == TelephonyManager.SIM_STATE_READY) {
            val countryCode = tm?.simCountryIso?.uppercase() ?: return null
            if (countryCode.length == 2) {
                val locale = Locale("", countryCode)
                val countryName = locale.getDisplayCountry(Locale.ENGLISH)
                logE("Country from SIM State: $countryName ($countryCode)")
                return countryName
            }
        }
        null
    } catch (e: Exception) {
        logE("SIM State method failed: ${e.message}")
        null
    }
}


/**
 * Method 3: Get country from Device Locale
 * Fallback to system locale
 */
private fun Context.getCountryFromLocale(): String? {
    return try {
        val locale = Locale.getDefault()
        val countryCode = locale.country

        if (countryCode.isNotEmpty() && countryCode.length == 2) {
            val countryLocale = Locale("", countryCode)
            val countryName = countryLocale.getDisplayCountry(Locale.ENGLISH)
            logE("Country from Locale: $countryName ($countryCode)")
            return countryName
        }
        null
    } catch (e: Exception) {
        logE("Locale method failed: ${e.message}")
        null
    }
}


/**
 * Method 4: Get country from Geocoder (requires location permission)
 * Uses device location if available
 */
private fun Context.getCountryFromGeoIP(): String? {
    return try {
        val geocoder = Geocoder(this, Locale.ENGLISH)

        // Try to get last known location
        val locationManager =
            getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val lastLocation = try {
            locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: SecurityException) {
            null
        }

        if (lastLocation != null) {
            @Suppress("MissingPermission")
            val addresses =
                geocoder.getFromLocation(lastLocation.latitude, lastLocation.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val countryName = addresses[0].countryName
                logE("Country from Geocoder: $countryName")
                return countryName
            }
        }
        null
    } catch (e: Exception) {
        logE("Geocoder method failed: ${e.message}")
        null
    }
}


/**
 * Get country code (2-letter ISO code)
 * Also tries multiple methods
 */
fun Context.getCountryCode(): String {
    return try {
        getCountryCodeFromTelephonyManager()
            ?: getCountryCodeFromLocale()
            ?: "UN"
    } catch (e: Exception) {
        logE("Error getting country code: ${e.message}")
        "UN"
    }
}


private fun Context.getCountryCodeFromTelephonyManager(): String? {
    return try {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val code = tm?.networkCountryIso?.uppercase()
            ?: tm?.simCountryIso?.uppercase()

        if (!code.isNullOrEmpty() && code.length == 2) {
            logE("Country code from TelephonyManager: $code")
            return code
        }
        null
    } catch (e: Exception) {
        null
    }
}


private fun Context.getCountryCodeFromLocale(): String? {
    return try {
        val code = Locale.getDefault().country
        if (code.isNotEmpty() && code.length == 2) {
            logE("Country code from Locale: $code")
            return code
        }
        null
    } catch (e: Exception) {
        null
    }
}


/**
 * Checks if device supports displaying overlay windows.
 *
 * Some custom ROMs or restricted devices may throw exceptions;
 * this function handles them gracefully.
 *
 * @receiver Context
 * @return true if overlay window manager is available
 */
fun Context.isOverlaySupported(): Boolean {
    return try {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Returns true if Truecaller is set as the default dialer.
 *
 * Used by CallSDK to detect potential interference from Truecaller
 * when managing incoming/outgoing call screens.
 *
 * @receiver Context
 */
fun isTrueCallerDefaultDialer(context: Context): Boolean {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    logE("${telecomManager.defaultDialerPackage}")
    return telecomManager.defaultDialerPackage == "com.truecaller"
}

/**
 * Determines if Truecaller is currently running in the foreground.
 *
 * Relevant for caller ID conflicts and overlay behaviour.
 *
 * @receiver Context
 */
fun isTrueCallerInForeground(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val processes = am.runningAppProcesses ?: return false

    return processes.any {
        it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
            it.processName == "com.truecaller"
    }
}

/**
 * Applies system bar padding to any view automatically using WindowInsets.
 *
 * This ensures UI aligns correctly behind status/navigation bars when
 * using edge-to-edge mode.
 *
 * @receiver View
 */
fun View.applySystemBarsPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }
    /*ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(sys.left, sys.top, sys.right, 0)   // <-- bottom padding removed
        insets
    }*/
}


/**
 * Finds and returns a theme color from resources.
 *
 * @receiver Context
 * @param colorResId resource ID of the color
 */
fun Context.getThemeColor(colorResId: Int): Int {
    return getColor(colorResId)
}

/**
 * Logs a debug message only when CallSDK debug mode is enabled.
 *
 * Controlled by: cdoPrefs.isDebugEnabled
 * Used for verbose internal debugging without polluting release builds.
 *
 * @param message The text to log.
 */
//fun logDebug(message: String) {
//
//    Log.d(TAG, message)
//
//}

/**
 * Logs an error-level message when debug mode is enabled.
 *
 * Useful for capturing warnings or recoverable failures that need visibility
 * during SDK integration testing.
 */
fun logE(message: String) {

    Log.e("OOO", message)

}

/**
 * Logs a warning when debug mode is enabled.
 *
 * Typically used for unexpected states that are not critical.
 */
fun logW(message: String) {

    Log.w("OOO", message)

}

/**
 * Logs an info-level message when debug mode is enabled.
 *
 * Suitable for general lifecycle tracking and informational traces.
 */
fun logI(message: String) {

    Log.i("OOO", message)

}


/**
 * Checks if POST_NOTIFICATIONS permission is granted (Android 13+).
 *
 * @return true if permission is granted, false otherwise.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Context.haNotificationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Wraps Retrofit API calls with unified error handling.
 *
 * Behavior:
 * - On success → Result.success(data)
 * - On error → Result.failure(Exception)
 * - Automatically handles null body
 *
 * Intended for ViewModels/Repositories to simplify API error flow.
 *
 * @param block A suspend Retrofit request returning Response<T>
 */
suspend fun <T> safeApiCall(block: suspend () -> Response<T>): Result<T> {
    return try {
        val response = block()
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("API Error ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Observes SYSTEM_ALERT_WINDOW (overlay) permission changes on MIUI and other ROMs.
 *
 * When user toggles the overlay permission in Settings:
 *  - The Activity is automatically restarted
 *  - The callback is invoked when permission becomes granted
 *
 * Used primarily when SDK requires overlay for Caller ID windows.
 *
 * @receiver Activity
 * @param callBack Executes when overlay permission becomes allowed
 */
fun Activity.observerOverLayPermissionSystemScreen(callBack: () -> Unit) {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
        Process.myUid(),
        packageName
    )

    if (mode == AppOpsManager.MODE_ALLOWED) {
        return
    }

    appOps.startWatchingMode(
        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
        applicationContext.packageName,
        object : AppOpsManager.OnOpChangedListener {
            override fun onOpChanged(op: String, packageName: String) {
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    getPackageName()
                )

                if (mode != AppOpsManager.MODE_ALLOWED) {
                    return
                }

                // Stop watching
                appOps.stopWatchingMode(this)

                val intent = intent
                overridePendingTransition(0, 0)

                intent.flags =
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

                // Special MIUI handling
                if (isMi()) {
                    // Xiaomi-specific behaviour disabled here (empty block)
                } else {
                    finish()
                }

                overridePendingTransition(0, 0)
                intent.putExtra(ACTIVITY_RECREATED, true)
                startActivity(intent)
            }
        })
}

/**
 * Determines whether the device is considered "limited" for advanced SDK features.
 *
 * A limited device typically cannot support heavy features like:
 *  - Voice processing
 *  - Caller ID overlays
 *  - Live transcription
 *  - Advanced analytics
 *
 * Criteria for being considered a limited device:
 *  1. **Android Go Edition** → low RAM, restricted background execution
 *  2. **No microphone** → cannot record audio or use voice features
 *  3. **2GB or less RAM** → not powerful enough for memory-heavy processes
 *
 * This function logs diagnostic results and evaluates all three conditions.
 *
 * @receiver Context
 * @return true if device should be treated as limited, false otherwise.
 */
fun Context.isLimitedDevice(): Boolean {
    logW("isAndroidGo : ${isAndroidGo()}")
    logW("hasMicrophone : ${hasMicrophone()}")
    logW("hasLowRam : ${hasLowRam()}")

    return isAndroidGo() || !hasMicrophone() || hasLowRam()
}

/**
 * Checks whether the device has a physical microphone.
 *
 * Required for features such as:
 *  - Call recording
 *  - Voice commands
 *  - STT/TTS activation via microphone
 *
 * @receiver Context
 * @return true if microphone hardware is present.
 */
fun Context.hasMicrophone(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
}

/**
 * Determines if the device has low total RAM (≤ 2GB).
 *
 * Many modern SDK features require more memory:
 *  - Bitmap caching
 *  - Call overlays
 *  - AI/ML models
 *
 * Devices with ≤ 2GB RAM are likely to struggle with performance.
 *
 * @receiver Context
 * @return true if device RAM is 2GB or below.
 */
fun Context.hasLowRam(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    val totalMemoryGB = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    return totalMemoryGB <= 2.0
}

/**
 * Detects whether the device is running Android Go Edition.
 *
 * Android Go devices are optimized for low-end hardware and impose strict limits on:
 *  - Background activity
 *  - Overlay windows
 *  - Memory usage
 *
 * Detection method includes:
 *  1. System property checks (`ro.low_ram`, etc.)
 *  2. Device flagged as low-RAM by ActivityManager
 *  3. Explicit build fingerprints/model containing "go"
 *
 * @receiver Context
 * @return true if device is Android Go or behaves like a Go device.
 */
fun Context.isAndroidGo(): Boolean {
    return when {
        isSystemPropertyGoEdition() -> true
        isLowRamDevice() -> true
        else -> isExplicitAndroidGo()
    }
}

/**
 * Checks system properties commonly set on Android Go Edition devices.
 *
 * Reads system properties (via `getprop`) such as:
 *  - ro.opa.eligible_device
 *  - ro.low_ram
 *
 * Manufacturers may expose these differently, so detection is heuristic.
 *
 * @return true if any Go-specific property is enabled.
 */
private fun isSystemPropertyGoEdition(): Boolean {
    return try {
        val goProps = listOf(
            "ro.opa.eligible_device",
            "ro.low_ram"
        )

        goProps.any { prop ->
            runCatching {
                val process = Runtime.getRuntime().exec("getprop $prop")
                val reader = Scanner(process.inputStream).useDelimiter("\\A")
                val value = if (reader.hasNext()) reader.next().trim() else ""
                value.isNotEmpty() && value != "false"
            }.getOrDefault(false)
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * Uses ActivityManager to determine if the device is marked internally as low RAM.
 * Android Go devices always return true for this flag.
 *
 * @receiver Context
 * @return true if the OS marks the device as low RAM.
 */
private fun Context.isLowRamDevice(): Boolean {
    return try {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.isLowRamDevice
    } catch (e: Exception) {
        false
    }
}

/**
 * Detects Android Go Edition by checking public Build fields.
 *
 * Some manufacturers include "go" in:
 *  - DEVICE
 *  - MODEL
 *  - PRODUCT
 *  - FINGERPRINT
 *
 * This method acts as a final fallback.
 *
 * @return true if Go indicators are present in build strings.
 */
private fun isExplicitAndroidGo(): Boolean {
    return when {
        Build.DEVICE?.contains("go", ignoreCase = true) == true -> true
        Build.MODEL?.contains("go", ignoreCase = true) == true -> true
        Build.PRODUCT?.contains("go", ignoreCase = true) == true -> true
        Build.FINGERPRINT?.contains("go", ignoreCase = true) == true -> true
        else -> false
    }
}

/**
 * Simplified limited-device check.
 *
 * Unlike the full version, this only checks:
 *  - Microphone presence
 *  - RAM amount
 *  - Low-RAM flag
 *
 * This is used in places where a fast evaluation is needed without
 * calling system properties or multiple fallbacks.
 *
 * @receiver Context
 * @return true if device fails basic capability requirements.
 */
fun Context.isLimitedDeviceSimple(): Boolean {
    val hasMic = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    val totalMemoryGB = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

    return !hasMic && totalMemoryGB <= 2.0 && !activityManager.isLowRamDevice
}


/**
 * ============================================================================
 * getLocalizedResources()
 * ============================================================================
 *
 * Creates a new configuration context with a custom locale.
 *
 * PURPOSE:
 * • Ensures that views/layouts fetch strings of the selected CallSDK language
 * • Prevents needing to globally override the host app language
 *
 * HOW IT WORKS:
 * • Clones existing configuration
 * • Applies the desired Locale
 * • Returns a localized Resources instance from new context
 *
 * @param desiredLocale Locale to override
 * @return Localized Resources object
 */
fun getLocalizedResources(context: Context, desiredLocale: Locale?): Resources {
    var conf: Configuration = context.resources.configuration
    conf = Configuration(conf)
    conf.setLocale(desiredLocale)
    val localizedContext = context.createConfigurationContext(conf)
    return localizedContext.resources
}


/**
 * Check if notification service is enabled
 */
fun Context.isNotificationServiceEnabled(): Boolean {
    val packageName = packageName
    val flat = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return flat.contains(packageName)
}

fun String.isValidMobileNumber(): Boolean {
    if (isEmpty()) return false

    // Remove ALL invisible / non-printable / formatting characters
    val input = this.replace(Regex("[\\p{C}]"), "").trim()
    if (input.isEmpty()) return false

    // Digits, spaces, dash, parentheses and optional leading +
    if (!input.matches("""\+?[0-9()\-\s]+""".toRegex())) {
        return false
    }

    // Only one + and only at the start
    if (input.count { it == '+' } > 1) return false
    if (input.contains('+') && !input.startsWith("+")) return false

    // Extract only digits
    val digits = input.filter { it.isDigit() }

    return digits.length >= 3
}


/*Additionally added extention for adding and sending ads data
* Changes by Pratik Akoliya
* This useful for a saved data in database using this extensions
* */

fun Context.getNetworkCapabilities(): NetworkCapabilities? {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return null
    return cm.getNetworkCapabilities(network)
}

fun Context.getConnectionType(): String {
    val caps = getNetworkCapabilities() ?: return "NO_INTERNET"

    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
        else -> "UNKNOWN"
    }
}

fun Context.getMobileNetworkGeneration(): String {
    val caps = getNetworkCapabilities() ?: return "NO_INTERNET"

    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> getCellularGeneration()
    }
}

@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
fun Context.getCellularGeneration(): String {
    val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        telephonyManager.dataNetworkType
    } else {
        @Suppress("DEPRECATION") telephonyManager.networkType
    }

    return when (networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
        TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        else -> "CELLULAR"
    }
}

fun Context.isDeviceRoaming(): Int {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkInfo = cm.activeNetworkInfo
    return if (networkInfo?.isRoaming == true) 1 else 0
}

fun Context.getOrientation(): String {
    val resourcesOrientation = resources.configuration.orientation
    return when (resourcesOrientation) {
        Configuration.ORIENTATION_PORTRAIT -> "PORTRAIT"
        Configuration.ORIENTATION_LANDSCAPE -> "LANDSCAPE"
        else -> "UNKNOWN"
    }
}

fun Context.getLowMemory(): Int {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return if (info.lowMemory) 1 else 0
}
