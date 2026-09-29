package com.andres.walksecurity

import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.core.model.SosMessageBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class SosMessageBuilderTest {

    private val bogota = TimeZone.getTimeZone("America/Bogota")

    // 2026-09-28 20:30 hora de Bogotá (UTC-5)
    private val time = 1_790_645_400_000L

    @Test
    fun `incluye nombre, enlace de mapa, precision y hora`() {
        val location = GeoPoint(4.711, -74.0721, 12.7f, time)
        val message = SosMessageBuilder.build("Ana", location, time, bogota)

        assertTrue(message.startsWith("ALERTA SOS: Ana necesita ayuda."))
        assertTrue(message.contains("https://maps.google.com/?q=4.711000,-74.072100"))
        assertTrue(message.contains("precision aprox. 12 m"))
        assertTrue(message.contains("28/09/2026 20:30"))
    }

    @Test
    fun `funciona sin ubicacion ni nombre`() {
        val message = SosMessageBuilder.build(null, null, time, bogota)
        assertTrue(message.startsWith("ALERTA SOS: Un contacto necesita ayuda."))
        assertTrue(message.contains("Ubicacion no disponible"))
    }

    @Test
    fun `indica cuando el SOS viene del reloj y sigue cabiendo en dos SMS`() {
        val location = GeoPoint(4.711, -74.0721, 12.7f, time)
        val message = SosMessageBuilder.build("Andres Camilo Perez Gomez", location, time, bogota, fromWatch = true)
        assertTrue(message.contains("necesita ayuda (desde su reloj)."))
        assertTrue("Longitud ${message.length}", message.length <= 306)
    }

    @Test
    fun `usa punto decimal sin importar el idioma del telefono`() {
        val url = SosMessageBuilder.mapsUrl(GeoPoint(-4.5, 10.25, null, 0))
        assertEquals("https://maps.google.com/?q=-4.500000,10.250000", url)
    }

    @Test
    fun `mensaje cabe en dos segmentos SMS`() {
        val location = GeoPoint(4.711, -74.0721, 12.7f, time)
        val message = SosMessageBuilder.build("Andres Camilo Perez Gomez", location, time, bogota)
        // GSM-7 multiparte: 153 caracteres por segmento
        assertTrue("Longitud ${message.length}", message.length <= 306)
    }
}
