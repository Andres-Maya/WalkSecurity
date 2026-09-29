package com.andres.walksecurity.core.model

object Validators {
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneRegex = Regex("^\\+?\\d{7,15}$")

    const val MIN_PASSWORD_LENGTH = 8

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email.trim())

    /** Quita espacios, guiones y paréntesis: "+57 300-123 4567" -> "+573001234567". */
    fun normalizePhone(phone: String): String = phone.filter { it.isDigit() || it == '+' }

    fun isValidPhone(phone: String): Boolean = phoneRegex.matches(normalizePhone(phone))

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH
}
