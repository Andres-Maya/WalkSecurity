package com.andres.walksecurity.emulator

import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.watch.PhoneNotReachableException
import com.andres.walksecurity.watch.WatchLocation
import com.andres.walksecurity.watch.WatchTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * El reloj del computador habla con el teléfono REAL si está conectado por USB;
 * si no, con el teléfono simulado del panel.
 */
class EmulatorTransport(
    private val simulated: SimulatedPhone,
    private val real: PhoneLinkServer,
    scope: CoroutineScope,
) : WatchTransport {

    override val risk: StateFlow<RiskStatus?> =
        combine(real.connected, real.risk, simulated.risk) { connected, realRisk, simulatedRisk ->
            if (connected) realRisk else simulatedRisk
        }.stateIn(scope, SharingStarted.Eagerly, simulated.risk.value)

    override val sosOutcome: StateFlow<SosOutcome?> =
        merge(real.sosOutcome, simulated.sosOutcome).stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun isPhoneReachable(): Boolean =
        real.connected.value || simulated.isPhoneReachable()

    override suspend fun sendSos(request: SosRequest): Result<Unit> =
        if (real.connected.value) {
            if (real.sendSos(request)) Result.success(Unit) else Result.failure(PhoneNotReachableException())
        } else {
            simulated.sendSos(request)
        }

    /** Con el teléfono real se usa su GPS (el computador no tiene); con el simulado, coordenadas de ejemplo. */
    override suspend fun watchLocation(): WatchLocation? =
        if (real.connected.value) null else simulated.watchLocation()
}
