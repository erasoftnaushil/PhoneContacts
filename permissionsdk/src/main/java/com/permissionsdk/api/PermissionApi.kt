package com.permissionsdk.api

import com.permissionsdk.model.PermissionEntity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface PermissionApi {
    @Headers("Content-Type: application/json")
    @POST("v1/update")
    suspend fun updatePermissions(@Body permissionData: PermissionEntity): Response<PermissionResponse>
}
