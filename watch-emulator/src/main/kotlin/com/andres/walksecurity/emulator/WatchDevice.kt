package com.andres.walksecurity.emulator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Tamaños lógicos de pantalla de relojes Wear OS reales (mismos que los previews de Android Studio). */
enum class WatchShape(val label: String, val screen: Dp, val round: Boolean) {
    LARGE_ROUND("Redondo grande (227 dp)", 227.dp, true),
    SMALL_ROUND("Redondo pequeño (192 dp)", 192.dp, true),
    SQUARE("Cuadrado (180 dp)", 180.dp, false),
}

class VibrationUi(val offsetPx: Float, val label: String?, val active: Boolean)

/** Convierte los eventos de vibración en un "sacudón" del reloj en pantalla. */
@Composable
fun rememberVibrationUi(haptics: DesktopHaptics): VibrationUi {
    val offset = remember { Animatable(0f) }
    var label by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(haptics) {
        haptics.events.collectLatest { vibration ->
            if (vibration.label != null) label = vibration.label
            val amplitude = if (vibration.label == null) 3f else 9f
            vibration.pattern.forEachIndexed { index, millis ->
                if (index % 2 == 0) {
                    delay(millis) // pausa
                } else {
                    active = true
                    val end = System.currentTimeMillis() + millis
                    var direction = 1f
                    while (System.currentTimeMillis() < end) {
                        offset.animateTo(direction * amplitude, tween(durationMillis = 30))
                        direction = -direction
                    }
                    offset.animateTo(0f, tween(durationMillis = 30))
                    active = false
                }
            }
            if (vibration.label != null) {
                delay(1_200)
                label = null
            }
        }
    }
    return VibrationUi(offset.value, label, active)
}

/**
 * Reloj completo: correa, caja, corona y pantalla. El contenido se dibuja al tamaño lógico real
 * de la pantalla ([WatchShape.screen]) y se escala, así las proporciones son las del dispositivo.
 */
@Composable
fun WatchDevice(
    shape: WatchShape,
    vibration: VibrationUi,
    modifier: Modifier = Modifier,
    screen: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val caseSize = min(min(maxWidth / 1.12f, maxHeight / 1.9f), 440.dp)
        val bezel = caseSize * 0.055f
        val strapWidth = caseSize * 0.58f
        val strapHeight = caseSize * 0.44f
        val crownWidth = caseSize * 0.05f
        val caseShape: Shape = if (shape.round) CircleShape else RoundedCornerShape(caseSize * 0.2f)
        val screenShape: Shape = if (shape.round) CircleShape else RoundedCornerShape(caseSize * 0.13f)
        val scale = (caseSize - bezel * 2) / shape.screen

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { translationX = vibration.offsetPx },
        ) {
            // La correa entra un poco detrás de la caja para que se vea unida (zIndex negativo)
            Strap(strapWidth, strapHeight, top = true, Modifier.offset(y = caseSize * 0.09f).zIndex(-1f))
            Box(Modifier.width(caseSize + crownWidth * 1.6f), contentAlignment = Alignment.Center) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(caseSize)
                        .shadow(28.dp, caseShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF4A4E55), Color(0xFF24272B), Color(0xFF3A3E44))),
                            caseShape,
                        )
                        .border(
                            width = 2.dp,
                            color = if (vibration.active) Color(0xFFFFB74D) else Color(0xFF5A5F66),
                            shape = caseShape,
                        )
                        .padding(bezel)
                        .clip(screenShape)
                        .background(Color.Black),
                ) {
                    Box(
                        Modifier
                            .requiredSize(shape.screen)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        screen()
                    }
                }
                // Corona
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(-1f)
                        .width(crownWidth)
                        .height(caseSize * 0.17f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF5A5F66), Color(0xFF2E3136))))
                )
            }
            Strap(strapWidth, strapHeight, top = false, Modifier.offset(y = -caseSize * 0.09f).zIndex(-1f))
        }
    }
}

@Composable
private fun Strap(width: Dp, height: Dp, top: Boolean, modifier: Modifier = Modifier) {
    val radius = width * 0.18f
    val shape = if (top) {
        RoundedCornerShape(topStart = radius, topEnd = radius)
    } else {
        RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    }
    val colors = listOf(Color(0xFF2B2E33), Color(0xFF1C1E22))
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(if (top) colors else colors.reversed()))
    )
}
