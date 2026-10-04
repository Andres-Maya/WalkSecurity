package com.andres.walksecurity.ui.contacts

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andres.walksecurity.core.contacts.PickPhoneNumber
import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.ui.components.FormTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    viewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<TrustedContact?>(null) }

    // "Agregar" abre la agenda del teléfono; el número elegido llega prellenado al diálogo
    val pickFromPhonebook = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        uri?.let(viewModel::onContactPicked)
    }
    val openPhonebook: () -> Unit = {
        if (viewModel.canAdd()) {
            try {
                pickFromPhonebook.launch(Unit)
            } catch (_: ActivityNotFoundException) {
                viewModel.startManual() // sin app de contactos: se escribe a mano
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contactos de confianza") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { if (viewModel.canAdd()) viewModel.startManual() }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Escribir un número a mano")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = openPhonebook,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Agregar desde contactos") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.contacts.isEmpty()) {
                EmptyContacts()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            "Recibirán un SMS con tu ubicación cuando actives el SOS.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(state.contacts, key = { it.localId }) { contact ->
                        ContactRow(contact, onDelete = { pendingDelete = contact })
                    }
                }
            }
        }
    }

    state.draft?.let { draft ->
        AddContactDialog(
            draft = draft,
            error = state.formError,
            onSave = viewModel::add,
            onDismiss = viewModel::closeAddDialog,
        )
    }

    pendingDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar contacto") },
            text = { Text("${contact.name} dejará de recibir tus alertas.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(contact)
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EmptyContacts() {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aún no tienes contactos de confianza.\nToca Agregar desde contactos y elige a quienes deben enterarse si activas el SOS.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun ContactRow(contact: TrustedContact, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(contact.phone, contact.relationship).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${contact.name}")
            }
        }
    }
}

@Composable
private fun AddContactDialog(
    draft: ContactDraft,
    error: String?,
    onSave: (name: String, phone: String, relationship: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Se reinicia con cada contacto elegido en la agenda
    var name by rememberSaveable(draft) { mutableStateOf(draft.name) }
    var phone by rememberSaveable(draft) { mutableStateOf(draft.phone) }
    var relationship by rememberSaveable(draft) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.fromPhonebook) "Confirmar contacto" else "Nuevo contacto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.fromPhonebook) {
                    Text(
                        "Revisa el número (idealmente con indicativo, p. ej. +57) y agrega el parentesco.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FormTextField(name, { name = it }, "Nombre")
                FormTextField(phone, { phone = it }, "Teléfono (+57…)", keyboardType = KeyboardType.Phone)
                FormTextField(
                    relationship, { relationship = it }, "Parentesco (opcional)",
                    imeAction = ImeAction.Done,
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, phone, relationship) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
