package com.andres.walksecurity.ui.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
import com.andres.walksecurity.core.contacts.PhoneContactsReader
import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.repository.ContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Datos del contacto antes de guardarlo (vacío si se escribe a mano, prellenado desde la agenda). */
data class ContactDraft(val name: String = "", val phone: String = "", val fromPhonebook: Boolean = false)

data class ContactsUiState(
    val contacts: List<TrustedContact> = emptyList(),
    /** Diálogo de nuevo contacto abierto con estos datos. */
    val draft: ContactDraft? = null,
    val formError: String? = null,
    val message: String? = null,
)

private data class LocalState(
    val draft: ContactDraft? = null,
    val formError: String? = null,
    val message: String? = null,
)

class ContactsViewModel(
    private val repository: ContactsRepository,
    private val phoneContacts: PhoneContactsReader,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<ContactsUiState> = combine(repository.contacts, local) { contacts, l ->
        ContactsUiState(
            contacts = contacts,
            draft = l.draft,
            formError = l.formError,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    init {
        // Respaldo automático en el servidor si está disponible (silencioso)
        repository.requestSync()
    }

    /** @return false (y avisa) si ya se alcanzó el máximo de contactos. */
    fun canAdd(): Boolean {
        if (uiState.value.contacts.size < ContactsRepository.MAX_CONTACTS) return true
        local.update { it.copy(message = "Puedes tener máximo ${ContactsRepository.MAX_CONTACTS} contactos de confianza.") }
        return false
    }

    /** Escribir el contacto a mano (o si el teléfono no tiene app de contactos). */
    fun startManual() = local.update { it.copy(draft = ContactDraft(), formError = null) }

    /** El usuario eligió un número en la agenda: se abre el diálogo prellenado para confirmar. */
    fun onContactPicked(uri: Uri) {
        viewModelScope.launch {
            val picked = phoneContacts.read(uri)
            local.update {
                if (picked == null) {
                    it.copy(message = "No se pudo leer ese contacto. Escríbelo a mano.", draft = ContactDraft())
                } else {
                    it.copy(draft = ContactDraft(picked.name, picked.phone, fromPhonebook = true), formError = null)
                }
            }
        }
    }

    fun closeAddDialog() = local.update { it.copy(draft = null, formError = null) }

    /** Se guarda al instante en el teléfono; la subida al servidor ocurre en segundo plano. */
    fun add(name: String, phone: String, relationship: String) {
        val error = when {
            name.isBlank() -> "Ingresa el nombre del contacto."
            !Validators.isValidPhone(phone) -> "Teléfono inválido. Usa 7 a 15 dígitos, con indicativo si es posible (+57…)."
            uiState.value.contacts.any { Validators.normalizePhone(it.phone) == Validators.normalizePhone(phone) } ->
                "Ese número ya está registrado."
            else -> null
        }
        if (error != null) {
            local.update { it.copy(formError = error) }
            return
        }
        viewModelScope.launch {
            repository.add(name, phone, relationship)
            local.update { it.copy(draft = null, formError = null, message = "${name.trim()} recibirá tus alertas.") }
        }
    }

    fun delete(contact: TrustedContact) {
        viewModelScope.launch { repository.delete(contact.localId) }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { with(appContainer) { ContactsViewModel(contactsRepository, phoneContactsReader) } }
        }
    }
}
