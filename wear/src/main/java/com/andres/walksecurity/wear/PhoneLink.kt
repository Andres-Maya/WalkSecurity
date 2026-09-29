package com.andres.walksecurity.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.WearProtocol
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class PhoneNotReachableException : Exception("Teléfono no conectado")

/** Comunicación reloj -> teléfono. */
class PhoneLink(private val context: Context) {

    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val messageClient = Wearable.getMessageClient(context)

    suspend fun isPhoneReachable(): Boolean = findPhone() != null

    suspend fun sendSos(request: SosRequest): Result<Unit> =
        try {
            val phone = findPhone() ?: throw PhoneNotReachableException()
            messageClient.sendMessage(phone.id, WearProtocol.PATH_SOS, request.toBytes()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Ubicación del reloj como respaldo de la del teléfono (el teléfono sigue siendo la fuente principal).
     * En relojes sin GPS propio, Wear OS la obtiene del teléfono emparejado.
     */
    @SuppressLint("MissingPermission")
    suspend fun watchLocation(timeoutMs: Long = 5_000): Triple<Double, Double, Float?>? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        return try {
            val location = withTimeoutOrNull(timeoutMs) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            } ?: fused.lastLocation.await()
            location?.let { Triple(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private suspend fun findPhone(): Node? =
        try {
            val nodes = capabilityClient
                .getCapability(WearProtocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
            nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
