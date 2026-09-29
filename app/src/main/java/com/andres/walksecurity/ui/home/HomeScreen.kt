package com.andres.walksecurity.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
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
import com.andres.walksecurity.ui.theme.SosRed
import com.andres.walksecurity.ui.theme.SosRedDark
import java.util.Locale

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.SEND_SMS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenContacts: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
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
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SafetyMap(
                location = location,
                myLocationEnabled = state.permissions.location,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PermissionWarnings(
                    permissions = state.permissions,
                    onRequest = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                        )
                    },
                )
                StatusCard(
                    userName = state.user?.name,
                    location = location,
                    contactsCount = state.contactsCount,
                    onOpenContacts = onOpenContacts,
                )
                SosButton(onClick = viewModel::onSosPressed)
            }
        }
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
    onOpenSettings: () -> Unit,
) {
    if (permissions.location && permissions.sms) return
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
private fun StatusCard(
    userName: String?,
    location: GeoPoint?,
    contactsCount: Int,
    onOpenContacts: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Hola${userName?.let { ", ${it.substringBefore(' ')}" }.orEmpty()}",
                style = MaterialTheme.typography.titleMedium,
            )
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
