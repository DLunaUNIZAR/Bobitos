package com.dlunaunizar.bobitos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveMembersTest {
    @Test
    fun `keeps the active members in order with their membership names`() {
        val result = activeMembers(
            ids = listOf("ana", "luis"),
            records = listOf(
                MembershipRecord(userId = "ana", status = "ACTIVE", displayName = "Ana (espacio)"),
                MembershipRecord(userId = "luis", status = "ACTIVE", displayName = null),
            ),
        )

        assertEquals(listOf(ActiveMember("ana", "Ana (espacio)"), ActiveMember("luis", "")), result)
    }

    @Test
    fun `drops a missing, left or mismatched membership`() {
        val result = activeMembers(
            ids = listOf("ana", "luis", "eva", "leo"),
            records = listOf(
                null,
                MembershipRecord(userId = "luis", status = "LEFT", displayName = "Luis"),
                MembershipRecord(userId = "otra", status = "ACTIVE", displayName = "Eva"),
                MembershipRecord(userId = "leo", status = "ACTIVE", displayName = "Leo"),
            ),
        )

        assertEquals(listOf(ActiveMember("leo", "Leo")), result)
    }
}
