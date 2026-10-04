package com.andres.walksecurity.core.risk

import android.content.Context
import android.util.Log
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.data.remote.AppJson
import com.andres.walksecurity.data.repository.RiskStatusRepository
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus

/**
 * Detección automática de zonas: con cada posición del GPS decide el nivel de riesgo y, si cambió
 * (otra zona u otro nivel), lo publica al reloj. Solo publica cambios, así que una zona elegida a
 * mano en el simulador se mantiene hasta que el GPS detecte un cambio real.
 *
 * La alimentan el GPS con la app abierta (HomeViewModel) y las geocercas en segundo plano (GeofenceReceiver).
 */
class ZoneDetector(
    context: Context,
    private val riskStatusRepository: RiskStatusRepository,
) {
    /** Modelo generado por ml/train_model.py y empaquetado en la app (funciona sin servidor). */
    val model: RiskModel = try {
        context.assets.open(ASSET).bufferedReader().use { AppJson.decodeFromString<RiskModel>(it.readText()) }
    } catch (e: Exception) {
        Log.w(TAG, "Sin modelo de zonas de riesgo: ${e.message}")
        RiskModel()
    }

    private val evaluator = ZoneRiskEvaluator(model)
    private var lastPublished: Pair<String?, RiskLevel>? = null

    /** @return el nuevo estado si cambió la zona o el nivel; null si sigue igual. */
    suspend fun onLocation(location: GeoPoint): RiskStatus? {
        if (model.zones.isEmpty()) return null
        val status = evaluator.evaluate(location.latitude, location.longitude, System.currentTimeMillis())
        val key = status.zoneName to status.level
        // Las dos fuentes (GPS y geocercas) pueden llegar a la vez desde hilos distintos
        synchronized(this) {
            if (key == lastPublished) return null
            lastPublished = key
        }
        riskStatusRepository.publish(status)
        return status
    }

    private companion object {
        const val TAG = "ZoneDetector"
        const val ASSET = "risk_zones.json"
    }
}
