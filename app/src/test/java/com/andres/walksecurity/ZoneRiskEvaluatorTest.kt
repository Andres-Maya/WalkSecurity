package com.andres.walksecurity

import com.andres.walksecurity.core.risk.RiskModel
import com.andres.walksecurity.core.risk.RiskZone
import com.andres.walksecurity.core.risk.ZoneRiskEvaluator
import com.andres.walksecurity.shared.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ZoneRiskEvaluatorTest {

    private val alertZone = RiskZone("Zona A", 1.2136, -77.2811, radiusMeters = 300f, riskScore = 0.8f)
    private val cautionZone = RiskZone("Zona B", 1.2140, -77.2815, radiusMeters = 300f, riskScore = 0.5f)
    private val now = System.currentTimeMillis()

    @Test
    fun `fuera de toda zona es seguro`() {
        val status = ZoneRiskEvaluator(RiskModel(zones = listOf(alertZone))).evaluate(1.25, -77.30, now)
        assertEquals(RiskLevel.SAFE, status.level)
        assertNull(status.zoneName)
    }

    @Test
    fun `dentro de una zona toma su nivel y su nombre`() {
        val status = ZoneRiskEvaluator(RiskModel(zones = listOf(alertZone))).evaluate(1.2137, -77.2812, now)
        assertEquals(RiskLevel.ALERT, status.level)
        assertEquals("Zona A", status.zoneName)
    }

    @Test
    fun `si las zonas se solapan gana la de mayor riesgo`() {
        val status = ZoneRiskEvaluator(RiskModel(zones = listOf(cautionZone, alertZone))).evaluate(1.2138, -77.2813, now)
        assertEquals("Zona A", status.zoneName)
    }

    @Test
    fun `el borde de la zona respeta el radio`() {
        // ~333 m al norte del centro: fuera de un radio de 300 m
        val status = ZoneRiskEvaluator(RiskModel(zones = listOf(alertZone))).evaluate(1.2166, -77.2811, now)
        assertEquals(RiskLevel.SAFE, status.level)
        assertTrue(ZoneRiskEvaluator.distanceMeters(1.2136, -77.2811, 1.2166, -77.2811) in 320.0..345.0)
    }

    @Test
    fun `el dia y la hora ajustan el puntaje`() {
        val monday3am = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 5, 3, 0) }.timeInMillis
        val weekday = List(7) { if (it == 0) 0.8f else 1f }      // lunes baja
        val hours = List(24) { if (it == 3) 0.8f else 1f }       // 3 a. m. baja
        val model = RiskModel(zones = listOf(alertZone), weekdayMultipliers = weekday, hourMultipliers = hours)
        val status = ZoneRiskEvaluator(model).evaluate(1.2136, -77.2811, monday3am)
        assertEquals(0.8f * 0.8f * 0.8f, status.score!!, 0.001f)
        assertEquals(RiskLevel.CAUTION, status.level)
    }
}
