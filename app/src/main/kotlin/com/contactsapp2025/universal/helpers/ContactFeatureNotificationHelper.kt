package com.contactsapp2025.universal.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.activities.EditContactActivity
import com.contactsapp2025.universal.activities.MainActivity
import com.contactsapp2025.universal.activities.SettingsActivity

object ContactFeatureNotificationHelper {
    const val CHANNEL_ID = "contact_feature_notifications"
    const val NOTIFICATION_ID = 7777

    data class NotificationContent(
        val title: String,
        val description: String,
        val category: String,
        val destinationClass: Class<*>
    )

    val notifications = listOf(
        NotificationContent("Your Contacts, Your Way", "Easily manage and organize your entire contact list.", "contact_management", MainActivity::class.java),
        NotificationContent("Give Your Contacts a Personal Touch", "Add profile pictures to make contacts easier to recognize.", "profile_picture", MainActivity::class.java),
        NotificationContent("Protect Your Contact List", "Back up your contacts before you need them.", "backup", SettingsActivity::class.java),
        NotificationContent("Save Someone New Today", "Add a new contact and keep their details handy.", "add_contact", EditContactActivity::class.java),
        NotificationContent("Your Favorite People, One Tap Away ⭐", "Add important contacts to Favorites for quick access.", "favorites", MainActivity::class.java),
        NotificationContent("A Cleaner Contact List Starts Here", "Update old details and keep your contacts organized.", "contact_management", MainActivity::class.java),
        NotificationContent("Restore Your Contacts Anytime", "Bring your saved contacts back whenever you need them.", "restore", SettingsActivity::class.java),
        NotificationContent("Make Your Contacts Look Better", "Add photos and keep your contact list more personal.", "profile_picture", MainActivity::class.java),
        NotificationContent("Need to Update a Contact?", "Edit names, numbers, and details whenever you need.", "edit_contact", MainActivity::class.java),
        NotificationContent("Keep Your Contacts Organized", "Add, edit, and manage your contacts with ease.", "contact_management", MainActivity::class.java),
        NotificationContent("Make Important Contacts Stand Out", "Add your favorite people for faster access.", "favorites", MainActivity::class.java),
        NotificationContent("Your Contact Backup Is Waiting", "Save your contacts and keep them ready for restore.", "backup", SettingsActivity::class.java),
        NotificationContent("Personalize Your Contact List", "Add photos and details to make contacts feel more personal.", "profile_picture", MainActivity::class.java),
        NotificationContent("Quickly Find Your Favorites", "Keep the people you contact most right at your fingertips.", "favorites", MainActivity::class.java),
        NotificationContent("Add Details to Your Contacts", "Keep names, numbers, photos, and other details together.", "edit_contact", MainActivity::class.java),
        NotificationContent("Keep Your Contact List Up to Date", "Quickly edit and update contact information anytime.", "edit_contact", MainActivity::class.java),
        NotificationContent("Add a New Contact", "Save a new contact in just a few taps.", "add_contact", EditContactActivity::class.java),
        NotificationContent("Keep Your Contacts Safe", "Back up your contacts so your important numbers stay protected.", "backup", SettingsActivity::class.java),
        NotificationContent("A Little Contact Cleanup?", "Update outdated contact details and keep things tidy.", "contact_management", MainActivity::class.java),
        NotificationContent("Make Contacts Easier to Recognize", "Add profile pictures to your important contacts.", "profile_picture", MainActivity::class.java),
        NotificationContent("One Place for All Your Contacts", "Easily view, edit, and organize your contact list.", "contact_management", MainActivity::class.java),
        NotificationContent("Keep Your Important People Close", "Add your most-used contacts to Favorites.", "favorites", MainActivity::class.java),
        NotificationContent("Your Contacts Deserve a Backup", "Protect your important numbers with a quick backup.", "backup", SettingsActivity::class.java),
        NotificationContent("Ready to Organize Your Contacts?", "Explore simple tools to manage your contacts better.", "contact_management", MainActivity::class.java),
        NotificationContent("Never Lose an Important Contact", "Create a backup and keep your contacts protected.", "backup", SettingsActivity::class.java)
    )

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Contact Tips & Features",
                    NotificationManager.IMPORTANCE_DEFAULT // Default importance per requirements "Do not force unnecessary sound/vibration"
                )
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun buildNotification(
        context: Context,
        content: NotificationContent,
        index: Int,
        count: Int
    ): android.app.Notification {
        createChannel(context)

        val actionIntent = Intent(context, com.contactsapp2025.universal.receivers.ContactFeatureActionActivity::class.java).apply {
            putExtra("notification_index", index)
            putExtra("notification_category", content.category)
            putExtra("notification_count", count)
            putExtra("destination_class", content.destinationClass.name)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            index, // Use index to differentiate intents if needed
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val collapsedView = RemoteViews(context.packageName, R.layout.layout_notification_onboarding)
        collapsedView.setTextViewText(R.id.tv_title, content.title)
        collapsedView.setOnClickPendingIntent(R.id.btn_view, pendingIntent)

        val expandedView = RemoteViews(context.packageName, R.layout.layout_notification_onboarding_expanded)
        expandedView.setTextViewText(R.id.tv_title, content.title)
        expandedView.setTextViewText(R.id.tv_message, content.description)
        expandedView.setOnClickPendingIntent(R.id.btn_view, pendingIntent)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti_icon)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentIntent(pendingIntent) // Tap on body also goes to action
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
