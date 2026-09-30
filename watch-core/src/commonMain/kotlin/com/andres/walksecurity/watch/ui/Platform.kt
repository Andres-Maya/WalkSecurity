package com.andres.walksecurity.watch.ui

import androidx.compose.runtime.Composable

/** Mantiene la pantalla encendida mientras [enabled] (SOS o alerta en curso). */
@Composable
internal expect fun KeepScreenOn(enabled: Boolean)

/** Hora local "HH:mm" para el reloj de la pantalla. */
internal expect fun currentHourMinute(): String
