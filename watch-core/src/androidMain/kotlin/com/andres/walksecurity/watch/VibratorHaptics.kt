package com.andres.walksecurity.watch

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.andres.walksecurity.shared.RiskLevel

/** Patrones de vibración distinguibles sin mirar la pantalla. Funciona en reloj y teléfono. */
class VibratorHaptics(context: Context) : WatchHaptics {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun riskChanged(level: RiskLevel) {
        when (level) {
            RiskLevel.SAFE -> Unit
            RiskLevel.CAUTION -> waveform(CAUTION_PATTERN)
            RiskLevel.ALERT -> waveform(ALERT_PATTERN)
        }
    }

    override fun sosSent() = waveform(SOS_SENT_PATTERN)

    override fun tick() {
        val v = vibrator ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> @Suppress("DEPRECATION") v.vibrate(30)
        }
    }

    private fun waveform(pattern: LongArray) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    private companion object {
        val CAUTION_PATTERN = longArrayOf(0, 250, 150, 250)
        val ALERT_PATTERN = longArrayOf(0, 600, 200, 600, 200, 600)
        val SOS_SENT_PATTERN = longArrayOf(0, 100, 100, 100, 100, 400)
    }
}
