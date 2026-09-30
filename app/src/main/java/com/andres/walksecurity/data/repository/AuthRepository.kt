package com.andres.walksecurity.data.repository

import com.andres.walksecurity.core.model.Session
import com.andres.walksecurity.core.model.User
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.remote.ApiService
import com.andres.walksecurity.data.remote.AuthResponse
import com.andres.walksecurity.data.remote.LoginRequest
import com.andres.walksecurity.data.remote.RegisterRequest
import com.andres.walksecurity.data.remote.apiCall
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val api: ApiService,
    private val sessionStore: SessionStore,
    private val contactsRepository: ContactsRepository,
) {
    val session: Flow<Session?> = sessionStore.session

    suspend fun login(email: String, password: String): Result<Unit> =
        apiCall { api.login(LoginRequest(email.trim().lowercase(), password)) }
            .map { saveSession(it) }

    suspend fun register(name: String, email: String, phone: String, password: String): Result<Unit> =
        apiCall {
            api.register(
                RegisterRequest(
                    name = name.trim(),
                    email = email.trim().lowercase(),
                    phone = Validators.normalizePhone(phone),
                    password = password,
                )
            )
        }.map { saveSession(it) }

    /**
     * Modo local: usar la app sin cuenta (p. ej. sin servidor disponible). Todo funciona —contactos,
     * SOS por SMS, reloj— y los datos se suben al servidor si después se crea la cuenta.
     */
    suspend fun startLocalSession(name: String, phone: String) {
        sessionStore.saveLocalProfile(
            User(id = 0, name = name.trim(), email = "", phone = Validators.normalizePhone(phone))
        )
    }

    suspend fun logout() = sessionStore.clear()

    private suspend fun saveSession(response: AuthResponse) {
        sessionStore.saveSession(response.token, response.user)
        // Sube los contactos creados en modo local y baja los que ya estaban en la cuenta
        contactsRepository.requestSync()
    }
}
