package com.andres.walksecurity.wear

import android.os.Build
import android.util.Log
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.repository.AlertRepository
import com.andres.walksecurity.data.repository.RiskStatusRepository
import com.andres.walksecurity.shared.DesktopLink
import com.andres.walksecurity.shared.LinkMessage
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

sealed interface DesktopLinkState {
    data object Off : DesktopLinkState
    data class Searching(val hint: String) : DesktopLinkState
    data object Connected : DesktopLinkState
}

/**
 * Conecta este teléfono con el reloj emulado del computador (watch-emulator) por el cable USB.
 * Hace lo mismo que PhoneWearListenerService con un reloj Wear OS real: le envía el estado de la zona
 * y, cuando el reloj pide SOS, envía los SMS reales desde este teléfono y le devuelve el resultado.
 */
class DesktopWatchLink(
    private val riskStatusRepository: RiskStatusRepository,
    private val alertRepository: AlertRepository,
    private val sessionStore: SessionStore,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<DesktopLinkState>(DesktopLinkState.Off)
    val state: StateFlow<DesktopLinkState> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var socket: Socket? = null

    fun start() {
        if (job?.isActive == true) return
        job = appScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    _state.value = DesktopLinkState.Searching("Buscando el reloj del computador…")
                    try {
                        Socket().use { s ->
                            socket = s
                            s.connect(InetSocketAddress(HOST, DesktopLink.PORT), CONNECT_TIMEOUT_MS)
                            s.tcpNoDelay = true
                            runSession(s)
                        }
                    } catch (e: IOException) {
                        Log.d(TAG, "Sin conexión con el computador: ${e.message}")
                    } finally {
                        socket = null
                    }
                    _state.value = DesktopLinkState.Searching(
                        "No se encuentra el reloj del computador. Abre el emulador en el PC y deja el cable USB conectado."
                    )
                    delay(RETRY_MS)
                }
            } finally {
                _state.value = DesktopLinkState.Off
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // La lectura del socket es bloqueante: cerrarlo la interrumpe
        runCatching { socket?.close() }
    }

    private suspend fun runSession(s: Socket) = coroutineScope {
        val writer = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
        val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
        val writeLock = Mutex()
        val send: suspend (LinkMessage) -> Unit = { message -> writeLock.withLock { writer.writeLine(message) } }

        send(LinkMessage.Hello(device = "${Build.MANUFACTURER} ${Build.MODEL}", userName = sessionStore.currentUser()?.name))
        _state.value = DesktopLinkState.Connected

        // Si aún no se ha elegido zona, el reloj arranca en "zona segura"
        if (riskStatusRepository.current.value == null) {
            send(LinkMessage.Risk.from(RiskStatus(RiskLevel.SAFE, null, 0.1f, System.currentTimeMillis(), simulated = true)))
        }
        val forwardRisk = launch {
            riskStatusRepository.current.filterNotNull().collect { send(LinkMessage.Risk.from(it)) }
        }
        try {
            while (true) {
                val line = reader.readLine() ?: break
                val message = DesktopLink.decode(line)
                if (message is LinkMessage.Sos) launch { handleSos(message, send) }
            }
        } finally {
            forwardRisk.cancel()
        }
    }

    private suspend fun handleSos(message: LinkMessage.Sos, send: suspend (LinkMessage) -> Unit) {
        val lat = message.latitude
        val lng = message.longitude
        val watchLocation = if (lat != null && lng != null) {
            GeoPoint(lat, lng, message.accuracyMeters, System.currentTimeMillis())
        } else {
            null
        }
        val result = alertRepository.sendSos(lastKnownLocation = watchLocation, fromWatch = true)
        try {
            send(
                LinkMessage.SosResult(
                    requestId = message.requestId,
                    smsSent = result.smsSent,
                    contactsTotal = result.contactsTotal,
                    serverRegistered = result.serverRegistered,
                    completedAt = System.currentTimeMillis(),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "El SOS se envió, pero el computador se desconectó antes de recibir el resultado")
        }
    }

    private fun BufferedWriter.writeLine(message: LinkMessage) {
        write(DesktopLink.encode(message))
        newLine()
        flush()
    }

    private companion object {
        const val TAG = "DesktopWatchLink"
        // Con `adb reverse tcp:8766 tcp:8766`, el localhost del teléfono llega al computador
        const val HOST = "127.0.0.1"
        const val CONNECT_TIMEOUT_MS = 3_000
        const val RETRY_MS = 3_000L
    }
}
