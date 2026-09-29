package com.andres.walksecurity.wear

import android.content.Context
import android.net.Uri
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.WearProtocol
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Estado del reloj en memoria. La fuente de verdad son los DataItems que publica el teléfono. */
object WatchState {

    private val _risk = MutableStateFlow<RiskStatus?>(null)
    val risk: StateFlow<RiskStatus?> = _risk.asStateFlow()

    private val _sosOutcome = MutableStateFlow<SosOutcome?>(null)
    val sosOutcome: StateFlow<SosOutcome?> = _sosOutcome.asStateFlow()

    /** Lee los DataItems ya sincronizados (p. ej. al abrir la app después de un cambio). */
    suspend fun loadFromDataLayer(context: Context) {
        try {
            val client = Wearable.getDataClient(context)
            for (path in listOf(WearProtocol.PATH_RISK_STATUS, WearProtocol.PATH_SOS_RESULT)) {
                val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()
                val buffer = client.getDataItems(uri).await()
                try {
                    buffer.forEach { apply(it) }
                } finally {
                    buffer.release()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin Data Layer disponible: la UI muestra "sin datos del teléfono"
        }
    }

    /** Devuelve el nuevo estado de riesgo si el DataItem lo cambió. */
    fun apply(item: DataItem): RiskStatus? {
        val map = DataMapItem.fromDataItem(item).dataMap
        return when (item.uri.path) {
            WearProtocol.PATH_RISK_STATUS -> {
                val status = RiskStatus.fromDataMap(map)
                val current = _risk.value
                if (current == null || status.updatedAt >= current.updatedAt) {
                    _risk.value = status
                    status
                } else {
                    null
                }
            }
            WearProtocol.PATH_SOS_RESULT -> {
                _sosOutcome.value = SosOutcome.fromDataMap(map)
                null
            }
            else -> null
        }
    }
}
