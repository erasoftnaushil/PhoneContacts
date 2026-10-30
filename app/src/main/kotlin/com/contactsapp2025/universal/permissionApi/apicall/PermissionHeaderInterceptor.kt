package com.contactsapp2025.universal.permissionApi.apicall

import android.content.Context

import com.contactsapp2025.universal.BuildConfig
import com.contactsapp2025.universal.permissionApi.PermissionUtil.trustedTimeClient

import okhttp3.Interceptor
import okhttp3.Response
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PermissionHeaderInterceptor(
    private val context: Context
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        val millis = trustedTimeClient?.computeCurrentUnixEpochMillis()?.div(1000)
        val newts: String = (millis ?: (System.currentTimeMillis()/1000)).toString()

        val pkg = context.packageName
        val secret = BuildConfig.API_SECRET

        val token = hmacSha256Hex(
            secret,
            "$pkg.$newts"
        )

        val request = chain.request()
            .newBuilder()
            .header("X-Package-Name", pkg)
            .header("X-Timestamp", newts)
            .header("X-Auth-Token", token)
            .build()

        return chain.proceed(request)
    }

    fun hmacSha256Hex(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") {
            "%02x".format(it) }
    }

}
