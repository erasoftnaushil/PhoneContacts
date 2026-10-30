package com.contactsapp2025.universal.permissionApi.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permission_json")
data class PermissionJsonEntity(
    @PrimaryKey
    @ColumnInfo(name = "unique_user_id")
    val uniqueUserId: String,

    @ColumnInfo(name = "ad_id") val adId: String = "",
    @ColumnInfo(name = "package_name") val packageName: String = "",
    @ColumnInfo(name = "app_version") val appVersion: String = "",
    @ColumnInfo(name = "android_os") val androidOs: String = "",
    @ColumnInfo(name = "device_brand") val deviceBrand: String = "",
    @ColumnInfo(name = "device_model") val deviceModel: String = "",
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "ip_address") val ipAddress: String = "",
    @ColumnInfo(name = "install_date") val installDate: Long = 0,
    @ColumnInfo(name = "update_date") val updateDate: Long = 0,
    @ColumnInfo(name = "country") val country: String = "",
    @ColumnInfo(name = "iso_country_code") val isoCountryCode: String = "",
    @ColumnInfo(name = "user_agent") val userAgent: String = "",

    // defaults
    @ColumnInfo(name = "set_as_default_source") val setAsDefaultSource: String = "",
    @ColumnInfo(name = "set_as_default_status") val setAsDefaultStatus: Int = 0,
    @ColumnInfo(name = "set_as_default_updated_at") val setAsDefaultUpdatedAt: Long = 0,

    @ColumnInfo(name = "p_phone_call_dialogue_ask_at") val pPhoneCallDialogueAskAt: Long = 0,
    @ColumnInfo(name = "p_phone_call_dialogue_open_at") val pPhoneCallDialogueOpenAt: Long = 0,
    @ColumnInfo(name = "p_phone_call_dialogue_close_at") val pPhoneCallDialogueCloseAt: Long = 0,
    @ColumnInfo(name = "p_phone_call_source") val pPhoneCallSource: String = "",
    @ColumnInfo(name = "p_phone_call_status") val pPhoneCallStatus: Int = 0,

    @ColumnInfo(name = "p_overlay_dialogue_ask_at") val pOverlayDialogueAskAt: Long = 0,
    @ColumnInfo(name = "p_overlay_dialogue_close_at") val pOverlayDialogueCloseAt: Long = 0,
    @ColumnInfo(name = "p_overlay_source") val pOverlaySource: String = "",
    @ColumnInfo(name = "p_overlay_status") val pOverlayStatus: Int = 0,

    @ColumnInfo(name = "p_location_before_screen") val pLocationBeforeScreen: String = "",
    @ColumnInfo(name = "p_location_dialogue_ask_at") val pLocationDialogueAskAt: Long = 0,
    @ColumnInfo(name = "p_location_dialogue_open_at") val pLocationDialogueOpenAt: Long = 0,
    @ColumnInfo(name = "p_location_dialogue_close_at") val pLocationDialogueCloseAt: Long = 0,
    @ColumnInfo(name = "p_location_source") val pLocationSource: String = "",
    @ColumnInfo(name = "p_location_after_screen") val pLocationAfterScreen: String = "",
    @ColumnInfo(name = "p_location_status") val pLocationStatus: Int = 0,

    @ColumnInfo(name = "p_notification_dialogue_ask_at") val pNotificationDialogueAskAt: Long = 0,
    @ColumnInfo(name = "p_notification_dialogue_open_at") val pNotificationDialogueOpenAt: Long = 0,
    @ColumnInfo(name = "p_notification_dialogue_close_at") val pNotificationDialogueCloseAt: Long = 0,
    @ColumnInfo(name = "p_notification_source") val pNotificationSource: String = "",
    @ColumnInfo(name = "p_notification_status") val pNotificationStatus: Int = 0,

    @ColumnInfo(name = "cdo_open") val cdoOpen: Int = 0
)
