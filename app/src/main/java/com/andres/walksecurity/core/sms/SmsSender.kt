package com.andres.walksecurity.core.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsSender(private val context: Context) {

    fun hasTelephony(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun canSend(): Boolean = hasTelephony() && hasPermission()

    /**
     * Entrega el mensaje a la red móvil (usa la SIM predeterminada para SMS).
     * `true` significa que el sistema aceptó el envío, no que haya sido entregado.
     */
    fun send(phone: String, message: String): Boolean {
        if (!canSend()) return false
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallo enviando SMS a $phone", e)
            false
        }
    }

    private companion object {
        const val TAG = "SmsSender"
    }
}
