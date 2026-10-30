package com.contactsapp2025.universal.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationManagerCompat
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.receivers.OnboardingNotificationReceiver

object OnboardingNotificationEngine {

    private const val TAG = "OnboardingNotificationEngine"
    private const val ALARM_REQUEST_CODE = 9999
    const val NOTIFICATION_ID = 9999

    const val STAGE_5M = 1
    const val STAGE_30M = 2
    const val STAGE_1H = 3

    fun startOnboarding(context: Context) {
        val config = context.config
        if (config.isOnboardingCompleted) {
            Log.d(TAG, "Onboarding already completed, skipping engine start.")
            Log.e("ONBOARD_DEBUG", "startOnboarding: Skipped because onboarding is already marked as completed.")
            return
        }

        if (config.notificationEngineActive) {
            Log.d(TAG, "Notification engine already active.")
            Log.e("ONBOARD_DEBUG", "startOnboarding: Skipped because engine is already active.")
            return
        }

        Log.d(TAG, "Onboarding started")
        Log.e("ONBOARD_DEBUG", "startOnboarding: Engine activated for the first time.")
        config.onboardingStartedAt = System.currentTimeMillis()
        config.notificationEngineActive = true
        config.notificationStage = 0
        config.notificationCount = 0

        val params = Bundle().apply {
            putString("entry_point", "first_launch")
        }
        AnalyticsHelper.logEvent(context, "onboarding_started", params)
        Log.d(TAG, "Firebase event logged: onboarding_started")

        scheduleNextNotification(context)
    }

    fun scheduleNextNotification(context: Context) {
        val config = context.config

        if (!config.notificationEngineActive || config.isOnboardingCompleted) {
            Log.d(TAG, "Engine inactive or onboarding completed. Aborting schedule.")
            Log.e("ONBOARD_DEBUG", "scheduleNextNotification: Aborted! engineActive=${config.notificationEngineActive}, isCompleted=${config.isOnboardingCompleted}")
            cancelEngine(context)
            return
        }

        val delayMillis = when (config.notificationStage) {
            0 -> 5 * 60 * 1000L // 5 seconds (testing)
            1 -> 10 * 60 * 1000L // 10 seconds (testing)
            else -> 15 * 60 * 1000L // 15 seconds (testing)
        }

        val nextStage = if (config.notificationStage == 0) STAGE_5M else if (config.notificationStage == 1) STAGE_30M else STAGE_1H
        val triggerAtMillis = System.currentTimeMillis() + delayMillis

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, OnboardingNotificationReceiver::class.java)
        
        // Use FLAG_UPDATE_CURRENT to ensure only one next onboarding notification alarm exists.
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            ALARM_REQUEST_CODE, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            
            val logStage = when (nextStage) {
                STAGE_5M -> "5m"
                STAGE_30M -> "30m"
                else -> "1h"
            }
            Log.d(TAG, "First/Next notification scheduled for: $delayMillis ms from now (Stage $logStage).")
            Log.e("ONBOARD_DEBUG", "scheduleNextNotification: SUCCESS! Alarm set to trigger in ${delayMillis / 1000} seconds (Stage: $logStage).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
            Log.e("ONBOARD_DEBUG", "scheduleNextNotification: FAILED to schedule AlarmManager!", e)
        }
    }

    fun handleDeviceRestart(context: Context) {
        val config = context.config
        if (!config.notificationEngineActive || config.isOnboardingCompleted) {
            return
        }
        Log.d(TAG, "Device restarted, restoring notification engine.")
        scheduleNextNotification(context)
    }

    fun resumeOnboarding(context: Context) {
        Log.d(TAG, "Onboarding resumed from notification")
        val params = Bundle().apply {
            putString("entry_point", "notification")
        }
        AnalyticsHelper.logEvent(context, "onboarding_resumed", params)
    }

    fun completeOnboarding(context: Context, source: String = "app") {
        val config = context.config
        if (config.isOnboardingCompleted) {
            return
        }

        Log.d(TAG, "Onboarding completed")
        config.isOnboardingCompleted = true

        val completionTimeSec = (System.currentTimeMillis() - config.onboardingStartedAt) / 1000
        val params = Bundle().apply {
            putLong("completion_time_sec", completionTimeSec)
            putString("completion_source", source)
        }
        AnalyticsHelper.logEvent(context, "onboarding_completed", params)

        cancelEngine(context)
    }

    fun cancelEngine(context: Context) {
        Log.d(TAG, "All onboarding alarms cancelled")
        val config = context.config
        config.notificationEngineActive = false

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, OnboardingNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            ALARM_REQUEST_CODE, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
