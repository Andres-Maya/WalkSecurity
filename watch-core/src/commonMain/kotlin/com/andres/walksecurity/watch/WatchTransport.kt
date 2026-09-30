package com.andres.walksecurity.watch

import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Cómo se comunica el reloj con el teléfono.
 * - Reloj real: Wearable Data Layer (módulo wear/).
 * - Reloj emulado: llamadas directas dentro de la app del teléfono (módulo app/).
 */
interface WatchTransport {
    /** Estado de riesgo publicado por el teléfono. */
    val risk: StateFlow<RiskStatus?>

    /** Resultado de los SOS que el teléfono ya procesó. */
    val sosOutcome: StateFlow<SosOutcome?>

    suspend fun isPhoneReachable(): Boolean

    /** Entrega el SOS al teléfono. El resultado final llega después por [sosOutcome]. */
    suspend fun sendSos(request: SosRequest): Result<Unit>

    /** Ubicación propia del reloj (respaldo del GPS del teléfono), o null. */
    suspend fun watchLocation(): WatchLocation?
}

data class WatchLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)

class PhoneNotReachableException : Exception("Teléfono no conectado")

/** Vibraciones del reloj; se inyecta para poder probar la lógica sin hardware. */
interface WatchHaptics {
    fun riskChanged(level: RiskLevel)
    fun sosSent()
    fun tick()
}
