package com.contactsapp2025.universal.activities

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.contactsapp2025.universal.adapters.EditFavoritesAdapter
import com.contactsapp2025.universal.databinding.ActivityEditFavoritesBinding
import com.contactsapp2025.universal.helpers.FavoritesHelper
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.models.contacts.Contact

/** "Edit favorites": remove favorites with the x badge, long-press to reorder, then Save. */
class EditFavoritesActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityEditFavoritesBinding::inflate)
    private var adapter: EditFavoritesAdapter? = null
    private var original: List<Contact> = emptyList()
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.editFavRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        binding.editFavList.layoutManager = GridLayoutManager(this, 4)
        binding.editFavBack.setOnClickListener { finish() }
        binding.editFavBar.barCancel.setOnClickListener { finish() }
        binding.editFavBar.barSave.setOnClickListener { save() }
        applyPalette()
        updateState()

        FavoritesHelper.load(this) { _, favorites ->
            original = favorites
            adapter = EditFavoritesAdapter(this, favorites.toMutableList()) { updateState() }.also {
                binding.editFavList.adapter = it
                it.createTouchHelper().attachToRecyclerView(binding.editFavList)
            }
            updateState()
        }
    }

    override fun onResume() {
        super.onResume()
        applyPalette()
    }

    private fun applyPalette() {
        val palette = HomeThemeHelper.getPalette(this)
        updateStatusbarColor(palette.pageBg)
        updateNavigationBarColor(palette.pageBg)
        window.decorView.setBackgroundColor(palette.pageBg)
        binding.editFavRoot.setBackgroundColor(palette.pageBg)
        binding.editFavTitle.setTextColor(palette.itemTitleText)
        binding.editFavHint.setTextColor(palette.itemSubtitleText)
        binding.editFavPlaceholder.setTextColor(palette.itemSubtitleText)
        ImageViewCompat.setImageTintList(binding.editFavBack, ColorStateList.valueOf(palette.itemTitleText))
        binding.editFavBarDivider.setBackgroundColor(palette.itemDivider)
        binding.editFavBar.barDivider.setBackgroundColor(palette.itemDivider)
        binding.editFavBar.barCancel.setTextColor(palette.itemTitleText)
        binding.editFavBar.barSave.setTextColor(palette.primaryBlue)
    }

    private fun current(): List<Contact> = adapter?.items ?: original

    private fun hasChanges() = current().map { it.id } != original.map { it.id }

    private fun updateState() {
        val enabled = hasChanges() && !isSaving
        binding.editFavBar.barSave.isEnabled = enabled
        binding.editFavBar.barSave.alpha = if (enabled) 1f else 0.4f
        binding.editFavPlaceholder.beVisibleIf(adapter != null && current().isEmpty())
        binding.editFavHint.beVisibleIf(current().isNotEmpty())
    }

    private fun save() {
        if (!hasChanges() || isSaving) {
            finish()
            return
        }
        isSaving = true
        updateState()
        val remaining = ArrayList(current())
        val remainingIds = remaining.map { it.id }.toSet()
        val removed = ArrayList(original.filter { !remainingIds.contains(it.id) })
        FavoritesHelper.saveOrder(this, remaining)
        if (removed.isEmpty()) {
            setResult(RESULT_OK)
            finish()
            return
        }
        ContactsHelper(this).updateFavorites(ArrayList(), removed) {
            setResult(RESULT_OK)
            finish()
        }
    }
}
