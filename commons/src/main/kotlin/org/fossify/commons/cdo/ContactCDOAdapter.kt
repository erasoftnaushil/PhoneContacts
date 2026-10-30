package org.fossify.commons.cdo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.R
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.contacts.Contact

class ContactCDOAdapter(
    private val onItemClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactCDOAdapter.ContactViewHolder>(ContactDiffCallback()) {

    // ViewHolder class to bind data to item views
    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.item_contact_name)
        private val phoneNumberTextView: TextView = itemView.findViewById(R.id.item_contact_number)
        private val imageView: ImageView = itemView.findViewById(R.id.item_contact_image)

        fun bind(contact: Contact) {
            nameTextView.text = contact.name
            phoneNumberTextView.text = contact.phoneNumbers.firstOrNull()?.value ?: ""
            val context = itemView.context
            val fullName = contact.getNameToDisplay()
            val placeholderImage = SimpleContactsHelper(context).getContactLetterIcon(fullName).toDrawable(context.resources)
            val options = RequestOptions()
                .signature(ObjectKey(contact.getSignatureKey()))
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .error(placeholderImage)
                .centerCrop()

            val itemToLoad: Any? = contact.photoUri.ifEmpty {
                contact.photo
            }
            Glide.with(context)
                .load(itemToLoad)
                .apply(options)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView)
            itemView.setOnClickListener {
                onItemClick(contact) // Handle item click
            }
        }
    }

    // Create the view holder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_with_number, parent, false)
        return ContactViewHolder(view)
    }

    // Bind data to the view holder
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact)
    }
}

