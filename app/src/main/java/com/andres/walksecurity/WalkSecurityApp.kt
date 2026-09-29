package com.andres.walksecurity

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class WalkSecurityApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Acceso al contenedor desde las fábricas de ViewModel. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as WalkSecurityApp).container
