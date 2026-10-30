package com.contactsapp2025.universal.adapters

import android.content.res.ColorStateList
import android.provider.CallLog.Calls
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.SimpleActivity
import com.contactsapp2025.universal.databinding.ItemDialpadActionBinding
import com.contactsapp2025.universal.databinding.ItemDialpadEntryBinding
import com.contactsapp2025.universal.databinding.ItemDialpadHeaderBinding
import com.contactsapp2025.universal.helpers.HomePalette
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.contacts.Contact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DialpadAction { CREATE_CONTACT, ADD_TO_CONTACT, SEND_MESSAGE }

sealed class DialpadListItem {
    data class Header(val title: String) : DialpadListItem()

    data class Recent(val item: CallLogItem) : DialpadListItem()

    /**
     * A contact matching the typed digits.
     * [nameMatch] is the T9 range inside the name to highlight (or null),
     * [numberQuery] is the raw digit query to highlight inside the number (or empty).
     */
    data class ContactMatch(
        val contact: Contact,
        val number: String,
        val nameMatch: IntRange?,
        val numberQuery: String
    ) : DialpadListItem()

    data class Action(val action: DialpadAction) : DialpadListItem()
}

class DialpadAdapter(
    private val activity: SimpleActivity,
    private val onCallNumber: (name: String, number: String) -> Unit,
    private val onEntryClick: (number: String) -> Unit,
    private val onActionClick: (DialpadAction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
        private const val TYPE_ACTION = 2
    }

    private var items: List<DialpadListItem> = emptyList()
    private var palette: HomePalette = HomeThemeHelper.getPalette(activity)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val lettersHelper = SimpleContactsHelper(activity)

    fun submit(newItems: List<DialpadListItem>) {
        palette = HomeThemeHelper.getPalette(activity)
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is DialpadListItem.Header -> TYPE_HEADER
        is DialpadListItem.Action -> TYPE_ACTION
        else -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemDialpadHeaderBinding.inflate(inflater, parent, false))
            TYPE_ACTION -> ActionHolder(ItemDialpadActionBinding.inflate(inflater, parent, false))
            else -> EntryHolder(ItemDialpadEntryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DialpadListItem.Header -> (holder as HeaderHolder).bind(item)
            is DialpadListItem.Recent -> (holder as EntryHolder).bindRecent(item.item)
            is DialpadListItem.ContactMatch -> (holder as EntryHolder).bindContact(item)
            is DialpadListItem.Action -> (holder as ActionHolder).bind(item.action)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is EntryHolder && !activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.binding.dialpadEntryImage)
        }
    }

    inner class HeaderHolder(private val binding: ItemDialpadHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DialpadListItem.Header) {
            binding.dialpadHeaderTitle.text = item.title
            binding.dialpadHeaderTitle.setTextColor(palette.sectionHeaderText)
        }
    }

    inner class ActionHolder(private val binding: ItemDialpadActionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(action: DialpadAction) {
            val (icon, text) = when (action) {
                DialpadAction.CREATE_CONTACT -> R.drawable.ic_person_add_header to org.fossify.commons.R.string.create_new_contact
                DialpadAction.ADD_TO_CONTACT -> R.drawable.ic_person_add_header to R.string.dialpad_add_to_contact
                DialpadAction.SEND_MESSAGE -> R.drawable.ic_action_text_vector to R.string.dialpad_send_message
            }
            binding.dialpadActionIcon.setImageResource(icon)
            ImageViewCompat.setImageTintList(binding.dialpadActionIcon, ColorStateList.valueOf(palette.itemTitleText))
            binding.dialpadActionTitle.setText(text)
            binding.dialpadActionTitle.setTextColor(palette.itemTitleText)
            binding.dialpadActionHolder.background = HomeThemeHelper.createFlatItemRipple(palette.isDark)
            binding.dialpadActionHolder.setOnClickListener { onActionClick(action) }
        }
    }

    inner class EntryHolder(val binding: ItemDialpadEntryBinding) : RecyclerView.ViewHolder(binding.root) {

        private fun applyCommonStyle() {
            binding.dialpadEntryHolder.background = HomeThemeHelper.createFlatItemRipple(palette.isDark)
            binding.dialpadEntryTitle.setTextColor(palette.itemTitleText)
            binding.dialpadEntrySubtitle.setTextColor(palette.itemSubtitleText)
            ImageViewCompat.setImageTintList(binding.dialpadEntryCall, ColorStateList.valueOf(palette.callBtnIcon))
        }

        fun bindRecent(item: CallLogItem) {
            applyCommonStyle()
            val title = item.getDisplayTitle()
            binding.dialpadEntryTitle.text = item.getDisplayTitleWithCount()

            val iconRes = when (item.callType) {
                Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing_vector
                Calls.MISSED_TYPE, Calls.REJECTED_TYPE -> R.drawable.ic_call_missed_vector
                Calls.BLOCKED_TYPE -> R.drawable.ic_call_blocked_vector
                else -> R.drawable.ic_call_incoming_vector
            }
            binding.dialpadEntryTypeIcon.setImageResource(iconRes)
            binding.dialpadEntryTypeIcon.beVisibleIf(true)

            val date = Date(item.timestamp)
            val time = timeFormat.format(date).uppercase(Locale.getDefault())
            val whenText = if (android.text.format.DateUtils.isToday(item.timestamp)) time else "${dateFormat.format(date)}, $time"
            binding.dialpadEntrySubtitle.text = "$whenText  •  ${item.simLabel}"

            val photo = item.matchedContact?.photoUri?.ifEmpty { item.photoUri } ?: item.photoUri
            loadAvatar(title, photo, item.matchedContact?.photo)

            binding.dialpadEntryHolder.setOnClickListener { onEntryClick(item.phoneNumber) }
            binding.dialpadEntryCall.setOnClickListener { onCallNumber(title, item.phoneNumber) }
        }

        fun bindContact(match: DialpadListItem.ContactMatch) {
            applyCommonStyle()
            val name = match.contact.getNameToDisplay().ifEmpty { match.number }

            binding.dialpadEntryTitle.text = match.nameMatch?.let { range ->
                SpannableString(name).apply {
                    setSpan(
                        ForegroundColorSpan(palette.primaryBlue),
                        range.first,
                        (range.last + 1).coerceAtMost(name.length),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } ?: name

            binding.dialpadEntryTypeIcon.beVisibleIf(false)
            binding.dialpadEntrySubtitle.text = if (match.numberQuery.isNotEmpty()) {
                match.number.highlightTextPart(match.numberQuery, palette.primaryBlue, highlightAll = false, ignoreCharsBetweenDigits = true)
            } else {
                match.number
            }

            loadAvatar(name, match.contact.photoUri, match.contact.photo)

            binding.dialpadEntryHolder.setOnClickListener { onEntryClick(match.number) }
            binding.dialpadEntryCall.setOnClickListener { onCallNumber(name, match.number) }
        }

        private fun loadAvatar(title: String, photoUri: String, photo: android.graphics.Bitmap?) {
            val placeholder = lettersHelper.getContactLetterIcon(title).toDrawable(activity.resources)
            if (photoUri.isEmpty() && photo == null) {
                binding.dialpadEntryImage.setImageDrawable(placeholder)
                return
            }
            val model: Any? = photoUri.ifEmpty { photo }
            Glide.with(activity)
                .load(model)
                .apply(
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .error(placeholder)
                        .placeholder(placeholder)
                        .centerCrop()
                )
                .apply(RequestOptions.circleCropTransform())
                .into(binding.dialpadEntryImage)
        }
    }
}
