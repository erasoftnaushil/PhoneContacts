package com.contactsapp2025.universal.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.AnalyticsHelper
import com.contactsapp2025.universal.helpers.OnboardingNotificationEngine

class OnboardingNotificationReceiver : BroadcastReceiver() {

    private val TAG = "OnboardingNotificationEngine"

    override fun onReceive(context: Context, intent: Intent?) {
        val config = context.config

        Log.e("ONBOARD_DEBUG", "onReceive: ALARM TRIGGERED! Woke up receiver.")
        Log.d(TAG, "Checking onboarding completion")
        if (!config.notificationEngineActive || config.isOnboardingCompleted) {
            Log.d(TAG, "Onboarding already completed or engine inactive. Cancelling alarms.")
            Log.e("ONBOARD_DEBUG", "onReceive: Aborted! engineActive=${config.notificationEngineActive}, isCompleted=${config.isOnboardingCompleted}")
            OnboardingNotificationEngine.cancelEngine(context)
            return
        }

        Log.d(TAG, "Onboarding incomplete")

        // Determine next stage
        val currentStage = config.notificationStage
        val stageName = when (currentStage) {
            0 -> "5m"
            1 -> "30m"
            else -> "1h"
        }

        val title: String
        val message: String

        when (currentStage) {
            0 -> {
                title = "Your Contacts Are Waiting \uD83D\uDCF1"
                message = "Complete your setup to make managing your contacts easier."
                config.notificationStage = 1
            }
            1 -> {
                title = "Make Your Contacts Smarter ✨"
                message = "Finish setting up your contact experience and explore what's waiting for you."
                config.notificationStage = 2
            }
            else -> {
                title = "One Step Away!"
                message = "Complete your setup and get the most out of your Contacts app."
            }
        }

        config.notificationCount += 1
        config.lastNotificationTime = System.currentTimeMillis()

        Log.d(TAG, "Sending ${stageName} notification")
        Log.e("ONBOARD_DEBUG", "onReceive: Preparing to show notification for stage=$stageName")

        // Schedule next notification (30m or 1h)
        OnboardingNotificationEngine.scheduleNextNotification(context)

        // Show notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "onboarding_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Onboarding Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val actionIntent = Intent(context, com.contactsapp2025.universal.activities.SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("notification_stage", stageName)
            putExtra("notification_count", config.notificationCount)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            10000,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // RemoteViews Limitation: Continuous scale animations are not supported natively in RemoteViews.
        // As per requirements ("No Foreground Service", "Do not use unsupported hacks"), the VIEW button
        // is implemented statically matching the design.
        val collapsedView = RemoteViews(context.packageName, R.layout.layout_notification_onboarding)
        collapsedView.setTextViewText(R.id.tv_title, title)
        collapsedView.setOnClickPendingIntent(R.id.btn_view, pendingIntent)

        val expandedView = RemoteViews(context.packageName, R.layout.layout_notification_onboarding_expanded)
        expandedView.setTextViewText(R.id.tv_title, title)
        expandedView.setTextViewText(R.id.tv_message, message)
        expandedView.setOnClickPendingIntent(R.id.btn_view, pendingIntent)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_noti_icon)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.e("ONBOARD_DEBUG", "onReceive: Attempting to notify OS...")
                    notificationManager.notify(9999, notification)
                    Log.e("ONBOARD_DEBUG", "onReceive: Notification successfully handed to OS!")
                    logSent(context, stageName, config.notificationCount)
                } else {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission. Cannot display notification.")
                    Log.e("ONBOARD_DEBUG", "onReceive: FAILED! App does not have POST_NOTIFICATIONS permission.")
                }
            } else {
                Log.e("ONBOARD_DEBUG", "onReceive: Attempting to notify OS (Pre-Android 13)...")
                notificationManager.notify(9999, notification)
                Log.e("ONBOARD_DEBUG", "onReceive: Notification successfully handed to OS (Pre-Android 13)!")
                logSent(context, stageName, config.notificationCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying notification", e)
            Log.e("ONBOARD_DEBUG", "onReceive: FATAL ERROR displaying notification!", e)
        }
    }

    private fun logSent(context: Context, stage: String, count: Int) {
        val params = Bundle().apply {
            putString("notification_stage", stage)
            putInt("notification_count", count)
        }
        AnalyticsHelper.logEvent(context, "onboarding_notification_sent", params)
        Log.d(TAG, "Firebase event logged: onboarding_notification_sent")
    }
}
