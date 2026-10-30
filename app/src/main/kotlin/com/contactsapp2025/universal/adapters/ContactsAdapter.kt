package com.contactsapp2025.universal.adapters

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.activities.ViewContactActivity
import com.contactsapp2025.universal.dialog.ProgressDialogFragment
import com.contactsapp2025.universal.dialogs.ContactActionConfirmDialog
import com.contactsapp2025.universal.dialogs.CreateNewGroupDialog
import com.contactsapp2025.universal.extensions.callContact
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.editContact
import com.contactsapp2025.universal.extensions.shareContacts
import com.contactsapp2025.universal.helpers.*
import com.contactsapp2025.universal.interfaces.RefreshContactsListener
import com.contactsapp2025.universal.interfaces.RemoveFromGroupListener
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.interfaces.StartReorderDragListener
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import java.util.Collections

class ContactsAdapter(
    activity: SimpleActivity,
    var contactItems: MutableList<Contact>,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    private val refreshListener: RefreshContactsListener?,
    private val location: Int,
    private val removeListener: RemoveFromGroupListener?,
    private val enableDrag: Boolean = false,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate, ItemTouchHelperContract {

    private val NEW_GROUP_ID = -1

    private var config = activity.config
    private var textToHighlight = highlightText

    var startNameWithSurname = config.startNameWithSurname
    var showContactThumbnails = config.showContactThumbnails
    var showPhoneNumbers = config.showPhoneNumbers
    var fontSize = activity.getTextSize()
    var onDragEndListener: (() -> Unit)? = null

    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null

    init {
        setupDragListener(true)

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this, false))//viewType == VIEW_TYPE_GRID
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_edit).isVisible = isOneItemSelected()
            findItem(R.id.cab_remove).isVisible = location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_add_to_favorites).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_add_to_group).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB
            findItem(R.id.cab_send_sms_to_contacts).isVisible =
                location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_send_email_to_contacts).isVisible =
                location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_delete).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_create_shortcut).isVisible =
                isOreoPlus() && isOneItemSelected() && (location == LOCATION_FAVORITES_TAB || location == LOCATION_CONTACTS_TAB)

            if (location == LOCATION_GROUP_CONTACTS) {
                findItem(R.id.cab_remove).title = activity.getString(R.string.remove_from_group)
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_edit -> editContact()
            R.id.cab_select_all -> selectAll()
            R.id.cab_add_to_favorites -> addToFavorites()
            R.id.cab_add_to_group -> addToGroup()
            R.id.cab_share -> shareContacts()
            R.id.cab_send_sms_to_contacts -> sendSMSToContacts()
            R.id.cab_send_email_to_contacts -> sendEmailToContacts()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_remove -> removeContacts()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = contactItems.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contactItems.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = contactItems.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when {
            isFavoritesGrid() -> R.layout.item_favorite_grid
            // Favorites list mode: avatar + name + call button, same row as group members
            location == LOCATION_GROUP_CONTACTS || location == LOCATION_FAVORITES_TAB -> R.layout.item_group_contact
            showPhoneNumbers -> R.layout.item_contact_home_with_number
            else -> R.layout.item_contact_home_without_number
        }
        return createViewHolder(layout, parent)
    }

    /** Favorites tab shows round avatars in a grid unless the user picked "List" in Display mode. */
    private fun isFavoritesGrid() =
        location == LOCATION_FAVORITES_TAB && config.favoritesDisplayMode == org.fossify.commons.helpers.VIEW_TYPE_GRID

    private fun getContactSectionLetter(contact: Contact): String {
        val sorting = config.sorting
        var name = when {
            contact.isABusinessContact() -> contact.getFullCompany()
            sorting and SORT_BY_SURNAME != 0 && contact.surname.isNotEmpty() -> contact.surname
            sorting and SORT_BY_MIDDLE_NAME != 0 && contact.middleName.isNotEmpty() -> contact.middleName
            sorting and SORT_BY_FIRST_NAME != 0 && contact.firstName.isNotEmpty() -> contact.firstName
            config.startNameWithSurname -> contact.surname
            else -> contact.firstName
        }
        if (name.isEmpty()) {
            name = contact.getNameToDisplay()
        }
        val ch = name.trim().firstOrNull()?.toString()?.normalizeString()?.uppercase() ?: "#"
        return if (ch.isNotEmpty() && ch[0].isLetter()) ch else "#"
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contactItems[position]
        val allowLongClick = location != LOCATION_INSERT_OR_EDIT
        holder.bindView(contact, true, allowLongClick) { itemView, layoutPosition ->
            setupView(itemView, contact, holder)
        }
        bindViewHolder(holder)
    }


    override fun getItemCount() = contactItems.size

    private fun getItemWithKey(key: Int): Contact? = contactItems.firstOrNull { it.id == key }

    fun updateItems(newItems: List<Contact>, highlightText: String = "") {
        if (newItems.hashCode() != contactItems.hashCode()) {
            contactItems = newItems.toMutableList()
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun editContact() {
        val contact = getItemWithKey(selectedKeys.first()) ?: return
        activity.editContact(contact, config.mergeDuplicateContacts)
    }

    private fun askConfirmDelete() {
        ContactActionConfirmDialog(
            activity = activity,
            title = activity.getString(R.string.are_you_sure_title),
            message = activity.getString(R.string.delete_contact_message),
            positiveText = activity.getString(org.fossify.commons.R.string.delete)
        ) {
            deleteContacts()
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val contactsToRemove = getSelectedItems()
        val totalContactsToDelete = contactsToRemove.size
        val progress = ProgressDialogFragment.newInstance(totalContactsToDelete)
        progress.show(activity.supportFragmentManager, "delete_progress")


        val positions = getSelectedItemPositions()
        contactItems.removeAll(contactsToRemove)

        ContactsHelper(activity).getContacts(true) { allContacts ->
            ensureBackgroundThread {
                try {
                    val toDelete = contactsToRemove
                        .flatMap { contactToRemove ->
                            allContacts.filter {
                                (config.mergeDuplicateContacts || it.id == contactToRemove.id) &&
                                    (it.getHashToCompare() == contactToRemove.getHashToCompare())
                            }
                        }
                        .toMutableList() as ArrayList<Contact>

                    ContactsHelper(activity).deleteContacts(toDelete)

                    activity.runOnUiThread {
                        if (contactItems.isEmpty()) {
                            refreshListener?.refreshContacts(ALL_TABS_MASK)
                            finishActMode()
                        } else {
                            removeSelectedItems(positions)
                            refreshListener?.refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
                        }
                    }
                } finally {
                    activity.runOnUiThread {
                        if (progress.isAdded) {
                            progress.dismissAllowingStateLoss()
                        }
                    }
                }
            }
        }
    }

    // used for removing contacts from groups or favorites, not deleting actual contacts
    private fun removeContacts() {
        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contactItems.removeAll(contactsToRemove)

        if (location == LOCATION_FAVORITES_TAB) {
            Log.e("AAA", "remove from favorites: ")

            CoroutineScope(Dispatchers.IO).launch {
                ContactsHelper(activity).removeFavorites(contactsToRemove)
                if (contactItems.isEmpty()) {
                    refreshListener?.refreshContacts(TAB_FAVORITES)
                    CoroutineScope(Dispatchers.Main).launch {
                        finishActMode()
                    }

                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        removeSelectedItems(positions)
                    }
                }
            }


        } else if (location == LOCATION_GROUP_CONTACTS) {
            removeListener?.removeFromGroup(contactsToRemove)
            removeSelectedItems(positions)
        }
    }

    private fun addToFavorites() {
        CoroutineScope(Dispatchers.IO).launch {
            ContactsHelper(activity).addFavorites(getSelectedItems())
            refreshListener?.refreshContacts(TAB_FAVORITES)
            CoroutineScope(Dispatchers.Main).launch {
                finishActMode()
            }
        }
    }

    private fun addToGroup() {
        val items = ArrayList<RadioItem>()
        ContactsHelper(activity).getStoredGroups {
            it.forEach {
                items.add(RadioItem(it.id!!.toInt(), it.title))
            }
            items.add(RadioItem(NEW_GROUP_ID, activity.getString(R.string.create_new_group)))
            showGroupsPicker(items)
        }
    }

    private fun showGroupsPicker(radioItems: ArrayList<RadioItem>) {
        val selectedContacts = getSelectedItems()
        RadioGroupDialog(activity, radioItems, 0) {
            if (it as Int == NEW_GROUP_ID) {
                CreateNewGroupDialog(activity) {
                    ensureBackgroundThread {
                        activity.addContactsToGroup(selectedContacts, it.id!!.toLong())
                        refreshListener?.refreshContacts(TAB_GROUPS)
                    }
                    finishActMode()
                }
            } else {
                ensureBackgroundThread {
                    activity.addContactsToGroup(selectedContacts, it.toLong())
                    refreshListener?.refreshContacts(TAB_GROUPS)
                }
                finishActMode()
            }
        }
    }

    private fun shareContacts() {
        activity.shareContacts(getSelectedItems())
    }

    private fun sendSMSToContacts() {
        if (getSelectedItems().size <= 30) {
            activity.sendSMSToContacts(getSelectedItems())
        } else {
            Toast.makeText(activity, "Maximum 30 person allow for send SMS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmailToContacts() {
        activity.sendEmailToContacts(getSelectedItems())
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val contact = getSelectedItems().first()
            val drawable = resources.getDrawable(R.drawable.shortcut_contact).mutate()
            getShortcutImage(contact, drawable) {
                val intent = Intent(activity, ViewContactActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                intent.putExtra(CONTACT_ID, contact.id)
                intent.putExtra(IS_PRIVATE, contact.isPrivate())
                intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY

                val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                    .setShortLabel(contact.getNameToDisplay())
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun getShortcutImage(contact: Contact, drawable: Drawable, callback: () -> Unit) {
        val appIconColor = baseConfig.appIconColor
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_contact_background).applyColorFilter(appIconColor)
        val placeholderImage = BitmapDrawable(resources, SimpleContactsHelper(activity).getContactLetterIcon(contact.getNameToDisplay()))
        if (contact.photoUri.isEmpty() && contact.photo == null) {
            drawable.setDrawableByLayerId(R.id.shortcut_contact_image, placeholderImage)
            callback()
        } else {
            ensureBackgroundThread {
                val options = RequestOptions()
                    .signature(ObjectKey(contact.getSignatureKey()))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .error(placeholderImage)

                val size = activity.resources.getDimension(org.fossify.commons.R.dimen.shortcut_size).toInt()
                val itemToLoad: Any? = contact.photoUri.ifEmpty {
                    contact.photo
                }

                val builder = Glide.with(activity)
                    .asDrawable()
                    .load(itemToLoad)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(size, size)

                try {
                    val bitmap = builder.get()
                    drawable.setDrawableByLayerId(R.id.shortcut_contact_image, bitmap)
                } catch (e: Exception) {
                }

                activity.runOnUiThread {
                    callback()
                }
            }
        }
    }

    private fun getSelectedItems(): ArrayList<Contact> {
        return ArrayList(
            contactItems.filter { selectedKeys.contains(it.id) }
        )
    }


    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.findViewById<ImageView>(org.fossify.commons.R.id.item_contact_image))
        }
    }

    private fun setupView(view: View, contact: Contact, holder: ViewHolder) {
        view.apply {
            val palette = HomeThemeHelper.getPalette(activity)
            val sectionHolder = findViewById<View>(R.id.section_header_holder)
            val sectionStar = findViewById<ImageView>(R.id.section_header_star)
            val sectionTitle = findViewById<TextView>(R.id.section_header_title)

            if (sectionHolder != null && sectionTitle != null) {
                sectionHolder.setOnClickListener { }
                if (location == LOCATION_CONTACTS_TAB && textToHighlight.isEmpty()) {
                    val pos = holder.bindingAdapterPosition.takeIf { it >= 0 } ?: contactItems.indexOf(contact)
                    val prev = contactItems.getOrNull(pos - 1)
                    val currLetter = getContactSectionLetter(contact)
                    val prevLetter = if (prev != null) getContactSectionLetter(prev) else null
                    if (prevLetter != currLetter) {
                        sectionHolder.beVisible()
                        sectionStar?.beGone()
                        sectionTitle.text = currLetter
                        sectionTitle.setTextColor(palette.itemTitleText)
                    } else {
                        sectionHolder.beGone()
                    }
                } else {
                    sectionHolder.beGone()
                }
            }

            setupViewBackground(activity)
            findViewById<View>(org.fossify.commons.R.id.item_contact_frame)?.apply {
                isSelected = selectedKeys.contains(contact.id)
                isClickable = false
                isFocusable = false
            }
            val fullName = contact.getNameToDisplay()
            findViewById<TextView>(org.fossify.commons.R.id.item_contact_name).text = if (textToHighlight.isEmpty()) fullName else {
                if (fullName.contains(textToHighlight, true)) {
                    fullName.highlightTextPart(textToHighlight, resources.getColor(org.fossify.commons.R.color.md_blue_900))
                } else {
                    fullName.highlightTextFromNumbers(textToHighlight, resources.getColor(org.fossify.commons.R.color.md_blue_900))
                }
            }

            val isGrid = isFavoritesGrid()
            findViewById<TextView>(org.fossify.commons.R.id.item_contact_name).apply {
                setTextColor(palette.itemTitleText)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * if (isGrid) 0.8f else 0.94f)
            }

            findViewById<ImageView>(R.id.item_contact_call_icon)?.apply {
                androidx.core.widget.ImageViewCompat.setImageTintList(this, android.content.res.ColorStateList.valueOf(palette.itemTitleText))
                setOnClickListener {
                    (activity as? SimpleActivity)?.callContact(contact)
                }
            }

            if (findViewById<TextView>(org.fossify.commons.R.id.item_contact_number) != null) {
                val phoneNumberToUse = if (textToHighlight.isEmpty()) {
                    contact.phoneNumbers.firstOrNull()
                } else {
                    contact.phoneNumbers.firstOrNull { it.value.contains(textToHighlight) } ?: contact.phoneNumbers.firstOrNull()
                }
                val phoneNumberToFormat = phoneNumberToUse?.value ?: ""
                val numberText = if (config.formatPhoneNumbers) {
                    phoneNumberToFormat.formatPhoneNumber()
                } else {
                    phoneNumberToUse?.value ?: ""
                }
                findViewById<TextView>(org.fossify.commons.R.id.item_contact_number).apply {
                    text = if (textToHighlight.isEmpty()) numberText else numberText.highlightTextPart(textToHighlight, resources.getColor(org.fossify.commons.R.color.md_blue_900), false, true)
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.72f)
                }
            }

            val showAvatar = showContactThumbnails || isGrid
            findViewById<View>(org.fossify.commons.R.id.item_contact_image).beVisibleIf(showAvatar)

            if (showAvatar) {
                val itemPos = holder.bindingAdapterPosition.takeIf { it >= 0 } ?: contactItems.indexOf(contact).coerceAtLeast(0)
                val placeholderImage = if (isGrid) {
                    // colour keyed on the contact so it matches Pick contact / Edit favorites
                    HomeThemeHelper.createContactAvatarBitmap(activity, fullName, contact.id, 62f).toDrawable(resources)
                } else if (location == LOCATION_FAVORITES_TAB) {
                    HomeThemeHelper.createContactAvatarBitmap(activity, fullName, contact.id).toDrawable(resources)
                } else if (location == LOCATION_GROUP_CONTACTS) {
                    HomeThemeHelper.createContactAvatarBitmap(activity, fullName, itemPos).toDrawable(resources)
                } else {
                    SimpleContactsHelper(context).getContactLetterIcon(fullName).toDrawable(resources)
                }
                if (contact.photoUri.isEmpty() && contact.photo == null) {
                    findViewById<ImageView>(org.fossify.commons.R.id.item_contact_image).setImageDrawable(placeholderImage)
                } else {
                    val options = RequestOptions()
                        .signature(ObjectKey(contact.getSignatureKey()))
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .error(placeholderImage)
                        .centerCrop()

                    val itemToLoad: Any? = contact.photoUri.ifEmpty {
                        contact.photo
                    }

                    Glide.with(activity)
                        .load(itemToLoad)
                        .apply(options)
                        .apply(RequestOptions.circleCropTransform())
                        .into(findViewById(org.fossify.commons.R.id.item_contact_image))
                }
            }

            val dragIcon = findViewById<ImageView>(org.fossify.commons.R.id.drag_handle_icon)
            if (enableDrag && textToHighlight.isEmpty()) {
                dragIcon.apply {
                    beVisibleIf(selectedKeys.isNotEmpty())
                    //applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }
        }
    }

    override fun onChange(position: Int) = contactItems.getOrNull(position)?.getBubbleText() ?: ""

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contactItems, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contactItems, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }


}
