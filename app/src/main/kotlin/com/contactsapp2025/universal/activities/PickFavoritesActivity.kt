package com.contactsapp2025.universal.activities

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.adapters.PickContactAdapter
import com.contactsapp2025.universal.adapters.PickListItem
import com.contactsapp2025.universal.databinding.ActivityPickFavoritesBinding
import com.contactsapp2025.universal.helpers.FavoritesHelper
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.models.contacts.Contact

/** "Pick contact" screen: choose which contacts are favorites, saved with the bottom Save button. */
class PickFavoritesActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityPickFavoritesBinding::inflate)
    private lateinit var adapter: PickContactAdapter

    private var allContacts: List<Contact> = emptyList()
    private var favorites: List<Contact> = emptyList()
    private val existingIds = HashSet<Int>()
    private val selectedIds = HashSet<Int>()
    private var isSearchOpen = false
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.pickRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }

        adapter = PickContactAdapter(this, existingIds, selectedIds) { updateSaveState() }
        binding.pickList.adapter = adapter

        binding.pickBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.pickSearch.setOnClickListener { if (isSearchOpen) closeSearch() else openSearch() }
        binding.pickSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = buildList()
        })
        binding.pickBar.barCancel.setOnClickListener { finish() }
        binding.pickBar.barSave.setOnClickListener { save() }

        onBackPressedDispatcher.addCallback(this) {
            if (isSearchOpen) closeSearch() else finish()
        }

        applyPalette()
        updateSaveState()

        FavoritesHelper.load(this) { all, favs ->
            allContacts = all
            favorites = favs
            existingIds.clear()
            existingIds.addAll(favs.map { it.id })
            selectedIds.clear()
            selectedIds.addAll(existingIds)
            buildList()
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
        binding.pickRoot.setBackgroundColor(palette.pageBg)
        binding.pickTitle.setTextColor(palette.itemTitleText)
        val tint = ColorStateList.valueOf(palette.itemTitleText)
        ImageViewCompat.setImageTintList(binding.pickBack, tint)
        ImageViewCompat.setImageTintList(binding.pickSearch, tint)
        binding.pickSearchInput.background = HomeThemeHelper.createSearchPillBackground(this, palette.searchBg)
        binding.pickSearchInput.setTextColor(palette.itemTitleText)
        binding.pickSearchInput.setHintTextColor(palette.searchHint)
        binding.pickPlaceholder.setTextColor(palette.itemSubtitleText)
        binding.pickBarDivider.setBackgroundColor(palette.itemDivider)
        binding.pickBar.barDivider.setBackgroundColor(palette.itemDivider)
        binding.pickBar.barCancel.setTextColor(palette.itemTitleText)
    }

    private fun buildList() {
        val query = binding.pickSearchInput.text.toString().trim()
        val items = ArrayList<PickListItem>()

        if (query.isEmpty() && favorites.isNotEmpty()) {
            items.add(PickListItem.Header(getString(R.string.favourites_header), showStar = true))
            favorites.forEach { items.add(PickListItem.Row(it, it.id)) }
        }

        val digits = query.filter { it.isDigit() }
        val source = if (query.isEmpty()) {
            allContacts
        } else {
            allContacts.filter { contact ->
                contact.getNameToDisplay().contains(query, ignoreCase = true) ||
                    (digits.isNotEmpty() && digits.length == query.length &&
                        contact.phoneNumbers.any { it.value.filter { c -> c.isDigit() }.contains(digits) })
            }
        }

        var lastLetter = ""
        source.forEach { contact ->
            val letter = FavoritesHelper.sectionLetter(contact)
            if (letter != lastLetter) {
                items.add(PickListItem.Header(letter))
                lastLetter = letter
            }
            items.add(PickListItem.Row(contact, contact.id))
        }

        adapter.submit(items, query)
        binding.pickPlaceholder.text = getString(
            if (query.isEmpty()) org.fossify.commons.R.string.no_contacts_found else org.fossify.commons.R.string.no_items_found
        )
        binding.pickPlaceholder.beVisibleIf(source.isEmpty())
    }

    private fun hasChanges() = selectedIds != existingIds

    private fun updateSaveState() {
        val enabled = hasChanges() && !isSaving
        binding.pickBar.barSave.isEnabled = enabled
        binding.pickBar.barSave.setTextColor(HomeThemeHelper.getPalette(this).primaryBlue)
        binding.pickBar.barSave.alpha = if (enabled) 1f else 0.4f
    }

    private fun save() {
        if (!hasChanges() || isSaving) {
            finish()
            return
        }
        isSaving = true
        updateSaveState()
        val byId = allContacts.associateBy { it.id }
        val added = ArrayList(selectedIds.minus(existingIds).mapNotNull { byId[it] })
        val removed = ArrayList(existingIds.minus(selectedIds).mapNotNull { byId[it] })
        ContactsHelper(this).updateFavorites(added, removed) {
            FavoritesHelper.updateOrderAfterChange(this, added, removed)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun openSearch() {
        isSearchOpen = true
        binding.pickTitle.beGone()
        binding.pickSearchInput.visibility = View.VISIBLE
        binding.pickSearch.setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
        binding.pickSearchInput.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(binding.pickSearchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        isSearchOpen = false
        hideKeyboard(binding.pickSearchInput)
        binding.pickSearchInput.setText("")
        binding.pickSearchInput.visibility = View.GONE
        binding.pickTitle.beVisible()
        binding.pickSearch.setImageResource(org.fossify.commons.R.drawable.ic_search_vector)
    }
}
