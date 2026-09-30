package com.andres.walksecurity.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Enlace entre la app del teléfono y el reloj emulado del computador (watch-emulator).
 *
 * El emulador escucha en localhost:[PORT] y el teléfono se conecta por el cable USB gracias a
 * `adb reverse tcp:8766 tcp:8766`. Cada mensaje es una línea JSON. Cumple el mismo papel que el
 * Wearable Data Layer con un reloj Wear OS real:
 *
 *   Teléfono -> reloj:  Hello, Risk (estado de la zona), SosResult (cuántos SMS salieron)
 *   Reloj -> teléfono:  Sos
 */
object DesktopLink {
    const val PORT = 8766

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(message: LinkMessage): String = json.encodeToString(LinkMessage.serializer(), message)

    /** @return null si la línea no es un mensaje válido (versión distinta, basura, etc.). */
    fun decode(line: String): LinkMessage? =
        try {
            json.decodeFromString(LinkMessage.serializer(), line)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}

@Serializable
sealed interface LinkMessage {

    /** Primer mensaje del teléfono: para mostrar en el computador con quién está conectado. */
    @Serializable
    @SerialName("hello")
    data class Hello(val device: String, val userName: String? = null) : LinkMessage

    @Serializable
    @SerialName("risk")
    data class Risk(
        val level: RiskLevel,
        val zoneName: String? = null,
        val score: Float? = null,
        val updatedAt: Long,
        val simulated: Boolean = false,
    ) : LinkMessage {
        fun toStatus() = RiskStatus(level, zoneName, score, updatedAt, simulated)

        companion object {
            fun from(status: RiskStatus) =
                Risk(status.level, status.zoneName, status.score, status.updatedAt, status.simulated)
        }
    }

    @Serializable
    @SerialName("sos")
    data class Sos(
        val requestId: String,
        val trigger: SosTrigger,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val accuracyMeters: Float? = null,
    ) : LinkMessage {
        companion object {
            fun from(request: SosRequest) = Sos(
                request.requestId, request.trigger, request.latitude, request.longitude, request.accuracyMeters,
            )
        }
    }

    @Serializable
    @SerialName("sosResult")
    data class SosResult(
        val requestId: String,
        val smsSent: Int,
        val contactsTotal: Int,
        val serverRegistered: Boolean,
        val completedAt: Long,
    ) : LinkMessage {
        fun toOutcome() = SosOutcome(requestId, smsSent, contactsTotal, serverRegistered, completedAt)
    }
}
