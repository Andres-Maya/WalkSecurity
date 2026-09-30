package com.andres.walksecurity.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLinkTest {

    private fun roundTrip(message: LinkMessage) = DesktopLink.decode(DesktopLink.encode(message))

    @Test
    fun `los mensajes viajan sin perder datos`() {
        val messages = listOf(
            LinkMessage.Hello(device = "OPPO CPH2269", userName = "Andrés"),
            LinkMessage.Risk(RiskLevel.ALERT, "Calle 19", 0.86f, updatedAt = 42, simulated = true),
            LinkMessage.Sos("abc", SosTrigger.RISK_CONFIRMED, 4.61, -74.08, 12f),
            LinkMessage.SosResult("abc", smsSent = 2, contactsTotal = 2, serverRegistered = false, completedAt = 7),
        )
        messages.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `cada mensaje es una sola linea con su tipo`() {
        val line = DesktopLink.encode(LinkMessage.Sos("x", SosTrigger.BUTTON))
        assertTrue(line.contains("\"type\":\"sos\""))
        assertTrue('\n' !in line)
    }

    @Test
    fun `las lineas invalidas se ignoran`() {
        assertNull(DesktopLink.decode("hola"))
        assertNull(DesktopLink.decode("{\"type\":\"desconocido\"}"))
        assertNull(DesktopLink.decode(""))
    }

    @Test
    fun `convierte entre protocolo y modelos`() {
        val status = RiskStatus(RiskLevel.CAUTION, "Parque", 0.5f, 9, simulated = false)
        assertEquals(status, LinkMessage.Risk.from(status).toStatus())
        val outcome = LinkMessage.SosResult("id", 1, 3, true, 5).toOutcome()
        assertEquals(1, outcome.smsSent)
        assertTrue(outcome.contactsNotified)
    }
}
