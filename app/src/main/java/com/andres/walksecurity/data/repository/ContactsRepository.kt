package com.andres.walksecurity.data.repository

import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.remote.ApiException
import com.andres.walksecurity.data.remote.ApiService
import com.andres.walksecurity.data.remote.ContactRequest
import com.andres.walksecurity.data.remote.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.UUID

/**
 * Contactos "offline-first": agregar y eliminar funciona siempre (se guarda en el teléfono al instante)
 * y la sincronización con el servidor ocurre en segundo plano cuando hay cuenta y conexión.
 */
class ContactsRepository(
    private val api: ApiService,
    private val sessionStore: SessionStore,
    private val serverAccount: ServerAccount,
    private val appScope: CoroutineScope,
) {
    private val syncMutex = Mutex()

    /** Contactos vigentes (sin los eliminados pendientes de sincronizar). Son los que recibe el SOS. */
    val contacts: Flow<List<TrustedContact>> = sessionStore.contacts.map { list -> list.filterNot { it.pendingDelete } }

    suspend fun add(name: String, phone: String, relationship: String?): TrustedContact {
        val contact = TrustedContact(
            localId = UUID.randomUUID().toString(),
            name = name.trim(),
            phone = Validators.normalizePhone(phone),
            relationship = relationship?.trim()?.takeIf { it.isNotEmpty() },
        )
        sessionStore.updateContacts { it + contact }
        requestSync()
        return contact
    }

    suspend fun delete(localId: String) {
        sessionStore.updateContacts { list ->
            list.mapNotNull { contact ->
                when {
                    contact.localId != localId -> contact
                    contact.serverId == null -> null // nunca llegó al servidor: se borra ya
                    else -> contact.copy(pendingDelete = true)
                }
            }
        }
        requestSync()
    }

    /** Sincroniza en segundo plano; si falla, los cambios quedan pendientes para la próxima vez. */
    fun requestSync() {
        appScope.launch { sync() }
    }

    /**
     * Respaldo en el servidor: 1) elimina allí lo borrado en el teléfono, 2) fusiona con su lista,
     * 3) sube los contactos nuevos. Si el servidor no está disponible, no hace nada.
     */
    suspend fun sync(): Result<Unit> = syncMutex.withLock {
        if (!serverAccount.ensureToken()) return@withLock Result.failure(IllegalStateException("Servidor no disponible"))

        for (contact in sessionStore.contactsSnapshot().filter { it.pendingDelete }) {
            val serverId = contact.serverId ?: continue
            val deleted = apiCall {
                val response = api.deleteContact(serverId)
                if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
            }
            if (deleted.isFailure) return@withLock deleted
            sessionStore.updateContacts { list -> list.filterNot { it.localId == contact.localId } }
        }

        val remote = apiCall { api.contacts() }.getOrElse { return@withLock Result.failure(it) }
        sessionStore.updateContacts { local -> ContactsMerger.merge(local, remote) }

        for (contact in sessionStore.contactsSnapshot().filter { it.serverId == null && !it.pendingDelete }) {
            val created = apiCall { api.addContact(ContactRequest(contact.name, contact.phone, contact.relationship)) }
            val error = created.exceptionOrNull()
            if (error != null) {
                // 409 = el número ya existe en el servidor: se enlazará en la próxima fusión
                if ((error as? ApiException)?.code == 409) continue
                return@withLock Result.failure(error)
            }
            val dto = created.getOrThrow()
            sessionStore.updateContacts { list ->
                list.map { if (it.localId == contact.localId) it.copy(serverId = dto.id) else it }
            }
        }
        Result.success(Unit)
    }

    companion object {
        /** Mismo límite que el servidor. */
        const val MAX_CONTACTS = 10
    }
}
