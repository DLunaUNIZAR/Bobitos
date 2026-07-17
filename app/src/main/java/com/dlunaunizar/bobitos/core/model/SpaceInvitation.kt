package com.dlunaunizar.bobitos.core.model

import java.time.Instant

data class SpaceInvitation(val id: String, val spaceId: String, val expiresAt: Instant, val status: InvitationStatus) {
    val code: String
        get() = id.chunked(CODE_GROUP_LENGTH).joinToString("-")

    val link: String
        get() = "bobitos://invite/$id"

    fun isExpired(now: Instant = Instant.now()): Boolean = !expiresAt.isAfter(now)

    private companion object {
        const val CODE_GROUP_LENGTH = 4
    }
}

enum class InvitationStatus {
    ACTIVE,
    USED,
    REVOKED,
}
