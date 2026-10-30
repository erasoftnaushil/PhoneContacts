package com.contactsapp2025.universal.helpers

import android.app.Activity
import android.content.Context
import com.contactsapp2025.universal.extensions.config
import com.google.gson.Gson
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.Converters
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.models.contacts.Contact
import java.util.Locale

/** Shared loading / ordering logic for the Favorites tab, Pick contact and Edit favorites screens. */
object FavoritesHelper {

    /** Loads visible contacts and returns (all contacts sorted by name, favorites in the user's order) on the UI thread. */
    fun load(activity: Activity, callback: (all: List<Contact>, favorites: List<Contact>) -> Unit) {
        ContactsHelper(activity).getContacts { contacts ->
            val sources = activity.getVisibleContactSources()
            val visible = contacts.filter { sources.contains(it.source) }
            val all = visible.sortedWith(compareBy({ sectionLetter(it) == "#" }, { it.getNameToDisplay().lowercase(Locale.getDefault()) }))
            val favorites = sortByUserOrder(activity, visible.filter { it.starred == 1 })
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) callback(all, favorites)
            }
        }
    }

    fun sortByUserOrder(context: Context, favorites: List<Contact>): List<Contact> {
        val config = context.config
        val order = config.favoritesContactsOrder
        if (!config.isCustomOrderSelected || order.isEmpty()) {
            return favorites // same order the Favorites tab uses (ContactsHelper's sorting)
        }
        val positions = try {
            Converters().jsonToStringList(order).withIndex().associate { it.value to it.index }
        } catch (_: Exception) {
            emptyMap()
        }
        return favorites.sortedWith(compareBy({ positions[it.id.toString()] ?: Int.MAX_VALUE }, { it.getNameToDisplay().lowercase(Locale.getDefault()) }))
    }

    fun saveOrder(context: Context, favorites: List<Contact>) {
        context.config.apply {
            isCustomOrderSelected = true
            favoritesContactsOrder = Gson().toJson(favorites.map { it.id.toString() })
        }
    }

    /** Keeps an existing custom order in sync after favorites were added / removed elsewhere. */
    fun updateOrderAfterChange(context: Context, added: List<Contact>, removed: List<Contact>) {
        val config = context.config
        if (!config.isCustomOrderSelected || config.favoritesContactsOrder.isEmpty()) return
        val ids = try {
            Converters().jsonToStringList(config.favoritesContactsOrder)
        } catch (_: Exception) {
            arrayListOf()
        }
        val removedIds = removed.map { it.id.toString() }.toSet()
        ids.removeAll(removedIds)
        added.map { it.id.toString() }.forEach { if (!ids.contains(it)) ids.add(it) }
        config.favoritesContactsOrder = Gson().toJson(ids)
    }

    fun sectionLetter(contact: Contact): String {
        val ch = contact.getNameToDisplay().trim().firstOrNull()?.toString()?.normalizeString()?.uppercase(Locale.getDefault()) ?: "#"
        return if (ch.isNotEmpty() && ch[0].isLetter()) ch else "#"
    }
}
