package com.contactsapp2025.universal.cdo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.callsdk.CallSDK
import com.callsdk.CallSDKSettings
import com.callsdk.permissions.helper.AutoStartPermissionHelper
import com.callsdk.permissions.helper.XiaomiUtilities
import com.callsdk.utils.ACTIVITY_RECREATED
import com.contactsapp2025.universal.databinding.ActivityXiaomiPermissionBinding
import com.contactsapp2025.universal.extensions.getNextTargetAfterPermissions
import com.contactsapp2025.universal.permissionApi.isOverlayPermissionGranted

//import com.permissionsdk.PermissionSDK

class XiaomiPermissionActivity : AppCompatActivity() {
    private val TAG = "MiUIPermissionCheck"
    private val mBinding by lazy { ActivityXiaomiPermissionBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mBinding.root)
        initStatusBar()

        Log.e(
            TAG,
            "onCreate: " + CallSDKSettings.isAutoStartNeeded(this@XiaomiPermissionActivity) + " " + getBgPermissionGranted() + " " + getAutoStartGranted()
        )
        if (CallSDKSettings.isAutoStartNeeded(this) && (!getBgPermissionGranted() || !Settings.canDrawOverlays(this))){
            firstPage()
        }else{
            secondPage()
        }
        initUI()
        initClick()
        if (intent.getBooleanExtra(ACTIVITY_RECREATED, false)) {
            Log.e(
                TAG,
                "onCreate: ACTIVITY_RECREATED " + CallSDKSettings.isAutoStartNeeded(this@XiaomiPermissionActivity) + " " + getBgPermissionGranted() + " " + getAutoStartGranted()
            )
            if (!CallSDKSettings.isAutoStartNeeded(this)) {
                nextActivity()
            } else {
                checkBackgroundOEM()
            }
        }
    }

    private fun initStatusBar() {
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            !isDarkModeEnabled(this)
    }
    fun isDarkModeEnabled(context: Context): Boolean {
        val currentNightMode =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun askOverlayPermission() {
        val intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${packageName}".toUri())
        CallSDK.notifyOpeningOverlaySettings()
//         CallSDK.notifyOpeningOverlaySettings()
        overlayPermissionResultLauncher.launch(intent)
    }


    private fun initClick() {
        mBinding.apply {
            btnOverlayPermission.setOnClickListener {
                val bgStart = XiaomiUtilities.isCustomPermissionGranted(
                    this@XiaomiPermissionActivity,
                    XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY
                )
                Log.e(
                    TAG,
                    "initClick:  isOverlayPermissionGranted ${isOverlayPermissionGranted()} bgStart  $bgStart  isAutoStartNeed ${CallSDKSettings.isAutoStartNeeded(this@XiaomiPermissionActivity)}"
                )
                if (!Settings.canDrawOverlays(applicationContext)) {
                    askOverlayPermission()
                } else {
                    checkBackgroundOEM()
                }
            }
        }
    }


    private fun initUI() {
        mBinding.apply {
            // UI
            tvAppName.text = getAppNameFromPackageName()
            btnOverlayPermission.background = ContextCompat.getDrawable(this@XiaomiPermissionActivity, com.contactsapp2025.universal.R.drawable.btn_bg)
            //  btnOverlayPermission.backgroundTintList = ColorStateList.valueOf(prefs.themeColor.get())
            imageView5.imageTintList = ColorStateList.valueOf(getColorFromRes(CallSDKSettings.getThemeColor()))
        }
    }

    @ColorInt
    fun Context.getColorFromRes(@ColorRes id: Int): Int {
        return ContextCompat.getColor(this, id)
    }

    fun getAppNameFromPackageName(): String {
        return try {
            if (packageName.isBlank()) {
                return "this app"
            }

            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(applicationInfo).toString()

            appName.ifBlank { "this app" }
        } catch (e: PackageManager.NameNotFoundException) {
            "this app"
        } catch (e: SecurityException) {
            "this app"
        } catch (e: Exception) {
            "this app"
        }
    }


    val overlayPermissionResultLauncher =registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Log.e(TAG, "overlayPermissionResultLauncher:  isOverlayPermissionGranted ${Settings.canDrawOverlays(applicationContext)} ")
        if (Settings.canDrawOverlays(applicationContext)) {
           // logEvent(this@XiaomiPermissionActivity,"xiaomi_class_permission_overlay_granted")
            checkBackgroundOEM()
        }
    }


    val bgPermissionLauncher =registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (getBgPermissionGranted()) {
          //  logEvent(this@XiaomiPermissionActivity,"xiaomi_class_permission_autostart_granted")
            checkAutoStart()
        }else{
            firstPage()
        }
    }


    val autoStartLauncher =registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (getAutoStartGranted()) {
            checkAutoStart()
        }else{
            secondPage()
        }
    }



    private fun checkBackgroundOEM() {
        if (!getBgPermissionGranted()) {
            try {
                // Method 1: Specific MIUI intent
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                intent.setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                intent.putExtra("extra_pkgname", packageName)
                bgPermissionLauncher.launch(intent)
                return
            } catch (e: Exception) {
                Log.d("OEMPermissionHelper", "MIUI Method 1 failed: ${e.message}")
            }


            try {
                // Method 2: Alternative MIUI intent
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                intent.setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                intent.putExtra("extra_pkgname", packageName)
                bgPermissionLauncher.launch(intent)
                return
            } catch (e: Exception) {
                Log.d("OEMPermissionHelper", "MIUI Method 2 failed: ${e.message}")
            }
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                bgPermissionLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("OEMPermissionHelper", "Default method also failed: ${e.message}")
                // Fallback to general app info
                val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                bgPermissionLauncher.launch(intent)
            }
        } else {
            checkAutoStart()
        }
    }


    fun checkAutoStart(){
        if (getAutoStartGranted()){
            checkAllPermission()
        }else{
            AutoStartPermissionHelper.openAutoStartPermission(this@XiaomiPermissionActivity, autoStartLauncher)
        }
    }


    fun checkAllPermission() {
        // 1. Overlay required always
        if (!Settings.canDrawOverlays(applicationContext)) return firstPage()


        // 2. If auto-start not needed → direct next
        if (!CallSDKSettings.isAutoStartNeeded(this)) return nextActivity()


        // 3. Background permission required
        if (!getBgPermissionGranted()) return firstPage()


        // 4. Auto-start required
        if (!getAutoStartGranted()) return secondPage()


        // 5. All clear
        nextActivity()
    }


    private fun secondPage() {
//        logEvent(this@XiaomiPermissionActivity,"xiaomi_class_permission_second_page")
        mBinding.apply {
            cvSecond.visibility = View.VISIBLE
            tvCount.text = "2"
            tvCount.visibility = View.VISIBLE
            tvAppName.visibility = View.GONE
            ivMainImage.setImageResource(com.callsdk.R.drawable.ic_clsdk_temp1)
            btnOverlayPermission.text = "Grant Auto-Start Permission"
            ivAppIcon.getImageFromPackageName()
            tvAppName1.text = getAppNameFromPackageName()
        }
    }

    fun ImageView.getImageFromPackageName() {
        try {
            val icon = context.packageManager.getApplicationIcon(packageName)
            this.setImageDrawable(icon)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }


    private fun firstPage() {
//        logEvent(this@XiaomiPermissionActivity,"xiaomi_class_permission_first_page")
        mBinding.apply {
            tvCount.text = "1"
            tvCount.visibility = View.VISIBLE
            tvAppName.visibility = View.VISIBLE
            ivMainImage.setImageResource(com.callsdk.R.drawable.ic_clsdk_temp)
            btnOverlayPermission.text = "Grant Permission"
            cvSecond.visibility = View.GONE
        }
    }



    private fun nextActivity() {
        val intent = Intent(this@XiaomiPermissionActivity, getNextTargetAfterPermissions())
        startActivity(intent)
        finish()
    }


    fun getAutoStartGranted(): Boolean {
        return XiaomiUtilities.isCustomPermissionGranted(
            this@XiaomiPermissionActivity,
            XiaomiUtilities.OP_AUTO_START
        )
    }


    fun getBgPermissionGranted(): Boolean {
        return XiaomiUtilities.isCustomPermissionGranted(
            this@XiaomiPermissionActivity,
            XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY
        )
    }
}
