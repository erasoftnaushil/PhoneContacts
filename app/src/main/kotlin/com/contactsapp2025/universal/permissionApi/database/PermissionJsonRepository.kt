package com.contactsapp2025.universal.permissionApi.database

import android.util.Log
import org.json.JSONObject

class PermissionJsonRepository(
    private val dao: PermissionJsonDao
) {

    suspend fun upsert(
        userId: String,
        updates: Map<String, Any?>
    ) {
        try {
            val existing = dao.getByUserId(userId) ?: PermissionJsonEntity(uniqueUserId = userId)

            var adId = existing.adId
            var packageName = existing.packageName
            var appVersion = existing.appVersion
            var androidOs = existing.androidOs
            var deviceBrand = existing.deviceBrand
            var deviceModel = existing.deviceModel
            var deviceId = existing.deviceId
            var ipAddress = existing.ipAddress
            var installDate = existing.installDate
            var updateDate = existing.updateDate
            var country = existing.country
            var isoCountryCode = existing.isoCountryCode
            var userAgent = existing.userAgent

            var setAsDefaultSource = existing.setAsDefaultSource
            var setAsDefaultStatus = existing.setAsDefaultStatus
            var setAsDefaultUpdatedAt = existing.setAsDefaultUpdatedAt

            var pPhoneCallDialogueAskAt = existing.pPhoneCallDialogueAskAt
            var pPhoneCallDialogueOpenAt = existing.pPhoneCallDialogueOpenAt
            var pPhoneCallDialogueCloseAt = existing.pPhoneCallDialogueCloseAt
            var pPhoneCallSource = existing.pPhoneCallSource
            var pPhoneCallStatus = existing.pPhoneCallStatus

            var pOverlayDialogueAskAt = existing.pOverlayDialogueAskAt
            var pOverlayDialogueCloseAt = existing.pOverlayDialogueCloseAt
            var pOverlaySource = existing.pOverlaySource
            var pOverlayStatus = existing.pOverlayStatus

            var pLocationBeforeScreen = existing.pLocationBeforeScreen
            var pLocationDialogueAskAt = existing.pLocationDialogueAskAt
            var pLocationDialogueOpenAt = existing.pLocationDialogueOpenAt
            var pLocationDialogueCloseAt = existing.pLocationDialogueCloseAt
            var pLocationSource = existing.pLocationSource
            var pLocationAfterScreen = existing.pLocationAfterScreen
            var pLocationStatus = existing.pLocationStatus

            var pNotificationDialogueAskAt = existing.pNotificationDialogueAskAt
            var pNotificationDialogueOpenAt = existing.pNotificationDialogueOpenAt
            var pNotificationDialogueCloseAt = existing.pNotificationDialogueCloseAt
            var pNotificationSource = existing.pNotificationSource
            var pNotificationStatus = existing.pNotificationStatus

            var cdoOpen = existing.cdoOpen

            updates.forEach { (key, value) ->
                when (key) {
                    "ad_id" -> adId = value as? String ?: adId
                    "package_name" -> packageName = value as? String ?: packageName
                    "app_version" -> appVersion = value as? String ?: appVersion
                    "android_os" -> androidOs = value as? String ?: androidOs
                    "device_brand" -> deviceBrand = value as? String ?: deviceBrand
                    "device_model" -> deviceModel = value as? String ?: deviceModel
                    "device_id" -> deviceId = value as? String ?: deviceId
                    "ip_address" -> ipAddress = value as? String ?: ipAddress
                    "install_date" -> installDate = (value as? Number)?.toLong() ?: installDate
                    "update_date" -> updateDate = (value as? Number)?.toLong() ?: updateDate
                    "country" -> country = value as? String ?: country
                    "iso_country_code" -> isoCountryCode = value as? String ?: isoCountryCode
                    "user_agent" -> userAgent = value as? String ?: userAgent

                    "set_as_default_source" -> setAsDefaultSource = value as? String ?: setAsDefaultSource
                    "set_as_default_status" -> setAsDefaultStatus = (value as? Number)?.toInt() ?: setAsDefaultStatus
                    "set_as_default_updated_at" -> setAsDefaultUpdatedAt = (value as? Number)?.toLong() ?: setAsDefaultUpdatedAt

                    "p_phone_call_dialogue_ask_at" -> pPhoneCallDialogueAskAt = (value as? Number)?.toLong() ?: pPhoneCallDialogueAskAt
                    "p_phone_call_dialogue_open_at" -> pPhoneCallDialogueOpenAt = (value as? Number)?.toLong() ?: pPhoneCallDialogueOpenAt
                    "p_phone_call_dialogue_close_at" -> pPhoneCallDialogueCloseAt = (value as? Number)?.toLong() ?: pPhoneCallDialogueCloseAt
                    "p_phone_call_source" -> pPhoneCallSource = value as? String ?: pPhoneCallSource
                    "p_phone_call_status" -> pPhoneCallStatus = (value as? Number)?.toInt() ?: pPhoneCallStatus

                    "p_overlay_dialogue_ask_at" -> pOverlayDialogueAskAt = (value as? Number)?.toLong() ?: pOverlayDialogueAskAt
                    "p_overlay_dialogue_close_at" -> pOverlayDialogueCloseAt = (value as? Number)?.toLong() ?: pOverlayDialogueCloseAt
                    "p_overlay_source" -> pOverlaySource = value as? String ?: pOverlaySource
                    "p_overlay_status" -> pOverlayStatus = (value as? Number)?.toInt() ?: pOverlayStatus

                    "p_location_before_screen" -> pLocationBeforeScreen = value as? String ?: pLocationBeforeScreen
                    "p_location_dialogue_ask_at" -> pLocationDialogueAskAt = (value as? Number)?.toLong() ?: pLocationDialogueAskAt
                    "p_location_dialogue_open_at" -> pLocationDialogueOpenAt = (value as? Number)?.toLong() ?: pLocationDialogueOpenAt
                    "p_location_dialogue_close_at" -> pLocationDialogueCloseAt = (value as? Number)?.toLong() ?: pLocationDialogueCloseAt
                    "p_location_source" -> pLocationSource = value as? String ?: pLocationSource
                    "p_location_after_screen" -> pLocationAfterScreen = value as? String ?: pLocationAfterScreen
                    "p_location_status" -> pLocationStatus = (value as? Number)?.toInt() ?: pLocationStatus

                    "p_notification_dialogue_ask_at" -> pNotificationDialogueAskAt = (value as? Number)?.toLong() ?: pNotificationDialogueAskAt
                    "p_notification_dialogue_open_at" -> pNotificationDialogueOpenAt = (value as? Number)?.toLong() ?: pNotificationDialogueOpenAt
                    "p_notification_dialogue_close_at" -> pNotificationDialogueCloseAt = (value as? Number)?.toLong() ?: pNotificationDialogueCloseAt
                    "p_notification_source" -> pNotificationSource = value as? String ?: pNotificationSource
                    "p_notification_status" -> pNotificationStatus = (value as? Number)?.toInt() ?: pNotificationStatus

                    "cdo_open" -> cdoOpen = (value as? Number)?.toInt() ?: cdoOpen
                }
            }

            dao.insert(
                PermissionJsonEntity(
                    uniqueUserId = userId,
                    adId = adId,
                    packageName = packageName,
                    appVersion = appVersion,
                    androidOs = androidOs,
                    deviceBrand = deviceBrand,
                    deviceModel = deviceModel,
                    deviceId = deviceId,
                    ipAddress = ipAddress,
                    installDate = installDate,
                    updateDate = updateDate,
                    country = country,
                    isoCountryCode = isoCountryCode,
                    userAgent = userAgent,
                    setAsDefaultSource = setAsDefaultSource,
                    setAsDefaultStatus = setAsDefaultStatus,
                    setAsDefaultUpdatedAt = setAsDefaultUpdatedAt,
                    pPhoneCallDialogueAskAt = pPhoneCallDialogueAskAt,
                    pPhoneCallDialogueOpenAt = pPhoneCallDialogueOpenAt,
                    pPhoneCallDialogueCloseAt = pPhoneCallDialogueCloseAt,
                    pPhoneCallSource = pPhoneCallSource,
                    pPhoneCallStatus = pPhoneCallStatus,
                    pOverlayDialogueAskAt = pOverlayDialogueAskAt,
                    pOverlayDialogueCloseAt = pOverlayDialogueCloseAt,
                    pOverlaySource = pOverlaySource,
                    pOverlayStatus = pOverlayStatus,
                    pLocationBeforeScreen = pLocationBeforeScreen,
                    pLocationDialogueAskAt = pLocationDialogueAskAt,
                    pLocationDialogueOpenAt = pLocationDialogueOpenAt,
                    pLocationDialogueCloseAt = pLocationDialogueCloseAt,
                    pLocationSource = pLocationSource,
                    pLocationAfterScreen = pLocationAfterScreen,
                    pLocationStatus = pLocationStatus,
                    pNotificationDialogueAskAt = pNotificationDialogueAskAt,
                    pNotificationDialogueOpenAt = pNotificationDialogueOpenAt,
                    pNotificationDialogueCloseAt = pNotificationDialogueCloseAt,
                    pNotificationSource = pNotificationSource,
                    pNotificationStatus = pNotificationStatus,
                    cdoOpen = cdoOpen
                )
            )
        } catch (ee: Exception) {
            Log.e("AAA", "upsert: print catch error - " + ee.toString())
        }
    }

    suspend fun getPayload(userId: String): JSONObject? {
        val entity = dao.getByUserId(userId) ?: return null
        return JSONObject().apply {
            put("unique_user_id", entity.uniqueUserId)
            put("ad_id", entity.adId)
            put("package_name", entity.packageName)
            put("app_version", entity.appVersion)
            put("android_os", entity.androidOs)
            put("device_brand", entity.deviceBrand)
            put("device_model", entity.deviceModel)
            put("device_id", entity.deviceId)
            put("ip_address", entity.ipAddress)
            put("install_date", entity.installDate)
            put("update_date", entity.updateDate)
            put("country", entity.country)
            put("iso_country_code", entity.isoCountryCode)
            put("user_agent", entity.userAgent)

            put("set_as_default_source", entity.setAsDefaultSource)
            put("set_as_default_status", entity.setAsDefaultStatus)
            put("set_as_default_updated_at", entity.setAsDefaultUpdatedAt)

            put("p_phone_call_dialogue_ask_at", entity.pPhoneCallDialogueAskAt)
            put("p_phone_call_dialogue_open_at", entity.pPhoneCallDialogueOpenAt)
            put("p_phone_call_dialogue_close_at", entity.pPhoneCallDialogueCloseAt)
            put("p_phone_call_source", entity.pPhoneCallSource)
            put("p_phone_call_status", entity.pPhoneCallStatus)

            put("p_overlay_dialogue_ask_at", entity.pOverlayDialogueAskAt)
            put("p_overlay_dialogue_close_at", entity.pOverlayDialogueCloseAt)
            put("p_overlay_source", entity.pOverlaySource)
            put("p_overlay_status", entity.pOverlayStatus)

            put("p_location_before_screen", entity.pLocationBeforeScreen)
            put("p_location_dialogue_ask_at", entity.pLocationDialogueAskAt)
            put("p_location_dialogue_open_at", entity.pLocationDialogueOpenAt)
            put("p_location_dialogue_close_at", entity.pLocationDialogueCloseAt)
            put("p_location_source", entity.pLocationSource)
            put("p_location_after_screen", entity.pLocationAfterScreen)
            put("p_location_status", entity.pLocationStatus)

            put("p_notification_dialogue_ask_at", entity.pNotificationDialogueAskAt)
            put("p_notification_dialogue_open_at", entity.pNotificationDialogueOpenAt)
            put("p_notification_dialogue_close_at", entity.pNotificationDialogueCloseAt)
            put("p_notification_source", entity.pNotificationSource)
            put("p_notification_status", entity.pNotificationStatus)

            put("cdo_open", entity.cdoOpen)
        }
    }
}
