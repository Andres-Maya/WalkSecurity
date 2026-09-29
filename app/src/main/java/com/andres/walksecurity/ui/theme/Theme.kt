package com.andres.walksecurity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Navy80,
    secondary = Slate80,
    tertiary = Teal80,
    error = AlertRed80,
)

private val LightColorScheme = lightColorScheme(
    primary = Navy40,
    secondary = Slate40,
    tertiary = Teal40,
    error = AlertRed,
)

// Colores fijos (sin color dinámico) para que los estados de riesgo y el SOS sean siempre reconocibles
@Composable
fun WalkSecurityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
