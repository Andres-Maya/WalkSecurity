package com.andres.walksecurity.emulator

import com.andres.walksecurity.shared.DesktopLink
import com.andres.walksecurity.shared.LinkMessage
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

sealed interface PhoneLinkStatus {
    data object Starting : PhoneLinkStatus
    data object Waiting : PhoneLinkStatus
    data class Connected(val device: String, val userName: String?) : PhoneLinkStatus
    data class Failed(val message: String) : PhoneLinkStatus
}

/**
 * Espera a la app WalkSecurity del teléfono real (por USB, con `adb reverse`) y hace de puente:
 * el teléfono publica la zona y ejecuta el SOS real; este reloj solo muestra y envía la orden.
 * Escucha únicamente en localhost: no queda expuesto a la red.
 */
class PhoneLinkServer(
    private val scope: CoroutineScope,
    private val log: EventLog,
    private val port: Int = DesktopLink.PORT,
) {

    private val _status = MutableStateFlow<PhoneLinkStatus>(PhoneLinkStatus.Starting)
    val status: StateFlow<PhoneLinkStatus> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _risk = MutableStateFlow<RiskStatus?>(null)
    val risk: StateFlow<RiskStatus?> = _risk.asStateFlow()

    private val _sosOutcome = MutableStateFlow<SosOutcome?>(null)
    val sosOutcome: StateFlow<SosOutcome?> = _sosOutcome.asStateFlow()

    @Volatile
    private var writer: BufferedWriter? = null

    /** @param prepareUsb ejecutar `adb reverse` (false en las pruebas). */
    fun start(prepareUsb: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            val server = try {
                ServerSocket(port, 1, InetAddress.getLoopbackAddress())
            } catch (e: IOException) {
                _status.value = PhoneLinkStatus.Failed(
                    "El puerto $port está ocupado (¿hay otro emulador abierto?)."
                )
                return@launch
            }
            server.use {
                while (isActive) {
                    _status.value = PhoneLinkStatus.Waiting
                    val client = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    serve(client)
                }
            }
        }
        if (prepareUsb) scope.launch(Dispatchers.IO) { prepareUsb() }
    }

    /** Ejecuta `adb reverse` para que el teléfono conectado por USB llegue a este computador. */
    fun prepareUsb() {
        log.add(LogSource.PC, Adb.reverse(DesktopLink.PORT))
    }

    /** @return false si no hay teléfono conectado o se cortó la conexión. */
    fun sendSos(request: SosRequest): Boolean {
        val out = writer ?: return false
        return try {
            synchronized(this) {
                out.write(DesktopLink.encode(LinkMessage.Sos.from(request)))
                out.newLine()
                out.flush()
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun serve(client: Socket) {
        client.use {
            client.tcpNoDelay = true
            writer = client.getOutputStream().bufferedWriter(Charsets.UTF_8)
            val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
            _connected.value = true
            _status.value = PhoneLinkStatus.Connected("Teléfono", null)
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    when (val message = DesktopLink.decode(line)) {
                        is LinkMessage.Hello -> {
                            _status.value = PhoneLinkStatus.Connected(message.device, message.userName)
                            log.add(LogSource.PHONE, "Teléfono real conectado: ${message.device}" +
                                message.userName?.let { " ($it)" }.orEmpty())
                        }
                        is LinkMessage.Risk -> {
                            val status = message.toStatus()
                            _risk.value = status
                            log.add(LogSource.PHONE, "Envía el estado de la zona: ${status.level.label()}" +
                                status.zoneName?.let { " · $it" }.orEmpty())
                        }
                        is LinkMessage.SosResult -> {
                            val outcome = message.toOutcome()
                            _sosOutcome.value = outcome
                            log.add(
                                LogSource.PHONE,
                                if (outcome.contactsNotified) {
                                    "SMS REALES enviados a ${outcome.smsSent} de ${outcome.contactsTotal} contactos"
                                } else if (outcome.contactsTotal == 0) {
                                    "El teléfono no tiene contactos de emergencia: no se envió ningún SMS"
                                } else {
                                    "El teléfono no pudo enviar los SMS (revisa el permiso de SMS o la SIM)"
                                },
                            )
                        }
                        is LinkMessage.Sos, null -> Unit
                    }
                }
            } catch (_: IOException) {
                // Cable desconectado o app cerrada
            } finally {
                writer = null
                _connected.value = false
                _risk.value = null
                log.add(LogSource.PHONE, "Teléfono real desconectado: vuelve el teléfono simulado")
            }
        }
    }
}

/** Utilidades de adb (Android SDK platform-tools). */
object Adb {

    fun reverse(port: Int): String {
        val adb = find() ?: return "No se encontró adb: el teléfono real no podrá conectarse por USB."
        return try {
            val process = ProcessBuilder(adb, "reverse", "tcp:$port", "tcp:$port").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroy()
                return "adb no respondió."
            }
            if (process.exitValue() == 0) {
                "USB listo (adb reverse $port): en el teléfono, Reloj → Reloj del computador."
            } else {
                "USB: ${output.lineSequence().lastOrNull().orEmpty().ifBlank { "no hay teléfono conectado" }}"
            }
        } catch (e: IOException) {
            "No se pudo ejecutar adb: ${e.message}"
        }
    }

    private fun find(): String? {
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val sdkDirs = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk" },
            System.getProperty("user.home")?.let { "$it/Library/Android/sdk" },
        )
        sdkDirs.map { File(it, "platform-tools/$exe") }.firstOrNull { it.isFile }?.let { return it.path }
        // Último recurso: adb en el PATH
        return System.getenv("PATH")?.split(File.pathSeparator)
            ?.map { File(it, exe) }
            ?.firstOrNull { it.isFile }
            ?.path
    }
}
