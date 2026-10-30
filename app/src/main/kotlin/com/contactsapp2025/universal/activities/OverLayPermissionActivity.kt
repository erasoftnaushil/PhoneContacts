package com.contactsapp2025.universal.activities

import android.app.AppOpsManager
import android.app.AppOpsManager.OnOpChangedListener
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.viewpager2.widget.ViewPager2
import com.callsdk.CallSDK
import com.callsdk.CallSDKSettings
import com.callsdk.permissions.helper.OEMPermissionHelper.isMi
import com.callsdk.permissions.helper.XiaomiUtilities


import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.PermissionActivity.Companion.showOccurDialog
import com.contactsapp2025.universal.cdo.XiaomiPermissionActivity
import com.contactsapp2025.universal.databinding.ActivityOverlayBinding
import com.contactsapp2025.universal.extensions.applySystemBarsForTheme
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.getNextTargetAfterPermissions
import com.contactsapp2025.universal.language.LanguageActivity

//import com.contactsapp2025.universal.permissionApi.PermissionUtil.onAskOverlay
//import com.contactsapp2025.universal.permissionApi.PermissionUtil.onOverlayResult


import com.contactsapp2025.universal.utils.ImageAdapter
import com.contactsapp2025.universal.utils.SharedViewModel
import com.inmobi.media.th
//import com.permissionsdk.PermissionSDK

import java.util.Locale
import kotlin.math.abs

class OverLayPermissionActivity : AppCompatActivity() {
    private var _binding: ActivityOverlayBinding? = null
    private val binding get() = _binding!!

    private var appOps: AppOpsManager? = null
    private val REQUEST_CODE_OVERLAY_PERMISSION = 101

    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val languageCode = config.getLanguage() // Use the existing config instance
        if (!languageCode.isNullOrEmpty()) {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val configuration = resources.configuration
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        super.onCreate(savedInstanceState)
        _binding = ActivityOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarsForTheme()

        setViewPager()
        binding.allowPermissionButton.setOnClickListener {
            requestOverlayPermission()
        }
        sharedViewModel.clickEvent.observe(this) {
            requestOverlayPermission()
        }

        onBackPressedDispatcher.addCallback(this) {
            showOccurDialog(
                this@OverLayPermissionActivity,
                "The app can only function if it is overlay permission on your device without this, it will not be able to work properly."
            ) {
                requestOverlayPermission()
            }
        }
    }

    //request overlay permission
    private fun requestOverlayPermission() {
        try {
            // ask overlay
           // onAskOverlay(this@OverLayPermissionActivity)
            appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            appOps!!.startWatchingMode(
                AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                applicationContext.packageName,
                object : OnOpChangedListener {
                    override fun onOpChanged(op: String?, packageName: String?) {
                        val mode = appOps!!.checkOpNoThrow(
                            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                            Process.myUid(), getPackageName()
                        )
                        if (mode != AppOpsManager.MODE_ALLOWED) {

                            Log.e("OverlayScreen", "onOpChanged: 0", )
                            // deny overlay
                           // onOverlayResult(this@OverLayPermissionActivity,false)
                            return
                        }
                        Log.e("OverlayScreen", "onOpChanged: 1", )
                            // allow overlay
                       // onOverlayResult(this@OverLayPermissionActivity,true)

                        appOps!!.stopWatchingMode(this)
                        runOnUiThread {
                            try {
                                Log.e("OverlayScreen", "onOpChanged: 3", )
                                val autoStart = XiaomiUtilities.isCustomPermissionGranted(this@OverLayPermissionActivity, XiaomiUtilities.OP_AUTO_START)
                                val bgStart =
                                    XiaomiUtilities.isCustomPermissionGranted(this@OverLayPermissionActivity, XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY)

                                val targetActivity = if (isMi() && CallSDKSettings.isAutoStartNeeded(this@OverLayPermissionActivity)) {
                                    if (Settings.canDrawOverlays(this@OverLayPermissionActivity) && autoStart && bgStart) {
                                        getNextTargetAfterPermissions()
                                    } else {
                                        XiaomiPermissionActivity::class.java
                                    }
                                } else {
                                    getNextTargetAfterPermissions()
                                }
                                // Start Activity
                                val intent = Intent(this@OverLayPermissionActivity, targetActivity)
                                overridePendingTransition(0, 0)
                                intent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                finish()
                                overridePendingTransition(0, 0)
                                startActivity(intent)
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                })

            openOverlayPermissionPage()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openOverlayPermissionPage() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                ("package:" + applicationContext.packageName).toUri()
            )
            CallSDK.notifyOpeningOverlaySettings()
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                Log.e("OverlayScreen", "onOpChanged: 2", )

                // allow overlay
               // onOverlayResult(this@OverLayPermissionActivity,true)

                val intent = Intent(this, getNextTargetAfterPermissions())
                startActivity(intent)
                finish()
            }
        }
    }

    private fun setViewPager() {
        val imageAdapter = ImageAdapter(this)
        binding.imageViewPager.adapter = imageAdapter
        binding.imageViewPager.setPageTransformer { page, position ->
            if (position == 0f || position == 1f) {
                return@setPageTransformer
            }
            page.translationX = -position * (page.width / 2 - 100)
            page.alpha = 1 - abs(position)
        }
        binding.imageViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.dot1.setImageResource(R.drawable.dot_inactive)
                binding.dot2.setImageResource(R.drawable.dot_inactive)
                if (position == 0) {
                    binding.dot1.setImageResource(R.drawable.dot_active)
                } else {
                    binding.dot2.setImageResource(R.drawable.dot_active)
                }
            }
        })
    }
}
