package com.andres.walksecurity.data.remote

import com.andres.walksecurity.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

class ApiException(val code: Int?, message: String) : Exception(message)

/** Ejecuta una llamada al API y traduce los errores a mensajes legibles para el usuario. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(ApiException(e.code(), e.serverMessage() ?: defaultMessage(e.code())))
    } catch (e: IOException) {
        Result.failure(ApiException(null, connectionErrorMessage()))
    } catch (e: SerializationException) {
        Result.failure(ApiException(null, "Respuesta inesperada del servidor."))
    }

// En debug se muestra la URL para diagnosticar rápido (cable/adb reverse o backend apagado)
private fun connectionErrorMessage(): String {
    val base = "No se pudo conectar con el servidor. Verifica tu conexión."
    return if (BuildConfig.DEBUG) "$base\n[debug] ${BuildConfig.API_BASE_URL}" else base
}

private fun HttpException.serverMessage(): String? =
    runCatching {
        response()?.errorBody()?.string()?.let { AppJson.decodeFromString<ErrorResponse>(it).message }
    }.getOrNull()?.takeIf { it.isNotBlank() }

private fun defaultMessage(code: Int): String = when (code) {
    400 -> "Datos inválidos."
    401 -> "Sesión no válida o credenciales incorrectas."
    403 -> "No tienes permiso para esta acción."
    404 -> "Recurso no encontrado."
    409 -> "El recurso ya existe."
    in 500..599 -> "Error del servidor. Intenta más tarde."
    else -> "Error inesperado ($code)."
}
