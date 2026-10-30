package com.contactsapp2025.universal.cdo

import android.Manifest
import android.content.Intent

import android.content.pm.PackageManager

import android.os.Bundle
import android.util.Log

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.ViewContactActivity
import com.contactsapp2025.universal.extensions.viewContact


//import com.contactsapp2025.universal.permissionApi.PermissionUtil

import org.fossify.commons.cdo.ContactCDOAdapter
import org.fossify.commons.compose.extensions.config
import org.fossify.commons.databinding.FragmentCallBinding
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.Converters
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.models.contacts.Contact

class ContactsCDOFragment : Fragment() {

    lateinit var binding: FragmentCallBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.e("AAAAA", " :::::OPEN CDO::::: ")
        binding = FragmentCallBinding.inflate(inflater, container, false)
        binding.progressBar.visibility = View.VISIBLE
        loadContacts()
        binding.permissionButton.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1001)
        }
        // cdo open field update,all permission check and server call
        // PermissionUtil.onAppOpen(requireActivity())
        return binding.root
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            binding.permissionButton.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
            loadContacts()
        }
    }

    private fun loadContacts() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            ContactsHelper(requireContext()).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                if (contacts.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyList.visibility = View.VISIBLE
                    return@getContacts
                } else {
                    binding.permissionButton.visibility = View.GONE
                }
                val contactSources = if (isAdded) {
                    requireActivity().getVisibleContactSources()
                } else {
                    return@getContacts
                }
                val favouriteContacts = contacts.filter { it.starred == 1 && contactSources.contains(it.source) }

                activity?.runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    if (favouriteContacts.isEmpty()) {
                        setupContacts(contacts.take(50))
                    } else {
                        setupContacts(favouriteContacts)
                    }
                }
            }
        } else {
            binding.progressBar.visibility = View.GONE
            binding.permissionButton.visibility = View.VISIBLE
        }
    }

    private fun setupContacts(contacts: List<Contact>) {
        val contactAdapter = ContactCDOAdapter { contact ->
            val hostActivity = requireActivity()

            val mainIntent = Intent(hostActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val detailIntent = Intent(hostActivity, ViewContactActivity::class.java).apply {
                putExtra(CONTACT_ID, contact.id)
                putExtra(IS_PRIVATE, contact.isPrivate())
                putExtra("fromCDO", true)
            }

            TaskStackBuilder.create(hostActivity).apply {
                addNextIntent(mainIntent)
                addNextIntent(detailIntent)
                startActivities()
            }

            hostActivity.finish()
        }
        binding.fragmentList.layoutManager = LinearLayoutManager(context)
        binding.fragmentList.adapter = contactAdapter
        contactAdapter.submitList(contacts)
    }

    private fun sortFavourites(contacts: List<Contact>): List<Contact> {
        val favoritesOrder = activity?.config?.favoritesContactsOrder
        if (favoritesOrder.isNullOrEmpty()) {
            return contacts
        }

        val orderList = Converters().jsonToStringList(favoritesOrder)
        val map = orderList.withIndex().associate { it.value to it.index }

        return contacts.sortedBy { contact ->
            map[contact.id.toString()]
        }
    }
}
