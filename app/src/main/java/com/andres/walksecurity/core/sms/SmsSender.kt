package com.andres.walksecurity.core.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.andres.walksecurity.core.model.Validators
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Resultado real del envío: cuántos SMS salieron y, si alguno falló, por qué. */
data class SmsReport(val sent: Int, val error: String? = null)

class SmsSender(private val context: Context) {

    /**
     * FEATURE_TELEPHONY_MESSAGING solo existe desde Android 13: en versiones anteriores ningún
     * teléfono lo declara, así que ahí se pregunta por la telefonía en general.
     */
    fun hasTelephony(): Boolean = with(context.packageManager) {
        hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING))
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun canSend(): Boolean = hasTelephony() && hasPermission()

    /**
     * Envía el mensaje a cada número con la SIM del teléfono y espera la respuesta de la red, para
     * saber si de verdad salió (antes solo se sabía que el sistema lo había recibido).
     * Un SMS sin respuesta a tiempo se cuenta como enviado: la red puede tardar con poca señal.
     */
    suspend fun sendAll(phones: List<String>, message: String): SmsReport {
        if (phones.isEmpty()) return SmsReport(0)
        if (!hasTelephony()) return SmsReport(0, "Este dispositivo no puede enviar SMS.")
        if (!hasPermission()) return SmsReport(0, "Falta el permiso de SMS.")

        val action = "${context.packageName}.SMS_SENT.${System.nanoTime()}"
        val results = ConcurrentHashMap<Int, CompletableDeferred<Int>>()
        val pendingParts = ConcurrentHashMap<Int, AtomicInteger>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val index = intent.getIntExtra(EXTRA_INDEX, -1)
                val result = results[index] ?: return
                // Un mensaje largo va en varias partes: basta que una falle para darlo por fallido
                if (resultCode != Activity.RESULT_OK) result.complete(resultCode)
                else if (pendingParts[index]?.decrementAndGet() == 0) result.complete(Activity.RESULT_OK)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val manager = smsManager()
            val parts = manager.divideMessage(message)
            phones.forEachIndexed { index, phone ->
                val result = CompletableDeferred<Int>().also { results[index] = it }
                pendingParts[index] = AtomicInteger(parts.size)
                val sentIntent = PendingIntent.getBroadcast(
                    context, index,
                    Intent(action).setPackage(context.packageName).putExtra(EXTRA_INDEX, index),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                try {
                    manager.sendMultipartTextMessage(
                        Validators.normalizePhone(phone), null, parts, ArrayList(parts.map { sentIntent }), null,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "El sistema rechazó el SMS", e)
                    result.complete(SmsManager.RESULT_ERROR_GENERIC_FAILURE)
                }
            }
            val codes = withTimeoutOrNull(CONFIRMATION_TIMEOUT_MS) { results.values.map { it.await() } }
                ?: results.values.map { if (it.isCompleted) it.getCompleted() else Activity.RESULT_OK }
            val failures = codes.filter { it != Activity.RESULT_OK }
            failures.forEach { Log.w(TAG, "SMS no enviado, código $it") }
            return SmsReport(sent = codes.size - failures.size, error = failures.firstOrNull()?.let(::describe))
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Con dos SIM y sin una elegida para SMS, el envío por defecto falla en silencio en muchos
     * teléfonos: se usa la SIM predeterminada para SMS y, si no hay, la predeterminada del teléfono.
     */
    private fun smsManager(): SmsManager {
        val subscription = SmsManager.getDefaultSmsSubscriptionId()
            .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?: SubscriptionManager.getDefaultSubscriptionId()
        val valid = subscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(SmsManager::class.java)
            if (valid) manager.createForSubscriptionId(subscription) else manager
        } else {
            @Suppress("DEPRECATION")
            if (valid) SmsManager.getSmsManagerForSubscriptionId(subscription) else SmsManager.getDefault()
        }
    }

    private fun describe(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "Sin señal de la red móvil."
        SmsManager.RESULT_ERROR_RADIO_OFF -> "El teléfono está en modo avión."
        SmsManager.RESULT_ERROR_NULL_PDU -> "El operador no aceptó el mensaje."
        SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
            "El operador rechazó el SMS: revisa el saldo o el plan de la SIM y cuál SIM usa el teléfono para SMS."
        else -> "El teléfono no pudo enviar el SMS (código $code)."
    }

    private companion object {
        const val TAG = "SmsSender"
        const val EXTRA_INDEX = "index"
        const val CONFIRMATION_TIMEOUT_MS = 15_000L
    }
}
