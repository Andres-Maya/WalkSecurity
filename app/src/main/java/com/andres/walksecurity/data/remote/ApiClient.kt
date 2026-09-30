package com.andres.walksecurity.data.remote

import com.andres.walksecurity.BuildConfig
import com.andres.walksecurity.data.local.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

object ApiClient {

    fun create(baseUrl: String, sessionStore: SessionStore): ApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(sessionStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC para no imprimir contraseñas ni tokens en Logcat
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

/**
 * Adjunta el JWT. Si el servidor lo rechaza (expirado), se pasa a modo local
 * conservando los contactos de emergencia; la app pedirá volver a iniciar sesión.
 */
private class AuthInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/api/auth/")) return chain.proceed(request)

        // Se ejecuta en hilos de OkHttp, nunca en el hilo principal
        val token = runBlocking { sessionStore.token() }
        val authorized = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        val response = chain.proceed(authorized)
        if (response.code == 401 && token != null) {
            runBlocking { sessionStore.clearToken() }
        }
        return response
    }
}
