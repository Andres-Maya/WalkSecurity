package com.andres.walksecurity.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.watch.VibratorHaptics

/**
 * Avisa cuando el nivel de riesgo SUBE (seguro -> precaución -> alerta).
 * Recuerda el último nivel notificado para no repetir la alerta si el proceso se reinicia.
 */
object RiskNotifier {

    private const val CHANNEL_ID = "risk_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "risk_notifier"
    private const val KEY_LAST_LEVEL = "last_level"

    fun onRiskStatus(context: Context, status: RiskStatus) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = RiskLevel.parse(prefs.getString(KEY_LAST_LEVEL, RiskLevel.SAFE.name))
        prefs.edit().putString(KEY_LAST_LEVEL, status.level.name).apply()

        if (status.level.ordinal > previous.ordinal) {
            VibratorHaptics(context).riskChanged(status.level)
            showNotification(context, status)
        } else if (status.level == RiskLevel.SAFE) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    private fun showNotification(context: Context, status: RiskStatus) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (status.level == RiskLevel.ALERT) "Zona de riesgo alto" else "Precaución"
        val zone = status.zoneName?.let { " ($it)" }.orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Riesgo estimado$zone. Toca para responder.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Alertas de zona", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso al entrar en una zona con riesgo estimado"
            // La vibración la controla Haptics con patrones propios de cada nivel
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}
