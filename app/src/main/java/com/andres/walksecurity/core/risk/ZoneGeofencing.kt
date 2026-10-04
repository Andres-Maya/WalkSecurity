package com.andres.walksecurity.core.risk

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.andres.walksecurity.WalkSecurityApp
import com.andres.walksecurity.core.model.GeoPoint
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Detección en segundo plano: registra cada zona de riesgo como una geocerca del sistema (Geofencing
 * API). Android despierta la app al entrar o salir de una zona, aunque esté cerrada, sin mantener el
 * GPS encendido. Requiere el permiso de ubicación "Todo el tiempo".
 */
class ZoneGeofencing(private val context: Context, private val zones: List<RiskZone>) {

    private val client = LocationServices.getGeofencingClient(context)

    fun hasPermissions(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    /**
     * Registra (o reemplaza) las geocercas. Es seguro llamarlo varias veces: los identificadores se
     * repiten y el sistema las sustituye. Hay que repetirlo tras reiniciar el teléfono.
     * @return true si quedaron registradas.
     */
    @SuppressLint("MissingPermission")
    suspend fun register(): Boolean {
        if (zones.isEmpty() || !hasPermissions()) return false
        // Límite del sistema: 100 geocercas por app. Si el modelo crece, se quedan las de mayor riesgo
        val geofences = zones.sortedByDescending { it.riskScore }.take(MAX_GEOFENCES).map { zone ->
            Geofence.Builder()
                .setRequestId(zone.name)
                .setCircularRegion(zone.latitude, zone.longitude, zone.radiusMeters)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        val request = GeofencingRequest.Builder()
            // Si al registrar ya se está dentro de una zona, avisa de inmediato
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        return try {
            client.addGeofences(request, pendingIntent(context)).await()
            Log.i(TAG, "${geofences.size} geocercas registradas")
            true
        } catch (e: Exception) {   // ubicación del sistema apagada, sin Google Play Services, etc.
            Log.w(TAG, "No se pudieron registrar las geocercas: ${e.message}")
            false
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "ZoneGeofencing"
        const val MAX_GEOFENCES = 100

        fun pendingIntent(context: Context): PendingIntent {
            // MUTABLE: el sistema añade al intent los datos de la transición
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(
                context, 0, Intent(context, GeofenceReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
        }
    }
}

/**
 * Recibe las entradas y salidas de zona. Las zonas se solapan, así que no se confía en cuál geocerca
 * disparó: con la posición del evento se vuelve a evaluar el nivel igual que con la app abierta.
 */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Error de geocercas: ${event.errorCode}")
            return
        }
        val location = event.triggeringLocation ?: return
        val container = (context.applicationContext as WalkSecurityApp).container
        val point = GeoPoint(
            location.latitude, location.longitude,
            if (location.hasAccuracy()) location.accuracy else null, location.time,
        )
        val pending = goAsync()   // el sistema da unos segundos antes de dormir la app otra vez
        container.appScope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    // null = el nivel no cambió (p. ej. la app abierta ya lo había detectado)
                    container.zoneDetector.onLocation(point)?.let(container.riskNotifier::show)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}

/** Las geocercas se pierden al reiniciar el teléfono o actualizar la app: se registran de nuevo. */
class GeofenceRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as WalkSecurityApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                withTimeoutOrNull(8_000) { container.zoneGeofencing.register() }
            } finally {
                pending.finish()
            }
        }
    }
}
