package com.contactsapp2025.universal.language

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale


class FunctionUtils {
    companion object {

        fun shareContent(context: Context, text: String) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(intent, "Share with"))
        }

        fun isUsageAccessGranted(context: Context): Boolean {
            val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager =
                context.getSystemService(AppCompatActivity.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName,
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun setLocale(activity: Activity, languageCode: String) {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val resources: Resources = activity.resources
            val config: Configuration = resources.configuration
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        fun Bitmap.getDominantColor(): Int {
            val newBitmap = Bitmap.createScaledBitmap(this, 1, 1, true)
            val color = newBitmap.getPixel(0, 0)
            newBitmap.recycle()
            return color
        }
    }
}
