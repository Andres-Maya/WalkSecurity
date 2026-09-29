package com.andres.walksecurity.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Recibe los DataItems del teléfono incluso con la app del reloj cerrada. */
class WatchListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // El buffer se libera al salir de este método: se procesa aquí mismo
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .forEach { event ->
                WatchState.apply(event.dataItem)?.let { status ->
                    RiskNotifier.onRiskStatus(applicationContext, status)
                }
            }
    }
}
