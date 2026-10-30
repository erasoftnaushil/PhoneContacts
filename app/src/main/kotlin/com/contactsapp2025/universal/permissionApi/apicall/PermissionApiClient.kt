package com.contactsapp2025.universal.permissionApi.apicall

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object PermissionApiClient {

    const val baseUrl = "https://permission-api.jkapi.in/"

    fun create(context: Context): PermissionApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(PermissionHeaderInterceptor(context))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
            )
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(PermissionApi::class.java)
    }
}
