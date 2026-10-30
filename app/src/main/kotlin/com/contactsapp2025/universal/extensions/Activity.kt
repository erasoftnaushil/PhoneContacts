package com.contactsapp2025.universal.extensions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.*
import com.contactsapp2025.universal.BuildConfig
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.EditContactActivity
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.activities.ViewContactActivity
import com.contactsapp2025.universal.adapters.CallLogItem
import com.contactsapp2025.universal.dialogs.ImportContactsDialog
import com.contactsapp2025.universal.helpers.DEFAULT_FILE_NAME
import com.contactsapp2025.universal.helpers.KEY_NAME
import com.contactsapp2025.universal.helpers.VcfExporter
import com.contactsapp2025.universal.language.LanguageActivity
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import java.io.FileOutputStream


private var lastClickTime = 0L
private const val CLICK_INTERVAL = 600L
fun MenuItem.setSingleClickListener(action: () -> Unit) {
    setOnMenuItemClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > CLICK_INTERVAL) {
            lastClickTime = currentTime
            action()
        }
        true
    }
}

fun Activity.applySystemBarsForTheme(edgeToEdge: Boolean = true) {
    val window = window
    if (edgeToEdge) WindowCompat.setDecorFitsSystemWindows(window, false)

    val isDark =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // Light icons = false; Dark icons = true
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.isAppearanceLightStatusBars = !isDark
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        controller.isAppearanceLightNavigationBars = !isDark
    }

    // Colors (tweak to your palette)
    if (edgeToEdge) {
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.navigationBarColor = Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false
        } else {
            window.navigationBarColor = if (isDark) Color.BLACK else Color.WHITE
        }
    } else {
        window.statusBarColor = if (isDark) Color.BLACK else Color.WHITE
        window.navigationBarColor = if (isDark) Color.BLACK else Color.WHITE
    }
}


//fun Activity.setActivity(view: View) {
//    WindowCompat.getInsetsController(window, window.decorView).apply {
//        isAppearanceLightStatusBars = false
//        isAppearanceLightNavigationBars = false
//    }
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) { // Android 15+
//        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.updatePadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
//            v.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
//            insets
//        }
//    } else {
//        // For Android 14 and below
//        val color = ContextCompat.getColor(this, com.demo.aftercall.R.color.blackFinal)
//        window.statusBarColor = color
//        window.navigationBarColor = color
//    }
//}

fun SimpleActivity.startCallIntent(recipient: String, onCallStarted: (() -> Unit)? = null) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            launchActivityIntent(this)
            onCallStarted?.invoke()
        }
    }
}

fun SimpleActivity.showContactSourcePicker(currentSource: String, callback: (newSource: String) -> Unit) {
    ContactsHelper(this).getSaveableContactSources { sources ->
        val items = ArrayList<RadioItem>()
        var sourceNames = sources.map { it.name }
        var currentSourceIndex = sourceNames.indexOfFirst { it == currentSource }
        sourceNames = sources.map { it.publicName }

        sourceNames.forEachIndexed { index, account ->
            items.add(RadioItem(index, account))
            if (currentSource == SMT_PRIVATE && account == getString(R.string.phone_storage_hidden)) {
                currentSourceIndex = index
            }
        }

        runOnUiThread {
            RadioGroupDialog(this, items, currentSourceIndex) {
                callback(sources[it as Int].name)
            }
        }
    }
}

fun BaseSimpleActivity.shareContacts(contacts: ArrayList<Contact>) {
    val filename = if (contacts.size == 1) {
        "${contacts.first().getNameToDisplay()}.vcf"
    } else {
        DEFAULT_FILE_NAME
    }

    val file = getTempFile(filename)
    if (file == null) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    getFileOutputStream(file.toFileDirItem(this), true) {
        VcfExporter().exportContacts(
            context = this,
            outputStream = it,
            contacts = contacts,
            showExportingToast = false
        ) {
            if (it == VcfExporter.ExportResult.EXPORT_OK) {
                sharePathIntent(file.absolutePath, BuildConfig.APPLICATION_ID)
            } else {
                showErrorToast("$it")
            }
        }
    }
}

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> callContact(contact)
        ON_CLICK_VIEW_CONTACT -> viewContact(contact,false)
        ON_CLICK_EDIT_CONTACT -> editContact(contact, config.mergeDuplicateContacts)
    }
}

fun SimpleActivity.callContact(contact: Contact) {
    hideKeyboard()
    if (contact.phoneNumbers.isNotEmpty()) {
        tryInitiateCall(contact) { startCallIntent(it) }
    } else {
        toast(org.fossify.commons.R.string.no_phone_number_found)
    }
}

/*
fun Activity.viewContact(contact: Contact, fromCDO: Boolean) {
    hideKeyboard()

    if (fromCDO) {
        // First create/bring MainActivity into the task
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(this)
        }

        // Then open ViewContactActivity on top of MainActivity
        Intent(this, ViewContactActivity::class.java).apply {
            putExtra(CONTACT_ID, contact.id)
            putExtra(IS_PRIVATE, contact.isPrivate())
            putExtra("fromCDO", true)
            startActivity(this)
        }
    } else {
        Intent(this, ViewContactActivity::class.java).apply {
            putExtra(CONTACT_ID, contact.id)
            putExtra(IS_PRIVATE, contact.isPrivate())
            putExtra("fromCDO", false)
            startActivity(this)
        }
    }
}
*/

fun Activity.viewContact(contact: Contact,fromCDO : Boolean) {
    hideKeyboard()
    //finishAndRemoveTask()
    Intent(applicationContext, ViewContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        putExtra("fromCDO",fromCDO)
        //addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
//        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
//            Intent.FLAG_ACTIVITY_CLEAR_TASK or
//            Intent.FLAG_ACTIVITY_CLEAR_TOP or
//            Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(this)
    }
}

fun Activity.viewCallLogDetail(callLogItem: CallLogItem) {
    hideKeyboard()
    if (callLogItem.matchedContact != null) {
        Intent(applicationContext, ViewContactActivity::class.java).apply {
            putExtra(CONTACT_ID, callLogItem.matchedContact.id)
            putExtra(IS_PRIVATE, callLogItem.matchedContact.isPrivate())
            putExtra(KEY_PHONE, callLogItem.phoneNumber)
            putExtra(KEY_NAME, callLogItem.name)
            putExtra("fromCDO", false)
            putExtra("fromCallHistory", true)
            startActivity(this)
        }
    } else {
        Intent(applicationContext, ViewContactActivity::class.java).apply {
            putExtra(KEY_PHONE, callLogItem.phoneNumber)
            putExtra(KEY_NAME, callLogItem.name)
            putExtra("fromCDO", false)
            putExtra("fromCallHistory", true)
            startActivity(this)
        }
    }
}

fun Activity.editContact(contact: Contact, isMergedDuplicate: Boolean) {
    if (!isMergedDuplicate || contact.id == 0) {
        editContact(contact)
    } else {
        ContactsHelper(this).getContactSources { contactSources ->
            getDuplicateContacts(contact, true) { contacts ->
                if (contacts.size == 1) {
                    runOnUiThread {
                        editContact(contacts.first())
                    }
                } else {
                    val items = ArrayList(contacts.mapIndexed { index, contact ->
                        var source = getPublicContactSourceSync(contact.source, contactSources)
                        if (source == "") {
                            source = getString(R.string.phone_storage)
                        }
                        RadioItem(index, source, contact)
                    }.sortedBy { it.title })

                    runOnUiThread {
                        RadioGroupDialog(
                            activity = this,
                            items = items,
                            titleId = R.string.select_account,
                        ) {
                            editContact(it as Contact)
                        }
                    }
                }
            }
        }
    }
}

fun Activity.getDuplicateContacts(contact: Contact, includeCurrent: Boolean, callback: (duplicateContacts: ArrayList<Contact>) -> Unit) {
    val duplicateContacts = ArrayList<Contact>()
    if (includeCurrent) {
        duplicateContacts.add(contact)
    }
    ContactsHelper(this).getDuplicatesOfContact(contact, false) { contacts ->
        ensureBackgroundThread {
            val displayContactSources = getVisibleContactSources()
            contacts.filter { displayContactSources.contains(it.source) }.forEach {
                val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                if (duplicate != null) {
                    duplicateContacts.add(duplicate)
                }
            }
            callback(duplicateContacts)
        }
    }
}

fun Activity.editContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, EditContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        if (contact.id == 0) {
            action = Intent.ACTION_INSERT
            contact.phoneNumbers.firstOrNull()?.value?.takeIf { it.isNotEmpty() }?.let {
                putExtra(KEY_PHONE, it)
            }
            if (contact.firstName.isNotEmpty()) {
                putExtra(KEY_NAME, contact.firstName)
            }
        }
        startActivity(this)
    }
}

fun SimpleActivity.tryImportContactsFromFile(uri: Uri, callback: (Boolean) -> Unit, onPositiveClick: () -> Unit) {
    when (uri.scheme) {
        "file" -> showImportContactsDialog(uri.path!!, callback) {
            onPositiveClick()
        }

        "content" -> {
            val tempFile = getTempFile()
            if (tempFile == null) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return
            }

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val out = FileOutputStream(tempFile)
                inputStream!!.copyTo(out)
                showImportContactsDialog(tempFile.absolutePath, callback) {
                    onPositiveClick()
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

        else -> toast(org.fossify.commons.R.string.invalid_file_format)
    }
}

fun SimpleActivity.showImportContactsDialog(path: String, callback: (Boolean) -> Unit, onPositiveClick: () -> Unit) {
    ImportContactsDialog(this, path, callback) {
        onPositiveClick()
    }
}
