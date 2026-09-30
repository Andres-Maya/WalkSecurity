package com.andres.walksecurity.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andres.walksecurity.core.model.Session
import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.core.model.User
import com.andres.walksecurity.data.remote.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Persistencia local: sesión, perfil y contactos de confianza.
 * Los contactos viven aquí primero para que el SOS funcione sin servidor.
 *
 * TODO(seguridad): cifrar el token (Tink / Android Keystore) antes de producción.
 */
class SessionStore(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore

    val session: Flow<Session?> = dataStore.data.map { it.toSession() }

    /** Todos los contactos guardados, incluidos los borrados pendientes de sincronizar. */
    val contacts: Flow<List<TrustedContact>> = dataStore.data.map { decodeContacts(it[CONTACTS]) }

    suspend fun token(): String? = dataStore.data.first()[TOKEN]

    suspend fun currentUser(): User? = dataStore.data.first().toSession()?.user

    suspend fun currentSession(): Session? = dataStore.data.first().toSession()

    suspend fun contactsSnapshot(): List<TrustedContact> = contacts.first()

    suspend fun saveSession(token: String, user: User) {
        dataStore.edit {
            it[TOKEN] = token
            it[USER] = AppJson.encodeToString(user)
        }
    }

    /** Perfil sin cuenta en el servidor (modo local). Conserva los contactos existentes. */
    suspend fun saveLocalProfile(user: User) {
        dataStore.edit {
            it.remove(TOKEN)
            it[USER] = AppJson.encodeToString(user)
        }
    }

    /** Sesión expirada o rechazada: se pasa a modo local SIN borrar los contactos de emergencia. */
    suspend fun clearToken() {
        dataStore.edit { it.remove(TOKEN) }
    }

    /** Modifica la lista de contactos de forma atómica. */
    suspend fun updateContacts(transform: (List<TrustedContact>) -> List<TrustedContact>) {
        dataStore.edit { prefs ->
            prefs[CONTACTS] = AppJson.encodeToString(transform(decodeContacts(prefs[CONTACTS])))
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun decodeContacts(json: String?): List<TrustedContact> =
        json?.let { runCatching { AppJson.decodeFromString<List<TrustedContact>>(it) }.getOrNull() } ?: emptyList()

    private fun Preferences.toSession(): Session? {
        val user = this[USER]?.let { runCatching { AppJson.decodeFromString<User>(it) }.getOrNull() } ?: return null
        return Session(token = this[TOKEN], user = user)
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER = stringPreferencesKey("user")
        val CONTACTS = stringPreferencesKey("contacts_v2")
    }
}
