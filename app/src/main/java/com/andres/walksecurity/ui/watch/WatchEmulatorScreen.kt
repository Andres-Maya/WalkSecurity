package com.andres.walksecurity.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.watch.WatchUiState
import com.andres.walksecurity.watch.ui.WatchActions
import com.andres.walksecurity.watch.ui.WatchApp
import com.andres.walksecurity.ui.theme.AlertRed
import com.andres.walksecurity.ui.theme.CautionAmber
import com.andres.walksecurity.ui.theme.SafeGreen

/** Tamaño lógico de una pantalla de reloj redonda típica (Galaxy Watch / Pixel Watch ≈ 192–227 dp). */
private val WATCH_SCREEN = 210.dp
private val BEZEL = 10.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchEmulatorScreen(
    onBack: () -> Unit,
    viewModel: WatchEmulatorViewModel = viewModel(factory = WatchEmulatorViewModel.Factory),
) {
    val controller = viewModel.controller
    val state by controller.state.collectAsStateWithLifecycle()
    val currentLevel by viewModel.currentLevel.collectAsStateWithLifecycle()
    val actions = remember(controller) {
        WatchActions(
            onSos = controller::onSosPressed,
            onSendNow = controller::sendNow,
            onCancel = controller::cancelSos,
            onDismissResult = controller::dismissResult,
            onImOk = controller::acknowledgeAlert,
            onEmergency = controller::confirmEmergency,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reloj emulado") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Esta es la misma app del reloj Wear OS, ejecutándose en tu teléfono. " +
                    "El SOS es real: enviará SMS a tus contactos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            WatchFrame(state, actions)
            SimulatorCard(currentLevel, onSimulate = viewModel::simulate)
        }
    }
}

@Composable
private fun WatchFrame(state: WatchUiState, actions: WatchActions) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val frame = min(maxWidth, 320.dp)
        val scale = (frame - BEZEL * 2) / WATCH_SCREEN
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(frame)
                .shadow(12.dp, CircleShape)
                .background(Color(0xFF1F1F1F), CircleShape)
                .border(2.dp, Color(0xFF3A3A3A), CircleShape)
                .padding(BEZEL)
                .clip(CircleShape)
                .background(Color.Black),
        ) {
            // Se dibuja a tamaño real de reloj y se escala: así las proporciones son las del dispositivo
            Box(
                Modifier
                    .requiredSize(WATCH_SCREEN)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                WatchApp(state, actions, showClock = true)
            }
        }
    }
}

@Composable
private fun SimulatorCard(currentLevel: RiskLevel?, onSimulate: (RiskLevel) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Simular zona", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Mientras llega el geofencing (fase 2), cambia aquí el nivel de riesgo. " +
                    "Al subir de nivel el teléfono vibra como lo haría el reloj.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelChip("Seguro", SafeGreen, currentLevel == RiskLevel.SAFE) { onSimulate(RiskLevel.SAFE) }
                LevelChip("Precaución", CautionAmber, currentLevel == RiskLevel.CAUTION) { onSimulate(RiskLevel.CAUTION) }
                LevelChip("Alerta", AlertRed, currentLevel == RiskLevel.ALERT) { onSimulate(RiskLevel.ALERT) }
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
        ),
    )
}
