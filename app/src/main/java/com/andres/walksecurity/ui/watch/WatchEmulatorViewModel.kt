package com.andres.walksecurity.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
import com.andres.walksecurity.data.repository.RiskStatusRepository
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.watch.WatchController
import com.andres.walksecurity.wear.DesktopLinkState
import com.andres.walksecurity.wear.DesktopWatchLink
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchEmulatorViewModel(
    /** Vive en el scope de la app: el ViewModel solo lo expone a la pantalla. */
    val controller: WatchController,
    private val riskStatusRepository: RiskStatusRepository,
    private val desktopWatchLink: DesktopWatchLink,
) : ViewModel() {

    /** Conexión con el reloj emulado del computador. Sigue activa aunque se salga de esta pantalla. */
    val desktopLink: StateFlow<DesktopLinkState> = desktopWatchLink.state

    fun setDesktopLink(enabled: Boolean) {
        if (enabled) desktopWatchLink.start() else desktopWatchLink.stop()
    }

    val currentLevel: StateFlow<RiskLevel?> = riskStatusRepository.current
        .map { it?.level }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), riskStatusRepository.current.value?.level)

    /**
     * Simulador de zonas mientras llega la fase 2. Publica el estado por el mismo camino
     * que usará el geofencing: reloj emulado y, si existe, reloj Wear OS real.
     */
    fun simulate(level: RiskLevel) {
        viewModelScope.launch {
            riskStatusRepository.publish(
                RiskStatus(
                    level = level,
                    zoneName = if (level == RiskLevel.SAFE) null else "Zona de prueba",
                    score = when (level) {
                        RiskLevel.SAFE -> 0.1f
                        RiskLevel.CAUTION -> 0.5f
                        RiskLevel.ALERT -> 0.85f
                    },
                    updatedAt = System.currentTimeMillis(),
                    simulated = true,
                )
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                with(appContainer) { WatchEmulatorViewModel(emulatedWatch, riskStatusRepository, desktopWatchLink) }
            }
        }
    }
}
