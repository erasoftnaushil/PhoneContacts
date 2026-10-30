package com.contactsapp2025.universal.dialogs

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import com.contactsapp2025.universal.databinding.DialogContactActionConfirmBinding
import com.contactsapp2025.universal.helpers.HomeThemeHelper

class ContactActionConfirmDialog(
    val activity: Activity,
    title: String,
    message: String,
    positiveText: String,
    val onConfirm: () -> Unit
) {
    init {
        val binding = DialogContactActionConfirmBinding.inflate(activity.layoutInflater)
        val palette = HomeThemeHelper.getPalette(activity)

        val cardBgColor = if (palette.isDark) Color.parseColor("#1C1F2B") else Color.parseColor("#FFFFFF")
        binding.dialogConfirmHolder.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = HomeThemeHelper.dpToPx(activity, 22f)
            setColor(cardBgColor)
        }

        binding.dialogConfirmTitle.text = title
        binding.dialogConfirmTitle.setTextColor(palette.itemTitleText)

        binding.dialogConfirmMessage.text = message
        binding.dialogConfirmMessage.setTextColor(palette.itemSubtitleText)

        binding.btnConfirmCancel.setTextColor(palette.itemSubtitleText)
        binding.btnConfirmPositive.text = positiveText
        binding.btnConfirmPositive.setTextColor(palette.primaryBlue)

        val alertDialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.btnConfirmCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        binding.btnConfirmPositive.setOnClickListener {
            alertDialog.dismiss()
            onConfirm()
        }

        alertDialog.show()
    }
}
