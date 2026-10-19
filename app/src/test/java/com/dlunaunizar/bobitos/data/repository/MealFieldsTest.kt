package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MealFieldsTest {
    private val me = AuthUser(id = "me", displayName = "Yo", email = "yo@example.com", isEmailVerified = true)
    private val date = LocalDate.of(2026, 10, 19)

    @Test
    fun `a participant who left is dropped with their name`() {
        val people = mealPeople(cookId = null, active = listOf(ActiveMember("a", "Ana"), ActiveMember("c", "Carla")))

        assertEquals(listOf("a", "c"), people.participantIds)
        assertEquals(listOf("Ana", "Carla"), people.participantNames)
    }

    @Test
    fun `a cook who left leaves the meal without cook`() {
        val people = mealPeople(cookId = "b", active = listOf(ActiveMember("a", "Ana")))

        assertEquals(listOf("a"), people.participantIds)
        assertNull(people.cookId)
        assertNull(people.cookName)
    }

    @Test
    fun `an active cook is kept with the name of their membership`() {
        val people = mealPeople(cookId = "b", active = listOf(ActiveMember("a", "Ana"), ActiveMember("b", "Berta")))

        assertEquals("b", people.cookId)
        assertEquals("Berta", people.cookName)
    }

    @Test
    fun `a restored meal keeps cooked and a new one is written not cooked`() {
        val people = mealPeople(cookId = "a", active = listOf(ActiveMember("a", "Ana")))

        val restored = mealData(me, date, MealSlot.COMIDA, "Lentejas", people, "r1", cooked = true)
        val added = mealData(me, date, MealSlot.COMIDA, "Lentejas", people, "r1", cooked = false)

        assertEquals(true, restored["cooked"])
        assertEquals(false, added["cooked"])
        assertEquals("me", added["createdBy"])
        assertEquals("a", added["cookId"])
        assertEquals("Ana", added["cookName"])
        assertEquals(listOf("a"), added["participantIds"])
    }
}
