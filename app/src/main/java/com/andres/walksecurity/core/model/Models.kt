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
 * @property token null = modo local: sin cuenta en el servidor (o sesión expirada);
 *   la app funciona igual y los datos se guardan solo en el teléfono.
 */
data class Session(val token: String?, val user: User) {
    val isLocal: Boolean get() = token == null
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
    /** Modo local: no se intentó registrar la alerta en el servidor. */
    val localMode: Boolean = false,
)
