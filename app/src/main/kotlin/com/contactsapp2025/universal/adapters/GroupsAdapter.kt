package com.contactsapp2025.universal.adapters

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.ImageViewCompat
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.groupsDB
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.TAB_GROUPS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Group
import org.fossify.commons.views.MyRecyclerView
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.databinding.ItemGroupBinding
import com.contactsapp2025.universal.dialogs.RenameGroupDialog
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import com.contactsapp2025.universal.interfaces.RefreshContactsListener

class GroupsAdapter(
    activity: SimpleActivity, var groups: ArrayList<Group>, val refreshListener: RefreshContactsListener?, recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textToHighlight = ""
    var showContactThumbnails = activity.config.showContactThumbnails
    var fontSize = activity.getTextSize()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_groups

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_rename -> renameGroup()
            R.id.cab_select_all -> selectAll()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = groups.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = groups.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = groups.indexOfFirst { it.id!!.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemGroupBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        holder.bindView(group, true, true) { itemView, layoutPosition ->
            setupView(itemView, group, layoutPosition)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = groups.size

    private fun getItemWithKey(key: Int): Group? = groups.firstOrNull { it.id!!.toInt() == key }

    fun updateItems(newItems: ArrayList<Group>, highlightText: String = "") {
        groups = newItems
        textToHighlight = highlightText
        notifyDataSetChanged()
        finishActMode()
    }

    private fun renameGroup() {
        val group = getItemWithKey(selectedKeys.first()) ?: return
        RenameGroupDialog(activity, group) {
            finishActMode()
            refreshListener?.refreshContacts(TAB_GROUPS)
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().first()
        val items = if (itemsCnt == 1) {
            "\"${firstItem.title}\""
        } else {
            resources.getQuantityString(R.plurals.delete_groups, itemsCnt, itemsCnt)
        }

        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteGroups()
            }
        }
    }

    private fun deleteGroups() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val groupsToRemove = groups.filter { selectedKeys.contains(it.id!!.toInt()) } as ArrayList<Group>
        val positions = getSelectedItemPositions()
        groupsToRemove.forEach {
            if (it.isPrivateSecretGroup()) {
                activity.groupsDB.deleteGroupId(it.id!!)
            } else {
                ContactsHelper(activity).deleteGroup(it.id!!)
            }
        }
        groups.removeAll(groupsToRemove)

        activity.runOnUiThread {
            if (groups.isEmpty()) {
                removeSelectedItems(positions)
                refreshListener?.refreshContacts(TAB_GROUPS)
                finishActMode()
            } else {
                removeSelectedItems(positions)
            }
        }
    }

    private fun getSelectedItems() = groups.filter { selectedKeys.contains(it.id?.toInt()) } as ArrayList<Group>

    private fun setupView(view: View, group: Group, position: Int) {
        val palette = HomeThemeHelper.getPalette(activity)
        ItemGroupBinding.bind(view).apply {
            groupFrame.isSelected = selectedKeys.contains(group.id!!.toInt())
            val groupTitle = if (textToHighlight.isEmpty()) {
                group.title
            } else {
                group.title.highlightTextPart(textToHighlight, palette.primaryBlue)
            }

            groupName.apply {
                setTextColor(palette.itemTitleText)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.94f)
                text = groupTitle
            }

            groupContactsCount.apply {
                setTextColor(palette.itemSubtitleText)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.75f)
                text = "${group.contactsCount} ${activity.getString(R.string.contacts)}"
            }

            ImageViewCompat.setImageTintList(groupChevron, ColorStateList.valueOf(palette.chevronTint))

            groupTmb.beVisibleIf(showContactThumbnails)
            if (showContactThumbnails) {
                groupTmb.setImageDrawable(HomeThemeHelper.createGroupAvatarDrawable(activity, position))
            }
        }
    }

    override fun onChange(position: Int) = groups.getOrNull(position)?.getBubbleText() ?: ""
}
