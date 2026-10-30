package com.permissionsdk.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "permissions")
data class PermissionEntity(
    @PrimaryKey
    @SerializedName("unique_user_id")
    val uniqueUserId: String,
    
    @SerializedName("ad_id")
    val adId: String = "",
    
    @SerializedName("package_name")
    val packageName: String = "",
    
    @SerializedName("app_version")
    val appVersion: String = "",
    
    @SerializedName("android_os")
    val androidOs: String = "",
    
    @SerializedName("device_brand")
    val deviceBrand: String = "",
    
    @SerializedName("device_model")
    val deviceModel: String = "",
    
    @SerializedName("device_id")
    val deviceId: String = "",
    
    @SerializedName("ip_address")
    val ipAddress: String = "",
    
    @SerializedName("install_date")
    val installDate: Long = 0,
    
    @SerializedName("update_date")
    val updateDate: Long = 0,
    
    @SerializedName("country")
    val country: String = "",
    
    @SerializedName("iso_country_code")
    val isoCountryCode: String = "",
    
    @SerializedName("user_agent")
    val userAgent: String = "",
    
    @SerializedName("set_as_default_source")
    val setAsDefaultSource: String = "",
    
    @SerializedName("set_as_default_status")
    val setAsDefaultStatus: Int = 0,
    
    @SerializedName("set_as_default_updated_at")
    val setAsDefaultUpdatedAt: Long = 0,
    
    @SerializedName("p_phone_call_dialogue_ask_at")
    val pPhoneCallDialogueAskAt: Long = 0,
    
    @SerializedName("p_phone_call_dialogue_open_at")
    val pPhoneCallDialogueOpenAt: Long = 0,
    
    @SerializedName("p_phone_call_dialogue_close_at")
    val pPhoneCallDialogueCloseAt: Long = 0,
    
    @SerializedName("p_phone_call_source")
    val pPhoneCallSource: String = "",
    
    @SerializedName("p_phone_call_status")
    val pPhoneCallStatus: Int = 0,
    
    @SerializedName("p_overlay_dialogue_ask_at")
    val pOverlayDialogueAskAt: Long = 0,
    
    @SerializedName("p_overlay_dialogue_open_at")
    val pOverlayDialogueOpenAt: Long = 0,
    
    @SerializedName("p_overlay_dialogue_close_at")
    val pOverlayDialogueCloseAt: Long = 0,
    
    @SerializedName("p_overlay_source")
    val pOverlaySource: String = "",
    
    @SerializedName("p_overlay_status")
    val pOverlayStatus: Int = 0,
    
    @SerializedName("p_location_before_screen")
    val pLocationBeforeScreen: String = "",
    
    @SerializedName("p_location_dialogue_ask_at")
    val pLocationDialogueAskAt: Long = 0,
    
    @SerializedName("p_location_dialogue_open_at")
    val pLocationDialogueOpenAt: Long = 0,
    
    @SerializedName("p_location_dialogue_close_at")
    val pLocationDialogueCloseAt: Long = 0,
    
    @SerializedName("p_location_source")
    val pLocationSource: String = "",
    
    @SerializedName("p_location_after_screen")
    val pLocationAfterScreen: String = "",
    
    @SerializedName("p_location_status")
    val pLocationStatus: Int = 0,
    
    @SerializedName("p_notification_dialogue_ask_at")
    val pNotificationDialogueAskAt: Long = 0,
    
    @SerializedName("p_notification_dialogue_open_at")
    val pNotificationDialogueOpenAt: Long = 0,
    
    @SerializedName("p_notification_dialogue_close_at")
    val pNotificationDialogueCloseAt: Long = 0,
    
    @SerializedName("p_notification_source")
    val pNotificationSource: String = "",
    
    @SerializedName("p_notification_status")
    val pNotificationStatus: Int = 0,
    
    @SerializedName("cdo_open")
    val cdoOpen: Int = 0,

    @SerializedName("is_synced")
    val isSynced: Boolean = false
)
