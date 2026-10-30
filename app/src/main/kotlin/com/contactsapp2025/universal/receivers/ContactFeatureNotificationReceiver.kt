package com.contactsapp2025.universal.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.AnalyticsHelper
import com.contactsapp2025.universal.helpers.ContactFeatureNotificationEngine
import com.contactsapp2025.universal.helpers.ContactFeatureNotificationHelper
import kotlin.random.Random

class ContactFeatureNotificationReceiver : BroadcastReceiver() {

    private val TAG = "ContactFeatureNotification"

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Alarm received")
        
        val config = context.config
        if (!config.contactFeatureNotificationEnabled) {
            Log.d(TAG, "Notification engine is disabled. Ignoring alarm.")
            return
        }

        // Randomize notification, preventing consecutive duplicate
        val notifications = ContactFeatureNotificationHelper.notifications
        val lastIndex = config.lastContactFeatureNotificationIndex
        var nextIndex = Random.nextInt(notifications.size)
        while (nextIndex == lastIndex && notifications.size > 1) {
            nextIndex = Random.nextInt(notifications.size)
        }
        
        config.lastContactFeatureNotificationIndex = nextIndex
        val content = notifications[nextIndex]
        
        Log.d(TAG, "Selected notification index: $nextIndex")
        Log.d(TAG, "Selected category: ${content.category}")
        
        config.contactFeatureNotificationCount += 1
        val currentCount = config.contactFeatureNotificationCount
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = ContactFeatureNotificationHelper.buildNotification(context, content, nextIndex, currentCount)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(ContactFeatureNotificationHelper.NOTIFICATION_ID, notification)
                    logSent(context, nextIndex, content.category, currentCount)
                    Log.d(TAG, "Notification posted")
                } else {
                    Log.w(TAG, "Notification permission unavailable")
                }
            } else {
                notificationManager.notify(ContactFeatureNotificationHelper.NOTIFICATION_ID, notification)
                logSent(context, nextIndex, content.category, currentCount)
                Log.d(TAG, "Notification posted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying notification", e)
        }

        config.lastContactFeatureNotificationTime = System.currentTimeMillis()
        
        // Schedule next 3-hour alarm
        ContactFeatureNotificationEngine.scheduleNext(context)
    }

    private fun logSent(context: Context, index: Int, category: String, count: Int) {
        val params = Bundle().apply {
            putInt("notification_index", index)
            putString("notification_category", category)
            putInt("notification_count", count)
        }
        AnalyticsHelper.logEvent(context, "contact_feature_notification_sent", params)
        Log.d(TAG, "Firebase event logged: contact_feature_notification_sent")
    }
}
