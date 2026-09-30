package com.andres.walksecurity.shared

import com.google.android.gms.wearable.DataMap

// Serialización del protocolo reloj <-> teléfono sobre el Wearable Data Layer (solo Android).

private const val KEY_LEVEL = "level"
private const val KEY_ZONE = "zone"
private const val KEY_SCORE = "score"
private const val KEY_UPDATED_AT = "updatedAt"
private const val KEY_SIMULATED = "simulated"

fun RiskStatus.toDataMap(map: DataMap) {
    map.putString(KEY_LEVEL, level.name)
    zoneName?.let { map.putString(KEY_ZONE, it) }
    score?.let { map.putFloat(KEY_SCORE, it) }
    map.putLong(KEY_UPDATED_AT, updatedAt)
    map.putBoolean(KEY_SIMULATED, simulated)
}

fun RiskStatus.Companion.fromDataMap(map: DataMap) = RiskStatus(
    level = RiskLevel.parse(map.getString(KEY_LEVEL)),
    zoneName = map.getString(KEY_ZONE),
    score = if (map.containsKey(KEY_SCORE)) map.getFloat(KEY_SCORE) else null,
    updatedAt = map.getLong(KEY_UPDATED_AT),
    simulated = map.getBoolean(KEY_SIMULATED),
)

private const val KEY_ID = "id"
private const val KEY_TRIGGER = "trigger"
private const val KEY_LAT = "lat"
private const val KEY_LNG = "lng"
private const val KEY_ACCURACY = "accuracy"

fun SosRequest.toBytes(): ByteArray = DataMap().apply {
    putString(KEY_ID, requestId)
    putString(KEY_TRIGGER, trigger.name)
    if (latitude != null && longitude != null) {
        putDouble(KEY_LAT, latitude)
        putDouble(KEY_LNG, longitude)
        accuracyMeters?.let { putFloat(KEY_ACCURACY, it) }
    }
}.toByteArray()

fun SosRequest.Companion.fromBytes(bytes: ByteArray): SosRequest {
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

private const val KEY_SMS = "smsSent"
private const val KEY_TOTAL = "contactsTotal"
private const val KEY_SERVER = "server"
private const val KEY_AT = "completedAt"

fun SosOutcome.toDataMap(map: DataMap) {
    map.putString(KEY_ID, requestId)
    map.putInt(KEY_SMS, smsSent)
    map.putInt(KEY_TOTAL, contactsTotal)
    map.putBoolean(KEY_SERVER, serverRegistered)
    map.putLong(KEY_AT, completedAt)
}

fun SosOutcome.Companion.fromDataMap(map: DataMap) = SosOutcome(
    requestId = map.getString(KEY_ID) ?: "",
    smsSent = map.getInt(KEY_SMS),
    contactsTotal = map.getInt(KEY_TOTAL),
    serverRegistered = map.getBoolean(KEY_SERVER),
    completedAt = map.getLong(KEY_AT),
)
