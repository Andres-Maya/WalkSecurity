package com.andres.walksecurity.shared

import com.google.android.gms.wearable.DataMap

/**
 * Contrato de comunicación entre el reloj y el teléfono.
 *
 * - Teléfono -> reloj: DataItems (estado persistente, se sincroniza aunque el reloj se reconecte después).
 * - Reloj -> teléfono: Message (acción puntual: SOS).
 *
 * Ambas apps deben tener el mismo applicationId y la misma firma para que el Data Layer las conecte.
 */
object WearProtocol {
    const val PATH_PREFIX = "/walksecurity"

    /** DataItem: estado de riesgo actual (teléfono -> reloj). */
    const val PATH_RISK_STATUS = "$PATH_PREFIX/risk-status"

    /** DataItem: resultado del último SOS (teléfono -> reloj). */
    const val PATH_SOS_RESULT = "$PATH_PREFIX/sos-result"

    /** Message: solicitud de SOS (reloj -> teléfono). */
    const val PATH_SOS = "$PATH_PREFIX/sos"

    /** Capabilities declaradas en res/values/wear.xml de cada app. */
    const val CAPABILITY_PHONE = "walksecurity_phone"
    const val CAPABILITY_WATCH = "walksecurity_watch"
}

data class RiskStatus(
    val level: RiskLevel,
    val zoneName: String?,
    val score: Float?,
    val updatedAt: Long,
    /** true cuando el estado proviene del simulador de pruebas y no de datos reales. */
    val simulated: Boolean = false,
) {
    fun toDataMap(map: DataMap) {
        map.putString(KEY_LEVEL, level.name)
        zoneName?.let { map.putString(KEY_ZONE, it) }
        score?.let { map.putFloat(KEY_SCORE, it) }
        map.putLong(KEY_UPDATED_AT, updatedAt)
        map.putBoolean(KEY_SIMULATED, simulated)
    }

    companion object {
        private const val KEY_LEVEL = "level"
        private const val KEY_ZONE = "zone"
        private const val KEY_SCORE = "score"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_SIMULATED = "simulated"

        fun fromDataMap(map: DataMap) = RiskStatus(
            level = RiskLevel.parse(map.getString(KEY_LEVEL)),
            zoneName = map.getString(KEY_ZONE),
            score = if (map.containsKey(KEY_SCORE)) map.getFloat(KEY_SCORE) else null,
            updatedAt = map.getLong(KEY_UPDATED_AT),
            simulated = map.getBoolean(KEY_SIMULATED),
        )
    }
}

/** Cómo se originó el SOS en el reloj. */
enum class SosTrigger { BUTTON, RISK_CONFIRMED }

data class SosRequest(
    val requestId: String,
    val trigger: SosTrigger,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
) {
    fun toBytes(): ByteArray = DataMap().apply {
        putString(KEY_ID, requestId)
        putString(KEY_TRIGGER, trigger.name)
        if (latitude != null && longitude != null) {
            putDouble(KEY_LAT, latitude)
            putDouble(KEY_LNG, longitude)
            accuracyMeters?.let { putFloat(KEY_ACCURACY, it) }
        }
    }.toByteArray()

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_TRIGGER = "trigger"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_ACCURACY = "accuracy"

        fun fromBytes(bytes: ByteArray): SosRequest {
            val map = DataMap.fromByteArray(bytes)
            val hasLocation = map.containsKey(KEY_LAT) && map.containsKey(KEY_LNG)
            return SosRequest(
                requestId = map.getString(KEY_ID) ?: "",
                trigger = SosTrigger.entries.firstOrNull { it.name == map.getString(KEY_TRIGGER) } ?: SosTrigger.BUTTON,
                latitude = if (hasLocation) map.getDouble(KEY_LAT) else null,
                longitude = if (hasLocation) map.getDouble(KEY_LNG) else null,
                accuracyMeters = if (map.containsKey(KEY_ACCURACY)) map.getFloat(KEY_ACCURACY) else null,
            )
        }
    }
}

data class SosOutcome(
    val requestId: String,
    val smsSent: Int,
    val contactsTotal: Int,
    val serverRegistered: Boolean,
    val completedAt: Long,
) {
    val contactsNotified: Boolean get() = smsSent > 0

    fun toDataMap(map: DataMap) {
        map.putString(KEY_ID, requestId)
        map.putInt(KEY_SMS, smsSent)
        map.putInt(KEY_TOTAL, contactsTotal)
        map.putBoolean(KEY_SERVER, serverRegistered)
        map.putLong(KEY_AT, completedAt)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_SMS = "smsSent"
        private const val KEY_TOTAL = "contactsTotal"
        private const val KEY_SERVER = "server"
        private const val KEY_AT = "completedAt"

        fun fromDataMap(map: DataMap) = SosOutcome(
            requestId = map.getString(KEY_ID) ?: "",
            smsSent = map.getInt(KEY_SMS),
            contactsTotal = map.getInt(KEY_TOTAL),
            serverRegistered = map.getBoolean(KEY_SERVER),
            completedAt = map.getLong(KEY_AT),
        )
    }
}
