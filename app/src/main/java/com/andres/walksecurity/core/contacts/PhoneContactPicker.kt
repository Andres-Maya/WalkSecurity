package com.andres.walksecurity.core.contacts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedContact(val name: String, val phone: String)

/**
 * Abre la agenda del teléfono para elegir UN número. Android concede acceso solo a ese contacto,
 * así que la app no necesita el permiso READ_CONTACTS (no puede leer el resto de la agenda).
 */
class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

class PhoneContactsReader(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /** Lee nombre y número del contacto elegido en la agenda. */
    suspend fun read(uri: Uri): PickedContact? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getString(0).orEmpty()
                val number = cursor.getString(1).orEmpty()
                if (number.isBlank()) null else PickedContact(name, number)
            }
        }.getOrNull()
    }
}
