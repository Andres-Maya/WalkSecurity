package com.andres.walksecurity.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andres.walksecurity.shared.RiskLevel
import com.andres.walksecurity.shared.RiskStatus
import com.andres.walksecurity.shared.SosOutcome
import com.andres.walksecurity.shared.SosTrigger
import com.andres.walksecurity.watch.SosUi
import com.andres.walksecurity.watch.WatchUiState
import com.andres.walksecurity.watch.ui.WatchActions
import com.andres.walksecurity.watch.ui.WatchApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Genera imágenes PNG de cada pantalla del reloj (sin abrir ventana), con la misma
 * interfaz que corre en Wear OS. Útil para presentaciones y documentación.
 */
object ScreenshotExporter {

    /** 227 dp a densidad 2 = 454 px: resolución típica de un reloj redondo grande. */
    private const val DENSITY = 2f
    private const val WATCH_PX = 454

    private data class Shot(val file: String, val caption: String, val state: WatchUiState)

    private val safe = RiskStatus(RiskLevel.SAFE, null, 0.12f, updatedAt = 1, simulated = true)
    private val caution = RiskStatus(RiskLevel.CAUTION, "Parque Central", 0.52f, updatedAt = 2, simulated = true)
    private val alert = RiskStatus(RiskLevel.ALERT, "Calle 19 con Cra. 7", 0.86f, updatedAt = 3, simulated = true)

    private val shots = listOf(
        Shot("01-zona-segura", "Zona segura", WatchUiState(risk = safe, phoneReachable = true)),
        Shot("02-precaucion", "Precaución", WatchUiState(risk = caution, phoneReachable = true)),
        Shot("03-alerta-estas-bien", "Alerta: ¿Estás bien?", WatchUiState(risk = alert, phoneReachable = true, pendingAlert = alert)),
        Shot("04-alerta", "Alerta (respondida)", WatchUiState(risk = alert, phoneReachable = true)),
        Shot("05-sos-cuenta-regresiva", "SOS: cuenta regresiva", WatchUiState(risk = safe, sos = SosUi.Countdown(3, SosTrigger.BUTTON))),
        Shot("06-sos-avisando", "SOS: avisando a contactos", WatchUiState(risk = safe, sos = SosUi.WaitingPhone)),
        Shot("07-sos-enviado", "SOS enviado", WatchUiState(risk = safe, sos = SosUi.Done(SosOutcome("demo", 2, 2, true, 0)))),
        Shot(
            "08-sin-telefono",
            "Sin conexión con el teléfono",
            WatchUiState(risk = safe, phoneReachable = false, sos = SosUi.Failed("Teléfono no conectado. Usa el teléfono para pedir ayuda.")),
        ),
    )

    private val noActions = WatchActions({}, {}, {}, {}, {}, {})

    /**
     * @return los archivos generados: láminas con todos los estados (reloj grande y pequeño),
     *   una imagen por estado y la ventana completa del emulador.
     */
    fun exportAll(dir: File): List<File> {
        dir.mkdirs()
        val singles = shots.map { shot ->
            render(File(dir, "${shot.file}.png"), WATCH_PX, WATCH_PX) { RoundWatchScreen(shot.state) }
        }
        val sheets = listOf(
            sheet(File(dir, "00-todos-los-estados.png"), "Reloj redondo grande (227 dp)", 227.dp),
            sheet(File(dir, "00b-reloj-pequeno.png"), "Reloj redondo pequeño (192 dp)", 192.dp),
        )
        val app = render(File(dir, "10-emulador.png"), width = 2400, height = 1640) { EmulatorApp() }
        return sheets + singles + app
    }

    private fun sheet(out: File, subtitle: String, watch: Dp): File {
        val columns = 4
        val rows = (shots.size + columns - 1) / columns
        val watchDp = watch.value.toInt()
        val widthDp = columns * watchDp + (columns - 1) * 24 + 40
        val heightDp = 70 + rows * (watchDp + 40) + (rows - 1) * 16 + 20
        return render(out, width = (widthDp * DENSITY).toInt(), height = (heightDp * DENSITY).toInt()) {
            Sheet(columns, subtitle, watch)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(out: File, width: Int, height: Int, content: @Composable () -> Unit): File {
        val scene = ImageComposeScene(width = width, height = height, density = Density(DENSITY), content = content)
        try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)
                ?: error("No se pudo codificar ${out.name}")
            out.writeBytes(png.bytes)
        } finally {
            scene.close()
        }
        return out
    }

    @Composable
    private fun RoundWatchScreen(state: WatchUiState, modifier: Modifier = Modifier.fillMaxSize()) {
        Box(modifier.clip(CircleShape).background(Color.Black)) {
            WatchApp(state, noActions, showClock = true)
        }
    }

    @Composable
    private fun Sheet(columns: Int, subtitle: String, watch: Dp) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF0F1115)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                "WalkSecurity · Pantallas del reloj (Wear OS) · $subtitle",
                style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            shots.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { shot ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // El contenido se dibuja al tamaño real de la pantalla indicada (227 o 192 dp)
                            Box(Modifier.size(watch).clip(CircleShape).background(Color(0xFF2A2D31)).padding(3.dp)) {
                                RoundWatchScreen(shot.state)
                            }
                            BasicText(
                                shot.caption,
                                modifier = Modifier.padding(top = 8.dp),
                                style = TextStyle(color = Color(0xFFE8EAED), fontSize = 13.sp, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            }
        }
    }
}
