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

    /**
     * @param lastKnownLocation respaldo si el GPS del teléfono no responde (p. ej. la ubicación del reloj).
     * @param fromWatch el SOS se activó desde el reloj (se indica en el mensaje).
     */
    suspend fun sendSos(lastKnownLocation: GeoPoint?, fromWatch: Boolean = false): SosResult =
        appScope.async { doSendSos(lastKnownLocation, fromWatch) }.await()

    private suspend fun doSendSos(lastKnownLocation: GeoPoint?, fromWatch: Boolean): SosResult {
        val location = locationClient.currentLocation(timeoutMs = 8_000) ?: lastKnownLocation
        val session = sessionStore.currentSession()
        val user = session?.user
        val contacts = sessionStore.contacts.first().filterNot { it.pendingDelete }
        val message = SosMessageBuilder.build(user?.name, location, System.currentTimeMillis(), fromWatch = fromWatch)

        // 1) SMS primero: es el canal que funciona sin internet
        val smsSent = withContext(Dispatchers.IO) {
            contacts.count { smsSender.send(it.phone, message) }
        }

        // 2) Registro en el servidor (historial y futuras notificaciones push). En modo local no hay cuenta.
        val localMode = session == null || session.isLocal
        val registered = !localMode && apiCall {
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
            localMode = localMode,
        )
    }
}
