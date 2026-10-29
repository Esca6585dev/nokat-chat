package com.nokat.chat.util

object PhoneUtils {
    const val DEFAULT_PREFIX = "+993 "

    /** Normalises to E.164 (`+` and 8–15 digits) or returns null if invalid. */
    fun normalize(input: String): String? {
        val cleaned = input.filter { it.isDigit() || it == '+' }
        val digits = cleaned.removePrefix("+")
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        if (digits.length !in 8..15) return null
        return "+$digits"
    }

    /** Keeps only characters that can appear while typing a phone number. */
    fun sanitizeInput(input: String): String =
        input.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }.take(20)
}
