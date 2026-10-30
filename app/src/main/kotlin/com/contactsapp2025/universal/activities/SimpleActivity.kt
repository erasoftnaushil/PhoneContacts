package com.contactsapp2025.universal.activities

import android.content.ContentValues
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.KEY_PHONE
import com.contactsapp2025.universal.R
import com.contactsapp2025.universal.helpers.KEY_MAILTO
import com.contactsapp2025.universal.helpers.LOCATION_CALL_HISTORY_TAB
import com.contactsapp2025.universal.helpers.LOCATION_CONTACTS_TAB
import com.contactsapp2025.universal.helpers.LOCATION_FAVORITES_TAB
import com.contactsapp2025.universal.helpers.LOCATION_GROUP_CONTACTS

open class SimpleActivity : BaseSimpleActivity() {

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    protected fun getPhoneNumberFromIntent(intent: Intent): String? {
        if (intent.extras?.containsKey(KEY_PHONE) == true) {
            return intent.getStringExtra(KEY_PHONE)
        } else if (intent.extras?.containsKey("data") == true) {
            // sample contact number from Google Contacts:
            // data: [data1=+123 456 789 mimetype=vnd.android.cursor.item/phone_v2 _id=-1 data2=0]
            val data = intent.extras!!.get("data")
            if (data != null) {
                val contentValues = (data as? ArrayList<*>)?.firstOrNull() as? ContentValues
                if (contentValues != null && contentValues.containsKey("data1")) {
                    return contentValues.getAsString("data1")
                }
            }
        }
        return null
    }

    protected fun getEmailFromIntent(intent: Intent): String? {
        return if (intent.dataString?.startsWith("$KEY_MAILTO:") == true) {
            Uri.decode(intent.dataString!!.substringAfter("$KEY_MAILTO:").trim())
        } else {
            null
        }
    }

    protected fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_tab_recent_selector
            1 -> R.drawable.ic_tab_contacts_selector
            2 -> R.drawable.ic_tab_favorites_selector
            else -> R.drawable.ic_tab_favorites_selector
        }

        return androidx.core.content.ContextCompat.getDrawable(this, drawableId)!!
    }

    protected fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> org.fossify.commons.R.string.call_history_tab
            1 -> org.fossify.commons.R.string.contacts_tab
            2 -> org.fossify.commons.R.string.favorites_tab
            else -> org.fossify.commons.R.string.favorites_tab
        }

        return resources.getString(stringId)
    }
}
