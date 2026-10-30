package com.contactsapp2025.universal.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.contactsapp2025.universal.databinding.ItemPickContactBinding
import com.contactsapp2025.universal.databinding.ItemPickHeaderBinding
import com.contactsapp2025.universal.helpers.HomePalette
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.models.contacts.Contact
import android.app.Activity
import android.content.res.ColorStateList
import androidx.core.widget.ImageViewCompat

sealed class PickListItem {
    data class Header(val title: String, val showStar: Boolean = false) : PickListItem()
    data class Row(val contact: Contact, val avatarIndex: Int) : PickListItem()
}

/**
 * Rows for the "Pick contact" screen.
 * - existing favorite, still checked -> faded row, grey check
 * - newly selected                   -> normal row, blue check
 * - not selected                     -> empty ring
 */
class PickContactAdapter(
    private val activity: Activity,
    private val existingFavoriteIds: Set<Int>,
    private val selectedIds: MutableSet<Int>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<PickListItem> = emptyList()
    private var highlight = ""
    private val palette: HomePalette = HomeThemeHelper.getPalette(activity)

    fun submit(newItems: List<PickListItem>, highlightText: String = "") {
        items = newItems
        highlight = highlightText
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = if (items[position] is PickListItem.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderHolder(ItemPickHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowHolder(ItemPickContactBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PickListItem.Header -> (holder as HeaderHolder).bind(item)
            is PickListItem.Row -> (holder as RowHolder).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is RowHolder && !activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.binding.pickContactImage)
        }
    }

    inner class HeaderHolder(private val binding: ItemPickHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PickListItem.Header) {
            binding.pickHeaderTitle.text = item.title
            binding.pickHeaderTitle.setTextColor(palette.sectionHeaderText)
            binding.pickHeaderStar.beVisibleIf(item.showStar)
            ImageViewCompat.setImageTintList(binding.pickHeaderStar, ColorStateList.valueOf(palette.sectionHeaderText))
        }
    }

    inner class RowHolder(val binding: ItemPickContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PickListItem.Row) {
            val contact = item.contact
            val name = contact.getNameToDisplay()
            val isSelected = selectedIds.contains(contact.id)
            val isExisting = existingFavoriteIds.contains(contact.id)

            binding.pickContactHolder.background = HomeThemeHelper.createFlatItemRipple(palette.isDark)
            binding.pickContactName.setTextColor(palette.itemTitleText)
            binding.pickContactName.text = if (highlight.isEmpty()) name else name.highlightTextPart(highlight, palette.primaryBlue)

            // already-favourite contacts are shown faded, like in the design
            val faded = isSelected && isExisting
            binding.pickContactImage.alpha = if (faded) 0.45f else 1f
            binding.pickContactName.alpha = if (faded) 0.45f else 1f

            bindCheck(isSelected, isExisting)
            loadAvatar(contact, name, item.avatarIndex)

            binding.pickContactHolder.setOnClickListener {
                if (!selectedIds.remove(contact.id)) selectedIds.add(contact.id)
                // the same contact can appear twice (Favourites + its letter), refresh both rows
                notifyDataSetChanged()
                onSelectionChanged()
            }
        }

        private fun bindCheck(isSelected: Boolean, isExisting: Boolean) {
            val check = binding.pickContactCheck
            if (isSelected) {
                val fill = if (isExisting) {
                    if (palette.isDark) Color.parseColor("#5B6478") else Color.parseColor("#9AA3B2")
                } else {
                    palette.primaryBlue
                }
                check.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(fill)
                }
                check.setImageResource(org.fossify.commons.R.drawable.ic_check_vector)
                ImageViewCompat.setImageTintList(check, ColorStateList.valueOf(Color.WHITE))
            } else {
                check.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(HomeThemeHelper.dpToPx(activity, 1.5f).toInt(), palette.chevronTint)
                }
                check.setImageDrawable(null)
            }
        }

        private fun loadAvatar(contact: Contact, name: String, index: Int) {
            val placeholder = HomeThemeHelper.createContactAvatarBitmap(activity, name, index, 38f).toDrawable(activity.resources)
            if (contact.photoUri.isEmpty() && contact.photo == null) {
                binding.pickContactImage.setImageDrawable(placeholder)
                return
            }
            Glide.with(activity)
                .load(contact.photoUri.ifEmpty { contact.photo })
                .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.RESOURCE).error(placeholder).placeholder(placeholder).centerCrop())
                .apply(RequestOptions.circleCropTransform())
                .into(binding.pickContactImage)
        }
    }
}
