package com.andres.walksecurity.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.SosTrigger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed interface SosUi {
    data object Idle : SosUi
    data class Countdown(val secondsLeft: Int, val trigger: SosTrigger) : SosUi
    data object Sending : SosUi
    /** El teléfono recibió el SOS; esperamos a que confirme el envío de los SMS. */
    data object WaitingPhone : SosUi
    /** outcome == null: el teléfono recibió la orden pero no confirmó a tiempo. */
    data class Done(val outcome: SosOutcome?) : SosUi
    data class Failed(val message: String) : SosUi
}

data class WatchUiState(
    val risk: RiskStatus? = null,
    val phoneReachable: Boolean? = null,
    val sos: SosUi = SosUi.Idle,
    /** Alerta pendiente de respuesta del usuario ("¿Estás bien?"). */
    val pendingAlert: RiskStatus? = null,
)

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    private val phoneLink = PhoneLink(application)
    private val phoneReachable = MutableStateFlow<Boolean?>(null)
    private val sos = MutableStateFlow<SosUi>(SosUi.Idle)
    private val acknowledgedAlertAt = MutableStateFlow(0L)
    private var sosJob: Job? = null

    val uiState: StateFlow<WatchUiState> = combine(
        WatchState.risk, phoneReachable, sos, acknowledgedAlertAt,
    ) { risk, reachable, sosUi, acknowledgedAt ->
        val pending = risk?.takeIf { it.level == RiskLevel.ALERT && it.updatedAt > acknowledgedAt }
        WatchUiState(risk, reachable, sosUi, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchUiState())

    init {
        viewModelScope.launch { WatchState.loadFromDataLayer(application) }
        refreshPhone()
    }

    fun refreshPhone() {
        viewModelScope.launch { phoneReachable.value = phoneLink.isPhoneReachable() }
    }

    /** Botón SOS: cuenta regresiva de 5 s para evitar activaciones accidentales. */
    fun onSosPressed() {
        if (sos.value != SosUi.Idle) return
        sosJob = viewModelScope.launch {
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                sos.value = SosUi.Countdown(seconds, SosTrigger.BUTTON)
                Haptics.tick(getApplication())
                delay(1_000)
            }
            send(SosTrigger.BUTTON)
        }
    }

    fun sendNow() {
        val countdown = sos.value as? SosUi.Countdown ?: return
        sosJob?.cancel()
        sosJob = viewModelScope.launch { send(countdown.trigger) }
    }

    fun cancelSos() {
        if (sos.value is SosUi.Countdown) {
            sosJob?.cancel()
            sos.value = SosUi.Idle
        }
    }

    fun dismissResult() {
        if (sos.value is SosUi.Done || sos.value is SosUi.Failed) sos.value = SosUi.Idle
    }

    /** "Estoy bien": se descarta la alerta actual (una nueva alerta volverá a preguntar). */
    fun acknowledgeAlert() {
        uiState.value.pendingAlert?.let { acknowledgedAlertAt.value = it.updatedAt }
    }

    /** El usuario confirma la emergencia en la pantalla de alerta: la confirmación ya es explícita. */
    fun confirmEmergency() {
        acknowledgeAlert()
        if (sos.value != SosUi.Idle) return
        sosJob = viewModelScope.launch { send(SosTrigger.RISK_CONFIRMED) }
    }

    private suspend fun send(trigger: SosTrigger) {
        sos.value = SosUi.Sending
        val location = phoneLink.watchLocation()
        val request = SosRequest(
            requestId = UUID.randomUUID().toString(),
            trigger = trigger,
            latitude = location?.first,
            longitude = location?.second,
            accuracyMeters = location?.third,
        )
        val sent = phoneLink.sendSos(request)
        if (sent.isFailure) {
            phoneReachable.value = false
            sos.value = SosUi.Failed(
                if (sent.exceptionOrNull() is PhoneNotReachableException) {
                    "Teléfono no conectado. Usa el teléfono para pedir ayuda."
                } else {
                    "No se pudo enviar al teléfono."
                }
            )
            return
        }
        phoneReachable.value = true
        sos.value = SosUi.WaitingPhone
        val outcome = withTimeoutOrNull(PHONE_CONFIRMATION_TIMEOUT_MS) {
            WatchState.sosOutcome.filterNotNull().first { it.requestId == request.requestId }
        }
        if (outcome?.contactsNotified == true) Haptics.sosSent(getApplication())
        sos.value = SosUi.Done(outcome)
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 5
        const val PHONE_CONFIRMATION_TIMEOUT_MS = 45_000L
    }
}
