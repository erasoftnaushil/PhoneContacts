package com.contactsapp2025.universal.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.contactsapp2025.universal.extensions.backupContacts

class AutomaticBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AutoBackup", "AutomaticBackupReceiver onReceive triggered with action: ${intent.action}! Acquiring WakeLock and starting backup...")
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "simplecontacts:automaticbackupreceiver")
        wakelock.acquire(3000)
        context.backupContacts()
    }
}
