package com.andres.walksecurity.emulator

import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.SosTrigger
import com.andres.walksecurity.watch.PhoneNotReachableException
import com.andres.walksecurity.watch.WatchLocation
import com.andres.walksecurity.watch.WatchTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class LogSource(val label: String) { PHONE("Teléfono"), WATCH("Reloj"), USER("Usuario"), PC("Computador") }

data class LogEntry(val time: String, val source: LogSource, val text: String)

/** Registro de lo que ocurre entre el reloj y el teléfono (lo más reciente primero). */
class EventLog {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(source: LogSource, text: String) {
        val entry = LogEntry(LocalTime.now().format(formatter), source, text)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}

/**
 * Teléfono simulado: cumple el mismo contrato ([WatchTransport]) que el Wearable Data Layer.
 * Igual que en la realidad, si el reloj está desconectado el estado se entrega al reconectar.
 */
class SimulatedPhone(private val scope: CoroutineScope, private val log: EventLog) : WatchTransport {

    private val _risk = MutableStateFlow<RiskStatus?>(null)
    override val risk: StateFlow<RiskStatus?> = _risk.asStateFlow()

    private val _sosOutcome = MutableStateFlow<SosOutcome?>(null)
    override val sosOutcome: StateFlow<SosOutcome?> = _sosOutcome.asStateFlow()

    private val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _contacts = MutableStateFlow(2)
    val contacts: StateFlow<Int> = _contacts.asStateFlow()

    /** Último estado calculado en el teléfono (puede no haber llegado aún al reloj). */
    private val _phoneLevel = MutableStateFlow(RiskLevel.SAFE)
    val phoneLevel: StateFlow<RiskLevel> = _phoneLevel.asStateFlow()
    private var phoneStatus: RiskStatus = statusFor(RiskLevel.SAFE)

    init {
        _risk.value = phoneStatus
    }

    fun publish(level: RiskLevel) {
        phoneStatus = statusFor(level)
        _phoneLevel.value = level
        val zone = phoneStatus.zoneName?.let { " · $it" }.orEmpty()
        val score = ((phoneStatus.score ?: 0f) * 100).roundToInt()
        log.add(LogSource.PHONE, "Calcula la zona: ${level.label()}$zone (riesgo estimado $score %)")
        if (_connected.value) deliver() else log.add(LogSource.PHONE, "Reloj desconectado: se entregará al reconectar")
    }

    fun setConnected(value: Boolean) {
        if (_connected.value == value) return
        _connected.value = value
        log.add(LogSource.USER, if (value) "Reconecta el reloj con el teléfono" else "Desconecta el reloj del teléfono")
        if (value) deliver()
    }

    fun setContacts(count: Int) {
        _contacts.value = count
        log.add(LogSource.USER, "Contactos de emergencia en el teléfono: $count")
    }

    override suspend fun isPhoneReachable(): Boolean = _connected.value

    override suspend fun sendSos(request: SosRequest): Result<Unit> {
        if (!_connected.value) {
            log.add(LogSource.WATCH, "No encuentra el teléfono: el SOS no se pudo entregar")
            return Result.failure(PhoneNotReachableException())
        }
        val origin = if (request.trigger == SosTrigger.RISK_CONFIRMED) "confirmó emergencia" else "botón SOS"
        val lat = request.latitude
        val lng = request.longitude
        val where = if (lat != null && lng != null) String.format(Locale.US, "%.4f, %.4f", lat, lng) else "sin ubicación"
        log.add(LogSource.WATCH, "Envía SOS al teléfono ($origin) · GPS del reloj: $where")
        scope.launch {
            delay(1_200) // el teléfono obtiene su GPS y envía los SMS
            val total = _contacts.value
            _sosOutcome.value = SosOutcome(
                requestId = request.requestId,
                smsSent = total,
                contactsTotal = total,
                serverRegistered = true,
                completedAt = System.currentTimeMillis(),
            )
            log.add(
                LogSource.PHONE,
                if (total > 0) "Envía SMS con la ubicación a $total de $total contactos y registra la alerta (simulado)"
                else "No hay contactos de emergencia: no se envió ningún SMS",
            )
        }
        return Result.success(Unit)
    }

    /** Coordenadas fijas de ejemplo (Bogotá): en el computador no hay GPS. */
    override suspend fun watchLocation(): WatchLocation = WatchLocation(4.6097, -74.0817, 12f)

    private fun deliver() {
        if (_risk.value == phoneStatus) return
        _risk.value = phoneStatus
        log.add(LogSource.WATCH, "Recibe el estado: ${phoneStatus.level.label()}")
    }

    private fun statusFor(level: RiskLevel) = RiskStatus(
        level = level,
        zoneName = when (level) {
            RiskLevel.SAFE -> null
            RiskLevel.CAUTION -> "Parque Central (simulado)"
            RiskLevel.ALERT -> "Calle 19 con Cra. 7 (simulado)"
        },
        score = when (level) {
            RiskLevel.SAFE -> 0.12f
            RiskLevel.CAUTION -> 0.52f
            RiskLevel.ALERT -> 0.86f
        },
        updatedAt = System.currentTimeMillis(),
        simulated = true,
    )
}

fun RiskLevel.label(): String = when (this) {
    RiskLevel.SAFE -> "Zona segura"
    RiskLevel.CAUTION -> "Precaución"
    RiskLevel.ALERT -> "Alerta"
}
