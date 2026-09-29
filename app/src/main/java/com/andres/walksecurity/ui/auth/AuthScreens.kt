package com.andres.walksecurity.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andres.walksecurity.ui.components.FormTextField
import com.andres.walksecurity.ui.components.PasswordField

@Composable
fun LoginScreen(
    onGoToRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(title = "Iniciar sesión", subtitle = "Tu seguridad al caminar, en tu muñeca.") {
        FormTextField(email, { email = it }, "Correo", keyboardType = KeyboardType.Email, enabled = !state.loading)
        PasswordField(password, { password = it }, "Contraseña", enabled = !state.loading)
        ErrorText(state.error)
        SubmitButton("Entrar", state.loading) { viewModel.login(email, password) }
        TextButton(onClick = { viewModel.clearError(); onGoToRegister() }) {
            Text("¿No tienes cuenta? Regístrate")
        }
    }
}

@Composable
fun RegisterScreen(
    onGoToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val enabled = !state.loading

    AuthScaffold(title = "Crear cuenta", subtitle = "Tus datos se usan para identificarte en las alertas.") {
        FormTextField(name, { name = it }, "Nombre completo", enabled = enabled)
        FormTextField(email, { email = it }, "Correo", keyboardType = KeyboardType.Email, enabled = enabled)
        FormTextField(phone, { phone = it }, "Teléfono (ej. +573001234567)", keyboardType = KeyboardType.Phone, enabled = enabled)
        PasswordField(password, { password = it }, "Contraseña (mín. 8 caracteres)", imeAction = ImeAction.Next, enabled = enabled)
        PasswordField(confirmation, { confirmation = it }, "Confirmar contraseña", enabled = enabled)
        ErrorText(state.error)
        SubmitButton("Registrarme", state.loading) {
            viewModel.register(name, email, phone, password, confirmation)
        }
        TextButton(onClick = { viewModel.clearError(); onGoToLogin() }) {
            Text("Ya tengo cuenta")
        }
    }
}

@Composable
private fun AuthScaffold(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "WalkSecurity",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        content()
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubmitButton(text: String, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}
