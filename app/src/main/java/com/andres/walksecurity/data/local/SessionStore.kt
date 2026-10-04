package com.andres.walksecurity.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andres.walksecurity.core.model.Profile
import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.core.model.User
import com.andres.walksecurity.data.remote.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Datos guardados en el teléfono: perfil, contactos de confianza e identificación ante el servidor.
 * No hay inicio de sesión: los contactos viven aquí y el SOS funciona sin servidor.
 *
 * TODO(seguridad): cifrar el token y el deviceId (Tink / Android Keystore) antes de producción.
 */
class SessionStore(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore

    val profile: Flow<Profile> = dataStore.data.map { it.toProfile() }

    /** Todos los contactos guardados, incluidos los borrados pendientes de sincronizar. */
    val contacts: Flow<List<TrustedContact>> = dataStore.data.map { decodeContacts(it[CONTACTS]) }

    suspend fun currentProfile(): Profile = profile.first()

    suspend fun saveProfile(profile: Profile) {
        dataStore.edit {
            it[PROFILE] = AppJson.encodeToString(profile)
            // El servidor debe conocer el nuevo nombre: se vuelve a identificar el dispositivo
            it.remove(TOKEN)
        }
    }

    suspend fun contactsSnapshot(): List<TrustedContact> = contacts.first()

    /** Modifica la lista de contactos de forma atómica. */
    suspend fun updateContacts(transform: (List<TrustedContact>) -> List<TrustedContact>) {
        dataStore.edit { prefs ->
            prefs[CONTACTS] = AppJson.encodeToString(transform(decodeContacts(prefs[CONTACTS])))
        }
    }

    // --- Identificación automática ante el servidor (sin inicio de sesión) ---

    suspend fun token(): String? = dataStore.data.first()[TOKEN]

    suspend fun saveToken(token: String) {
        dataStore.edit { it[TOKEN] = token }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(TOKEN) }
    }

    /** Identificador aleatorio de este teléfono, generado una sola vez. */
    suspend fun deviceId(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return id
    }

    private fun decodeContacts(json: String?): List<TrustedContact> =
        json?.let { runCatching { AppJson.decodeFromString<List<TrustedContact>>(it) }.getOrNull() } ?: emptyList()

    private fun Preferences.toProfile(): Profile {
        this[PROFILE]?.let { json -> runCatching { AppJson.decodeFromString<Profile>(json) }.getOrNull()?.let { return it } }
        // Versiones anteriores guardaban el usuario del inicio de sesión: se reutiliza su nombre
        val legacy = this[LEGACY_USER]?.let { runCatching { AppJson.decodeFromString<User>(it) }.getOrNull() }
        return Profile(name = legacy?.name.orEmpty(), phone = legacy?.phone.orEmpty())
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val PROFILE = stringPreferencesKey("profile")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CONTACTS = stringPreferencesKey("contacts_v2")
        val LEGACY_USER = stringPreferencesKey("user")
    }
}
