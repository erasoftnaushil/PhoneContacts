package com.contactsapp2025.universal.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.fossify.commons.helpers.ensureBackgroundThread
import com.contactsapp2025.universal.extensions.checkAndBackupContactsOnBoot

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootCompletedReceiver", "onReceive called with action: ${intent.action}")
        com.contactsapp2025.universal.helpers.OnboardingNotificationEngine.handleDeviceRestart(context)
        com.contactsapp2025.universal.helpers.ContactFeatureNotificationEngine.handleDeviceRestart(context)
        ensureBackgroundThread {
            context.apply {
                Log.d("BootCompletedReceiver", "Calling checkAndBackupContactsOnBoot()")
                checkAndBackupContactsOnBoot()
            }
        }
    }
}
