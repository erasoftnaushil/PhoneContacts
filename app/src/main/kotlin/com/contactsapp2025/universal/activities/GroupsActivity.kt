package com.contactsapp2025.universal.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.ImageViewCompat
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.adapters.GroupsAdapter
import com.contactsapp2025.universal.databinding.ActivityGroupsBinding
import com.contactsapp2025.universal.dialogs.CreateNewGroupDialog
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.handleGenericContactClick
import com.contactsapp2025.universal.helpers.GROUP
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import com.contactsapp2025.universal.interfaces.RefreshContactsListener
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Group
import java.util.Locale

class GroupsActivity : SimpleActivity(), RefreshContactsListener {
    private val binding by viewBinding(ActivityGroupsBinding::inflate)
    private var allGroups = ArrayList<Group>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateTextColors(binding.groupsCoordinator)
        setupMaterialScrollListener(binding.groupsList, binding.groupsToolbar)

        binding.groupsFab.setOnClickListener {
            showNewGroupsDialog()
        }

        binding.groupsAddBtn.setOnClickListener {
            showNewGroupsDialog()
        }

        binding.groupsPlaceholder2.setOnClickListener {
            showNewGroupsDialog()
        }

        binding.groupsSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterGroups(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        applyPaletteColors()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.groupsToolbar, NavigationIcon.Arrow)
        applyPaletteColors()
        (binding.groupsFab.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin =
            navigationBarHeight + resources.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt()
        refreshGroups()
    }

    private fun applyPaletteColors() {
        val palette = HomeThemeHelper.getPalette(this)
        updateStatusbarColor(palette.pageBg)
        window.decorView.setBackgroundColor(palette.pageBg)
        binding.groupsCoordinator.setBackgroundColor(palette.pageBg)
        binding.groupsHolder.setBackgroundColor(palette.pageBg)
        binding.groupsWrapper.setBackgroundColor(palette.pageBg)
        binding.groupsToolbar.setBackgroundColor(palette.pageBg)
        binding.groupsToolbar.setTitleTextColor(palette.itemTitleText)
        binding.groupsToolbar.navigationIcon?.setTint(palette.itemTitleText)

        binding.groupsSearchHolder.background = HomeThemeHelper.createSearchPillBackground(this, palette.searchBg)
        ImageViewCompat.setImageTintList(binding.groupsSearchIcon, ColorStateList.valueOf(palette.itemTitleText))
        ImageViewCompat.setImageTintList(binding.groupsAddBtn, ColorStateList.valueOf(palette.itemTitleText))
        binding.groupsSearchInput.setTextColor(palette.itemTitleText)
        binding.groupsSearchInput.setHintTextColor(palette.searchHint)

        binding.groupsFastscroller.updateColors(palette.primaryBlue)
        binding.groupsPlaceholder2.underlineText()
        binding.groupsPlaceholder2.setTextColor(palette.primaryBlue)
    }

    private fun showNewGroupsDialog() {
        (binding.groupsList.adapter as? GroupsAdapter)?.finishActMode()
        CreateNewGroupDialog(this) {
            refreshGroups()
        }
    }

    private fun filterGroups(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            ArrayList(allGroups)
        } else {
            ArrayList(allGroups.filter { it.title.contains(trimmed, ignoreCase = true) })
        }

        val palette = HomeThemeHelper.getPalette(this)
        if (allGroups.isEmpty()) {
            binding.groupsPlaceholder.text = getString(R.string.no_group_created)
            binding.groupsPlaceholder.beVisible()
            binding.groupsPlaceholder2.beVisible()
        } else if (filtered.isEmpty()) {
            binding.groupsPlaceholder.text = getString(org.fossify.commons.R.string.no_items_found)
            binding.groupsPlaceholder.beVisible()
            binding.groupsPlaceholder2.beGone()
        } else {
            binding.groupsPlaceholder.beGone()
            binding.groupsPlaceholder2.beGone()
        }
        binding.groupsPlaceholder.setTextColor(palette.itemSubtitleText)
        binding.groupsFastscroller.beVisibleIf(filtered.isNotEmpty())
        binding.groupsList.beVisibleIf(filtered.isNotEmpty())

        (binding.groupsList.adapter as? GroupsAdapter)?.apply {
            updateTextColor(palette.itemTitleText)
            showContactThumbnails = config.showContactThumbnails
            updateItems(filtered, trimmed)
        }
    }

    private fun refreshGroups() {
        ContactsHelper(this).getContacts { contacts ->
            ContactsHelper(this).getStoredGroups { stored ->
                var storedGroups = stored
                contacts.forEach { contact ->
                    contact.groups.forEach { group ->
                        val storedGroup = storedGroups.firstOrNull { it.id == group.id }
                        storedGroup?.addContact()
                    }
                }

                storedGroups = storedGroups.asSequence()
                    .sortedWith(compareBy { it.title.lowercase(Locale.getDefault()).normalizeString() })
                    .toMutableList() as ArrayList<Group>

                runOnUiThread {
                    allGroups = storedGroups
                    val palette = HomeThemeHelper.getPalette(this)
                    val query = binding.groupsSearchInput.text?.toString()?.trim() ?: ""
                    val displayGroups = if (query.isEmpty()) {
                        storedGroups
                    } else {
                        ArrayList(storedGroups.filter { it.title.contains(query, ignoreCase = true) })
                    }

                    binding.groupsPlaceholder2.beVisibleIf(storedGroups.isEmpty())
                    binding.groupsPlaceholder.beVisibleIf(displayGroups.isEmpty())
                    binding.groupsPlaceholder.text = getString(
                        if (storedGroups.isEmpty()) R.string.no_group_created else org.fossify.commons.R.string.no_items_found
                    )
                    binding.groupsPlaceholder.setTextColor(palette.itemSubtitleText)
                    binding.groupsFastscroller.beVisibleIf(displayGroups.isNotEmpty())
                    binding.groupsList.beVisibleIf(displayGroups.isNotEmpty())

                    val currAdapter = binding.groupsList.adapter
                    if (currAdapter == null) {
                        GroupsAdapter(this, displayGroups, this, binding.groupsList) {
                            hideKeyboard()
                            Intent(this, GroupContactsActivity::class.java).apply {
                                putExtra(GROUP, it as Group)
                                startActivity(this)
                            }
                        }.apply {
                            updateTextColor(palette.itemTitleText)
                            binding.groupsList.adapter = this
                        }

                        if (areSystemAnimationsEnabled) {
                            binding.groupsList.scheduleLayoutAnimation()
                        }
                    } else {
                        (currAdapter as GroupsAdapter).apply {
                            updateTextColor(palette.itemTitleText)
                            showContactThumbnails = config.showContactThumbnails
                            updateItems(displayGroups, query)
                        }
                    }
                }
            }
        }
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        refreshGroups()
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }
}
