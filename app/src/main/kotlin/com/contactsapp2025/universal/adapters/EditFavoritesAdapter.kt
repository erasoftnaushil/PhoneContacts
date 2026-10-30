package com.contactsapp2025.universal.adapters

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.helpers.HomeThemeHelper
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.models.contacts.Contact
import java.util.Collections

/** Grid of current favorites with a small "x" badge to remove each one; long-press to drag and reorder. */
class EditFavoritesAdapter(
    private val activity: Activity,
    val items: MutableList<Contact>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<EditFavoritesAdapter.Holder>() {

    private val palette = HomeThemeHelper.getPalette(activity)

    inner class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(org.fossify.commons.R.id.item_contact_image)
        val name: TextView = view.findViewById(org.fossify.commons.R.id.item_contact_name)
        val badge: ImageView = view.findViewById(R.id.favorite_remove_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_grid, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val contact = items[position]
        val name = contact.getNameToDisplay()
        holder.name.text = name
        holder.name.setTextColor(palette.itemTitleText)

        holder.badge.beVisible()
        holder.badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.bottomNavPill)
            setStroke(HomeThemeHelper.dpToPx(activity, 1.5f).toInt(), palette.pageBg)
        }
        ImageViewCompat.setImageTintList(holder.badge, ColorStateList.valueOf(palette.fabAddIcon))
        holder.badge.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
                onChanged()
            }
        }

        val placeholder = HomeThemeHelper.createContactAvatarBitmap(activity, name, contact.id, 62f)
            .toDrawable(activity.resources)
        if (contact.photoUri.isEmpty() && contact.photo == null) {
            holder.image.setImageDrawable(placeholder)
        } else {
            Glide.with(activity)
                .load(contact.photoUri.ifEmpty { contact.photo })
                .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.RESOURCE).error(placeholder).placeholder(placeholder).centerCrop())
                .apply(RequestOptions.circleCropTransform())
                .into(holder.image)
        }
    }

    /** Long-press drag in any direction to reorder the grid. */
    fun createTouchHelper() = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
    ) {
        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val fromPos = from.bindingAdapterPosition
            val toPos = to.bindingAdapterPosition
            if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
            if (fromPos < toPos) {
                for (i in fromPos until toPos) Collections.swap(items, i, i + 1)
            } else {
                for (i in fromPos downTo toPos + 1) Collections.swap(items, i, i - 1)
            }
            notifyItemMoved(fromPos, toPos)
            onChanged()
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.animate()?.scaleX(1.08f)?.scaleY(1.08f)?.setDuration(120)?.start()
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
    })
}
