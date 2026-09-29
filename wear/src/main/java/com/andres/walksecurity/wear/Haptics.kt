package com.andres.walksecurity.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.andres.walksecurity.shared.RiskLevel

/** Patrones de vibración distinguibles sin mirar el reloj. */
object Haptics {

    private val CAUTION_PATTERN = longArrayOf(0, 250, 150, 250)
    private val ALERT_PATTERN = longArrayOf(0, 600, 200, 600, 200, 600)
    private val SOS_SENT_PATTERN = longArrayOf(0, 100, 100, 100, 100, 400)

    fun riskChanged(context: Context, level: RiskLevel) {
        when (level) {
            RiskLevel.SAFE -> Unit
            RiskLevel.CAUTION -> waveform(context, CAUTION_PATTERN)
            RiskLevel.ALERT -> waveform(context, ALERT_PATTERN)
        }
    }

    fun sosSent(context: Context) = waveform(context, SOS_SENT_PATTERN)

    fun tick(context: Context) {
        vibrator(context).vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun waveform(context: Context, pattern: LongArray) {
        vibrator(context).vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
