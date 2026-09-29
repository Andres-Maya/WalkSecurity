package com.andres.walksecurity.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
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

data class ContactsUiState(
    val contacts: List<TrustedContact> = emptyList(),
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val showAddDialog: Boolean = false,
    val formError: String? = null,
    val message: String? = null,
)

private data class LocalState(
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val showAddDialog: Boolean = false,
    val formError: String? = null,
    val message: String? = null,
)

class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<ContactsUiState> = combine(repository.contacts, local) { contacts, l ->
        ContactsUiState(contacts, l.refreshing, l.saving, l.showAddDialog, l.formError, l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            val error = repository.refresh().exceptionOrNull()?.message
            local.update { it.copy(refreshing = false, message = error) }
        }
    }

    fun openAddDialog() = local.update { it.copy(showAddDialog = true, formError = null) }

    fun closeAddDialog() = local.update { it.copy(showAddDialog = false, formError = null) }

    fun add(name: String, phone: String, relationship: String) {
        if (local.value.saving) return
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
            local.update { it.copy(saving = true, formError = null) }
            repository.add(name, phone, relationship)
                .onSuccess { local.update { it.copy(saving = false, showAddDialog = false) } }
                .onFailure { e -> local.update { it.copy(saving = false, formError = e.message) } }
        }
    }

    fun delete(contact: TrustedContact) {
        viewModelScope.launch {
            repository.delete(contact.id).onFailure { e -> local.update { it.copy(message = e.message) } }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { ContactsViewModel(appContainer.contactsRepository) }
        }
    }
}
