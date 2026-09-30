package com.andres.walksecurity.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andres.walksecurity.watch.VibratorHaptics
import com.andres.walksecurity.watch.WatchController
import kotlinx.coroutines.launch

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    // En el reloj real la vibración por cambio de riesgo la hace WatchListenerService/RiskNotifier
    val controller = WatchController(
        transport = DataLayerWatchTransport(application),
        haptics = VibratorHaptics(application),
        scope = viewModelScope,
        vibrateOnEscalation = false,
    )

    init {
        viewModelScope.launch { WatchState.loadFromDataLayer(application) }
    }
}
