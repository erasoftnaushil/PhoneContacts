package com.contactsapp2025.universal.dialogs

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.CompoundButtonCompat
import com.contactsapp2025.universal.databinding.DialogDisplayModeBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST

/** Grid / List chooser for the Favorites tab. [onApplied] runs only when the mode actually changed. */
class DisplayModeDialog(activity: Activity, onApplied: () -> Unit) {
    init {
        val binding = DialogDisplayModeBinding.inflate(activity.layoutInflater)
        val palette = HomeThemeHelper.getPalette(activity)
        binding.displayModeHolder.background = GradientDrawable().apply {
            cornerRadius = HomeThemeHelper.dpToPx(activity, 22f)
            setColor(palette.cardBg)
        }
        binding.displayModeTitle.setTextColor(palette.itemTitleText)
        val radioTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(palette.primaryBlue, palette.chevronTint)
        )
        listOf(binding.displayModeGrid, binding.displayModeList).forEach {
            it.setTextColor(palette.itemTitleText)
            CompoundButtonCompat.setButtonTintList(it, radioTint)
        }
        binding.displayModeCancel.setTextColor(palette.itemSubtitleText)
        binding.displayModeApply.setTextColor(palette.primaryBlue)

        val current = activity.config.favoritesDisplayMode
        binding.displayModeGroup.check(if (current == VIEW_TYPE_LIST) binding.displayModeList.id else binding.displayModeGrid.id)

        val dialog = AlertDialog.Builder(activity).setView(binding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.displayModeCancel.setOnClickListener { dialog.dismiss() }
        binding.displayModeApply.setOnClickListener {
            val selected = if (binding.displayModeGroup.checkedRadioButtonId == binding.displayModeList.id) VIEW_TYPE_LIST else VIEW_TYPE_GRID
            dialog.dismiss()
            if (selected != current) {
                activity.config.favoritesDisplayMode = selected
                onApplied()
            }
        }
        dialog.show()
    }
}
