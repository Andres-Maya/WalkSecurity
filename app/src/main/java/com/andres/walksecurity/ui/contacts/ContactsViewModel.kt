package com.andres.walksecurity.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.repository.AuthRepository
import com.andres.walksecurity.data.repository.ContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<TrustedContact> = emptyList(),
    /** Modo local: no hay servidor con el que sincronizar. */
    val isLocal: Boolean = false,
    val refreshing: Boolean = false,
    val showAddDialog: Boolean = false,
    val formError: String? = null,
    val message: String? = null,
)

private data class LocalState(
    val refreshing: Boolean = false,
    val showAddDialog: Boolean = false,
    val formError: String? = null,
    val message: String? = null,
)

class ContactsViewModel(
    private val repository: ContactsRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<ContactsUiState> = combine(
        repository.contacts,
        authRepository.session,
        local,
    ) { contacts, session, l ->
        ContactsUiState(
            contacts = contacts,
            isLocal = session?.isLocal ?: true,
            refreshing = l.refreshing,
            showAddDialog = l.showAddDialog,
            formError = l.formError,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    init {
        repository.requestSync()
    }

    /** "Deslizar para actualizar": sincroniza con el servidor si hay cuenta. */
    fun refresh() {
        if (uiState.value.isLocal) {
            local.update { it.copy(message = "Modo local: tus contactos están guardados en este teléfono.") }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val failed = repository.sync().isFailure
            local.update {
                it.copy(
                    refreshing = false,
                    message = if (failed) {
                        "Sin conexión con el servidor. Tus contactos están guardados en el teléfono y se sincronizarán después."
                    } else {
                        "Contactos sincronizados."
                    },
                )
            }
        }
    }

    fun openAddDialog() {
        if (uiState.value.contacts.size >= ContactsRepository.MAX_CONTACTS) {
            local.update { it.copy(message = "Puedes tener máximo ${ContactsRepository.MAX_CONTACTS} contactos de confianza.") }
            return
        }
        local.update { it.copy(showAddDialog = true, formError = null) }
    }

    fun closeAddDialog() = local.update { it.copy(showAddDialog = false, formError = null) }

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
            local.update { it.copy(showAddDialog = false, formError = null) }
        }
    }

    fun delete(contact: TrustedContact) {
        viewModelScope.launch { repository.delete(contact.localId) }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { with(appContainer) { ContactsViewModel(contactsRepository, authRepository) } }
        }
    }
}
