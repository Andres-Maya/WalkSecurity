package com.andres.walksecurity.data.repository

import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.data.remote.ContactDto

/**
 * Fusiona la lista del teléfono con la del servidor. Regla principal: NUNCA perder un contacto
 * de emergencia del teléfono por algo que pase en el servidor.
 */
object ContactsMerger {

    fun merge(local: List<TrustedContact>, remote: List<ContactDto>): List<TrustedContact> {
        val remoteById = remote.associateBy { it.id }
        val claimedIds = local.mapNotNull { it.serverId }.filter { it in remoteById }.toMutableSet()
        val result = mutableListOf<TrustedContact>()

        for (contact in local) {
            val serverId = contact.serverId
            when {
                // Borrado pendiente: se conserva hasta que el servidor lo elimine (si aún existe allí)
                contact.pendingDelete -> if (serverId != null && serverId in remoteById) result += contact

                // Sincronizado: el servidor manda en nombre/teléfono/parentesco
                serverId != null && serverId in remoteById -> {
                    val dto = remoteById.getValue(serverId)
                    result += contact.copy(name = dto.name, phone = dto.phone, relationship = dto.relationship)
                }

                // Tenía id pero el servidor ya no lo tiene (base de datos nueva, otra cuenta...):
                // se conserva y se vuelve a subir.
                serverId != null -> result += contact.copy(serverId = null)

                // Pendiente de subir: si el servidor ya tiene ese número, se enlaza en vez de duplicarlo
                else -> {
                    val match = remote.firstOrNull { it.phone == contact.phone && it.id !in claimedIds }
                    if (match != null) {
                        claimedIds += match.id
                        result += contact.copy(serverId = match.id)
                    } else {
                        result += contact
                    }
                }
            }
        }

        // Contactos que existen en el servidor pero no en este teléfono (p. ej. creados en otro dispositivo)
        for (dto in remote) {
            if (dto.id !in claimedIds) {
                result += TrustedContact(
                    localId = "srv-${dto.id}",
                    name = dto.name,
                    phone = dto.phone,
                    relationship = dto.relationship,
                    serverId = dto.id,
                )
            }
        }
        return result
    }
}
