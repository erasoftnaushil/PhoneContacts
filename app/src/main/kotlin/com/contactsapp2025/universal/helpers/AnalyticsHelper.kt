package com.contactsapp2025.universal.helpers

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsHelper {
    fun logEvent(context: Context, eventName: String, params: Bundle? = null) {
        try {
            FirebaseAnalytics.getInstance(context).logEvent(eventName, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
