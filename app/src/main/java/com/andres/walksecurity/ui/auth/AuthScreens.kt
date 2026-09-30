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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * @param allowLocalMode muestra "Usar sin cuenta". Se oculta cuando ya se está en modo local.
 * @param onSuccess se llama al iniciar sesión (cuando se entra desde el modo local hay que volver atrás).
 */
@Composable
fun LoginScreen(
    onGoToRegister: () -> Unit,
    onSuccess: () -> Unit = {},
    allowLocalMode: Boolean = true,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showLocalDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onSuccess() }

    AuthScaffold(title = "Iniciar sesión", subtitle = "Tu seguridad al caminar, en tu muñeca.") {
        FormTextField(email, { email = it }, "Correo", keyboardType = KeyboardType.Email, enabled = !state.loading)
        PasswordField(password, { password = it }, "Contraseña", enabled = !state.loading)
        ErrorText(state.error, offline = state.offline && allowLocalMode)
        SubmitButton("Entrar", state.loading) { viewModel.login(email, password) }
        TextButton(onClick = { viewModel.clearError(); onGoToRegister() }) {
            Text("¿No tienes cuenta? Regístrate")
        }
        if (allowLocalMode) {
            LocalModeSection(onClick = { showLocalDialog = true })
        }
    }

    if (showLocalDialog) {
        LocalModeDialog(
            onStart = { name, phone -> viewModel.startLocal(name, phone) },
            onDismiss = { showLocalDialog = false },
        )
    }
}

@Composable
fun RegisterScreen(
    onGoToLogin: () -> Unit,
    onSuccess: () -> Unit = {},
    initialName: String = "",
    initialPhone: String = "",
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val enabled = !state.loading

    LaunchedEffect(state.success) { if (state.success) onSuccess() }

    AuthScaffold(title = "Crear cuenta", subtitle = "Tus contactos quedarán respaldados en el servidor.") {
        FormTextField(name, { name = it }, "Nombre completo", enabled = enabled)
        FormTextField(email, { email = it }, "Correo", keyboardType = KeyboardType.Email, enabled = enabled)
        FormTextField(phone, { phone = it }, "Teléfono (ej. +573001234567)", keyboardType = KeyboardType.Phone, enabled = enabled)
        PasswordField(password, { password = it }, "Contraseña (mín. 8 caracteres)", imeAction = ImeAction.Next, enabled = enabled)
        PasswordField(confirmation, { confirmation = it }, "Confirmar contraseña", enabled = enabled)
        ErrorText(state.error, offline = false)
        SubmitButton("Registrarme", state.loading) {
            viewModel.register(name, email, phone, password, confirmation)
        }
        TextButton(onClick = { viewModel.clearError(); onGoToLogin() }) {
            Text("Ya tengo cuenta")
        }
    }
}

@Composable
private fun LocalModeSection(onClick: () -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(
        "¿Sin conexión con el servidor? Puedes usar la app igual: contactos, SOS por SMS y reloj " +
            "funcionan sin cuenta. Luego podrás crear la cuenta y se subirán tus contactos.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("Usar sin cuenta (modo local)")
    }
}

@Composable
private fun LocalModeDialog(onStart: (name: String, phone: String) -> String?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modo local") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tu nombre aparecerá en los SMS de emergencia. Los datos se guardan solo en este teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FormTextField(name, { name = it }, "Tu nombre")
                FormTextField(phone, { phone = it }, "Tu teléfono", keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { error = onStart(name, phone) }) { Text("Entrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
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
private fun ErrorText(error: String?, offline: Boolean) {
    if (error != null) {
        Text(
            if (offline) "$error\nSi el servidor no está disponible, usa el modo local (abajo)." else error,
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
