package com.contactsapp2025.universal.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import com.contactsapp2025.universal.activities.EditContactActivity
import com.contactsapp2025.universal.activities.InsertOrEditContactActivity
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.adapters.ContactsAdapter
import com.contactsapp2025.universal.databinding.FragmentContactsBinding
import com.contactsapp2025.universal.databinding.FragmentLettersLayoutBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.LOCATION_CONTACTS_TAB
import com.contactsapp2025.universal.interfaces.RefreshContactsListener
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.models.contacts.Contact

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet) {

    private lateinit var binding: FragmentContactsBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentContactsBinding.bind(this)
        innerBinding = LetterLayout(FragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        activity?.hideKeyboard()
        Intent(context, EditContactActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    override fun placeholderClicked() {
        if (activity is MainActivity) {
            (activity as MainActivity).showFilterDialog()
        } else if (activity is InsertOrEditContactActivity) {
            (activity as InsertOrEditContactActivity).showFilterDialog()
        }
    }

    fun setupContactsAdapter(contacts: List<Contact>) {
        setupViewVisibility(contacts.isNotEmpty())
        val currAdapter = innerBinding.fragmentList.adapter
        val updatedContacts = contacts.toMutableList()

        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            val location = LOCATION_CONTACTS_TAB

            ContactsAdapter(
                activity = activity as SimpleActivity,
                contactItems = updatedContacts,
                refreshListener = activity as RefreshContactsListener,
                location = location,
                removeListener = null,
                recyclerView = innerBinding.fragmentList,
                enableDrag = false,
            ) {
                (activity as RefreshContactsListener).contactClicked(it as Contact)
            }.apply {
                innerBinding.fragmentList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).apply {
                startNameWithSurname = context.config.startNameWithSurname
                showPhoneNumbers = context.config.showPhoneNumbers
                showContactThumbnails = context.config.showContactThumbnails
                updateItems(updatedContacts)
            }
        }
    }
}
