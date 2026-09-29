package com.andres.walksecurity.shared

/**
 * Nivel de riesgo ESTIMADO de la zona actual. Es una estimación basada en datos,
 * no una garantía de que la persona esté (o no) en peligro.
 */
enum class RiskLevel {
    SAFE,
    CAUTION,
    ALERT;

    companion object {
        const val CAUTION_THRESHOLD = 0.4f
        const val ALERT_THRESHOLD = 0.7f

        fun fromScore(score: Float): RiskLevel = when {
            score >= ALERT_THRESHOLD -> ALERT
            score >= CAUTION_THRESHOLD -> CAUTION
            else -> SAFE
        }

        fun parse(value: String?): RiskLevel = entries.firstOrNull { it.name == value } ?: SAFE
    }
}
