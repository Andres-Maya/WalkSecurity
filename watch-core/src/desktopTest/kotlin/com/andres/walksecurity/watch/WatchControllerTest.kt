package com.andres.walksecurity.watch

import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.SosTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchControllerTest {

    private class FakeTransport(var reachable: Boolean = true) : WatchTransport {
        override val risk = MutableStateFlow<RiskStatus?>(null)
        override val sosOutcome = MutableStateFlow<SosOutcome?>(null)
        val sent = mutableListOf<SosRequest>()

        override suspend fun isPhoneReachable() = reachable
        override suspend fun sendSos(request: SosRequest): Result<Unit> {
            if (!reachable) return Result.failure(PhoneNotReachableException())
            sent += request
            return Result.success(Unit)
        }
        override suspend fun watchLocation() = WatchLocation(4.7, -74.0, 10f)

        fun completeLast(smsSent: Int, total: Int) {
            sosOutcome.value = SosOutcome(sent.last().requestId, smsSent, total, true, 0L)
        }
    }

    private class FakeHaptics : WatchHaptics {
        val events = mutableListOf<String>()
        override fun riskChanged(level: RiskLevel) { events += "risk:$level" }
        override fun sosSent() { events += "sent" }
        override fun tick() { events += "tick" }
    }

    private fun TestScope.controller(
        transport: FakeTransport,
        haptics: FakeHaptics = FakeHaptics(),
        vibrate: Boolean = true,
    ) = WatchController(transport, haptics, backgroundScope, vibrateOnEscalation = vibrate).also { runCurrent() }

    private fun status(level: RiskLevel, at: Long) = RiskStatus(level, "Zona", 0.5f, at)

    @Test
    fun `SOS espera la cuenta regresiva y luego se envia con la ubicacion del reloj`() = runTest {
        val transport = FakeTransport()
        val haptics = FakeHaptics()
        val c = controller(transport, haptics)

        c.onSosPressed()
        runCurrent()
        assertEquals(SosUi.Countdown(5, SosTrigger.BUTTON), c.state.value.sos)
        assertTrue(transport.sent.isEmpty())

        advanceTimeBy(5_001)
        assertEquals(1, transport.sent.size)
        assertEquals(4.7, transport.sent.single().latitude!!, 0.0)
        assertEquals(SosUi.WaitingPhone, c.state.value.sos)

        transport.completeLast(smsSent = 2, total = 2)
        runCurrent()
        val done = c.state.value.sos as SosUi.Done
        assertEquals(2, done.outcome!!.smsSent)
        assertEquals(5, haptics.events.count { it == "tick" })
        assertTrue("sent" in haptics.events)
    }

    @Test
    fun `un doble toque rapido en SOS envia una sola alerta`() = runTest {
        val transport = FakeTransport()
        val c = controller(transport)

        // Sin runCurrent entre toques: simula un scope no inmediato (Dispatchers.Default)
        c.onSosPressed()
        c.onSosPressed()
        c.sendNow()
        c.sendNow()
        advanceTimeBy(10_000)

        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `cancelar durante la cuenta regresiva no envia nada`() = runTest {
        val transport = FakeTransport()
        val c = controller(transport)

        c.onSosPressed()
        advanceTimeBy(2_000)
        c.cancelSos()
        advanceTimeBy(10_000)

        assertTrue(transport.sent.isEmpty())
        assertEquals(SosUi.Idle, c.state.value.sos)
    }

    @Test
    fun `sin telefono el SOS falla con un mensaje claro`() = runTest {
        val transport = FakeTransport(reachable = false)
        val c = controller(transport)

        c.onSosPressed()
        c.sendNow()
        runCurrent()

        val failed = c.state.value.sos as SosUi.Failed
        assertTrue(failed.message.contains("Teléfono no conectado"))
        assertEquals(false, c.state.value.phoneReachable)
    }

    @Test
    fun `si el telefono no confirma a tiempo se muestra enviado sin resultado`() = runTest {
        val transport = FakeTransport()
        val c = controller(transport)

        c.onSosPressed()
        c.sendNow()
        advanceTimeBy(46_000)

        assertNull((c.state.value.sos as SosUi.Done).outcome)
    }

    @Test
    fun `una alerta pregunta si estas bien hasta que respondes`() = runTest {
        val transport = FakeTransport()
        val c = controller(transport)

        transport.risk.value = status(RiskLevel.ALERT, at = 100)
        runCurrent()
        assertEquals(100L, c.state.value.pendingAlert?.updatedAt)

        c.acknowledgeAlert()
        runCurrent()
        assertNull(c.state.value.pendingAlert)

        // Una alerta nueva vuelve a preguntar
        transport.risk.value = status(RiskLevel.ALERT, at = 200)
        runCurrent()
        assertEquals(200L, c.state.value.pendingAlert?.updatedAt)
    }

    @Test
    fun `confirmar emergencia envia el SOS sin cuenta regresiva`() = runTest {
        val transport = FakeTransport()
        val c = controller(transport)
        transport.risk.value = status(RiskLevel.ALERT, at = 100)
        runCurrent()

        c.confirmEmergency()
        runCurrent()

        assertEquals(SosTrigger.RISK_CONFIRMED, transport.sent.single().trigger)
        assertNull(c.state.value.pendingAlert)
    }

    @Test
    fun `vibra solo cuando el riesgo sube`() = runTest {
        val transport = FakeTransport()
        val haptics = FakeHaptics()
        controller(transport, haptics)

        for ((i, level) in listOf(RiskLevel.CAUTION, RiskLevel.ALERT, RiskLevel.CAUTION, RiskLevel.SAFE, RiskLevel.ALERT).withIndex()) {
            transport.risk.value = status(level, at = i.toLong())
            runCurrent()
        }

        assertEquals(listOf("risk:CAUTION", "risk:ALERT", "risk:ALERT"), haptics.events)
    }

    @Test
    fun `el reloj real no vibra desde el controlador`() = runTest {
        val transport = FakeTransport()
        val haptics = FakeHaptics()
        controller(transport, haptics, vibrate = false)

        transport.risk.value = status(RiskLevel.ALERT, at = 1)
        runCurrent()

        assertTrue(haptics.events.isEmpty())
    }
}
