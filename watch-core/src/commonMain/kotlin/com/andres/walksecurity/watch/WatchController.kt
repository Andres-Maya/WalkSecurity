package com.andres.walksecurity.watch

import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.SosTrigger
import kotlinx.coroutines.CoroutineScope
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
import kotlin.random.Random

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

/**
 * Lógica del reloj, igual para el reloj real y el emulado.
 *
 * @param vibrateOnEscalation vibrar cuando sube el nivel de riesgo. En el reloj real lo hace el
 *   servicio en segundo plano (para que funcione con la app cerrada), así que allí va en false.
 */
class WatchController(
    private val transport: WatchTransport,
    private val haptics: WatchHaptics,
    private val scope: CoroutineScope,
    private val vibrateOnEscalation: Boolean,
    private val countdownSeconds: Int = 5,
    private val phoneConfirmationTimeoutMs: Long = 45_000,
) {
    private val phoneReachable = MutableStateFlow<Boolean?>(null)
    private val sos = MutableStateFlow<SosUi>(SosUi.Idle)
    private val acknowledgedAlertAt = MutableStateFlow(0L)
    private var sosJob: Job? = null

    val state: StateFlow<WatchUiState> = combine(
        transport.risk, phoneReachable, sos, acknowledgedAlertAt,
    ) { risk, reachable, sosUi, acknowledgedAt ->
        val pending = risk?.takeIf { it.level == RiskLevel.ALERT && it.updatedAt > acknowledgedAt }
        WatchUiState(risk, reachable, sosUi, pending)
    }.stateIn(scope, SharingStarted.Eagerly, WatchUiState())

    init {
        if (vibrateOnEscalation) {
            scope.launch {
                var previous = transport.risk.value?.level ?: RiskLevel.SAFE
                transport.risk.collect { status ->
                    val level = status?.level ?: RiskLevel.SAFE
                    if (level.ordinal > previous.ordinal) haptics.riskChanged(level)
                    previous = level
                }
            }
        }
        refreshPhone()
    }

    fun refreshPhone() {
        scope.launch { phoneReachable.value = transport.isPhoneReachable() }
    }

    /** Botón SOS: cuenta regresiva para evitar activaciones accidentales. */
    fun onSosPressed() {
        if (sos.value != SosUi.Idle) return
        // El estado cambia ANTES de lanzar la corrutina: si el scope no es inmediato (reloj emulado
        // usa Dispatchers.Default), un doble toque rápido no debe iniciar dos cuentas regresivas.
        sos.value = SosUi.Countdown(countdownSeconds, SosTrigger.BUTTON)
        sosJob = scope.launch {
            for (seconds in countdownSeconds downTo 1) {
                sos.value = SosUi.Countdown(seconds, SosTrigger.BUTTON)
                haptics.tick()
                delay(1_000)
            }
            send(SosTrigger.BUTTON)
        }
    }

    fun sendNow() {
        val countdown = sos.value as? SosUi.Countdown ?: return
        sosJob?.cancel()
        sos.value = SosUi.Sending
        sosJob = scope.launch { send(countdown.trigger) }
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
        state.value.pendingAlert?.let { acknowledgedAlertAt.value = it.updatedAt }
    }

    /** El usuario confirma la emergencia en la pantalla de alerta: la confirmación ya es explícita. */
    fun confirmEmergency() {
        acknowledgeAlert()
        if (sos.value != SosUi.Idle) return
        sos.value = SosUi.Sending
        sosJob = scope.launch { send(SosTrigger.RISK_CONFIRMED) }
    }

    private suspend fun send(trigger: SosTrigger) {
        sos.value = SosUi.Sending
        val location = transport.watchLocation()
        val request = SosRequest(
            requestId = Random.nextLong().toULong().toString(16),
            trigger = trigger,
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracyMeters,
        )
        val sent = transport.sendSos(request)
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
        val outcome = withTimeoutOrNull(phoneConfirmationTimeoutMs) {
            transport.sosOutcome.filterNotNull().first { it.requestId == request.requestId }
        }
        if (outcome?.contactsNotified == true) haptics.sosSent()
        sos.value = SosUi.Done(outcome)
    }
}
