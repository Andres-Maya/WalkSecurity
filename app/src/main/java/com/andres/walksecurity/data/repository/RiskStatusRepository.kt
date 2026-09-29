package com.andres.walksecurity.data.repository

import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.wear.WatchBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de riesgo actual del usuario. Hoy lo alimenta el simulador; en la fase 2 lo hará el geofencing.
 * Cada cambio se entrega al reloj emulado (en memoria) y al reloj real (Data Layer), si existe.
 */
class RiskStatusRepository(private val watchBridge: WatchBridge) {

    private val _current = MutableStateFlow<RiskStatus?>(null)
    val current: StateFlow<RiskStatus?> = _current.asStateFlow()

    /** @return true si además se entregó a un reloj real. */
    suspend fun publish(status: RiskStatus): Boolean {
        _current.value = status
        return watchBridge.publishRiskStatus(status)
    }
}
