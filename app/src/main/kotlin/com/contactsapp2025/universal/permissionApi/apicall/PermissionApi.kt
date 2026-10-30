package com.contactsapp2025.universal.permissionApi.apicall

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PermissionApi {

    @POST("api/v1/update")
    suspend fun updateUser(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>
}
