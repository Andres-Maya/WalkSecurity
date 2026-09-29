package com.andres.walksecurity.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String,
)

@Serializable
data class TrustedContact(
    val id: Long,
    val name: String,
    val phone: String,
    val relationship: String? = null,
)

data class Session(val token: String, val user: User)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timeMillis: Long,
)

/** Resultado de un envío SOS. El SMS es el canal principal porque funciona sin servidor ni datos móviles. */
data class SosResult(
    val location: GeoPoint?,
    val contactsTotal: Int,
    val smsSent: Int,
    val smsAvailable: Boolean,
    val serverRegistered: Boolean,
)
