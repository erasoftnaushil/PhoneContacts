package com.contactsapp2025.universal.receivers

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.helpers.AnalyticsHelper
import com.contactsapp2025.universal.helpers.ContactFeatureNotificationHelper
import com.contactsapp2025.universal.helpers.LOCATION_FAVORITES_TAB

class ContactFeatureActionActivity : Activity() {

    private val TAG = "ContactFeatureNotification"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Immediately dismiss the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ContactFeatureNotificationHelper.NOTIFICATION_ID)
        
        val index = intent?.getIntExtra("notification_index", -1) ?: -1
        val category = intent?.getStringExtra("notification_category") ?: "unknown"
        val count = intent?.getIntExtra("notification_count", 0) ?: 0
        val destinationClassName = intent?.getStringExtra("destination_class")

        Log.d(TAG, "Notification tapped and dismissed. Index: $index, Category: $category")

        val params = Bundle().apply {
            putInt("notification_index", index)
            putString("notification_category", category)
            putInt("notification_count", count)
        }
        AnalyticsHelper.logEvent(this, "contact_feature_notification_tapped", params)
        Log.d(TAG, "Firebase event logged: contact_feature_notification_tapped")


        // Launch SplashActivity with target destination extras
        try {
            val splashIntent = Intent(this, com.contactsapp2025.universal.activities.SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_feature_notification", true)
                putExtra("destination_class", destinationClassName)
                putExtra("notification_category", category)
                putExtra("notification_index", index)
                putExtra("notification_count", count)
            }
            startActivity(splashIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SplashActivity", e)
            val fallbackIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(fallbackIntent)
        }
        
        finish()
    }
}
