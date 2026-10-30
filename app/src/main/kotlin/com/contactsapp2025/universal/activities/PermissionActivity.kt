package com.contactsapp2025.universal.activities

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.databinding.ActivityPermissionBinding
import com.contactsapp2025.universal.databinding.LayoutPrivacyAlertBinding
import com.contactsapp2025.universal.extensions.applySystemBarsForTheme
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.getNextTargetAfterPermissions
import com.contactsapp2025.universal.utils.applyClickShrink
import com.permissionx.guolindev.PermissionX

class PermissionActivity : AppCompatActivity() {
    private var _binding: ActivityPermissionBinding? = null
    private val binding get() = _binding!!

    private val REQUEST_CODE_APP_SETTINGS = 222

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            binding.llNotificationPer.visibility = View.GONE
        }

        applySystemBarsForTheme()
        setPrivacyText()
        binding.btnGetStarted.applyClickShrink()
        binding.btnGetStarted.setOnClickListener {
            checkAndRequestPermissions()
        }

        onBackPressedDispatcher.addCallback(this) {
            showOccurDialog(this@PermissionActivity,"The app can only function if it is Phone state on your device without this, it will not be able to work properly.") {
                checkAndRequestPermissions()
            }
        }
    }


    companion object {
        fun showOccurDialog(
            activity: Activity,text : String,
            onOkClick: () -> Unit
        ) {
            val binding = LayoutPrivacyAlertBinding.inflate(activity.layoutInflater)
            val dialog = Dialog(activity)

            dialog.apply {
                setContentView(binding.root)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                window?.setGravity(Gravity.CENTER)

                val width = (context.resources.displayMetrics.widthPixels * 0.92).toInt()
                window?.setLayout(width, ConstraintLayout.LayoutParams.WRAP_CONTENT)
                window?.attributes?.windowAnimations = R.style.dialog_animation

                binding.titleText.setText(text)
                binding.actionOk.applyClickShrink()
                binding.actionOk.setOnClickListener {
                    dismiss()
                    onOkClick()   // ✅ callback here
                }

                if (!isShowing) show()
            }
        }

    }


    private fun isPermanentlyDenied(permission: String): Boolean {
        val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        return !shouldShow
    }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                requestCorePermissions()
            } else {
                config.setNotificationPermissionDenyCounter(config.notificationPermissionDenyCounter() + 1)
                requestCorePermissions()
            }
        }

    private val finalNotificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            goToMainActivity()
        }

    private fun checkAndRequestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (config.notificationPermissionDenyCounter() < 2) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
        }
        requestCorePermissions()
    }

    private fun requestCorePermissions() {
        val permissionsArray = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG
        )

        PermissionX.init(this@PermissionActivity)
            .permissions(*permissionsArray)
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@PermissionActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this@PermissionActivity, Manifest.permission.POST_NOTIFICATIONS)) {
                            finalNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            goToMainActivity()
                        }
                    } else {
                        goToMainActivity()
                    }
                } else {
                    val permanentlyDenied = deniedList.any { isPermanentlyDenied(it) }
                    if (permanentlyDenied) {
                        showContactPermissionSettingsDialog()
                    } else {
                        // Do nothing; allow user to click "Get Started" again.
                    }
                }
            }
    }


    private fun goToMainActivity() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(this, OverLayPermissionActivity::class.java))
            finish()
        } else {
            if (com.callsdk.permissions.helper.OEMPermissionHelper.isMi() && com.callsdk.CallSDKSettings.isAutoStartNeeded(this)) {
                val autoStart = com.callsdk.permissions.helper.XiaomiUtilities.isCustomPermissionGranted(this, com.callsdk.permissions.helper.XiaomiUtilities.OP_AUTO_START)
                val bgStart = com.callsdk.permissions.helper.XiaomiUtilities.isCustomPermissionGranted(this, com.callsdk.permissions.helper.XiaomiUtilities.OP_BACKGROUND_START_ACTIVITY)
                if (autoStart && bgStart) {
                    startActivity(Intent(this, getNextTargetAfterPermissions()))
                } else {
                    startActivity(Intent(this, com.contactsapp2025.universal.cdo.XiaomiPermissionActivity::class.java))
                }
            } else {
                startActivity(Intent(this, getNextTargetAfterPermissions()))
            }
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_APP_SETTINGS) {
            // Returned from app settings
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            ) {
                //handle phone result
              //  onPhoneCallResult(this@PermissionActivity,  true)


                goToMainActivity()
            } else {
                // User came back from settings but still didn't grant contacts permission
                showAppSettingsStillDeniedDialog()
            }
        }
    }

    // Removed showNotificationRationaleDialog

    // Removed showCorePermissionsRationaleDialog

    private fun showContactPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Contacts and Phone access are required for this app. Please enable the permissions in App Settings to continue.")
            .setPositiveButton("Settings") { dialog, _ ->
                openAppSettings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                //  finish() // User chose not to go to settings, exit.
            }
            .setCancelable(false)
            .create()
            .show()
    }

    private fun showAppSettingsStillDeniedDialog() {
        val contactsGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val phoneGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val messageDialog = when {
            !contactsGranted && !phoneGranted ->
                "Contacts and Phone access are required for this app. Please enable the permissions in App Settings to continue."

            !contactsGranted ->
                "Contacts are required for this app. Please enable the permissions in App Settings to continue."

            !phoneGranted ->
                "Phone access is required for this app. Please enable the permissions in App Settings to continue."

            else ->  "Contacts and Phone access are required for this app. Please enable the permissions in App Settings to continue."
        }

        AlertDialog.Builder(this)
            .setTitle("Permission Still Not Granted")
            .setMessage(messageDialog)
            .setPositiveButton("Settings Again") { dialog, _ ->
                openAppSettings()
                dialog.dismiss()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
                //finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = "package:$packageName".toUri()
        startActivityForResult(intent, REQUEST_CODE_APP_SETTINGS)
    }

    private fun setPrivacyText() {
        val fullText = getString(R.string.text_privacy_desc_2)
        val spannable = SpannableString(fullText)
        val clickableWord = "Privacy Policy"
        val startIndex = fullText.indexOf(clickableWord)
        val endIndex = startIndex + clickableWord.length
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent =
                    Intent(Intent.ACTION_VIEW, getString(R.string.privacy_policy_link).toUri())
                startActivity(intent)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@PermissionActivity, org.fossify.commons.R.color.md_blue)
                ds.isUnderlineText = true
            }
        }
        spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvPrivacydesc.text = spannable
        binding.tvPrivacydesc.movementMethod = LinkMovementMethod.getInstance()
        binding.tvPrivacydesc.highlightColor = Color.TRANSPARENT
    }
}
