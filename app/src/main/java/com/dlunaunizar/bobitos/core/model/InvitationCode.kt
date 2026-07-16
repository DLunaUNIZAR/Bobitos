package com.dlunaunizar.bobitos.core.model

import java.net.URI
import java.security.SecureRandom

object InvitationCode {
    private const val TOKEN_BYTES = 20
    private const val TOKEN_LENGTH = 32
    private const val INVITATION_HOST = "invite"
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray()
    private val allowedCode = Regex("^[A-Z2-7]{$TOKEN_LENGTH}$")

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val result = StringBuilder(TOKEN_LENGTH)
        var buffer = 0
        var bitsInBuffer = 0

        bytes.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsInBuffer += 8
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5
                result.append(alphabet[(buffer shr bitsInBuffer) and 0x1f])
            }
        }
        return result.toString()
    }

    fun normalize(rawCode: String): String? {
        val normalized = rawCode
            .trim()
            .uppercase()
            .filterNot { character -> character == '-' || character.isWhitespace() }
        return normalized.takeIf(allowedCode::matches)
    }

    fun fromDeepLink(rawUri: String?): String? {
        if (rawUri.isNullOrBlank()) return null
        val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
        if (uri.scheme != "bobitos" || uri.host != INVITATION_HOST) return null
        return normalize(uri.path.orEmpty().removePrefix("/"))
    }
}
