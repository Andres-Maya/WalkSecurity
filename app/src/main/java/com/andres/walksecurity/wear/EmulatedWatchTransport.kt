package com.andres.walksecurity.wear

import com.andres.walksecurity.data.repository.AlertRepository
import com.andres.walksecurity.data.repository.RiskStatusRepository
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.watch.WatchLocation
import com.andres.walksecurity.watch.WatchTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reloj emulado dentro del teléfono (para probar sin un reloj Wear OS).
 * Recorre el mismo flujo que el reloj real, pero sin Data Layer: el "mensaje" SOS llama
 * directamente al AlertRepository, igual que hace PhoneWearListenerService con el reloj real.
 */
class EmulatedWatchTransport(
    riskStatusRepository: RiskStatusRepository,
    private val alertRepository: AlertRepository,
    private val appScope: CoroutineScope,
) : WatchTransport {

    override val risk: StateFlow<RiskStatus?> = riskStatusRepository.current

    private val _sosOutcome = MutableStateFlow<SosOutcome?>(null)
    override val sosOutcome: StateFlow<SosOutcome?> = _sosOutcome.asStateFlow()

    override suspend fun isPhoneReachable(): Boolean = true

    override suspend fun sendSos(request: SosRequest): Result<Unit> {
        appScope.launch {
            val result = alertRepository.sendSos(lastKnownLocation = null, fromWatch = true)
            _sosOutcome.value = SosOutcome(
                requestId = request.requestId,
                smsSent = result.smsSent,
                contactsTotal = result.contactsTotal,
                serverRegistered = result.serverRegistered,
                completedAt = System.currentTimeMillis(),
            )
        }
        return Result.success(Unit)
    }

    /** El reloj emulado vive en el propio teléfono: el AlertRepository ya usa el GPS del teléfono. */
    override suspend fun watchLocation(): WatchLocation? = null
}
