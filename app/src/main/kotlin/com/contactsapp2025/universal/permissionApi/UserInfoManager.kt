package com.contactsapp2025.universal.permissionApi

import android.content.Context
import android.util.Log
import com.contactsapp2025.universal.permissionApi.apicall.PermissionApiClient

class UserInfoManager(context: Context) {

    private val api = PermissionApiClient.create(context)
    private val userData = UserData()

    suspend fun sendPayload(payload: Map<String, Any>) {
        try {
            val response = api.updateUser(payload)
            Log.e("PERMISSION_API", "SERVER RESPONSE : $response")
            Log.e("PERMISSION_API", "RECORD_SENT NEW $payload")
        } catch (e: Exception) {
            Log.e("PERMISSION_API", "sendPayload Error == ${e.message}")
        }
    }

    fun userData(): UserData = userData
}
