package com.contactsapp2025.universal.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.callsdk.CallSDKSettings
import com.callsdk.permissions.helper.OEMPermissionHelper.isMi
import com.callsdk.permissions.helper.XiaomiUtilities
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.cdo.XiaomiPermissionActivity
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.AnalyticsHelper
import com.contactsapp2025.universal.helpers.ContactFeatureNotificationHelper
import com.contactsapp2025.universal.helpers.OnboardingNotificationEngine
import com.contactsapp2025.universal.helpers.OnboardingNotificationEngine.startOnboarding
import com.contactsapp2025.universal.helpers.PermissionsHelper
import com.sdk.ads.Ads
import com.sdk.ads.core.model.AppOpenType
import com.jksol.notificationsdk.NotificationSdk
import org.fossify.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {

    private val splashHandler = Handler(Looper.getMainLooper())
    private var maxTimeoutRunnable: Runnable? = null
    private var offlineTimeoutRunnable: Runnable? = null
    private var isSplashContentReady = false
    private var isBaseInitializationComplete = false
    private var isStartupFlowStarted = false

    companion object {
        private const val TAG = "NotiSplashTrace"
        private const val OFFLINE_TIMEOUT_MS = 3000L
        private const val TOTAL_AD_TIMEOUT_MS = 12000L
        private const val APP_OPEN_TIMEOUT_MS = 8000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Immediately set splash layout so indeterminate loader and UI display while ads/bridge load
        setContentView(R.layout.activity_splash)
        Log.d(TAG, "[SplashActivity] onCreate: setContentView(R.layout.activity_splash) attached. Indeterminate loader is visible.")

        Log.d(TAG, "[SplashActivity] Calling NotificationSdk.handleLaunch(this)...")
        NotificationSdk.handleLaunch(
            this,
            onNormalLaunch = {
                Log.d(TAG, "[SplashActivity] >> NORMAL LAUNCH detected. Starting normal permission and splash ad flow.")
                handleNormalLaunch()
            },
            onNotificationLaunch = { target ->
                Log.d(TAG, "[SplashActivity] >> NOTIFICATION LAUNCH detected. Destination component: ${target.intent.component}, extras: ${target.intent.extras?.keySet()}")
                handleNotificationLaunch(target.intent)
            }
        )
    }

    private fun handleNotificationLaunch(destinationIntent: Intent) {
        runOnUiThread {
            Log.d(TAG, "[SplashActivity] handleNotificationLaunch: Checking permissions before navigating to notification destination...")
            if (PermissionsHelper.areAllPermissionsGranted(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE))) {
                if (Settings.canDrawOverlays(this@SplashActivity)) {
                    if (isMi() && CallSDKSettings.isAutoStartNeeded(this)) {
                        val autoStart = XiaomiUtilities.isCustomPermissionGranted(this@SplashActivity, XiaomiUtilities.OP_AUTO_START)
                        val bgStart = XiaomiUtilities.isCustomPermissionGranted(this@SplashActivity, XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY)
                        if (autoStart && bgStart) {
                            Log.d(TAG, "[SplashActivity] Notification Launch: All permissions granted. Directly navigating to destinationIntent (no host ads).")
                            executeProceed(destinationIntent)
                        } else {
                            Log.d(TAG, "[SplashActivity] Notification Launch: Xiaomi permission required. Routing to XiaomiPermissionActivity.")
                            startOnboarding(this)
                            val intent = Intent(this@SplashActivity, XiaomiPermissionActivity::class.java)
                            executeProceed(intent)
                        }
                    } else {
                        Log.d(TAG, "[SplashActivity] Notification Launch: All permissions granted. Directly navigating to destinationIntent (no host ads).")
                        executeProceed(destinationIntent)
                    }
                } else {
                    Log.d(TAG, "[SplashActivity] Notification Launch: Overlay permission required. Routing to OverLayPermissionActivity.")
                    startOnboarding(this)
                    executeProceed(Intent(this, OverLayPermissionActivity::class.java))
                }
            } else {
                Log.d(TAG, "[SplashActivity] Notification Launch: Contacts/Phone permissions required. Routing to PermissionActivity.")
                startOnboarding(this)
                executeProceed(Intent(this, PermissionActivity::class.java))
            }
        }
    }

    private fun handleNormalLaunch() {
        val isLaunchedFromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        Log.d(TAG, "[SplashActivity] handleNormalLaunch: isLaunchedFromHistory = $isLaunchedFromHistory")
        if (isLaunchedFromHistory) {
            intent.removeExtra("destination_class")
            intent.removeExtra(org.fossify.commons.helpers.CONTACT_ID)
            intent.removeExtra(org.fossify.commons.helpers.IS_PRIVATE)
            intent.removeExtra("fromCDO")
            intent.removeExtra("notification_category")
            intent.removeExtra("from_feature_notification")
            intent.removeExtra("from_notification")
            intent.removeExtra("tab_position")
        }

        intent.removeExtra("IS_FROM_CDO_ACTIVITY")
        com.contactsapp2025.universal.MyApplication.isColdStart = false

        if (intent.getBooleanExtra("from_notification", false) || intent.getBooleanExtra("from_feature_notification", false)) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(OnboardingNotificationEngine.NOTIFICATION_ID)
            notificationManager.cancel(ContactFeatureNotificationHelper.NOTIFICATION_ID)
        }

        if (intent.getBooleanExtra("from_notification", false)) {
            val stageName = intent.getStringExtra("notification_stage") ?: "unknown"
            val count = intent.getIntExtra("notification_count", 0)

            val params = Bundle().apply {
                putString("notification_stage", stageName)
                putInt("notification_count", count)
            }
            AnalyticsHelper.logEvent(this, "onboarding_notification_tapped", params)
            OnboardingNotificationEngine.resumeOnboarding(this)
        }

        isSplashContentReady = true
        startStartupFlowIfReady()
    }

    private var isProceeded = false
    private var isActivityPaused = false
    private var isAdShowing = false
    private var isAppOpenLoaded = false
    private var isInterstitialLoaded = false
    private var splashAdStartTime = 0L
    private var has12sExpiredWithoutAd = false
    private var pendingProceedIntent: Intent? = null
    private var targetIntent: Intent? = null

    override fun onResume() {
        super.onResume()
        isActivityPaused = false
        val elapsed = if (splashAdStartTime > 0L) SystemClock.elapsedRealtime() - splashAdStartTime else 0L
        Log.d(TAG, "[SplashActivity] onResume: elapsed=${elapsed}ms, isAppOpenLoaded=$isAppOpenLoaded, isInterstitialLoaded=$isInterstitialLoaded, has12sExpiredWithoutAd=$has12sExpiredWithoutAd, isAdShowing=$isAdShowing, isProceeded=$isProceeded")

        if (isProceeded || isAdShowing) return

        // 1. If App Open ad completed loading within 12s (even if while on Home screen), show it immediately
        if (isAppOpenLoaded) {
            Log.d(TAG, "[SplashActivity] onResume: App Open Ad was loaded within 12s. Directly showing ad now!")
            showSplashAppOpenAd(targetIntent ?: getMainDestinationIntent())
            return
        }

        // 2. If Interstitial ad completed loading within 12s, show it immediately
        if (isInterstitialLoaded || Ads.isInterstitialReady("interstitial_splash")) {
            Log.d(TAG, "[SplashActivity] onResume: Interstitial Ad was loaded within 12s. Directly showing ad now!")
            showInterstitialAd(targetIntent ?: getMainDestinationIntent())
            return
        }

        // 3. If 12 seconds expired without any ad loading (or both ads failed), navigate to next screen now
        if (has12sExpiredWithoutAd || (splashAdStartTime > 0L && elapsed >= TOTAL_AD_TIMEOUT_MS)) {
            Log.d(TAG, "[SplashActivity] onResume: 12 seconds expired without ad. Navigating to next screen now.")
            executeProceed(targetIntent ?: getMainDestinationIntent())
            return
        }

        // 4. If offline timeout expired while in background
        pendingProceedIntent?.let {
            Log.d(TAG, "[SplashActivity] onResume: Executing pending proceed intent.")
            pendingProceedIntent = null
            executeProceed(it)
            return
        }

        // 5. Otherwise, still within 12 seconds and ad is still loading: keep waiting on the splash screen!
        Log.d(TAG, "[SplashActivity] onResume: Still within 12s window (remaining: ${TOTAL_AD_TIMEOUT_MS - elapsed}ms). Waiting for ad.")
    }

    override fun onPause() {
        super.onPause()
        isActivityPaused = true
        val elapsed = if (splashAdStartTime > 0L) SystemClock.elapsedRealtime() - splashAdStartTime else 0L
        Log.d(TAG, "[SplashActivity] onPause: User pressed HOME / app minimized. Elapsed so far: ${elapsed}ms. Ad loading continues in background.")
    }

    private fun executeProceed(intent: Intent) {
        if (!isProceeded) {
            isProceeded = true
            Log.d(TAG, "[SplashActivity] executeProceed: Navigating to ${intent.component?.className ?: intent}. isProceeded set to true.")
            splashHandler.removeCallbacksAndMessages(null)
            startActivity(intent)
            finish()
        } else {
            Log.d(TAG, "[SplashActivity] executeProceed called but isProceeded is already true. Ignoring.")
        }
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadInterstitialFallback(intent: Intent) {
        if (isProceeded || isAdShowing) {
            Log.d(TAG, "[SplashActivity] loadInterstitialFallback: isProceeded=$isProceeded, isAdShowing=$isAdShowing. Skipping.")
            return
        }
        targetIntent = intent

        val elapsed = if (splashAdStartTime > 0L) SystemClock.elapsedRealtime() - splashAdStartTime else 0L
        val remainingMs = (TOTAL_AD_TIMEOUT_MS - elapsed).coerceAtLeast(1000L)
        Log.d(TAG, "[SplashActivity] loadInterstitialFallback: Starting Interstitial load (timeout: ${remainingMs}ms, inBackground=$isActivityPaused)...")

        Ads.loadInterstitial(
            placement = "interstitial_splash",
            timeoutMs = remainingMs,
            onLoaded = {
                val currentElapsed = if (splashAdStartTime > 0L) SystemClock.elapsedRealtime() - splashAdStartTime else 0L
                Log.d(TAG, "[SplashActivity] Interstitial Ad loaded in ${currentElapsed}ms.")
                if (currentElapsed <= TOTAL_AD_TIMEOUT_MS) {
                    isInterstitialLoaded = true
                    if (!isActivityPaused) {
                        showInterstitialAd(intent)
                    } else {
                        Log.d(TAG, "[SplashActivity] Interstitial Ad loaded in background within 12s. Waiting for user return.")
                    }
                } else {
                    Log.d(TAG, "[SplashActivity] Interstitial Ad loaded but after 12s window ($currentElapsed ms). Ignoring.")
                }
            },
            onFailed = { error ->
                Log.d(TAG, "[SplashActivity] Fallback Interstitial Ad also failed: $error. Both ads failed.")
                if (!isActivityPaused) {
                    splashHandler.removeCallbacksAndMessages(null)
                    executeProceed(intent)
                } else {
                    has12sExpiredWithoutAd = true
                }
            }
        )
    }

    private fun showInterstitialAd(intent: Intent) {
        if (isFinishing || isDestroyed || isProceeded || isAdShowing) return
        isAdShowing = true
        isInterstitialLoaded = false
        Log.d(TAG, "[SplashActivity] showInterstitialAd: Showing Interstitial Ad...")

        val shown = Ads.showInterstitial(
            activity = this,
            placement = "interstitial_splash",
            onDismissed = {
                Log.d(TAG, "[SplashActivity] Interstitial Ad dismissed. Proceeding to destination.")
                isAdShowing = false
                proceedAction(intent)
            },
            onFailedToShow = { error ->
                Log.d(TAG, "[SplashActivity] Interstitial Ad failed to show: $error. Proceeding to destination.")
                isAdShowing = false
                proceedAction(intent)
            }
        )
        if (!shown) {
            Log.d(TAG, "[SplashActivity] showInterstitial returned false. Proceeding.")
            isAdShowing = false
            proceedAction(intent)
        }
    }

    private fun showSplashAppOpenAd(intent: Intent) {
        if (isFinishing || isDestroyed || isProceeded || isAdShowing) {
            Log.d(TAG, "[SplashActivity] showSplashAppOpenAd: Cannot show (isFinishing=$isFinishing, isDestroyed=$isDestroyed, isProceeded=$isProceeded, isAdShowing=$isAdShowing)")
            return
        }
        isAdShowing = true
        isAppOpenLoaded = false
        Log.d(TAG, "[SplashActivity] showSplashAppOpenAd: Showing Splash App Open Ad...")

        Ads.showSplashAppOpenAd(
            activity = this,
            onDismissed = {
                Log.d(TAG, "[SplashActivity] Splash App Open Ad dismissed. Proceeding to destination.")
                isAdShowing = false
                proceedAction(intent)
            },
            onFailedToShow = {
                Log.d(TAG, "[SplashActivity] Splash App Open Ad failed to show. Proceeding to destination.")
                isAdShowing = false
                proceedAction(intent)
            }
        )
    }

    private fun proceedAction(intent: Intent) {
        if (!isFinishing && !isDestroyed) {
            if (isActivityPaused) {
                Log.d(TAG, "[SplashActivity] proceedAction: Activity is paused. Storing pendingProceedIntent.")
                pendingProceedIntent = intent
            } else {
                executeProceed(intent)
            }
        }
    }

    private fun proceedWithIntent(intent: Intent) {
        targetIntent = intent

        if (!isNetworkConnected()) {
            Log.d(TAG, "[SplashActivity] Device is OFFLINE. Showing splash for 3 seconds before proceeding.")
            offlineTimeoutRunnable = Runnable {
                Log.d(TAG, "[SplashActivity] Offline 3-second timeout reached. Proceeding.")
                proceedAction(intent)
            }
            splashHandler.postDelayed(offlineTimeoutRunnable!!, OFFLINE_TIMEOUT_MS)
            return
        }

        splashAdStartTime = SystemClock.elapsedRealtime()
        has12sExpiredWithoutAd = false
        isAppOpenLoaded = false
        isInterstitialLoaded = false

        Log.d(TAG, "[SplashActivity] Device is ONLINE. Starting 12-second splash ad window.")
        maxTimeoutRunnable = Runnable {
            val elapsed = SystemClock.elapsedRealtime() - splashAdStartTime
            Log.d(TAG, "[SplashActivity] 12-second total ad window reached (elapsed: ${elapsed}ms).")
            if (isAdShowing || isProceeded) return@Runnable

            val hasAdLoaded = isAppOpenLoaded || isInterstitialLoaded || Ads.isInterstitialReady("interstitial_splash")
            if (!hasAdLoaded) {
                Log.d(TAG, "[SplashActivity] 12 seconds expired without loaded ad. Navigating to next screen.")
                if (!isActivityPaused) {
                    executeProceed(intent)
                } else {
                    has12sExpiredWithoutAd = true
                }
            } else {
                Log.d(TAG, "[SplashActivity] 12s expired but ad was already loaded! Keeping ad ready.")
            }
        }
        splashHandler.postDelayed(maxTimeoutRunnable!!, TOTAL_AD_TIMEOUT_MS)

        Log.d(TAG, "[SplashActivity] Loading Ads Splash App Open Ad (timeout: ${APP_OPEN_TIMEOUT_MS}ms)...")
        Ads.loadSplashAppOpenAd(
            activity = this,
            placement = "app_open_splash",
            appOpenType = AppOpenType.APP_OPEN,
            timeoutMs = APP_OPEN_TIMEOUT_MS,
            onLoaded = {
                val elapsed = SystemClock.elapsedRealtime() - splashAdStartTime
                Log.d(TAG, "[SplashActivity] Ads Splash App Open Ad loaded in ${elapsed}ms.")
                if (elapsed <= TOTAL_AD_TIMEOUT_MS) {
                    isAppOpenLoaded = true
                    if (!isActivityPaused) {
                        showSplashAppOpenAd(intent)
                    } else {
                        Log.d(TAG, "[SplashActivity] App Open Ad loaded in background within 12s. Waiting for user return.")
                    }
                } else {
                    Log.d(TAG, "[SplashActivity] App Open Ad loaded but after 12s window ($elapsed ms). Ignoring.")
                }
            },
            onFailed = { error ->
                val elapsed = SystemClock.elapsedRealtime() - splashAdStartTime
                Log.d(TAG, "[SplashActivity] Ads Splash App Open Ad failed: $error (elapsed: ${elapsed}ms). Loading fallback interstitial.")
                loadInterstitialFallback(intent)
            }
        )
    }

    override fun initActivity() {
        Log.d(TAG, "[SplashActivity] initActivity called from BaseSplashActivity.")
        isBaseInitializationComplete = true
        startStartupFlowIfReady()
    }

    private fun startStartupFlowIfReady() {
        Log.d(TAG, "[SplashActivity] startStartupFlowIfReady: isSplashContentReady=$isSplashContentReady, isBaseInitializationComplete=$isBaseInitializationComplete, isStartupFlowStarted=$isStartupFlowStarted")
        if (!isSplashContentReady || !isBaseInitializationComplete || isStartupFlowStarted) {
            return
        }

        isStartupFlowStarted = true
        if (canShowNotificationPermissionPrompt()) {
            Log.d(TAG, "[SplashActivity] Requesting POST_NOTIFICATIONS permission prompt (code 101)...")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        } else {
            Log.d(TAG, "[SplashActivity] POST_NOTIFICATIONS prompt not needed. Proceeding to normal loader flow.")
            startLoaderAndContinue()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            config.notificationPermissionRequested = true
            val status = if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
            Log.d(TAG, "[SplashActivity] POST_NOTIFICATIONS permission result: $status")
            val params = Bundle().apply { putString("status", status) }
            AnalyticsHelper.logEvent(this, "notification_permission", params)
            startLoaderAndContinue()
        }
    }

    private fun canShowNotificationPermissionPrompt(): Boolean {
        return !(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && (!config.notificationPermissionRequested ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun getMainDestinationIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            intent.extras?.let { putExtras(it) }
        }
    }

    private fun startLoaderAndContinue() {
        runOnUiThread {
            Log.d(TAG, "[SplashActivity] startLoaderAndContinue: Checking permissions for normal launch...")

            Handler(Looper.getMainLooper()).postDelayed({
                if (PermissionsHelper.areAllPermissionsGranted(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE))) {
                    if (Settings.canDrawOverlays(this@SplashActivity)) {
                        if (isMi() && CallSDKSettings.isAutoStartNeeded(this)) {
                            val autoStart = XiaomiUtilities.isCustomPermissionGranted(this@SplashActivity, XiaomiUtilities.OP_AUTO_START)
                            val bgStart = XiaomiUtilities.isCustomPermissionGranted(this@SplashActivity, XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY)
                            if (autoStart && bgStart) {
                                Log.d(TAG, "[SplashActivity] Normal Launch: All permissions granted (Mi autoStart+bgStart OK). Proceeding to MainActivity.")
                                proceedWithIntent(getMainDestinationIntent())
                            } else {
                                Log.d(TAG, "[SplashActivity] Normal Launch: Xiaomi permission needed. Proceeding to XiaomiPermissionActivity.")
                                startOnboarding(this)
                                val intent = Intent(this@SplashActivity, XiaomiPermissionActivity::class.java)
                                proceedWithIntent(intent)
                            }
                        } else {
                            Log.d(TAG, "[SplashActivity] Normal Launch: All permissions granted. Proceeding to MainActivity.")
                            proceedWithIntent(getMainDestinationIntent())
                        }
                    } else {
                        Log.d(TAG, "[SplashActivity] Normal Launch: Overlay permission needed. Proceeding to OverLayPermissionActivity.")
                        startOnboarding(this)
                        proceedWithIntent(Intent(this, OverLayPermissionActivity::class.java))
                    }
                } else {
                    Log.d(TAG, "[SplashActivity] Normal Launch: Contacts/Phone permissions needed. Proceeding to PermissionActivity.")
                    startOnboarding(this)
                    proceedWithIntent(Intent(this, PermissionActivity::class.java))
                }
            }, 100)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "[SplashActivity] onNewIntent received. Extras: ${intent.extras?.keySet()}")
        if (intent.getBooleanExtra("from_notification", false) || intent.getBooleanExtra("from_feature_notification", false)) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(OnboardingNotificationEngine.NOTIFICATION_ID)
            notificationManager.cancel(ContactFeatureNotificationHelper.NOTIFICATION_ID)
            if (intent.getBooleanExtra("from_notification", false)) {
                OnboardingNotificationEngine.resumeOnboarding(this)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "[SplashActivity] onDestroy: clearing timeouts and handlers.")
        splashHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
