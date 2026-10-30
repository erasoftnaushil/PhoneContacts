package com.contactsapp2025.universal.dialogs

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.contactsapp2025.universal.databinding.DialogRenameGroupBinding
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Group

class RenameGroupDialog(val activity: BaseSimpleActivity, val group: Group, val callback: () -> Unit) {
    init {
        val binding = DialogRenameGroupBinding.inflate(activity.layoutInflater).apply {
            renameGroupTitle.setText(group.title)
            renameGroupTitle.setSelection(group.title.length)
        }
        val palette = HomeThemeHelper.getPalette(activity)

        binding.renameGroupHolder.background = HomeThemeHelper.createDialogCardBackground(activity, palette.isDark)
        binding.dialogTitle.setTextColor(palette.itemTitleText)
        binding.renameGroupTitle.background = HomeThemeHelper.createDialogInputBackground(activity, palette.isDark)
        binding.renameGroupTitle.setTextColor(palette.itemTitleText)
        binding.renameGroupTitle.setHintTextColor(palette.searchHint)
        binding.btnCancel.setTextColor(palette.itemSubtitleText)
        binding.btnSave.setTextColor(palette.primaryBlue)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cursorDrawable: Drawable? = binding.renameGroupTitle.textCursorDrawable
            if (cursorDrawable != null) {
                cursorDrawable.colorFilter = BlendModeColorFilter(palette.primaryBlue, BlendMode.SRC_ATOP)
                binding.renameGroupTitle.textCursorDrawable = cursorDrawable
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
            activity.hideKeyboard(binding.renameGroupTitle)
            alertDialog.dismiss()
        }

        binding.btnSave.setOnClickListener {
            val newTitle = binding.renameGroupTitle.value.trim()
            if (newTitle.isEmpty()) {
                activity.toast(org.fossify.commons.R.string.empty_name)
                return@setOnClickListener
            }

            if (!newTitle.isAValidFilename()) {
                activity.toast(org.fossify.commons.R.string.invalid_name)
                return@setOnClickListener
            }

            group.title = newTitle
            group.contactsCount = 0
            ensureBackgroundThread {
                if (group.isPrivateSecretGroup()) {
                    activity.groupsDB.insertOrUpdate(group)
                } else {
                    ContactsHelper(activity).renameGroup(group)
                }
                activity.runOnUiThread {
                    callback()
                    alertDialog.dismiss()
                }
            }
        }

        alertDialog.show()
        alertDialog.showKeyboard(binding.renameGroupTitle)
    }
}
