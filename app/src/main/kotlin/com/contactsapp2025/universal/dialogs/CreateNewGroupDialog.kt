package com.contactsapp2025.universal.dialogs

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.contactsapp2025.universal.databinding.DialogCreateNewGroupBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group

class CreateNewGroupDialog(val activity: BaseSimpleActivity, val callback: (newGroup: Group) -> Unit) {
    init {
        val binding = DialogCreateNewGroupBinding.inflate(activity.layoutInflater)
        val palette = HomeThemeHelper.getPalette(activity)

        binding.dialogHolder.background = HomeThemeHelper.createDialogCardBackground(activity, palette.isDark)
        binding.dialogTitle.setTextColor(palette.itemTitleText)
        binding.groupName.background = HomeThemeHelper.createDialogInputBackground(activity, palette.isDark)
        binding.groupName.setTextColor(palette.itemTitleText)
        binding.groupName.setHintTextColor(palette.searchHint)
        binding.btnCancel.setTextColor(palette.itemSubtitleText)
        binding.btnSave.setTextColor(palette.primaryBlue)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cursorDrawable: Drawable? = binding.groupName.textCursorDrawable
            if (cursorDrawable != null) {
                cursorDrawable.colorFilter = BlendModeColorFilter(palette.primaryBlue, BlendMode.SRC_ATOP)
                binding.groupName.textCursorDrawable = cursorDrawable
            }
        }

        val alertDialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        alertDialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }

        binding.btnCancel.setOnClickListener {
            activity.hideKeyboard(binding.groupName)
            alertDialog.dismiss()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.groupName.value.trim()
            if (name.isEmpty()) {
                activity.toast(org.fossify.commons.R.string.empty_name)
                return@setOnClickListener
            }

            val contactSources = ArrayList<ContactSource>()
            ContactsHelper(activity).getContactSources { sources ->
                sources.mapTo(contactSources) { ContactSource(it.name, it.type, it.publicName) }
                val preferredSource = contactSources.firstOrNull {
                    it.name == activity.config.lastUsedContactSource && it.type != SMT_PRIVATE
                } ?: contactSources.firstOrNull {
                    it.type != SMT_PRIVATE
                } ?: contactSources.firstOrNull() ?: ContactSource(SMT_PRIVATE, SMT_PRIVATE, SMT_PRIVATE)

                createGroupUnder(name, preferredSource, alertDialog)
            }
        }

        alertDialog.show()
        alertDialog.showKeyboard(binding.groupName)
    }

    private fun createGroupUnder(name: String, contactSource: ContactSource, dialog: AlertDialog) {
        ensureBackgroundThread {
            var newGroup = ContactsHelper(activity).createNewGroup(name, contactSource.name, contactSource.type)
            if (newGroup == null && contactSource.type != SMT_PRIVATE) {
                newGroup = ContactsHelper(activity).createNewGroup(name, SMT_PRIVATE, SMT_PRIVATE)
            }
            activity.runOnUiThread {
                if (newGroup != null) {
                    callback(newGroup)
                }
                dialog.dismiss()
            }
        }
    }
}
