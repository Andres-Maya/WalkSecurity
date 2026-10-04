package com.andres.walksecurity.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.core.model.Profile
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.ui.theme.AlertRed
import com.andres.walksecurity.ui.theme.CautionAmber
import com.andres.walksecurity.ui.theme.SafeGreen
import com.andres.walksecurity.ui.components.FormTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.andres.walksecurity.ui.theme.SosRed
import com.andres.walksecurity.ui.theme.SosRedDark
import java.util.Locale

private val REQUIRED_PERMISSIONS = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.SEND_SMS)
    // Android 13+: aviso al entrar en una zona de riesgo con la app cerrada
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenContacts: () -> Unit,
    onOpenWatch: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val context = LocalContext.current
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val risk by viewModel.risk.collectAsStateWithLifecycle()
    // Detección automática: cada posición del GPS se compara con las zonas de riesgo
    LaunchedEffect(location) { location?.let(viewModel::onLocation) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }
    // Android 11+ abre directamente Ajustes para elegir "Permitir todo el tiempo"
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    // Pedir permisos solo una vez automáticamente; después el usuario usa el botón del aviso
    var askedPermissions by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!askedPermissions) {
            askedPermissions = true
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }
    // Refresca al volver de Ajustes por si el usuario concedió permisos allí
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WalkSecurity") },
                actions = {
                    IconButton(onClick = onOpenContacts) {
                        Icon(Icons.Filled.Person, contentDescription = "Contactos de confianza")
                    }
                },
            )
        },
        // El SOS va fijo abajo: siempre visible, aunque haya avisos y haya que desplazar el resto
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                SosButton(onClick = viewModel::onSosPressed)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SafetyMap(
                location = location,
                zones = viewModel.zones,
                myLocationEnabled = state.permissions.location,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!state.profile.hasName) ProfilePrompt(onClick = { editingProfile = true })
                PermissionWarnings(
                    permissions = state.permissions,
                    onRequest = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
                    onRequestBackground = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                        )
                    },
                )
                StatusCard(
                    risk = risk,
                    userName = state.profile.name.ifBlank { null },
                    onEditProfile = { editingProfile = true },
                    location = location,
                    contactsCount = state.contactsCount,
                    onOpenContacts = onOpenContacts,
                )
                WatchCard(watch = state.watch, onOpenWatch = onOpenWatch)
            }
        }
    }

    if (editingProfile) {
        ProfileDialog(
            initial = state.profile,
            onSave = { name, phone -> viewModel.saveProfile(name, phone).also { if (it == null) editingProfile = false } },
            onDismiss = { editingProfile = false },
        )
    }

    SosDialogs(
        sos = state.sos,
        onSendNow = viewModel::sendNow,
        onDismiss = viewModel::dismissSos,
        onOpenContacts = {
            viewModel.dismissSos()
            onOpenContacts()
        },
    )
}

@Composable
private fun PermissionWarnings(
    permissions: PermissionsState,
    onRequest: () -> Unit,
    onRequestBackground: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (permissions.location && permissions.sms) {
        if (!permissions.backgroundLocation) BackgroundLocationHint(onRequestBackground)
        return
    }
    val missing = buildList {
        if (!permissions.location) add("ubicación")
        if (!permissions.sms) add("SMS")
    }.joinToString(" y ")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Faltan permisos de $missing. Sin ellos el SOS no puede enviar tu posición a tus contactos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row {
                TextButton(onClick = onRequest) { Text("Conceder") }
                TextButton(onClick = onOpenSettings) { Text("Abrir ajustes") }
            }
        }
    }
}

@Composable
private fun BackgroundLocationHint(onRequest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Para avisarte de las zonas de riesgo con la app cerrada y que el SOS del reloj " +
                    "incluya tu ubicación, permite la ubicación \"Todo el tiempo\".",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequest) { Text("Permitir") }
        }
    }
}

/** Aparece mientras no haya nombre: sin él, el SMS diría "Un contacto necesita ayuda". */
@Composable
private fun ProfilePrompt(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Escribe tu nombre para que tus contactos sepan quién pide ayuda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text("Escribir") }
        }
    }
}

@Composable
private fun ProfileDialog(
    initial: Profile,
    onSave: (name: String, phone: String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tus datos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tu nombre aparece en los SMS de emergencia.", style = MaterialTheme.typography.bodySmall)
                FormTextField(name, { name = it }, "Tu nombre")
                FormTextField(phone, { phone = it }, "Tu teléfono (opcional)", keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { error = onSave(name, phone) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WatchCard(watch: WatchUi, onOpenWatch: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (watch.connected) {
                    true -> "Reloj Wear OS conectado"
                    false -> "Sin reloj: usa el reloj emulado"
                    null -> "Buscando reloj…"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenWatch) { Text(if (watch.connected == true) "Reloj" else "Reloj emulado") }
        }
    }
}

/** Nivel de riesgo estimado de la zona actual, con el mismo color que usa el reloj. */
@Composable
private fun RiskLine(risk: RiskStatus?) {
    val (label, color) = when (risk?.level) {
        RiskLevel.ALERT -> "Alerta" to AlertRed
        RiskLevel.CAUTION -> "Precaución" to CautionAmber
        RiskLevel.SAFE -> "Zona segura" to SafeGreen
        null -> "Zona sin evaluar" to Color.Gray
    }
    val detail = listOfNotNull(risk?.zoneName, "simulado".takeIf { risk?.simulated == true }).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(
            "  $label" + if (detail.isNotEmpty()) " · $detail" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (risk != null && risk.level != RiskLevel.SAFE) {
        Text(
            "Riesgo estimado a partir de noticias: no es una garantía.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun StatusCard(
    risk: RiskStatus?,
    userName: String?,
    onEditProfile: () -> Unit,
    location: GeoPoint?,
    contactsCount: Int,
    onOpenContacts: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hola${userName?.let { ", ${it.substringBefore(' ')}" }.orEmpty()}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (userName != null) TextButton(onClick = onEditProfile) { Text("Editar") }
            }
            RiskLine(risk)
            Text(
                text = location?.let {
                    val accuracy = it.accuracyMeters?.let { a -> " · ±${a.toInt()} m" }.orEmpty()
                    String.format(Locale.US, "Ubicación: %.5f, %.5f", it.latitude, it.longitude) + accuracy
                } ?: "Obteniendo ubicación…",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (contactsCount == 0) "Sin contactos de confianza" else "Contactos de confianza: $contactsCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (contactsCount == 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenContacts) { Text(if (contactsCount == 0) "Agregar" else "Gestionar") }
            }
        }
    }
}

@Composable
private fun SosButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(150.dp)
            .border(6.dp, SosRedDark.copy(alpha = 0.35f), CircleShape)
            .padding(10.dp)
            .background(SosRed, CircleShape),
    ) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
            modifier = Modifier.fillMaxSize(),
        ) {
            Text("SOS", fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}
