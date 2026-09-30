package com.andres.walksecurity.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.walksecurity.core.model.SosMessageBuilder
import com.andres.walksecurity.core.model.SosResult
import com.andres.walksecurity.ui.theme.SosRed

/** Línea única de emergencias en Colombia. */
private const val EMERGENCY_NUMBER = "123"

@Composable
fun SosDialogs(
    sos: SosState,
    onSendNow: () -> Unit,
    onDismiss: () -> Unit,
    onOpenContacts: () -> Unit,
) {
    when (sos) {
        SosState.Idle -> Unit

        SosState.NoContacts -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sin contactos de confianza") },
            text = { Text("Agrega al menos un contacto para que el SOS pueda avisarle con tu ubicación.") },
            confirmButton = { Button(onClick = onOpenContacts) { Text("Agregar contacto") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        )

        is SosState.Countdown -> AlertDialog(
            // Tocar fuera no cancela: la cancelación debe ser explícita
            onDismissRequest = {},
            title = { Text("Enviando alerta SOS") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${sos.secondsLeft}",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = SosRed,
                    )
                    Text(
                        "Se enviará tu ubicación a tus contactos de confianza.",
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onSendNow,
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                ) { Text("Enviar ahora") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )

        SosState.Sending -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Enviando alerta…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("Obteniendo ubicación y avisando a tus contactos.")
                }
            },
            confirmButton = {},
        )

        is SosState.Sent -> SosResultDialog(sos.result, onDismiss)
    }
}

@Composable
private fun SosResultDialog(result: SosResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Solo el SMS llega hoy a los contactos; el registro en el servidor por sí solo no los avisa
    val title = when {
        result.smsSent > 0 -> "Alerta enviada"
        result.serverRegistered -> "Alerta registrada, pero sin SMS"
        else -> "No se pudo enviar la alerta"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        !result.smsAvailable -> "SMS no disponible: revisa el permiso de SMS o la SIM."
                        else -> "SMS enviados: ${result.smsSent} de ${result.contactsTotal} contactos."
                    }
                )
                Text(
                    when {
                        result.localMode -> "Modo local: la alerta no se registra en el servidor."
                        result.serverRegistered -> "Alerta registrada en el servidor."
                        else -> "El servidor no respondió; la alerta no quedó registrada."
                    }
                )
                Text(
                    result.location?.let { "Ubicación: ${SosMessageBuilder.mapsUrl(it)}" }
                        ?: "No se pudo obtener tu ubicación.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Si estás en peligro inmediato, llama a la línea de emergencias.",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EMERGENCY_NUMBER")))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SosRed),
            ) { Text("Llamar al $EMERGENCY_NUMBER") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
