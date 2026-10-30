package com.permissionsdk.api

import com.google.gson.annotations.SerializedName

data class PermissionResponse(
    @SerializedName("ok")
    val ok: Boolean,
    @SerializedName("document")
    val document: Any? = null
)
