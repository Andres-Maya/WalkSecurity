package com.andres.walksecurity.core.risk

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andres.walksecurity.MainActivity
import com.andres.walksecurity.R
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus

/** Notificación del teléfono al entrar en una zona de precaución o alerta con la app en segundo plano. */
class RiskNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(status: RiskStatus) {
        // Al volver a zona segura no se molesta: basta con quitar el aviso anterior
        if (status.level == RiskLevel.SAFE) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!hasPermission()) return
        createChannel()
        val alert = status.level == RiskLevel.ALERT
        val zone = status.zoneName?.let { " ($it)" }.orEmpty()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_risk)
            .setContentTitle(if (alert) "Zona de alerta$zone" else "Zona de precaución$zone")
            .setContentText("Riesgo estimado a partir de noticias: no es una garantía. Toca para abrir el SOS.")
            .setColor(if (alert) 0xFFD32F2F.toInt() else 0xFFFFA000.toInt())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(if (alert) longArrayOf(0, 400, 150, 400, 150, 400) else longArrayOf(0, 250, 150, 250))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // El permiso se retiró entre la comprobación y el aviso
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Zonas de riesgo", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso al entrar en una zona con riesgo estimado de precaución o alerta"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "risk_zones"
        const val NOTIFICATION_ID = 1001
    }
}
