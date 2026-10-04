package com.andres.walksecurity.core.risk

import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import kotlinx.serialization.Serializable
import java.util.Calendar
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Zona circular con un puntaje de riesgo ESTIMADO en [0, 1] (salida del modelo de ml/). */
@Serializable
data class RiskZone(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val riskScore: Float,
    val incidents: Int = 0,
) {
    val level: RiskLevel get() = RiskLevel.fromScore(riskScore)
}

/** Contenido de assets/risk_zones.json, generado por ml/train_model.py. */
@Serializable
data class RiskModel(
    val generatedAt: String = "",
    val city: String = "",
    val disclaimer: String = "",
    /** Lunes = índice 0. 1.0 = sin ajuste. */
    val weekdayMultipliers: List<Float> = emptyList(),
    val hourMultipliers: List<Float> = emptyList(),
    val zones: List<RiskZone> = emptyList(),
)

/**
 * Decide el nivel de riesgo de una posición: busca las zonas que la contienen y ajusta el puntaje
 * por día de la semana y hora. Es una estimación basada en datos, no una garantía de peligro.
 */
class ZoneRiskEvaluator(private val model: RiskModel) {

    fun evaluate(latitude: Double, longitude: Double, timeMillis: Long): RiskStatus {
        val zone = model.zones
            .filter { distanceMeters(latitude, longitude, it.latitude, it.longitude) <= it.radiusMeters }
            .maxByOrNull { it.riskScore }
            ?: return RiskStatus(RiskLevel.SAFE, zoneName = null, score = 0f, updatedAt = timeMillis)

        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        // Calendar: domingo = 1 ... sábado = 7  ->  lunes = 0 ... domingo = 6
        val weekday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val factor = model.weekdayMultipliers.getOrElse(weekday) { 1f } *
            model.hourMultipliers.getOrElse(calendar.get(Calendar.HOUR_OF_DAY)) { 1f }
        val score = (zone.riskScore * factor).coerceIn(0f, 1f)
        return RiskStatus(RiskLevel.fromScore(score), zone.name, score, timeMillis)
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Distancia haversine en metros. */
        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a))
        }
    }
}
