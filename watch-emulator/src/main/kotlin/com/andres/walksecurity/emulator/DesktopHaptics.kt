package com.andres.walksecurity.emulator

import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.watch.WatchHaptics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Una vibración con el mismo formato que VibrationEffect.createWaveform de Android:
 * [pausa, vibra, pausa, vibra, ...] en milisegundos.
 */
class Vibration(val label: String?, val pattern: List<Long>)

/** En el computador no hay motor de vibración: se emite un evento y la ventana sacude el reloj. */
class DesktopHaptics(private val log: EventLog) : WatchHaptics {

    private val _events = MutableSharedFlow<Vibration>(extraBufferCapacity = 16)
    val events: SharedFlow<Vibration> = _events.asSharedFlow()

    // Mismos patrones que VibratorHaptics (Android)
    override fun riskChanged(level: RiskLevel) {
        when (level) {
            RiskLevel.SAFE -> Unit
            RiskLevel.CAUTION -> vibrate("vibración corta (precaución)", listOf(0, 250, 150, 250))
            RiskLevel.ALERT -> vibrate("vibración larga (alerta)", listOf(0, 600, 200, 600, 200, 600))
        }
    }

    override fun sosSent() = vibrate("vibración de confirmación (SOS enviado)", listOf(0, 100, 100, 100, 100, 400))

    override fun tick() {
        _events.tryEmit(Vibration(label = null, pattern = listOf(0, 40)))
    }

    private fun vibrate(label: String, pattern: List<Long>) {
        log.add(LogSource.WATCH, "Vibra: $label")
        _events.tryEmit(Vibration(label, pattern))
    }
}
