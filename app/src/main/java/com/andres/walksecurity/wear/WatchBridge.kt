package com.andres.walksecurity.wear

import android.content.Context
import android.util.Log
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.WearProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Comunicación teléfono -> reloj. Si el teléfono no tiene Wear OS instalado o no hay reloj
 * emparejado, las llamadas fallan en silencio: el reloj es un complemento, no un requisito.
 */
class WatchBridge(context: Context) {

    private val dataClient = Wearable.getDataClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    suspend fun isWatchConnected(): Boolean = safely(false) {
        capabilityClient
            .getCapability(WearProtocol.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .isNotEmpty()
    }

    suspend fun publishRiskStatus(status: RiskStatus): Boolean =
        put(WearProtocol.PATH_RISK_STATUS) { status.toDataMap(it) }

    suspend fun publishSosOutcome(outcome: SosOutcome): Boolean =
        put(WearProtocol.PATH_SOS_RESULT) { outcome.toDataMap(it) }

    private suspend fun put(path: String, fill: (DataMap) -> Unit): Boolean = safely(false) {
        val request = PutDataMapRequest.create(path)
            .apply { fill(dataMap) }
            .asPutDataRequest()
            .setUrgent() // entrega inmediata: son avisos de seguridad
        dataClient.putDataItem(request).await()
        true
    }

    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Wear Data Layer no disponible: ${e.message}")
            fallback
        }

    private companion object {
        const val TAG = "WatchBridge"
    }
}
