package com.andres.walksecurity.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

internal actual fun currentHourMinute(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
