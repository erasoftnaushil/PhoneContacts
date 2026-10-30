package com.contactsapp2025.universal.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationManagerCompat
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.receivers.ContactFeatureNotificationReceiver

object ContactFeatureNotificationEngine {

    private const val TAG = "ContactFeatureNotification"
    private const val ALARM_REQUEST_CODE = 8888
    private const val DELAY_MILLIS = 3 * 60 * 60 * 1000L // 3 hours
    //private const val DELAY_MILLIS = 3 * 1000L // 3 hours

    fun start(context: Context) {
        val config = context.config
        if (!config.contactFeatureNotificationEnabled) {
            Log.d(TAG, "Notification engine disabled.")
            return
        }

        if (config.contactFeatureNotificationStarted && isAlarmScheduled(context)) {
            Log.d(TAG, "Notification engine already started and alarm scheduled.")
            return
        }

        Log.d(TAG, "Notification engine started")
        config.contactFeatureNotificationStarted = true
        
        // Don't reset count/index, just ensure it's scheduled
        scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        val config = context.config
        if (!config.contactFeatureNotificationEnabled) {
            Log.d(TAG, "Notification engine disabled. Aborting schedule.")
            stop(context)
            return
        }

        val triggerAtMillis = System.currentTimeMillis() + DELAY_MILLIS
        config.nextContactFeatureNotificationTime = triggerAtMillis

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ContactFeatureNotificationReceiver::class.java)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Use setAndAllowWhileIdle to allow scheduling during Doze
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            Log.d(TAG, "Next notification scheduled for: $triggerAtMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    fun stop(context: Context) {
        Log.d(TAG, "Notification engine stopped")
        val config = context.config
        config.contactFeatureNotificationStarted = false
        config.nextContactFeatureNotificationTime = 0L

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ContactFeatureNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(ContactFeatureNotificationHelper.NOTIFICATION_ID)
    }

    fun handleDeviceRestart(context: Context) {
        val config = context.config
        if (!config.contactFeatureNotificationEnabled || !config.contactFeatureNotificationStarted) {
            return
        }
        
        Log.d(TAG, "Device reboot detected. Restoring schedule.")
        
        // Restore based on next saved time, or just re-schedule 3 hours from now if missed
        val now = System.currentTimeMillis()
        var nextTime = config.nextContactFeatureNotificationTime
        
        if (nextTime <= now) {
            Log.d(TAG, "Missed alarm during reboot, scheduling now + 3h")
            scheduleNext(context)
        } else {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ContactFeatureNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                Log.d(TAG, "Next notification schedule restored for: $nextTime")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore scheduled alarm", e)
                scheduleNext(context) // fallback
            }
        }
    }

    private fun isAlarmScheduled(context: Context): Boolean {
        val intent = Intent(context, ContactFeatureNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return pendingIntent != null
    }
}
