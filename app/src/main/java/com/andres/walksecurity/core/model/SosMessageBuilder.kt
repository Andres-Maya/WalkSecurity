package com.andres.walksecurity.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Construye el texto del SMS de emergencia. Se evita usar emojis/tildes innecesarias
 * para que el mensaje quepa en menos segmentos SMS (codificación GSM-7).
 */
object SosMessageBuilder {

    fun build(
        userName: String?,
        location: GeoPoint?,
        timeMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        fromWatch: Boolean = false,
    ): String {
        val who = userName?.takeIf { it.isNotBlank() } ?: "Un contacto"
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
            .apply { this.timeZone = timeZone }
            .format(Date(timeMillis))
        val where = if (location != null) {
            val accuracy = location.accuracyMeters?.let { " (precision aprox. ${it.toInt()} m)" }.orEmpty()
            "Ubicacion: ${mapsUrl(location)}$accuracy"
        } else {
            "Ubicacion no disponible en este momento."
        }
        val via = if (fromWatch) " (desde su reloj)" else ""
        return "ALERTA SOS: $who necesita ayuda$via. $where Hora: $time. Enviado por WalkSecurity."
    }

    fun mapsUrl(location: GeoPoint): String =
        String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", location.latitude, location.longitude)
}
