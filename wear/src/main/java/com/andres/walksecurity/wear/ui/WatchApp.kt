package com.andres.walksecurity.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.wear.SosUi
import com.andres.walksecurity.wear.WatchUiState

val SafeGreen = Color(0xFF2E7D32)
val CautionAmber = Color(0xFFF9A825)
val AlertRed = Color(0xFFC62828)
val SosRed = Color(0xFFD32F2F)
private val Neutral = Color(0xFF455A64)

data class WatchActions(
    val onSos: () -> Unit,
    val onSendNow: () -> Unit,
    val onCancel: () -> Unit,
    val onDismissResult: () -> Unit,
    val onImOk: () -> Unit,
    val onEmergency: () -> Unit,
)

@Composable
fun WatchApp(state: WatchUiState, actions: WatchActions) {
    MaterialTheme {
        AppScaffold(timeText = { TimeText() }) {
            // Mantener la pantalla encendida mientras hay un SOS o una alerta en curso
            KeepScreenOn(enabled = state.sos != SosUi.Idle || state.pendingAlert != null)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.sos != SosUi.Idle -> SosScreen(state.sos, actions)
                    state.pendingAlert != null -> RiskAlertScreen(state.pendingAlert, actions)
                    else -> HomeScreen(state, actions)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(state: WatchUiState, actions: WatchActions) {
    val risk = state.risk
    val (color, title) = when (risk?.level) {
        RiskLevel.SAFE -> SafeGreen to "Zona segura"
        RiskLevel.CAUTION -> CautionAmber to "Precaución"
        RiskLevel.ALERT -> AlertRed to "Alerta"
        null -> Neutral to "Sin datos"
    }
    val subtitle = when {
        state.phoneReachable == false -> "Teléfono desconectado"
        risk == null -> "Abre WalkSecurity en el teléfono"
        else -> listOfNotNull(risk.zoneName, "simulado".takeIf { risk.simulated }).joinToString(" · ")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.size(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color.readableOnBlack())
        }
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = Color.LightGray,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        RoundActionButton(
            text = "SOS",
            color = SosRed,
            size = 84.dp,
            fontSize = 26,
            description = "Enviar alerta SOS",
            onClick = actions.onSos,
        )
    }
}

@Composable
private fun RiskAlertScreen(alert: RiskStatus, actions: WatchActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlertRed.copy(alpha = 0.35f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("¿Estás bien?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Entraste en una zona con riesgo estimado alto${alert.zoneName?.let { ": $it" }.orEmpty()}.",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        PillButton("Emergencia", SosRed, actions.onEmergency)
        PillButton("Estoy bien", Neutral, actions.onImOk)
        Text(
            "Es una estimación, no una garantía.",
            fontSize = 9.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SosScreen(sos: SosUi, actions: WatchActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        when (sos) {
            is SosUi.Countdown -> {
                Text("Enviando SOS en", fontSize = 13.sp)
                Text("${sos.secondsLeft}", fontSize = 44.sp, fontWeight = FontWeight.Black, color = SosRed)
                PillButton("Enviar ahora", SosRed, actions.onSendNow)
                PillButton("Cancelar", Neutral, actions.onCancel)
            }
            SosUi.Sending -> StatusText("Enviando al teléfono…")
            SosUi.WaitingPhone -> StatusText("Avisando a tus contactos…")
            is SosUi.Done -> {
                val outcome = sos.outcome
                val (title, detail) = when {
                    outcome == null -> "SOS enviado" to "El teléfono lo recibió. Revisa el teléfono."
                    outcome.contactsNotified -> "Alerta enviada" to "SMS a ${outcome.smsSent} de ${outcome.contactsTotal} contactos."
                    outcome.contactsTotal == 0 -> "Sin contactos" to "Agrega contactos en el teléfono."
                    else -> "SMS no enviados" to "Revisa el teléfono o llama al 123."
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                Text(detail, fontSize = 12.sp, textAlign = TextAlign.Center)
                PillButton("Cerrar", Neutral, actions.onDismissResult)
            }
            is SosUi.Failed -> {
                Text("No se envió", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SosRed)
                Text(sos.message, fontSize = 12.sp, textAlign = TextAlign.Center)
                PillButton("Cerrar", Neutral, actions.onDismissResult)
            }
            SosUi.Idle -> Unit
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, fontSize = 14.sp, textAlign = TextAlign.Center)
}

@Composable
private fun RoundActionButton(
    text: String,
    color: Color,
    size: Dp,
    fontSize: Int,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .border(3.dp, color.copy(alpha = 0.4f), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(text, fontSize = fontSize.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
private fun PillButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** El ámbar y el verde oscuro se leen mal sobre negro en pantallas pequeñas: se aclaran. */
private fun Color.readableOnBlack(): Color = when (this) {
    SafeGreen -> Color(0xFF81C784)
    AlertRed -> Color(0xFFEF9A9A)
    else -> this
}
