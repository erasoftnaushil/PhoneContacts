package com.contactsapp2025.universal


//import com.contactsapp2025.universal.permissionApi.PermissionUtil
//import com.permissionsdk.PermissionSDK

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.callsdk.CallSDK
import com.callsdk.callmoniter.domain.entity.CallSdkTab.Companion.customTab
import com.callsdk.callmoniter.domain.entity.CallSdkTab.Companion.messageTab
import com.callsdk.callmoniter.domain.entity.CallSdkTab.Companion.moreOptionsTab
import com.callsdk.callmoniter.domain.entity.CallSdkTab.Companion.reminderTab
import com.callsdk.callmoniter.presentation.activity.CallerIdActivity
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.OverLayPermissionActivity
import com.contactsapp2025.universal.activities.PermissionActivity
import com.contactsapp2025.universal.activities.ScanningActivity
import com.contactsapp2025.universal.activities.SplashActivity
import com.contactsapp2025.universal.cdo.ContactsCDOFragment
import com.contactsapp2025.universal.cdo.XiaomiPermissionActivity
import com.contactsapp2025.universal.helpers.Config
import com.contactsapp2025.universal.helpers.Settings.Companion.THEME_DARK
import com.contactsapp2025.universal.helpers.Settings.Companion.THEME_LIGHT
import com.contactsapp2025.universal.helpers.Settings.Companion.THEME_SYSTEM
import com.contactsapp2025.universal.language.LanguageActivity
import com.contactsapp2025.universal.permissionApi.PermissionUtil.getDeviceId
import com.sdk.ads.Ads
import com.sdk.ads.config.AdConfig
import com.sdk.ads.config.AppOpenResumeConfig
import com.jksol.notificationsdk.NotificationSdk
import com.jksol.notificationsdk.config.NotificationSdkConfig
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.checkUseEnglish

open class MyApplication : Application() {

    open val isAppLockFeatureAvailable = false

    companion object {
        lateinit var myApplication: MyApplication
        var isColdStart = true
    }

    override fun onCreate() {
        super.onCreate()
        Log.e("PERMISSION_API", "App class call : ")
        myApplication = this
        val currentTheme = baseConfig.theme
        AppCompatDelegate.setDefaultNightMode(currentTheme)
        checkUseEnglish()
        setupAppLockManager()
        setAppMetrica()
        initializeCallSDK()
        initializeAmplitude()

        initializeStickyNotification()

        val resumeConfig = AppOpenResumeConfig.Builder()
            .setPlacement("app_open_resume")
            .addExcludedActivities(listOf(
                SplashActivity::class.java,
                BaseSplashActivity::class.java,
                PermissionActivity::class.java,
                OverLayPermissionActivity::class.java,
                XiaomiPermissionActivity::class.java,
                ScanningActivity::class.java,
                CallerIdActivity::class.java,
                LanguageActivity::class.java
            ))
            .build()

        val adConfig = AdConfig.Builder()
            .setDebugMode(BuildConfig.DEBUG)
            .setTestAdsEnabled(true)
            .setTestConsentGeography(BuildConfig.DEBUG)
            .setSecretKey("d2fd2e6aa25be4bab8d220c99f47ee69df6f73c1f2258e7877a645d94c17d643")
            .setCallSdkVersion(CallSDK.SDK_VERSION)
            .build()

        Ads.initialize(this, adConfig) { success, msg ->
            Log.d("Ads", "Init: $success -> $msg")
            if (success) {
                Ads.setSubscribed(false)
            }
        }
        Ads.setAppOpenResumeConfig(this, resumeConfig)


    }

    private fun initializeAmplitude() {
        val amplitude = Amplitude(
            Configuration(
                apiKey = "193eb70365d0d2d00ec0d4038b790b0f",
                context = applicationContext,
            )
        )

        amplitude.track("" + getDeviceId(this@MyApplication))

    }

    private fun setAppMetrica() {
        CoroutineScope(Dispatchers.IO).launch {
            val config = AppMetricaConfig.newConfigBuilder(getString(R.string.app_metrica_key))
                .withAnrMonitoring(true).withAnrMonitoringTimeout(5).build()
            AppMetrica.activate(this@MyApplication, config)
        }
    }

    private fun initializeCallSDK() {

        CallSDK.with(this)
            .setOverlayActivity(
                activityName = "com.contactsapp2025.universal.activities.OverLayPermissionActivity"
            )
            .setTabs(
                listOf(
                    customTab(
                        ContactsCDOFragment::class.java.name,
                        com.callsdk.R.drawable.ic_clsdk_fragment_one_icon
                    ),
                    messageTab(),
                    reminderTab(com.callsdk.R.drawable.ic_clsdk_fragment_third_icon),
                    moreOptionsTab(com.callsdk.R.drawable.ic_clsdk_fragment_fourth_icon),
                )
            )
            .setNotificationIcon(R.drawable.ic_after_notification)
            .setThemeColor(
                themeColor = (R.color.md_blue_500),
                buttonColor = (R.color.md_blue_500),
                backgroundColor = (R.color.white),
            )
            .setThemeMode(
                when (baseConfig.theme) {
                    THEME_SYSTEM -> 0
                    THEME_LIGHT -> 1
                    THEME_DARK -> 2
                    else -> {
                        0
                    }
                }
            )
            .setLanguage(Config(this).getLanguage() ?: "en")
            .enableDebugLogs(BuildConfig.DEBUG)
            .setSecretKey("d2fd2e6aa25be4bab8d220c99f47ee69df6f73c1f2258e7877a645d94c17d643")
            .setPremiumUser(false)
            .initialize()
    }


    fun initializeStickyNotification() {
        Log.d("NotiSplashTrace", "[MyApplication] initializeStickyNotification: initializing NotificationSdk...")
        NotificationSdk.init(
            this@MyApplication,
            NotificationSdkConfig.Builder()
                .fcmOnly(MainActivity::class.java)
                .setSecretKey("d2fd2e6aa25be4bab8d220c99f47ee69df6f73c1f2258e7877a645d94c17d643")
                .setDefaultIconRes(R.drawable.ic_after_notification)
                .setCanRequestAds {
                    Log.d("NotiSplashTrace", "[MyApplication] NotificationSdk canRequestAds evaluated -> true")
                    true
                }
                .setDebugMode(BuildConfig.DEBUG)
                .build()
        )
        Log.d("NotiSplashTrace", "[MyApplication] NotificationSdk.init completed.")
    }


    private fun setupAppLockManager() {
        if (isAppLockFeatureAvailable) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        }
    }
}
