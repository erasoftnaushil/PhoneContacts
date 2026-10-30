package com.contactsapp2025.universal.dialogs

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import com.contactsapp2025.universal.databinding.DialogFavoritesOptionsBinding
import com.contactsapp2025.universal.helpers.HomeThemeHelper

/** "Options" card shown from the 3-dot menu on the Favorites tab. */
class FavoritesOptionsDialog(
    activity: Activity,
    onEditFavorites: () -> Unit,
    onDisplayMode: () -> Unit,
    onSettings: () -> Unit
) {
    init {
        val binding = DialogFavoritesOptionsBinding.inflate(activity.layoutInflater)
        val palette = HomeThemeHelper.getPalette(activity)
        binding.optionsHolder.background = GradientDrawable().apply {
            cornerRadius = HomeThemeHelper.dpToPx(activity, 22f)
            setColor(palette.cardBg)
        }
        binding.optionsTitle.setTextColor(palette.itemTitleText)
        listOf(binding.optionsEditFavorites, binding.optionsDisplayMode, binding.optionsSettings).forEach {
            it.setTextColor(palette.itemTitleText)
        }
        binding.optionsCancel.setTextColor(palette.primaryBlue)

        val dialog = AlertDialog.Builder(activity).setView(binding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.optionsEditFavorites.setOnClickListener { dialog.dismiss(); onEditFavorites() }
        binding.optionsDisplayMode.setOnClickListener { dialog.dismiss(); onDisplayMode() }
        binding.optionsSettings.setOnClickListener { dialog.dismiss(); onSettings() }
        binding.optionsCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
