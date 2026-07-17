package com.dlunaunizar.bobitos.core.model

data class SpaceSummary(val id: String, val name: String, val memberCount: Int, val role: SpaceRole)

enum class SpaceRole {
    OWNER,
    MEMBER,
}

data class SpaceMember(val userId: String, val displayName: String, val role: SpaceRole)
