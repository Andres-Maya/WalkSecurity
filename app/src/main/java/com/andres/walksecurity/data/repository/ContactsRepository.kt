package com.andres.walksecurity.data.repository

import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.local.SessionStore
import com.andres.walksecurity.data.remote.ApiService
import com.andres.walksecurity.data.remote.ContactRequest
import com.andres.walksecurity.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/** El servidor es la fuente de verdad; la copia local se usa para mostrar y para el SOS sin conexión. */
class ContactsRepository(
    private val api: ApiService,
    private val sessionStore: SessionStore,
) {
    val contacts: Flow<List<TrustedContact>> = sessionStore.contacts

    suspend fun refresh(): Result<Unit> =
        apiCall { api.contacts() }.map { sessionStore.saveContacts(it) }

    suspend fun add(name: String, phone: String, relationship: String?): Result<TrustedContact> =
        apiCall {
            api.addContact(
                ContactRequest(
                    name = name.trim(),
                    phone = Validators.normalizePhone(phone),
                    relationship = relationship?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
        }.onSuccess { created ->
            sessionStore.saveContacts(contacts.first().filterNot { it.id == created.id } + created)
        }

    suspend fun delete(id: Long): Result<Unit> =
        apiCall {
            val response = api.deleteContact(id)
            if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
        }.onSuccess {
            sessionStore.saveContacts(contacts.first().filterNot { it.id == id })
        }
}
