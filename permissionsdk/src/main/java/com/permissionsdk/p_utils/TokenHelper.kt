package com.permissionsdk.p_utils

import android.content.Context
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


object TokenHelper {
    private fun hmacSha256Hex(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun buildHeaders(context: Context, secret: String): Map<String, String> {
        val pkg = context.packageName
        val ts = (getTrustedUtcMillis() / 1000L).toString() // UTC seconds
        val data = "$pkg.$ts"
        val token = hmacSha256Hex(secret, data)

        return mapOf(
            "X-Package-Name" to pkg,
            "X-Timestamp" to ts,
            "X-Auth-Token" to token
        )
    }


}