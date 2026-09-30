package com.andres.walksecurity.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.watch.WatchController
import com.andres.walksecurity.watch.ui.AlertRed
import com.andres.walksecurity.watch.ui.CautionAmber
import com.andres.walksecurity.watch.ui.SafeGreen
import com.andres.walksecurity.watch.ui.WatchActions
import com.andres.walksecurity.watch.ui.WatchApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

private val Background = Color(0xFF0F1115)
private val PanelColor = Color(0xFF171A20)
private val Muted = Color(0xFF9AA0A6)

/** @param startLink abre el enlace con el teléfono real (false al generar capturas). */
@Composable
fun EmulatorApp(startLink: Boolean = true) {
    val scope = rememberCoroutineScope()
    val log = remember { EventLog() }
    val phone = remember { SimulatedPhone(scope, log) }
    val realPhone = remember { PhoneLinkServer(scope, log) }
    val transport = remember { EmulatorTransport(phone, realPhone, scope) }
    val haptics = remember { DesktopHaptics(log) }
    // El mismo controlador que usa el reloj Wear OS real; el teléfono es el real (USB) o el simulado
    val controller = remember { WatchController(transport, haptics, scope, vibrateOnEscalation = true) }
    val actions = remember(controller) { loggedActions(controller, log) }

    val watchState by controller.state.collectAsState()
    val connected by phone.connected.collectAsState()
    val contacts by phone.contacts.collectAsState()
    val phoneLevel by phone.phoneLevel.collectAsState()
    val entries by log.entries.collectAsState()
    val realConnected by realPhone.connected.collectAsState()
    val linkStatus by realPhone.status.collectAsState()

    LaunchedEffect(Unit) { if (startLink) realPhone.start() }
    // Al conectar o desconectar el teléfono real, el reloj vuelve a comprobar si lo alcanza
    LaunchedEffect(realConnected) { controller.refreshPhone() }
    val vibration = rememberVibrationUi(haptics)
    var shape by remember { mutableStateOf(WatchShape.LARGE_ROUND) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8AB4F8), surface = PanelColor)) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    modifier = Modifier.weight(1.1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("WalkSecurity · Reloj", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Misma interfaz y lógica que la app Wear OS. Haz clic en el reloj para usarlo.",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    WatchDevice(
                        shape = shape,
                        vibration = vibration,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        WatchApp(watchState, actions, showClock = true)
                    }
                    VibrationBadge(vibration)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WatchShape.entries.forEach { option ->
                            FilterChip(
                                selected = shape == option,
                                onClick = { shape = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(0.9f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RealPhoneCard(linkStatus, onPrepareUsb = { scope.launch(Dispatchers.IO) { realPhone.prepareUsb() } })

                    PanelCard("Zona actual (la calcula el teléfono)") {
                        Text(
                            if (realConnected) {
                                "La envía el teléfono real: cámbiala en la app (Reloj → Simular zona)."
                            } else {
                                "Mientras llega el geofencing, elige el nivel de riesgo estimado. " +
                                    "Al subir de nivel el reloj vibra; en Alerta pregunta \"¿Estás bien?\"."
                            },
                            color = Muted,
                            fontSize = 13.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val enabled = !realConnected
                            LevelChip("Zona segura", SafeGreen, phoneLevel == RiskLevel.SAFE, enabled) { phone.publish(RiskLevel.SAFE) }
                            LevelChip("Precaución", CautionAmber, phoneLevel == RiskLevel.CAUTION, enabled) { phone.publish(RiskLevel.CAUTION) }
                            LevelChip("Alerta", AlertRed, phoneLevel == RiskLevel.ALERT, enabled) { phone.publish(RiskLevel.ALERT) }
                        }
                    }

                    PanelCard(if (realConnected) "Teléfono simulado (inactivo)" else "Teléfono simulado") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (connected) "Reloj conectado al teléfono" else "Reloj desconectado", color = Color.White)
                                Text(
                                    "Desconéctalo para ver cómo reacciona el reloj sin teléfono.",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Switch(
                                checked = connected,
                                enabled = !realConnected,
                                onCheckedChange = {
                                    phone.setConnected(it)
                                    controller.refreshPhone()
                                },
                            )
                        }
                        Text("Contactos de emergencia registrados", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0..3).forEach { count ->
                                FilterChip(
                                    selected = contacts == count,
                                    enabled = !realConnected,
                                    onClick = { phone.setContacts(count) },
                                    label = { Text("$count") },
                                )
                            }
                        }
                    }

                    PanelCard("Registro de eventos", Modifier.weight(1f)) {
                        if (entries.isEmpty()) {
                            Text("Aquí verás lo que pasa entre el reloj y el teléfono.", color = Muted, fontSize = 13.sp)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(entries) { entry -> LogRow(entry) }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            scope.launch {
                                exportMessage = "Exportando…"
                                exportMessage = try {
                                    val dir = defaultExportDir()
                                    val files = withContext(Dispatchers.IO) { ScreenshotExporter.exportAll(dir) }
                                    runCatching { Desktop.getDesktop().open(dir) }
                                    "${files.size} imágenes en ${dir.path}"
                                } catch (e: Exception) {
                                    "No se pudieron exportar: ${e.message}"
                                }
                            }
                        }) { Text("Exportar capturas (PNG)") }
                        Spacer(Modifier.width(12.dp))
                        Text(exportMessage ?: "Para diapositivas o documentos.", color = Muted, fontSize = 12.sp)
                    }
                    Text(
                        "Simulación en el computador: no se envían SMS reales. " +
                            "Las zonas de riesgo son estimaciones, no garantías.",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** Registra en el log lo que hace el usuario sobre el reloj y delega en el controlador. */
private fun loggedActions(controller: WatchController, log: EventLog) = WatchActions(
    onSos = {
        log.add(LogSource.USER, "Pulsa SOS en el reloj (cuenta regresiva de 5 s)")
        controller.onSosPressed()
    },
    onSendNow = {
        log.add(LogSource.USER, "Pulsa \"Enviar ahora\"")
        controller.sendNow()
    },
    onCancel = {
        log.add(LogSource.USER, "Cancela el SOS")
        controller.cancelSos()
    },
    onDismissResult = controller::dismissResult,
    onImOk = {
        log.add(LogSource.USER, "Responde \"Estoy bien\"")
        controller.acknowledgeAlert()
    },
    onEmergency = {
        log.add(LogSource.USER, "Confirma \"Emergencia\" en la alerta")
        controller.confirmEmergency()
    },
)

private fun defaultExportDir(): File {
    val pictures = File(System.getProperty("user.home"), "Pictures")
    return File(if (pictures.isDirectory) pictures else File(System.getProperty("user.home")), "WalkSecurity-reloj")
}

@Composable
private fun PanelCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            content()
        }
    }
}

@Composable
private fun RealPhoneCard(status: PhoneLinkStatus, onPrepareUsb: () -> Unit) {
    PanelCard("Teléfono real (USB)") {
        when (status) {
            is PhoneLinkStatus.Connected -> {
                Text(
                    "Conectado: ${status.device}${status.userName?.let { " · $it" }.orEmpty()}",
                    color = Color(0xFF81C995),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Atención: el SOS de este reloj envía SMS REALES desde el teléfono a sus contactos.",
                    color = Color(0xFFFFB74D),
                    fontSize = 13.sp,
                )
            }
            PhoneLinkStatus.Starting, PhoneLinkStatus.Waiting -> {
                Text(
                    "Esperando al teléfono. Con el cable USB conectado, en la app abre Reloj y activa " +
                        "\"Reloj del computador\". Mientras tanto se usa el teléfono simulado.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = onPrepareUsb) { Text("Preparar USB de nuevo (adb reverse)") }
            }
            is PhoneLinkStatus.Failed -> Text(status.message, color = Color(0xFFF28B82), fontSize = 13.sp)
        }
    }
}

@Composable
private fun LevelChip(label: String, color: Color, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
private fun LogRow(entry: LogEntry) {
    val tagColor = when (entry.source) {
        LogSource.PHONE -> Color(0xFF8AB4F8)
        LogSource.WATCH -> Color(0xFFFFB74D)
        LogSource.USER -> Color(0xFF81C995)
        LogSource.PC -> Color(0xFFC58AF9)
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(entry.time, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tagColor.copy(alpha = 0.18f))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(entry.source.label, fontSize = 11.sp, color = tagColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(entry.text, fontSize = 13.sp, color = Color(0xFFE8EAED))
    }
}

@Composable
private fun VibrationBadge(vibration: VibrationUi) {
    val text = vibration.label?.let { "Vibrando: $it" } ?: " "
    Text(
        text,
        color = Color(0xFFFFB74D),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
