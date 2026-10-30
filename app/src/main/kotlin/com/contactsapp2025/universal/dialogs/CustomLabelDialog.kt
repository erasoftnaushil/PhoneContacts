package com.contactsapp2025.universal.dialogs

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import com.contactsapp2025.universal.databinding.DialogCustomLabelBinding
import org.fossify.commons.R

class CustomLabelDialog(val activity: BaseSimpleActivity, val callback: (label: String) -> Unit) {
    init {
        val binding = DialogCustomLabelBinding.inflate(activity.layoutInflater)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.label) { alertDialog ->
                    alertDialog.showKeyboard(binding.customLabelEdittext)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val cursorDrawable: Drawable? = binding.customLabelEdittext.textCursorDrawable
                        if (cursorDrawable != null) {
                            cursorDrawable.colorFilter = BlendModeColorFilter(ContextCompat.getColor(activity, R.color.black), BlendMode.SRC_ATOP)
                            binding.customLabelEdittext.textCursorDrawable = cursorDrawable
                        }
                    }
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val label = binding.customLabelEdittext.value
                        if (label.isEmpty()) {
                            activity.toast(org.fossify.commons.R.string.empty_name)
                            return@setOnClickListener
                        }
                        callback(label)
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
