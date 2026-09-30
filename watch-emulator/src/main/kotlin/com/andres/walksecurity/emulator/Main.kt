package com.andres.walksecurity.emulator

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import kotlin.system.exitProcess

/**
 * Emulador del reloj de WalkSecurity para el computador.
 *   (sin argumentos)          abre la ventana interactiva
 *   --export <carpeta>        genera capturas PNG de todas las pantallas y termina
 */
fun main(args: Array<String>) {
    redirectLogsWhenPackaged()
    val exportIndex = args.indexOf("--export")
    if (exportIndex >= 0) {
        val dir = File(args.getOrNull(exportIndex + 1) ?: "capturas-reloj").absoluteFile
        val files = ScreenshotExporter.exportAll(dir)
        println("Capturas guardadas en ${dir.path}:")
        files.forEach { println("  - ${it.name}") }
        exitProcess(0)
    }

    application {
        val windowState = rememberWindowState(
            size = DpSize(1200.dp, 820.dp),
            position = WindowPosition.Aligned(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "WalkSecurity · Emulador de reloj",
            state = windowState,
        ) {
            EmulatorApp()
        }
    }
}

/**
 * El .exe no tiene consola: si algo falla, el error quedaría oculto. En ese caso la salida
 * se guarda en %TEMP%\WalkSecurityReloj.log.
 */
private fun redirectLogsWhenPackaged() {
    if (System.getProperty("jpackage.app-version") == null) return
    runCatching {
        val log = File(System.getProperty("java.io.tmpdir"), "WalkSecurityReloj.log")
        val stream = PrintStream(FileOutputStream(log, false), true, Charsets.UTF_8)
        System.setOut(stream)
        System.setErr(stream)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            stream.println("Error no controlado en ${thread.name}:")
            error.printStackTrace(stream)
        }
        println("WalkSecurity Reloj · ${LocalDateTime.now()} · Java ${System.getProperty("java.version")}")
    }
}
