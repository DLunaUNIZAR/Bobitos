package com.dlunaunizar.bobitos.core.model

data class AuthUser(val id: String, val displayName: String, val email: String, val isEmailVerified: Boolean) {
    val initials: String
        get() {
            val words = displayName
                .trim()
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)

            return words
                .take(2)
                .mapNotNull(String::firstOrNull)
                .joinToString(separator = "")
                .ifBlank { email.substringBefore('@').take(2) }
                .uppercase()
        }
}
