package com.andres.walksecurity.wear

import android.util.Log
import com.andres.walksecurity.WalkSecurityApp
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosRequest
import com.andres.walksecurity.shared.WearProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/** Recibe el SOS del reloj aunque la app del teléfono esté cerrada. */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearProtocol.PATH_SOS) return
        val request = runCatching { SosRequest.fromBytes(event.data) }.getOrElse {
            Log.e(TAG, "SOS del reloj con formato inválido", it)
            return
        }
        val container = (application as WalkSecurityApp).container
        val latitude = request.latitude
        val longitude = request.longitude
        val watchLocation = if (latitude != null && longitude != null) {
            GeoPoint(latitude, longitude, request.accuracyMeters, System.currentTimeMillis())
        } else {
            null
        }

        // Este callback corre en un hilo de fondo. Bloquear mantiene vivo el servicio
        // hasta terminar de enviar los SMS (si retornara antes, el sistema podría matar el proceso).
        runBlocking {
            val result = container.alertRepository.sendSos(lastKnownLocation = watchLocation, fromWatch = true)
            container.watchBridge.publishSosOutcome(
                SosOutcome(
                    requestId = request.requestId,
                    smsSent = result.smsSent,
                    contactsTotal = result.contactsTotal,
                    serverRegistered = result.serverRegistered,
                    completedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private companion object {
        const val TAG = "PhoneWearListener"
    }
}
