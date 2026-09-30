package com.andres.walksecurity.watch.ui

import androidx.compose.runtime.Composable
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// En el computador no hay pantalla que se apague: no hace nada.
@Composable
internal actual fun KeepScreenOn(enabled: Boolean) = Unit

internal actual fun currentHourMinute(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
