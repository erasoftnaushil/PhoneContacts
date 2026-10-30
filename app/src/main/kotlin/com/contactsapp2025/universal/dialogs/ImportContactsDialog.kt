package com.contactsapp2025.universal.dialogs

import androidx.appcompat.app.AlertDialog
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.databinding.DialogImportContactsBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.showContactSourcePicker
import com.contactsapp2025.universal.helpers.VcfImporter
import com.contactsapp2025.universal.helpers.VcfImporter.ImportResult.IMPORT_FAIL
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getPublicContactSource
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread

class ImportContactsDialog(
    val activity: SimpleActivity,
    val path: String,
    private val callback: (refreshView: Boolean) -> Unit,
    onPositiveClick: () -> Unit
) {
    private var targetContactSource = ""
    private var ignoreClicks = false

    init {
        val binding = DialogImportContactsBinding.inflate(activity.layoutInflater).apply {
            targetContactSource = activity.config.lastUsedContactSource
            activity.getPublicContactSource(targetContactSource) {
                importContactsTitle.setText(it)
                if (it.isEmpty()) {
                    ContactsHelper(activity).getContactSources {
                        val localSource = it.firstOrNull { it.name == SMT_PRIVATE }
                        if (localSource != null) {
                            targetContactSource = localSource.name
                            activity.runOnUiThread {
                                importContactsTitle.setText(localSource.publicName)
                            }
                        }
                    }
                }
            }

            importContactsTitle.setOnClickListener {
                activity.showContactSourcePicker(targetContactSource) {
                    targetContactSource = if (it == activity.getString(R.string.phone_storage_hidden)) SMT_PRIVATE else it
                    activity.getPublicContactSource(it) {
                        val title = if (it == "") activity.getString(R.string.phone_storage) else it
                        importContactsTitle.setText(title)
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.import_contacts) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        alertDialog.dismiss()
                        onPositiveClick.invoke()
                        if (ignoreClicks) {
                            return@setOnClickListener
                        }
                        ignoreClicks = true
                        activity.toast(org.fossify.commons.R.string.importing)
                        ensureBackgroundThread {
                            val result = VcfImporter(activity).importContacts(path, targetContactSource)
                            handleParseResult(result)
//                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }

    private fun handleParseResult(result: VcfImporter.ImportResult) {
        activity.toast(
            when (result) {
                VcfImporter.ImportResult.IMPORT_OK -> org.fossify.commons.R.string.importing_successful
                VcfImporter.ImportResult.IMPORT_PARTIAL -> org.fossify.commons.R.string.importing_some_entries_failed
                else -> org.fossify.commons.R.string.importing_failed
            }
        )
        callback(result != IMPORT_FAIL)
    }
}
