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
 * Persistencia local de la sesión y de una copia de los contactos de confianza.
 * La copia local permite enviar el SOS por SMS aunque no haya conexión con el servidor.
 *
 * TODO(seguridad): cifrar el token (Tink / Android Keystore) antes de producción.
 */
class SessionStore(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore

    val session: Flow<Session?> = dataStore.data.map { it.toSession() }

    val contacts: Flow<List<TrustedContact>> = dataStore.data.map { prefs ->
        prefs[CONTACTS]?.let { runCatching { AppJson.decodeFromString<List<TrustedContact>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun token(): String? = dataStore.data.first()[TOKEN]

    suspend fun currentUser(): User? = dataStore.data.first().toSession()?.user

    suspend fun saveSession(token: String, user: User) {
        dataStore.edit {
            it[TOKEN] = token
            it[USER] = AppJson.encodeToString(user)
        }
    }

    suspend fun saveContacts(contacts: List<TrustedContact>) {
        dataStore.edit { it[CONTACTS] = AppJson.encodeToString(contacts) }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSession(): Session? {
        val token = this[TOKEN] ?: return null
        val user = this[USER]?.let { runCatching { AppJson.decodeFromString<User>(it) }.getOrNull() } ?: return null
        return Session(token, user)
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER = stringPreferencesKey("user")
        val CONTACTS = stringPreferencesKey("contacts")
    }
}
