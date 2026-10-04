package com.andres.walksecurity.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
import com.andres.walksecurity.core.location.LocationClient
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.core.model.SosResult
import com.andres.walksecurity.core.model.Profile
import com.andres.walksecurity.core.risk.RiskZone
import com.andres.walksecurity.core.risk.ZoneDetector
import com.andres.walksecurity.core.risk.ZoneGeofencing
import com.andres.walksecurity.data.repository.RiskStatusRepository
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.watch.WatchController
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.core.sms.SmsSender
import com.andres.walksecurity.data.repository.AlertRepository
import com.andres.walksecurity.data.local.SessionStore
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
    /** Perfil local: el nombre aparece en los SMS de emergencia. */
    val profile: Profile = Profile(),
    val permissions: PermissionsState = PermissionsState(),
    val contactsCount: Int = 0,
    val sos: SosState = SosState.Idle,
    val watch: WatchUi = WatchUi(),
)

class HomeViewModel(
    private val sessionStore: SessionStore,
    private val contactsRepository: ContactsRepository,
    private val alertRepository: AlertRepository,
    private val locationClient: LocationClient,
    private val smsSender: SmsSender,
    private val watchBridge: WatchBridge,
    private val zoneDetector: ZoneDetector,
    private val zoneGeofencing: ZoneGeofencing,
    riskStatusRepository: RiskStatusRepository,
    /** El reloj emulado hace vibrar el teléfono al subir el riesgo (no hay reloj físico). */
    @Suppress("unused") private val emulatedWatch: WatchController,
) : ViewModel() {

    /** Zonas de riesgo estimado que se dibujan en el mapa. */
    val zones: List<RiskZone> = zoneDetector.model.zones

    /** Nivel de riesgo actual (detectado por GPS o elegido en el simulador). */
    val risk: StateFlow<RiskStatus?> = riskStatusRepository.current

    /** Cada posición nueva se compara con las zonas; si cambia el nivel se avisa al reloj. */
    fun onLocation(point: GeoPoint) {
        viewModelScope.launch { zoneDetector.onLocation(point) }
    }

    private val permissions = MutableStateFlow(PermissionsState())
    private val sos = MutableStateFlow<SosState>(SosState.Idle)
    private val watch = MutableStateFlow(WatchUi())
    private var countdownJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        sessionStore.profile,
        permissions,
        contactsRepository.contacts.map { it.size },
        sos,
        watch,
    ) { profile, perms, contactsCount, sosState, watchUi ->
        HomeUiState(
            profile = profile,
            permissions = perms,
            contactsCount = contactsCount,
            sos = sosState,
            watch = watchUi,
        )
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
        contactsRepository.requestSync()
    }

    private var geofencesRegistered = false

    /** Se llama al entrar y al volver a la pantalla (p. ej. desde Ajustes). */
    fun refreshPermissions() {
        permissions.value = PermissionsState(
            location = locationClient.hasPermission(),
            backgroundLocation = locationClient.hasBackgroundPermission(),
            sms = smsSender.hasPermission(),
        )
        // Al conceder "Todo el tiempo" se activa la detección en segundo plano sin reiniciar la app
        if (zoneGeofencing.hasPermissions() && !geofencesRegistered) {
            geofencesRegistered = true
            viewModelScope.launch { geofencesRegistered = zoneGeofencing.register() }
        }
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

    /** @return mensaje de error, o null si se guardó. */
    fun saveProfile(name: String, phone: String): String? {
        val error = when {
            name.isBlank() -> "Escribe tu nombre: tus contactos lo verán en el SMS de emergencia."
            phone.isNotBlank() && !Validators.isValidPhone(phone) -> "Teléfono inválido (7 a 15 dígitos, puede iniciar con +)."
            else -> null
        }
        if (error == null) {
            viewModelScope.launch {
                sessionStore.saveProfile(Profile(name.trim(), Validators.normalizePhone(phone)))
                contactsRepository.requestSync() // actualiza el nombre en el servidor si está disponible
            }
        }
        return error
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
                    HomeViewModel(
                        sessionStore, contactsRepository, alertRepository, locationClient, smsSender, watchBridge,
                        zoneDetector, zoneGeofencing, riskStatusRepository, emulatedWatch,
                    )
                }
            }
        }
    }
}
