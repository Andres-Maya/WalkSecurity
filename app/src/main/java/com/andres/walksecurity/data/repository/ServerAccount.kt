package com.andres.walksecurity.data.repository

import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.remote.ApiService
import com.andres.walksecurity.data.remote.DeviceRequest
import com.andres.walksecurity.data.remote.apiCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * La app NO tiene inicio de sesión. Cuando el servidor está disponible, el teléfono se identifica
 * solo (con un deviceId aleatorio) para respaldar contactos y alertas. Si el servidor no responde,
 * no pasa nada: la app funciona igual y se reintenta en la próxima sincronización.
 */
class ServerAccount(
    private val api: ApiService,
    private val sessionStore: SessionStore,
) {
    private val mutex = Mutex()

    /** @return true si hay token para hablar con el servidor (lo obtiene si hace falta). */
    suspend fun ensureToken(): Boolean = mutex.withLock {
        if (sessionStore.token() != null) return@withLock true
        val profile = sessionStore.currentProfile()
        val phone = Validators.normalizePhone(profile.phone).takeIf { Validators.isValidPhone(it) }
        apiCall {
            api.registerDevice(DeviceRequest(sessionStore.deviceId(), profile.name.trim(), phone))
        }.onSuccess { sessionStore.saveToken(it.token) }
            .isSuccess
    }
}
