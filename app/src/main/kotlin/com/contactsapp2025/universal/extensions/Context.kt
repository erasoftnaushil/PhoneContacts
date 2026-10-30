package com.contactsapp2025.universal.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.FileProvider
import com.contactsapp2025.universal.BuildConfig
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.ScanningActivity
import com.contactsapp2025.universal.helpers.*
import com.contactsapp2025.universal.helpers.VcfExporter
import com.contactsapp2025.universal.receivers.AutomaticBackupReceiver

import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.contacts.Contact
import org.joda.time.DateTime
import java.io.File
import java.io.FileOutputStream

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getNextTargetAfterPermissions(): Class<*> {
    return if (isNetworkAvailable() && !config.hasReachedMainActivity) {
        ScanningActivity::class.java
    } else {
        MainActivity::class.java
    }
}

fun Context.getCachePhotoUri(file: File = getCachePhoto()) = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", file)


fun isDarkModeEnabled(context: Context): Boolean {
    val currentNightMode =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == Configuration.UI_MODE_NIGHT_YES
}

fun Activity.hideNavigationBar() {
    try {
        window.decorView.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.hide(WindowInsets.Type.navigationBars())
                    window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.navigationBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Activity.showNavigationBar() {
    try {
        window.decorView.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.show(WindowInsets.Type.navigationBars())
                }
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    show(WindowInsetsCompat.Type.navigationBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@SuppressLint("UseCompatLoadingForDrawables")
fun Context.getPackageDrawable(packageName: String): Drawable {
    return resources.getDrawable(
        when (packageName) {
            TELEGRAM_PACKAGE -> R.drawable.ic_telegram_rect_vector
            SIGNAL_PACKAGE -> R.drawable.ic_signal_rect_vector
            WHATSAPP_PACKAGE -> R.drawable.ic_whatsapp_rect_vector
            VIBER_PACKAGE -> R.drawable.ic_viber_rect_vector
            else -> R.drawable.ic_threema_rect_vector
        }, theme
    )
}

fun Context.getAutomaticBackupIntent(): PendingIntent {
    val intent = Intent(this, AutomaticBackupReceiver::class.java)
    return PendingIntent.getBroadcast(this, AUTOMATIC_BACKUP_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.scheduleNextAutomaticBackup() {
    if (config.autoBackup) {
        val nextBackupTime = getNextAutoBackupTime()
        val backupAtMillis = nextBackupTime.millis
        val pendingIntent = getAutomaticBackupIntent()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            Log.d("AutoBackup", "Scheduling next automatic backup for: $nextBackupTime ($backupAtMillis ms)")
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, backupAtMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e("AutoBackup", "Failed to schedule AlarmManager alarm", e)
            showErrorToast(e)
        }
    } else {
        Log.d("AutoBackup", "scheduleNextAutomaticBackup called but config.autoBackup is false. Skipping.")
    }
}

fun Context.cancelScheduledAutomaticBackup() {
    Log.d("AutoBackup", "Cancelling scheduled automatic backup alarm")
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(getAutomaticBackupIntent())
}

fun Context.checkAndBackupContactsOnBoot() {
    if (config.autoBackup) {
        val previousRealBackupTime = config.lastAutoBackupTime
        val previousScheduledBackupTime = getPreviousAutoBackupTime().millis
        val missedPreviousBackup = previousRealBackupTime < previousScheduledBackupTime
        Log.d("AutoBackup", "Boot check: previousRealBackupTime=$previousRealBackupTime, previousScheduledBackupTime=$previousScheduledBackupTime, missedPreviousBackup=$missedPreviousBackup")
        if (missedPreviousBackup) {
            // device was probably off at the scheduled time so backup now
            Log.d("AutoBackup", "Missed scheduled backup detected on boot! Initiating backup now...")
            backupContacts()
        }
    }
}

fun Context.backupContacts() {
    require(isRPlus())
    Log.d("AutoBackup", "backupContacts() initiated on background thread")
    ensureBackgroundThread {
        val config = config
        Log.d("AutoBackup", "Fetching contacts to export for sources: ${config.autoBackupContactSources}")
        ContactsHelper(this).getContactsToExport(selectedContactSources = config.autoBackupContactSources) { contactsToBackup ->
            if (contactsToBackup.isEmpty()) {
                Log.w("AutoBackup", "No contacts found to export for selected sources.")
                toast(org.fossify.commons.R.string.no_entries_for_exporting)
                config.lastAutoBackupTime = DateTime.now().millis
                scheduleNextAutomaticBackup()
                return@getContactsToExport
            }

            Log.d("AutoBackup", "Retrieved ${contactsToBackup.size} contacts to export")

            val now = DateTime.now()
            val year = now.year.toString()
            val month = now.monthOfYear.ensureTwoDigits()
            val day = now.dayOfMonth.ensureTwoDigits()
            val hours = now.hourOfDay.ensureTwoDigits()
            val minutes = now.minuteOfHour.ensureTwoDigits()
            val seconds = now.secondOfMinute.ensureTwoDigits()

            val filename = config.autoBackupFilename
                .replace("%Y", year, false)
                .replace("%M", month, false)
                .replace("%D", day, false)
                .replace("%h", hours, false)
                .replace("%m", minutes, false)
                .replace("%s", seconds, false)

            val outputFolder = File(config.autoBackupFolder).apply {
                mkdirs()
            }

            var exportFile = File(outputFolder, "$filename.vcf")
            var exportFilePath = exportFile.absolutePath
            Log.d("AutoBackup", "Target export path: $exportFilePath")

            val outputStream = try {
                if (hasProperStoredFirstParentUri(exportFilePath)) {
                    Log.d("AutoBackup", "Using SAF URI for output file: $exportFilePath")
                    val exportFileUri = createDocumentUriUsingFirstParentTreeUri(exportFilePath)
                    if (!getDoesFilePathExist(exportFilePath)) {
                        createSAFFileSdk30(exportFilePath)
                    }
                    applicationContext.contentResolver.openOutputStream(exportFileUri, "wt") ?: FileOutputStream(exportFile)
                } else {
                    Log.d("AutoBackup", "Using regular FileOutputStream for output file: $exportFilePath")
                    var num = 0
                    while (getDoesFilePathExist(exportFilePath) && !exportFile.canWrite()) {
                        num++
                        exportFile = File(outputFolder, "${filename}_${num}.vcf")
                        exportFilePath = exportFile.absolutePath
                    }
                    FileOutputStream(exportFile)
                }
            } catch (e: Exception) {
                Log.e("AutoBackup", "Error opening output stream for backup file: $exportFilePath", e)
                showErrorToast(e)
                scheduleNextAutomaticBackup()
                return@getContactsToExport
            }

            Log.d("AutoBackup", "Starting VcfExporter.exportContacts()...")
            VcfExporter().exportContacts(
                context = this,
                outputStream = outputStream,
                contacts = contactsToBackup.toMutableList() as ArrayList<Contact>,
                showExportingToast = false
            ) { exportResult ->
                if (exportResult == VcfExporter.ExportResult.EXPORT_FAIL) {
                    Log.e("AutoBackup", "VCF export FAILED!")
                    toast(org.fossify.commons.R.string.exporting_failed)
                } else {
                    Log.d("AutoBackup", "VCF export SUCCESS! File saved: $exportFilePath")
                }
            }

            config.lastAutoBackupTime = DateTime.now().millis
            Log.d("AutoBackup", "Updated lastAutoBackupTime: ${config.lastAutoBackupTime}. Re-scheduling next automatic backup...")
            scheduleNextAutomaticBackup()
        }
    }
}



