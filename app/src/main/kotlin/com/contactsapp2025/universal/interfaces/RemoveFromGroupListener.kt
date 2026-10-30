package com.contactsapp2025.universal.interfaces

import org.fossify.commons.models.contacts.Contact

interface RemoveFromGroupListener {
    fun removeFromGroup(contacts: ArrayList<Contact>)
}
