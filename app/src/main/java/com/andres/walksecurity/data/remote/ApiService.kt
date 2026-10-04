package com.andres.walksecurity.data.remote

import com.andres.walksecurity.core.model.User
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Contrato con el backend Spring Boot (ver backend/README.md). */
interface ApiService {

    /** Identificación automática del teléfono (la app no tiene inicio de sesión). */
    @POST("api/auth/device")
    suspend fun registerDevice(@Body body: DeviceRequest): AuthResponse

    @GET("api/contacts")
    suspend fun contacts(): List<ContactDto>

    @POST("api/contacts")
    suspend fun addContact(@Body body: ContactRequest): ContactDto

    @DELETE("api/contacts/{id}")
    suspend fun deleteContact(@Path("id") id: Long): Response<Unit>

    @POST("api/alerts")
    suspend fun createAlert(@Body body: AlertRequest): AlertResponse
}

@Serializable
data class DeviceRequest(val deviceId: String, val name: String, val phone: String? = null)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class ContactDto(val id: Long, val name: String, val phone: String, val relationship: String? = null)

@Serializable
data class ContactRequest(val name: String, val phone: String, val relationship: String? = null)

@Serializable
data class AlertRequest(
    val type: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val message: String,
)

@Serializable
data class AlertResponse(val id: Long, val type: String, val status: String, val createdAt: String)

@Serializable
data class ErrorResponse(val message: String? = null)
