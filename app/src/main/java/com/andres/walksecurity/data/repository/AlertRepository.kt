package com.andres.walksecurity.data.repository

import com.andres.walksecurity.core.location.LocationClient
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.core.model.SosMessageBuilder
import com.andres.walksecurity.core.model.SosResult
import com.andres.walksecurity.core.sms.SmsSender
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.remote.AlertRequest
import com.andres.walksecurity.data.remote.ApiService
import com.andres.walksecurity.data.remote.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AlertRepository(
    private val api: ApiService,
    private val sessionStore: SessionStore,
    private val locationClient: LocationClient,
    private val smsSender: SmsSender,
    /** Scope de aplicación: un SOS en curso no debe cancelarse si el usuario sale de la pantalla. */
    private val appScope: CoroutineScope,
) {

    suspend fun sendSos(lastKnownLocation: GeoPoint?): SosResult =
        appScope.async { doSendSos(lastKnownLocation) }.await()

    private suspend fun doSendSos(lastKnownLocation: GeoPoint?): SosResult {
        val location = locationClient.currentLocation(timeoutMs = 8_000) ?: lastKnownLocation
        val user = sessionStore.currentUser()
        val contacts = sessionStore.contacts.first()
        val message = SosMessageBuilder.build(user?.name, location, System.currentTimeMillis())

        // 1) SMS primero: es el canal que funciona sin internet
        val smsSent = withContext(Dispatchers.IO) {
            contacts.count { smsSender.send(it.phone, message) }
        }

        // 2) Registro en el servidor (historial y futuras notificaciones push)
        val registered = apiCall {
            api.createAlert(
                AlertRequest(
                    type = "SOS",
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracyMeters = location?.accuracyMeters,
                    message = message,
                )
            )
        }.isSuccess

        return SosResult(
            location = location,
            contactsTotal = contacts.size,
            smsSent = smsSent,
            smsAvailable = smsSender.canSend(),
            serverRegistered = registered,
        )
    }
}
