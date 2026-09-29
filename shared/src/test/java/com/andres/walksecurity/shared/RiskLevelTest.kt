package com.andres.walksecurity.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class RiskLevelTest {

    @Test
    fun `clasifica el puntaje segun los umbrales`() {
        assertEquals(RiskLevel.SAFE, RiskLevel.fromScore(0f))
        assertEquals(RiskLevel.SAFE, RiskLevel.fromScore(0.39f))
        assertEquals(RiskLevel.CAUTION, RiskLevel.fromScore(0.4f))
        assertEquals(RiskLevel.CAUTION, RiskLevel.fromScore(0.69f))
        assertEquals(RiskLevel.ALERT, RiskLevel.fromScore(0.7f))
        assertEquals(RiskLevel.ALERT, RiskLevel.fromScore(1f))
    }

    @Test
    fun `valores desconocidos se interpretan como seguro`() {
        assertEquals(RiskLevel.SAFE, RiskLevel.parse(null))
        assertEquals(RiskLevel.SAFE, RiskLevel.parse("OTRO"))
        assertEquals(RiskLevel.ALERT, RiskLevel.parse("ALERT"))
    }
}
