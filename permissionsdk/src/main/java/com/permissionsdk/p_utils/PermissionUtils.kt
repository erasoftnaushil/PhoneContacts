package com.permissionsdk.p_utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import com.google.android.gms.appset.AppSet
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.permissionsdk.PermissionSDK.trustedTimeClient
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


internal fun logW(message: String) {
    Log.w(TAG, message)
}

internal fun logE(message: String) {
    Log.e(TAG, message)
}

internal fun String.prettyJson(): String {
    return try {
        val parsed = JsonParser.parseString(this)
        GsonBuilder().setPrettyPrinting().create().toJson(parsed)
    } catch (e: Exception) {
        this
    }
}

internal fun JSONObject.prettyJson(): String = toString(2)

internal fun JSONArray.prettyJson(): String = toString(2)


/**
* Returns the current time in milliseconds, using TrustedTime if available, otherwise falling back to System.currentTimeMillis()
*/
internal fun getTrustedUtcMillis(): Long {
    val millis = trustedTimeClient?.computeCurrentUnixEpochMillis()
    return millis ?: System.currentTimeMillis()
}

internal fun getUtcDay(): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(getTrustedUtcMillis()))
}

/**
 * Returns current UTC time in the format yyyy-MM-dd HH:mm:ss.
 *
 * Used for analytics, event timestamping, and logs.
 */
internal fun getUtcDateTime(): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(getTrustedUtcMillis()))
}


/**
 * Returns current UTC time in the format yyyy-MM-dd HH:mm:ss.
 */

internal fun Long.toUtcDateTime(): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(this))
}

/**
 * Get Package info for install time and update time
 */
@Suppress("DEPRECATION")
internal fun Context.permissionPkgInfo(): android.content.pm.PackageInfo {
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
 * Extension function to get country name from multiple sources
 * Returns country name, never null - tries multiple methods if one fails
 */
fun Context.getCountryName(): String {
    val country = try {
        getCountryFromTelephonyManager()
            ?: getCountryFromSimState()
            ?: getCountryFromLocale()
            ?: getCountryFromGeoIP()
            ?: "Unknown"
    } catch (e: Exception) {
        logE("Error getting country: ${e.message}")
        "Unknown"
    }
    return if (country.isNullOrBlank() || country.lowercase() == "null") "Unknown" else country
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
            val countryName = locale.displayCountry
            logW("Country from TelephonyManager: $countryName ($countryCode)")
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
                val countryName = locale.displayCountry
                logW("Country from SIM State: $countryName ($countryCode)")
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
            val countryName = countryLocale.displayCountry
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
        val geocoder = Geocoder(this, Locale.getDefault())

        // Try to get last known location
        val locationManager =
            getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val lastLocation = try {
            locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
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
    val code = try {
        getCountryCodeFromTelephonyManager()
            ?: getCountryCodeFromLocale()
            ?: "UN"
    } catch (e: Exception) {
        logE("Error getting country code: ${e.message}")
        "UN"
    }
    return if (code.isNullOrBlank() || code.lowercase() == "null") "UN" else code
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


internal fun Context.getCountryCodeFromLocale(): String? {
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
 * Returns the application versionName from package manager.
 *
 * Safe against NameNotFoundException and returns "Unknown" when missing.
 *
 * @receiver Context
 * @return App versionName as defined in the manifest.
 */
internal fun Context.getAppVersion(): String {
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }.toString()
}

/**
 * Returns the device manufacturer brand name, such as:
 * - "Samsung"
 * - "Xiaomi"
 * - "Realme"
 * - "Google"
 *
 * Useful for brand-specific permission handling (MIUI, Oppo, Vivo, etc).
 */
internal fun Context.getBrand(): String {
    return Build.BRAND
}

/**
 * Retrieves the device model name, such as:
 * - "Pixel 7"
 * - "SM-S918B"
 *
 * Useful for debugging and analytics.
 */
internal fun Context.getModel(): String {
    return Build.MODEL
}

/**
 * Returns the OS version string (e.g., "14", "13", "12").
 *
 * Note: This is not always the API level. It is the release version.
 */
internal fun Context.getOsVersion(): String {
    return Build.VERSION.RELEASE
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
internal fun Context.findDeviceId(): String {
    return Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
}

internal suspend fun Context.getAppSetId(): String? {
    return try {
        val client = AppSet.getClient(this)
        val info = client.appSetIdInfo.await()
        info.id
    } catch (e: Exception) {
        null
    }
}

/**
 * Returns the public IP address of the device.
 * Note: This implementation returns the local IP which might not be the public IP.
 * For true public IP, an external API is usually needed.
 */
internal fun getIpAddress(): String {
    try {
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress ?: ""
                }
            }
        }
    } catch (ex: Exception) {
        logE("Error getting IP: ${ex.message}")
    }
    return ""
}

/**
 * Returns the WebView User Agent.
 */
internal fun Context.getUserAgent(): String {
    return try {
        WebSettings.getDefaultUserAgent(this)
    } catch (e: Exception) {
        try {
            WebView(this).settings.userAgentString
        } catch (e2: Exception) {
            System.getProperty("http.agent") ?: ""
        }
    }
}

internal val isTiramisuPlus: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

