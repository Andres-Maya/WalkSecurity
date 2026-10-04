package com.andres.walksecurity

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch

class WalkSecurityApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Deja activas las geocercas de las zonas de riesgo (no hace nada si falta el permiso)
        container.appScope.launch { container.zoneGeofencing.register() }
    }
}

/** Acceso al contenedor desde las fábricas de ViewModel. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as WalkSecurityApp).container
