package com.andres.walksecurity.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andres.walksecurity.appContainer
import com.andres.walksecurity.core.model.Validators
import com.andres.walksecurity.data.remote.ApiException
import com.andres.walksecurity.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** El error fue de conexión: se sugiere el modo local. */
    val offline: Boolean = false,
    /** Inicio de sesión / registro completado (útil cuando se entra desde el modo local). */
    val success: Boolean = false,
)

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

    /** @return mensaje de error de validación, o null si se inició el modo local. */
    fun startLocal(name: String, phone: String): String? {
        val error = when {
            name.isBlank() -> "Ingresa tu nombre: aparecerá en los SMS de emergencia."
            !Validators.isValidPhone(phone) -> "Ingresa un teléfono válido (7 a 15 dígitos, puede iniciar con +)."
            else -> null
        }
        if (error == null) viewModelScope.launch { repository.startLocalSession(name, phone) }
        return error
    }

    fun clearError() = _uiState.update { it.copy(error = null, offline = false) }

    private fun submit(validationError: String?, action: suspend () -> Result<Unit>) {
        if (_uiState.value.loading) return
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError, offline = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { AuthUiState(loading = true) }
            val failure = action().exceptionOrNull()
            _uiState.value = if (failure == null) {
                AuthUiState(success = true)
            } else {
                // ApiException sin código HTTP = no hubo respuesta del servidor
                AuthUiState(error = failure.message, offline = (failure as? ApiException)?.code == null)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AuthViewModel(appContainer.authRepository) }
        }
    }
}
