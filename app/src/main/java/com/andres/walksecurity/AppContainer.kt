package com.andres.walksecurity

import android.content.Context
import com.andres.walksecurity.core.location.LocationClient
import com.andres.walksecurity.core.sms.SmsSender
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.remote.ApiClient
import com.andres.walksecurity.data.repository.AlertRepository
import com.andres.walksecurity.data.repository.AuthRepository
import com.andres.walksecurity.data.repository.ContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Inyección de dependencias manual: suficiente para el tamaño actual del proyecto. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(context)
    val locationClient = LocationClient(context)
    val smsSender = SmsSender(context)

    private val api = ApiClient.create(BuildConfig.API_BASE_URL, sessionStore)

    val authRepository = AuthRepository(api, sessionStore)
    val contactsRepository = ContactsRepository(api, sessionStore)
    val alertRepository = AlertRepository(api, sessionStore, locationClient, smsSender, appScope)
}
