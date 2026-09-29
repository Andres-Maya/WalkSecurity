package com.andres.walksecurity.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

/** Al guardar la sesión, la raíz de la app detecta el cambio y muestra la pantalla principal. */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        val error = when {
            !Validators.isValidEmail(email) -> "Ingresa un correo válido."
            password.isEmpty() -> "Ingresa tu contraseña."
            else -> null
        }
        submit(error) { repository.login(email, password) }
    }

    fun register(name: String, email: String, phone: String, password: String, confirmation: String) {
        val error = when {
            name.isBlank() -> "Ingresa tu nombre."
            !Validators.isValidEmail(email) -> "Ingresa un correo válido."
            !Validators.isValidPhone(phone) -> "Ingresa un teléfono válido (7 a 15 dígitos, puede iniciar con +)."
            !Validators.isValidPassword(password) ->
                "La contraseña debe tener al menos ${Validators.MIN_PASSWORD_LENGTH} caracteres."
            password != confirmation -> "Las contraseñas no coinciden."
            else -> null
        }
        submit(error) { repository.register(name, email, phone, password) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun submit(validationError: String?, action: suspend () -> Result<Unit>) {
        if (_uiState.value.loading) return
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { AuthUiState(loading = true) }
            val result = action()
            _uiState.update { AuthUiState(error = result.exceptionOrNull()?.message) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AuthViewModel(appContainer.authRepository) }
        }
    }
}
