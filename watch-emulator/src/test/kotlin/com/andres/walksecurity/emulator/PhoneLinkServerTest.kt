package com.andres.walksecurity.emulator

import com.andres.walksecurity.shared.DesktopLink
import com.andres.walksecurity.shared.LinkMessage
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.SosTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket

/**
 * Prueba de integración con sockets reales: un "teléfono" falso habla con el servidor del
 * emulador igual que lo hace DesktopWatchLink en la app.
 */
class PhoneLinkServerTest {

    private val port = DesktopLink.PORT + 111
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = EventLog()
    private val server = PhoneLinkServer(scope, log, port)
    private val transport = EmulatorTransport(SimulatedPhone(scope, log), server, scope)

    @After
    fun tearDown() = scope.cancel()

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Tiempo agotado esperando: $what" }
            Thread.sleep(20)
        }
    }

    private fun connectPhone(): Socket {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try {
                return Socket("127.0.0.1", port)
            } catch (e: java.io.IOException) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(50)
            }
        }
    }

    @Test
    fun `el reloj del computador usa el telefono real y le envia el SOS`() = runBlocking {
        server.start(prepareUsb = false)
        val phone = connectPhone()
        val out = phone.getOutputStream().bufferedWriter()
        val input = phone.getInputStream().bufferedReader()
        fun send(message: LinkMessage) {
            out.write(DesktopLink.encode(message)); out.newLine(); out.flush()
        }

        // 1) El teléfono se presenta y publica una alerta
        send(LinkMessage.Hello("OPPO CPH2269", "Andrés"))
        send(LinkMessage.Risk(RiskLevel.ALERT, "Calle 19", 0.86f, updatedAt = 10))
        waitUntil("estado del teléfono real") { transport.risk.value?.level == RiskLevel.ALERT }
        assertTrue(server.connected.value)
        assertEquals(PhoneLinkStatus.Connected("OPPO CPH2269", "Andrés"), server.status.value)
        assertTrue(transport.isPhoneReachable())
        assertNull("El PC no tiene GPS: se usa el del teléfono", transport.watchLocation())

        // 2) El reloj del PC pide SOS: le llega al teléfono
        assertTrue(transport.sendSos(SosRequest("req-1", SosTrigger.RISK_CONFIRMED, null, null, null)).isSuccess)
        val sos = DesktopLink.decode(input.readLine()) as LinkMessage.Sos
        assertEquals("req-1", sos.requestId)
        assertEquals(SosTrigger.RISK_CONFIRMED, sos.trigger)

        // 3) El teléfono responde cuántos SMS envió
        send(LinkMessage.SosResult("req-1", smsSent = 2, contactsTotal = 2, serverRegistered = false, completedAt = 20))
        waitUntil("resultado del SOS") { transport.sosOutcome.value?.requestId == "req-1" }
        assertEquals(2, transport.sosOutcome.value?.smsSent)

        // 4) Al desconectar el cable, vuelve el teléfono simulado
        phone.close()
        waitUntil("desconexión") { !server.connected.value }
        assertFalse(server.connected.value)
        assertEquals(RiskLevel.SAFE, transport.risk.value?.level)
        assertTrue(log.entries.value.any { it.text.contains("Teléfono real conectado: OPPO CPH2269") })
    }

    @Test
    fun `sin telefono real el SOS se atiende con el telefono simulado`() = runBlocking {
        assertFalse(server.connected.value)
        assertTrue(transport.sendSos(SosRequest("req-2", SosTrigger.BUTTON, 4.6, -74.0, 5f)).isSuccess)
        waitUntil("resultado simulado") { transport.sosOutcome.value?.requestId == "req-2" }
    }
}
