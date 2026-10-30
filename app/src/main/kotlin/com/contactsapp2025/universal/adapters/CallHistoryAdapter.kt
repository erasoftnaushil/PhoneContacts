package com.contactsapp2025.universal.adapters

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.provider.CallLog.Calls
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.databinding.ItemCallHistoryBinding
import com.contactsapp2025.universal.databinding.ItemCallHistoryHeaderBinding
import com.contactsapp2025.universal.extensions.config
import com.contactsapp2025.universal.extensions.startCallIntent
import com.contactsapp2025.universal.extensions.viewCallLogDetail
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.PERMISSION_CALL_PHONE
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallLogItem(
    val id: Int,
    val name: String,
    val phoneNumber: String,
    val callType: Int,
    val timestamp: Long,
    val duration: Int,
    val photoUri: String = "",
    val matchedContact: Contact? = null,
    val callCount: Int = 1,
    val simLabel: String = "SIM1"
) {
    fun getDisplayTitle(): String = name.ifEmpty { phoneNumber }

    fun getDisplayTitleWithCount(): String {
        val baseTitle = getDisplayTitle()
        return if (callCount > 1) "$baseTitle ($callCount)" else baseTitle
    }
}

sealed class CallHistoryListItem {
    data class HeaderItem(val title: String, val countText: String) : CallHistoryListItem()
    data class EntryItem(
        val callLogItem: CallLogItem,
        val isFirstInSection: Boolean,
        val isLastInSection: Boolean
    ) : CallHistoryListItem()
}

class CallHistoryAdapter(
    private val simpleActivity: SimpleActivity,
    var callItems: ArrayList<CallLogItem>,
    recyclerView: MyRecyclerView,
    private val onBlockStateChanged: (() -> Unit)? = null,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(simpleActivity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    private var textToHighlight = ""
    var showContactThumbnails = activity.config.showContactThumbnails
    var fontSize = activity.getTextSize()
    private var displayList = ArrayList<CallHistoryListItem>()
    private var expandedItemId: Int? = null

    init {
        rebuildDisplayList()
    }

    fun toggleExpanded(itemId: Int) {
        expandedItemId = if (expandedItemId == itemId) null else itemId
        notifyDataSetChanged()
    }

    private fun rebuildDisplayList() {
        val newList = ArrayList<CallHistoryListItem>()
        callItems.forEachIndexed { index, callLogItem ->
            newList.add(
                CallHistoryListItem.EntryItem(
                    callLogItem = callLogItem,
                    isFirstInSection = index == 0,
                    isLastInSection = index == callItems.lastIndex
                )
            )
        }
        displayList = newList
        if (expandedItemId != null && callItems.none { it.id == expandedItemId }) {
            expandedItemId = null
        }
    }

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = 0

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int): Int? {
        return (displayList.getOrNull(position) as? CallHistoryListItem.EntryItem)?.callLogItem?.id
    }

    override fun getItemKeyPosition(key: Int): Int {
        return displayList.indexOfFirst {
            (it as? CallHistoryListItem.EntryItem)?.callLogItem?.id == key
        }
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int): Int {
        return when (displayList.getOrNull(position)) {
            is CallHistoryListItem.HeaderItem -> VIEW_TYPE_HEADER
            else -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = if (viewType == VIEW_TYPE_HEADER) {
            ItemCallHistoryHeaderBinding.inflate(layoutInflater, parent, false).root
        } else {
            ItemCallHistoryBinding.inflate(layoutInflater, parent, false).root
        }
        return createViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val row = displayList[position]) {
            is CallHistoryListItem.HeaderItem -> {
                holder.bindView(row, allowSingleClick = false, allowLongClick = false) { itemView, _ ->
                    setupHeaderView(itemView, row)
                }
            }
            is CallHistoryListItem.EntryItem -> {
                holder.bindView(row.callLogItem, allowSingleClick = true, allowLongClick = false) { itemView, layoutPosition ->
                    setupView(itemView, row, layoutPosition)
                }
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = displayList.size

    fun updateItems(newItems: ArrayList<CallLogItem>, highlightText: String = "") {
        callItems = newItems
        textToHighlight = highlightText
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing && holder.itemViewType == VIEW_TYPE_ITEM) {
            holder.itemView.findViewById<ImageView>(R.id.item_call_history_image)?.let { imageView ->
                Glide.with(activity).clear(imageView)
            }
        }
    }

    private fun setupHeaderView(view: View, header: CallHistoryListItem.HeaderItem) {
        val palette = HomeThemeHelper.getPalette(activity)
        ItemCallHistoryHeaderBinding.bind(view).apply {
            itemCallHistoryHeaderTitle.text = header.title
            itemCallHistoryHeaderTitle.setTextColor(palette.sectionHeaderText)
            itemCallHistoryHeaderCount.text = header.countText
            itemCallHistoryHeaderCount.setTextColor(palette.sectionHeaderText)
        }
    }

    private fun setupView(view: View, entry: CallHistoryListItem.EntryItem, position: Int) {
        val item = entry.callLogItem
        val palette = HomeThemeHelper.getPalette(activity)
        val isExpanded = expandedItemId == item.id
        val nextEntry = displayList.getOrNull(position + 1) as? CallHistoryListItem.EntryItem
        val isNextExpanded = nextEntry != null && expandedItemId == nextEntry.callLogItem.id
        val isBlocked = item.callType == Calls.BLOCKED_TYPE ||
            activity.config.isLocalNumberBlocked(item.phoneNumber) ||
            activity.isNumberBlocked(item.phoneNumber)

        ItemCallHistoryBinding.bind(view).apply {
            if (isExpanded) {
                itemCallHistoryFrame.background = HomeThemeHelper.createCardItemBackground(
                    context = activity,
                    cardColor = palette.cardBg,
                    isDark = palette.isDark,
                    isFirst = true,
                    isLast = true
                )
            } else {
                itemCallHistoryFrame.background = HomeThemeHelper.createFlatItemRipple(palette.isDark)
            }

            val padStart = HomeThemeHelper.dpToPx(activity, 14f).toInt()
            val padEnd = HomeThemeHelper.dpToPx(activity, 12f).toInt()
            val padTop = HomeThemeHelper.dpToPx(activity, if (isExpanded) 14f else 12f).toInt()
            itemCallHistoryFrame.setPaddingRelative(padStart, padTop, padEnd, 0)

            (itemCallHistoryDivider.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                lp.topMargin = HomeThemeHelper.dpToPx(activity, 12f).toInt()
                itemCallHistoryDivider.layoutParams = lp
            }

            (itemCallHistoryFrame.layoutParams as? RecyclerView.LayoutParams)?.let { rlp ->
                rlp.topMargin = if (isExpanded) HomeThemeHelper.dpToPx(activity, 6f).toInt() else 0
                rlp.bottomMargin = if (isExpanded) HomeThemeHelper.dpToPx(activity, 6f).toInt() else 0
                itemCallHistoryFrame.layoutParams = rlp
            }

            val avatarTitle = item.getDisplayTitle()
            val displayName = item.getDisplayTitleWithCount()
            itemCallHistoryName.apply {
                text = if (textToHighlight.isEmpty()) {
                    displayName
                } else {
                    displayName.highlightTextPart(
                        textToHighlight,
                        palette.primaryBlue,
                        highlightAll = false,
                        ignoreCharsBetweenDigits = true
                    )
                }
                setTextColor(palette.itemTitleText)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.94f)
            }

            val dateObj = Date(item.timestamp)
            val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(dateObj).uppercase(Locale.getDefault())
            itemCallHistoryTime.apply {
                text = "$timeFormatted  •  ${item.simLabel}"
                setTextColor(palette.itemSubtitleText)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.72f)
            }

            val callTypeIconRes = when {
                isBlocked -> R.drawable.ic_call_blocked_vector
                item.callType == Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing_vector
                item.callType == Calls.MISSED_TYPE || item.callType == Calls.REJECTED_TYPE -> R.drawable.ic_call_missed_vector
                else -> R.drawable.ic_call_incoming_vector
            }
            itemCallHistoryTypeIcon.setImageResource(callTypeIconRes)

            itemCallHistoryCallIcon.apply {
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(palette.callBtnIcon))
                setOnClickListener {
                    initiateCall(item)
                }
            }

            itemCallHistoryExpandedHolder.beVisibleIf(isExpanded)
            if (isExpanded) {
                itemCallHistoryExpandedDivider.setBackgroundColor(palette.itemDivider)

                val actionTint = ColorStateList.valueOf(palette.itemTitleText)
                ImageViewCompat.setImageTintList(imgActionText, actionTint)
                ImageViewCompat.setImageTintList(imgActionVideo, actionTint)
                ImageViewCompat.setImageTintList(imgActionBlock, actionTint)
                ImageViewCompat.setImageTintList(imgActionInfo, actionTint)

                txtActionText.setTextColor(palette.itemTitleText)
                txtActionVideo.setTextColor(palette.itemTitleText)
                txtActionBlock.setTextColor(palette.itemTitleText)
                txtActionInfo.setTextColor(palette.itemTitleText)
                txtActionBlock.text = activity.getString(if (isBlocked) R.string.action_unblock else R.string.action_block)

                btnActionText.setOnClickListener {
                    initiateSms(item)
                }
                btnActionVideo.setOnClickListener {
                    initiateVideoCall(item)
                }
                btnActionBlock.setOnClickListener {
                    toggleBlockNumber(item, isBlocked)
                }
                btnActionInfo.setOnClickListener {
                    activity.viewCallLogDetail(item)
                }
            }

            itemCallHistoryDivider.setBackgroundColor(palette.itemDivider)
            itemCallHistoryDivider.visibility = when {
                isExpanded -> View.GONE
                entry.isLastInSection || isNextExpanded -> View.INVISIBLE
                else -> View.VISIBLE
            }

            itemCallHistoryImage.beVisibleIf(showContactThumbnails)
            if (showContactThumbnails) {
                val placeholderImage = SimpleContactsHelper(activity).getContactLetterIcon(avatarTitle).toDrawable(resources)
                val photoToLoad = item.matchedContact?.photoUri?.ifEmpty { item.photoUri } ?: item.photoUri
                if (photoToLoad.isEmpty() && item.matchedContact?.photo == null) {
                    itemCallHistoryImage.setImageDrawable(placeholderImage)
                } else {
                    val options = RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .error(placeholderImage)
                        .centerCrop()

                    val model: Any? = photoToLoad.ifEmpty { item.matchedContact?.photo }
                    Glide.with(activity)
                        .load(model)
                        .apply(options)
                        .apply(RequestOptions.circleCropTransform())
                        .into(itemCallHistoryImage)
                }

                itemCallHistoryImage.setOnClickListener {
                    toggleExpanded(item.id)
                }
            }
        }
    }

    private fun initiateSms(item: CallLogItem) {
        if (item.phoneNumber.isEmpty()) {
            activity.toast(org.fossify.commons.R.string.no_phone_number_found)
            return
        }
        simpleActivity.launchSendSMSIntent(item.phoneNumber)
    }

    private fun initiateVideoCall(item: CallLogItem) {
        if (item.phoneNumber.isEmpty()) {
            activity.toast(org.fossify.commons.R.string.no_phone_number_found)
            return
        }
        val uri = Uri.fromParts("tel", item.phoneNumber, null)
        simpleActivity.handlePermission(PERMISSION_CALL_PHONE) { granted ->
            try {
                val videoIntent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).apply {
                    putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
                    putExtra("videocall", true)
                    putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", VideoProfile.STATE_BIDIRECTIONAL)
                }
                simpleActivity.launchActivityIntent(videoIntent)
            } catch (_: Exception) {
                simpleActivity.startCallIntent(item.phoneNumber)
            }
        }
    }

    private fun toggleBlockNumber(item: CallLogItem, currentlyBlocked: Boolean) {
        if (item.phoneNumber.isEmpty()) {
            activity.toast(org.fossify.commons.R.string.no_phone_number_found)
            return
        }
        val newBlocked = !currentlyBlocked
        activity.config.setLocalNumberBlocked(item.phoneNumber, newBlocked)
        if (activity.isDefaultDialer()) {
            try {
                if (newBlocked) {
                    activity.addBlockedNumber(item.phoneNumber)
                } else {
                    activity.deleteBlockedNumber(item.phoneNumber)
                }
            } catch (_: Exception) {
            }
        }
        activity.toast(if (newBlocked) R.string.number_blocked_toast else R.string.number_unblocked_toast)
        onBlockStateChanged?.invoke() ?: notifyDataSetChanged()
    }

    fun initiateCall(item: CallLogItem) {
        if (item.phoneNumber.isEmpty()) {
            activity.toast(org.fossify.commons.R.string.no_phone_number_found)
            return
        }
        if (activity.config.showCallConfirmation) {
            CallConfirmationDialog(activity, item.getDisplayTitle()) {
                simpleActivity.startCallIntent(item.phoneNumber)
            }
        } else {
            simpleActivity.startCallIntent(item.phoneNumber)
        }
    }

    override fun onChange(position: Int): CharSequence {
        val row = displayList.getOrNull(position) ?: return ""
        return when (row) {
            is CallHistoryListItem.HeaderItem -> row.title
            is CallHistoryListItem.EntryItem -> DateUtils.formatDateTime(
                activity,
                row.callLogItem.timestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
            )
        }
    }
}
