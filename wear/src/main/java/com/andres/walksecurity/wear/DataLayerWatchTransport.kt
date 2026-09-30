package com.andres.walksecurity.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.WearProtocol
import com.andres.walksecurity.shared.toBytes
import com.andres.walksecurity.watch.PhoneNotReachableException
import com.andres.walksecurity.watch.WatchLocation
import com.andres.walksecurity.watch.WatchTransport
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Transporte del reloj real: Wearable Data Layer hacia el teléfono emparejado. */
class DataLayerWatchTransport(private val context: Context) : WatchTransport {

    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val messageClient = Wearable.getMessageClient(context)

    // El estado lo alimenta WatchListenerService (también con la app cerrada)
    override val risk: StateFlow<RiskStatus?> = WatchState.risk
    override val sosOutcome: StateFlow<SosOutcome?> = WatchState.sosOutcome

    override suspend fun isPhoneReachable(): Boolean = findPhone() != null

    override suspend fun sendSos(request: SosRequest): Result<Unit> =
        try {
            val phone = findPhone() ?: throw PhoneNotReachableException()
            messageClient.sendMessage(phone.id, WearProtocol.PATH_SOS, request.toBytes()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** En relojes sin GPS propio, Wear OS obtiene la ubicación del teléfono emparejado. */
    @SuppressLint("MissingPermission")
    override suspend fun watchLocation(): WatchLocation? {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        return try {
            val location = withTimeoutOrNull(5_000) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            } ?: fused.lastLocation.await()
            location?.let { WatchLocation(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) }
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
