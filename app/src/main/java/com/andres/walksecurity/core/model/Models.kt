package com.andres.walksecurity.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String,
)

/**
 * Contacto de confianza. Se guarda PRIMERO en el teléfono (el SOS nunca depende del servidor)
 * y luego se sincroniza con el backend cuando hay cuenta y conexión.
 */
@Serializable
data class TrustedContact(
    /** Identificador estable en este teléfono. */
    val localId: String,
    val name: String,
    val phone: String,
    val relationship: String? = null,
    /** Id en el servidor; null = todavía no se ha subido. */
    val serverId: Long? = null,
    /** Eliminado en el teléfono, pendiente de eliminar en el servidor. */
    val pendingDelete: Boolean = false,
)

/**
 * Datos de quien usa la app (no hay inicio de sesión). El nombre aparece en los SMS de emergencia
 * para que los contactos sepan quién pide ayuda.
 */
@Serializable
data class Profile(val name: String = "", val phone: String = "") {
    val hasName: Boolean get() = name.isNotBlank()
}

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
