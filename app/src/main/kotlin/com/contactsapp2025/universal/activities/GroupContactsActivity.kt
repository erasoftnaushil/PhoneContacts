package com.contactsapp2025.universal.activities

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.get
import androidx.core.view.size
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.adapters.ContactsAdapter
import com.contactsapp2025.universal.databinding.ActivityGroupContactsBinding
import com.contactsapp2025.universal.dialogs.SelectContactsDialog
import com.contactsapp2025.universal.extensions.handleGenericContactClick
import com.contactsapp2025.universal.helpers.GROUP
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import com.contactsapp2025.universal.helpers.LOCATION_GROUP_CONTACTS
import com.contactsapp2025.universal.interfaces.RefreshContactsListener
import com.contactsapp2025.universal.interfaces.RemoveFromGroupListener
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Group

class GroupContactsActivity : SimpleActivity(), RemoveFromGroupListener, RefreshContactsListener {
    private var allContacts = ArrayList<Contact>()
    private var groupContacts = ArrayList<Contact>()
    private var wasInit = false
    private val binding by viewBinding(ActivityGroupContactsBinding::inflate)
    lateinit var group: Group

    protected val INTENT_SELECT_RINGTONE = 600

    protected var contact: Contact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateTextColors(binding.groupContactsCoordinator)
        setupOptionsMenu()

        setupMaterialScrollListener(binding.groupContactsList, binding.groupContactsToolbar)

        group = intent.extras?.getSerializable(GROUP) as Group
        binding.groupContactsToolbar.title = group.title

        binding.groupContactsFab.setOnClickListener {
            if (wasInit) {
                fabClicked()
            }
        }

        binding.groupContactsPlaceholder2.setOnClickListener {
            fabClicked()
        }

        applyPaletteColors()
    }

    override fun onResume() {
        super.onResume()
        refreshContacts()
        setupToolbar(binding.groupContactsToolbar, NavigationIcon.Arrow)
        applyPaletteColors()
        (binding.groupContactsFab.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin =
            navigationBarHeight + resources.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt()
    }

    private fun applyPaletteColors() {
        val palette = HomeThemeHelper.getPalette(this)
        updateStatusbarColor(palette.pageBg)
        window.decorView.setBackgroundColor(palette.pageBg)
        binding.groupContactsCoordinator.setBackgroundColor(palette.pageBg)
        binding.groupContactsHolder.setBackgroundColor(palette.pageBg)
        binding.groupContactsWrapper.setBackgroundColor(palette.pageBg)
        binding.groupContactsToolbar.setBackgroundColor(palette.pageBg)
        binding.groupContactsToolbar.setTitleTextColor(palette.itemTitleText)
        binding.groupContactsToolbar.navigationIcon?.setTint(palette.itemTitleText)

        val menu = binding.groupContactsToolbar.menu
        for (i in 0 until menu.size) {
            menu[i].icon?.setTint(palette.itemTitleText)
        }

        binding.groupContactsFastscroller.updateColors(palette.primaryBlue)
        binding.groupContactsPlaceholder.setTextColor(palette.itemSubtitleText)
        binding.groupContactsPlaceholder2.underlineText()
        binding.groupContactsPlaceholder2.setTextColor(palette.primaryBlue)
    }

    private fun setupOptionsMenu() {
        binding.groupContactsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_contacts_to_group -> {
                    if (wasInit) {
                        fabClicked()
                    }
                }
                R.id.send_sms_to_group -> sendSMSToGroup()
                R.id.send_email_to_group -> sendEmailToGroup()
                R.id.assign_ringtone_to_group -> assignRingtoneToGroup()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == INTENT_SELECT_RINGTONE && resultCode == Activity.RESULT_OK && resultData != null) {
            val extras = resultData.extras
            if (extras?.containsKey(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) == true) {
                val uri = extras.getParcelable<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: return
                try {
                    setRingtoneOnSelected(uri)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun fabClicked() {
        SelectContactsDialog(this, allContacts, true, false, groupContacts) { addedContacts, removedContacts ->
            ensureBackgroundThread {
                addContactsToGroup(addedContacts, group.id!!)
                removeContactsFromGroup(removedContacts, group.id!!)
                refreshContacts()
            }
        }
    }

    private fun refreshContacts() {
        ContactsHelper(this).getContacts {
            wasInit = true
            allContacts = it

            groupContacts = it.filter { it.groups.map { it.id }.contains(group.id) } as ArrayList<Contact>
            binding.groupContactsPlaceholder2.beVisibleIf(groupContacts.isEmpty())
            binding.groupContactsPlaceholder.beVisibleIf(groupContacts.isEmpty())
            binding.groupContactsFastscroller.beVisibleIf(groupContacts.isNotEmpty())
            updateContacts(groupContacts)
        }
    }

    private fun sendSMSToGroup() {
        if (groupContacts.isEmpty()) {
            toast(org.fossify.commons.R.string.no_contacts_found)
        } else {
            sendSMSToContacts(groupContacts)
        }
    }

    private fun sendEmailToGroup() {
        if (groupContacts.isEmpty()) {
            toast(org.fossify.commons.R.string.no_contacts_found)
        } else {
            sendEmailToContacts(groupContacts)
        }
    }

    private fun assignRingtoneToGroup() {
        val ringtonePickerIntent = getRingtonePickerIntent()
        try {
            startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
        } catch (e: Exception) {
            toast(e.toString())
        }
    }

    private fun updateContacts(contacts: ArrayList<Contact>) {
        val palette = HomeThemeHelper.getPalette(this)
        val currAdapter = binding.groupContactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(
                this,
                contactItems = contacts,
                recyclerView = binding.groupContactsList,
                location = LOCATION_GROUP_CONTACTS,
                removeListener = this,
                refreshListener = this
            ) {
                contactClicked(it as Contact)
            }.apply {
                updateTextColor(palette.itemTitleText)
                binding.groupContactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.groupContactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).apply {
                updateTextColor(palette.itemTitleText)
                updateItems(contacts)
            }
        }
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        refreshContacts()
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    override fun removeFromGroup(contacts: ArrayList<Contact>) {
        ensureBackgroundThread {
            removeContactsFromGroup(contacts, group.id!!)
            if (groupContacts.size == contacts.size) {
                refreshContacts()
            }
        }
    }

    private fun getDefaultRingtoneUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun getRingtonePickerIntent(): Intent {
        val defaultRingtoneUri = getDefaultRingtoneUri()

        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultRingtoneUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultRingtoneUri)
        }
    }

    private fun setRingtoneOnSelected(uri: Uri) {
        groupContacts.forEach {
            ContactsHelper(this).updateRingtone(it.contactId.toString(), uri.toString())
        }
    }
}
