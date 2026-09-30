package com.andres.walksecurity

import com.andres.walksecurity.core.model.TrustedContact
import com.andres.walksecurity.data.remote.ContactDto
import com.andres.walksecurity.data.repository.ContactsMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsMergerTest {

    private fun local(id: String, phone: String, serverId: Long? = null, pendingDelete: Boolean = false) =
        TrustedContact(localId = id, name = "Local $id", phone = phone, serverId = serverId, pendingDelete = pendingDelete)

    private fun remote(id: Long, phone: String, name: String = "Remoto $id") = ContactDto(id, name, phone)

    @Test
    fun `un contacto sincronizado toma los datos del servidor`() {
        val merged = ContactsMerger.merge(
            listOf(local("a", "+573001111111", serverId = 7)),
            listOf(remote(7, "+573001111111", name = "Mamá")),
        )
        assertEquals(1, merged.size)
        assertEquals("Mamá", merged.single().name)
        assertEquals("a", merged.single().localId)
    }

    @Test
    fun `los contactos creados sin conexion se conservan`() {
        val merged = ContactsMerger.merge(listOf(local("a", "+573001111111")), emptyList())
        assertEquals(listOf("a"), merged.map { it.localId })
        assertNull(merged.single().serverId)
    }

    @Test
    fun `si el servidor ya tiene el numero se enlaza en vez de duplicar`() {
        val merged = ContactsMerger.merge(
            listOf(local("a", "+573001111111")),
            listOf(remote(9, "+573001111111")),
        )
        assertEquals(1, merged.size)
        assertEquals(9L, merged.single().serverId)
    }

    @Test
    fun `nunca se pierde un contacto aunque el servidor ya no lo tenga`() {
        // p. ej. base de datos nueva: se conserva y queda pendiente de volver a subir
        val merged = ContactsMerger.merge(listOf(local("a", "+573001111111", serverId = 3)), emptyList())
        assertEquals(listOf("a"), merged.map { it.localId })
        assertNull(merged.single().serverId)
    }

    @Test
    fun `un borrado pendiente se mantiene hasta borrarlo en el servidor`() {
        val pending = local("a", "+573001111111", serverId = 3, pendingDelete = true)
        assertEquals(1, ContactsMerger.merge(listOf(pending), listOf(remote(3, "+573001111111"))).size)
        assertTrue(ContactsMerger.merge(listOf(pending), emptyList()).isEmpty())
    }

    @Test
    fun `se agregan los contactos que solo existen en el servidor`() {
        val merged = ContactsMerger.merge(
            listOf(local("a", "+573001111111", serverId = 1)),
            listOf(remote(1, "+573001111111"), remote(2, "+573002222222")),
        )
        assertEquals(listOf(1L, 2L), merged.map { it.serverId })
        assertEquals("srv-2", merged.last().localId)
    }

    @Test
    fun `un borrado pendiente no se vuelve a descargar del servidor`() {
        val merged = ContactsMerger.merge(
            listOf(local("a", "+573001111111", serverId = 5, pendingDelete = true)),
            listOf(remote(5, "+573001111111")),
        )
        assertEquals(1, merged.size)
        assertTrue(merged.single().pendingDelete)
    }
}
