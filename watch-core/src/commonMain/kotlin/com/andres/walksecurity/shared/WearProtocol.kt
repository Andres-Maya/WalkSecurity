package com.andres.walksecurity.shared

/**
 * Contrato de comunicación entre el reloj y el teléfono.
 *
 * - Teléfono -> reloj: DataItems (estado persistente, se sincroniza aunque el reloj se reconecte después).
 * - Reloj -> teléfono: Message (acción puntual: SOS).
 *
 * Ambas apps deben tener el mismo applicationId y la misma firma para que el Data Layer las conecte.
 * La conversión a DataMap está en androidMain (DataMapMappers.kt): aquí solo hay modelos puros,
 * para que el emulador de escritorio pueda usarlos.
 */
object WearProtocol {
    const val PATH_PREFIX = "/walksecurity"

    /** DataItem: estado de riesgo actual (teléfono -> reloj). */
    const val PATH_RISK_STATUS = "$PATH_PREFIX/risk-status"

    /** DataItem: resultado del último SOS (teléfono -> reloj). */
    const val PATH_SOS_RESULT = "$PATH_PREFIX/sos-result"

    /** Message: solicitud de SOS (reloj -> teléfono). */
    const val PATH_SOS = "$PATH_PREFIX/sos"

    /** Capabilities declaradas en res/values/wear.xml de cada app. */
    const val CAPABILITY_PHONE = "walksecurity_phone"
    const val CAPABILITY_WATCH = "walksecurity_watch"
}

data class RiskStatus(
    val level: RiskLevel,
    val zoneName: String?,
    val score: Float?,
    val updatedAt: Long,
    /** true cuando el estado proviene del simulador de pruebas y no de datos reales. */
    val simulated: Boolean = false,
) {
    companion object
}

/** Cómo se originó el SOS en el reloj. */
enum class SosTrigger { BUTTON, RISK_CONFIRMED }

data class SosRequest(
    val requestId: String,
    val trigger: SosTrigger,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
) {
    companion object
}

data class SosOutcome(
    val requestId: String,
    val smsSent: Int,
    val contactsTotal: Int,
    val serverRegistered: Boolean,
    val completedAt: Long,
) {
    val contactsNotified: Boolean get() = smsSent > 0

    companion object
}
