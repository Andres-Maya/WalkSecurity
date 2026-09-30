package com.andres.walksecurity

import android.content.Context
import com.andres.walksecurity.core.contacts.PhoneContactsReader
import com.andres.walksecurity.core.location.LocationClient
import com.andres.walksecurity.core.sms.SmsSender
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.remote.ApiClient
import com.andres.walksecurity.data.repository.AlertRepository
import com.andres.walksecurity.data.repository.AuthRepository
import com.andres.walksecurity.data.repository.ContactsRepository
import com.andres.walksecurity.data.repository.RiskStatusRepository
import com.andres.walksecurity.watch.VibratorHaptics
import com.andres.walksecurity.watch.WatchController
import com.andres.walksecurity.wear.DesktopWatchLink
import com.andres.walksecurity.wear.EmulatedWatchTransport
import com.andres.walksecurity.wear.WatchBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual: suficiente para el tamaño actual del proyecto. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(context)
    val locationClient = LocationClient(context)
    val smsSender = SmsSender(context)
    val watchBridge = WatchBridge(context)
    val phoneContactsReader = PhoneContactsReader(context)

    private val api = ApiClient.create(BuildConfig.API_BASE_URL, sessionStore)

    val contactsRepository = ContactsRepository(api, sessionStore, appScope)
    val authRepository = AuthRepository(api, sessionStore, contactsRepository)
    val alertRepository = AlertRepository(api, sessionStore, locationClient, smsSender, appScope)
    val riskStatusRepository = RiskStatusRepository(watchBridge)

    /**
     * Reloj emulado en el teléfono. Vive en el scope de la app para que "vibre en la muñeca"
     * aunque el usuario no tenga abierta la pantalla del reloj. Se crea al usarlo por primera vez.
     */
    /** Enlace con el reloj emulado del computador (watch-emulator) por USB. */
    val desktopWatchLink: DesktopWatchLink by lazy {
        DesktopWatchLink(riskStatusRepository, alertRepository, sessionStore, appScope)
    }

    val emulatedWatch: WatchController by lazy {
        WatchController(
            transport = EmulatedWatchTransport(riskStatusRepository, alertRepository, appScope),
            haptics = VibratorHaptics(context),
            scope = appScope,
            vibrateOnEscalation = true,
        )
    }
}
