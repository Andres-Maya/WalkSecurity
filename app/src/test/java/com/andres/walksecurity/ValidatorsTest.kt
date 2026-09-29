package com.andres.walksecurity

import com.andres.walksecurity.core.model.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `normaliza telefonos con espacios y guiones`() {
        assertEquals("+573001234567", Validators.normalizePhone("+57 300-123 (4567)"))
    }

    @Test
    fun `acepta telefonos validos`() {
        assertTrue(Validators.isValidPhone("+57 300 123 4567"))
        assertTrue(Validators.isValidPhone("3001234567"))
    }

    @Test
    fun `rechaza telefonos invalidos`() {
        assertFalse(Validators.isValidPhone("123"))
        assertFalse(Validators.isValidPhone("abc"))
        assertFalse(Validators.isValidPhone("+1234567890123456"))
    }

    @Test
    fun `valida correos`() {
        assertTrue(Validators.isValidEmail(" ana@example.com "))
        assertFalse(Validators.isValidEmail("ana@"))
        assertFalse(Validators.isValidEmail("ana.example.com"))
    }

    @Test
    fun `exige longitud minima de contrasena`() {
        assertFalse(Validators.isValidPassword("1234567"))
        assertTrue(Validators.isValidPassword("12345678"))
    }
}
