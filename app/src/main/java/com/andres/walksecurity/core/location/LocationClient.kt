package com.andres.walksecurity.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.andres.walksecurity.core.model.GeoPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Envoltorio sobre FusedLocationProviderClient con API de corrutinas. */
class LocationClient(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Android 10+: necesario para obtener la ubicación con la app en segundo plano (SOS desde el reloj). */
    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** Ubicación fresca de alta precisión; si tarda demasiado usa la última conocida. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMs: Long = 10_000): GeoPoint? {
        if (!hasPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            val fresh = withTimeoutOrNull(timeoutMs) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            }
            (fresh ?: fused.lastLocation.await())?.toGeoPoint()
        } catch (_: SecurityException) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    /** Actualizaciones continuas mientras haya un colector activo. */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 5_000): Flow<GeoPoint> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toGeoPoint()) }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Location.toGeoPoint() = GeoPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    timeMillis = time,
)
