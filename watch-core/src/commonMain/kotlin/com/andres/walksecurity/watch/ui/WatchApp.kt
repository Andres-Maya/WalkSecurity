package com.andres.walksecurity.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.watch.SosUi
import com.andres.walksecurity.watch.WatchUiState
import kotlinx.coroutines.delay

val SafeGreen = Color(0xFF2E7D32)
val CautionAmber = Color(0xFFF9A825)
val AlertRed = Color(0xFFC62828)
val SosRed = Color(0xFFD32F2F)
private val Neutral = Color(0xFF455A64)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFBDBDBD)

data class WatchActions(
    val onSos: () -> Unit,
    val onSendNow: () -> Unit,
    val onCancel: () -> Unit,
    val onDismissResult: () -> Unit,
    val onImOk: () -> Unit,
    val onEmergency: () -> Unit,
)

/**
 * Pantalla completa del reloj. Diseñada para pantallas redondas de ~200 dp.
 * @param showClock dibuja la hora arriba (el reloj real usa el TimeText del sistema).
 */
@Composable
fun WatchApp(state: WatchUiState, actions: WatchActions, showClock: Boolean = false) {
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
        if (showClock) Clock(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
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
            WText(title, 16.sp, color = color.readableOnBlack(), weight = FontWeight.Bold)
        }
        if (subtitle.isNotEmpty()) {
            WText(subtitle, 11.sp, color = TextSecondary, maxLines = 2)
        }
        Spacer(Modifier.height(10.dp))
        RoundActionButton(
            text = "SOS",
            color = SosRed,
            size = 84.dp,
            fontSize = 26.sp,
            description = "Enviar alerta SOS",
            onClick = actions.onSos,
        )
    }
}

/*
 * Pantallas redondas: el ancho útil se reduce arriba y abajo. Por eso el contenido va centrado
 * verticalmente, los botones van lado a lado (convención Wear OS: cancelar a la izquierda,
 * confirmar a la derecha) y nada ocupa el ancho completo cerca de los bordes.
 * Verificado en 227 dp y 192 dp con el emulador de escritorio (watch-emulator).
 */

@Composable
private fun RiskAlertScreen(alert: RiskStatus, actions: WatchActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlertRed.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        WText("¿Estás bien?", 16.sp, weight = FontWeight.Bold)
        WText(
            "Entraste en una zona con riesgo estimado alto${alert.zoneName?.let { ": $it" }.orEmpty()}.",
            11.sp,
            maxLines = 3,
        )
        PillRow(
            left = { PillButton("Estoy bien", Neutral, actions.onImOk, Modifier.weight(1f), textSize = 11.sp) },
            right = { PillButton("Emergencia", SosRed, actions.onEmergency, Modifier.weight(1f), textSize = 11.sp) },
        )
        WText("Estimación, no garantía.", 9.sp, color = TextSecondary)
    }
}

@Composable
private fun SosScreen(sos: SosUi, actions: WatchActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        when (sos) {
            is SosUi.Countdown -> {
                WText("Enviando SOS en", 12.sp)
                WText("${sos.secondsLeft}", 40.sp, color = SosRed, weight = FontWeight.Black)
                PillRow(
                    left = { PillButton("Cancelar", Neutral, actions.onCancel, Modifier.weight(1f), textSize = 11.sp) },
                    right = { PillButton("Enviar", SosRed, actions.onSendNow, Modifier.weight(1f), textSize = 11.sp) },
                )
            }
            SosUi.Sending -> WText("Enviando al teléfono…", 14.sp)
            SosUi.WaitingPhone -> WText("Avisando a tus contactos…", 14.sp)
            is SosUi.Done -> {
                val outcome = sos.outcome
                val (title, detail) = when {
                    outcome == null -> "SOS enviado" to "El teléfono lo recibió. Revisa el teléfono."
                    outcome.contactsNotified -> "Alerta enviada" to "SMS a ${outcome.smsSent} de ${outcome.contactsTotal} contactos."
                    outcome.contactsTotal == 0 -> "Sin contactos" to "Agrega contactos en el teléfono."
                    else -> "SMS no enviados" to "Revisa el teléfono o llama al 123."
                }
                WText(title, 16.sp, weight = FontWeight.Bold)
                WText(detail, 12.sp)
                PillButton("Cerrar", Neutral, actions.onDismissResult, Modifier.fillMaxWidth(0.7f))
            }
            is SosUi.Failed -> {
                WText("No se envió", 16.sp, color = SosRed, weight = FontWeight.Bold)
                WText(sos.message, 12.sp)
                PillButton("Cerrar", Neutral, actions.onDismissResult, Modifier.fillMaxWidth(0.7f))
            }
            SosUi.Idle -> Unit
        }
    }
}

@Composable
private fun WText(
    text: String,
    size: TextUnit,
    color: Color = TextPrimary,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        maxLines = maxLines,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = TextAlign.Center),
    )
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(currentHourMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentHourMinute()
            delay(10_000)
        }
    }
    Box(modifier) { WText(now, 12.sp, color = TextSecondary) }
}

@Composable
private fun RoundActionButton(
    text: String,
    color: Color,
    size: Dp,
    fontSize: TextUnit,
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
        WText(text, fontSize, weight = FontWeight.Black)
    }
}

/** Dos botones lado a lado, sin llegar a los bordes curvos de la pantalla. */
@Composable
private fun PillRow(left: @Composable RowScope.() -> Unit, right: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(0.94f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        left()
        right()
    }
}

@Composable
private fun PillButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 12.sp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        WText(text, textSize, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** El verde oscuro y el rojo se leen mal sobre negro en pantallas pequeñas: se aclaran. */
private fun Color.readableOnBlack(): Color = when (this) {
    SafeGreen -> Color(0xFF81C784)
    AlertRed -> Color(0xFFEF9A9A)
    else -> this
}
