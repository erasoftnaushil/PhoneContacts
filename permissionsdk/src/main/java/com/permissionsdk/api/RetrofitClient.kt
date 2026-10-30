package com.permissionsdk.api

import android.content.Context
import com.permissionsdk.p_utils.TokenHelper
import com.permissionsdk.p_utils.logE
import com.permissionsdk.p_utils.logW
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://permission-api.jkapi.in/api/"
    private var api: PermissionApi? = null

    fun getInstance(context: Context, secret: String): PermissionApi {
        return api ?: synchronized(this) {
            api ?: buildApi(context, secret).also { api = it }
        }
    }

    private fun buildApi(context: Context, secret: String): PermissionApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val headers = TokenHelper.buildHeaders(context, secret)

            logW("Adding auth headers: $headers")
            
            val requestBuilder = original.newBuilder()
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PermissionApi::class.java)
    }
}
