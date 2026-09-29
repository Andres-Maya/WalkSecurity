package com.andres.walksecurity.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
import com.andres.walksecurity.core.location.LocationClient
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.core.model.SosResult
import com.andres.walksecurity.core.model.User
import com.andres.walksecurity.core.sms.SmsSender
import com.andres.walksecurity.data.repository.AlertRepository
import com.andres.walksecurity.data.repository.AuthRepository
import com.andres.walksecurity.data.repository.ContactsRepository
import com.andres.walksecurity.wear.WatchBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SosState {
    data object Idle : SosState
    data object NoContacts : SosState
    data class Countdown(val secondsLeft: Int) : SosState
    data object Sending : SosState
    data class Sent(val result: SosResult) : SosState
}

data class PermissionsState(
    val location: Boolean = false,
    val backgroundLocation: Boolean = false,
    val sms: Boolean = false,
)

data class WatchUi(
    /** Reloj Wear OS real conectado; null mientras se consulta. */
    val connected: Boolean? = null,
)

data class HomeUiState(
    val user: User? = null,
    val permissions: PermissionsState = PermissionsState(),
    val contactsCount: Int = 0,
    val sos: SosState = SosState.Idle,
    val watch: WatchUi = WatchUi(),
)

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val alertRepository: AlertRepository,
    private val locationClient: LocationClient,
    private val smsSender: SmsSender,
    private val watchBridge: WatchBridge,
) : ViewModel() {

    private val permissions = MutableStateFlow(PermissionsState())
    private val sos = MutableStateFlow<SosState>(SosState.Idle)
    private val watch = MutableStateFlow(WatchUi())
    private var countdownJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        authRepository.session.map { it?.user },
        permissions,
        contactsRepository.contacts.map { it.size },
        sos,
        watch,
    ) { user, perms, contactsCount, sosState, watchUi ->
        HomeUiState(user, perms, contactsCount, sosState, watchUi)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** El GPS solo está activo mientras la pantalla es visible (ahorro de batería). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val location: StateFlow<GeoPoint?> = permissions
        .map { it.location }
        .distinctUntilChanged()
        .flatMapLatest { granted -> if (granted) locationClient.locationUpdates() else emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refreshPermissions()
        viewModelScope.launch { contactsRepository.refresh() }
    }

    /** Se llama al entrar y al volver a la pantalla (p. ej. desde Ajustes). */
    fun refreshPermissions() {
        permissions.value = PermissionsState(
            location = locationClient.hasPermission(),
            backgroundLocation = locationClient.hasBackgroundPermission(),
            sms = smsSender.hasPermission(),
        )
        viewModelScope.launch {
            val connected = watchBridge.isWatchConnected()
            watch.update { it.copy(connected = connected) }
        }
    }

    fun onSosPressed() {
        if (sos.value != SosState.Idle) return
        if (uiState.value.contactsCount == 0) {
            sos.value = SosState.NoContacts
            return
        }
        // Cuenta regresiva para evitar falsas alarmas por toques accidentales
        countdownJob = viewModelScope.launch {
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                sos.value = SosState.Countdown(seconds)
                delay(1_000)
            }
            send()
        }
    }

    fun sendNow() {
        if (sos.value !is SosState.Countdown) return
        countdownJob?.cancel()
        viewModelScope.launch { send() }
    }

    fun dismissSos() {
        countdownJob?.cancel()
        if (sos.value != SosState.Sending) sos.value = SosState.Idle
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private suspend fun send() {
        sos.value = SosState.Sending
        val result = alertRepository.sendSos(lastKnownLocation = location.value)
        sos.value = SosState.Sent(result)
    }

    companion object {
        const val COUNTDOWN_SECONDS = 5

        val Factory = viewModelFactory {
            initializer {
                with(appContainer) {
                    HomeViewModel(authRepository, contactsRepository, alertRepository, locationClient, smsSender, watchBridge)
                }
            }
        }
    }
}
