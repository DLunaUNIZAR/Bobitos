package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SpaceHomeDigestTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 25) // sábado
    private val me = "me"
    private val members = listOf(
        SpaceMember(me, "Yo", SpaceRole.OWNER),
        SpaceMember("leo", "Leo", SpaceRole.MEMBER),
    )

    @Test
    fun `my day keeps only my items due today`() {
        val tasks = listOf(
            task("t1", me, TaskStatus.TODO, due = today),
            task("t2", me, TaskStatus.TODO, due = today.plusDays(1)), // mañana
            task("t3", "leo", TaskStatus.TODO, due = today), // de otro
        )
        val meals = listOf(
            meal("m1", cook = me, participants = listOf(me)), // cocino
            meal("m2", cook = "leo", participants = listOf("leo", me)), // como
            meal("m3", cook = "leo", participants = listOf("leo")), // ni cocino ni como
        )

        val digest = buildHomeDigest(me, today, zone, tasks, meals, emptyList(), members)

        assertEquals(listOf("t1"), digest.myTasksToday.map(TaskItem::id))
        assertEquals(listOf("m1"), digest.myCookingToday.map(Meal::id))
        assertEquals(listOf("m2"), digest.myEatingToday.map(Meal::id))
    }

    @Test
    fun `workload counts pending and done this week per member`() {
        val thisWeek = today.atStartOfDay(zone).toInstant()
        val lastWeek = today.minusDays(9).atStartOfDay(zone).toInstant()
        val tasks = listOf(
            task("p1", me, TaskStatus.TODO, due = null),
            task("p2", me, TaskStatus.TODO, due = null),
            task("d1", me, TaskStatus.DONE, due = null, completedBy = me, completedAt = thisWeek),
            task("old", me, TaskStatus.DONE, due = null, completedBy = me, completedAt = lastWeek),
            task("p3", "leo", TaskStatus.TODO, due = null),
        )

        val workload = buildHomeDigest(me, today, zone, tasks, emptyList(), emptyList(), members).workload

        val mine = workload.first { it.userId == me }
        assertEquals(2, mine.pending)
        assertEquals(1, mine.doneThisWeek) // «old» quedó fuera de la semana
        val leo = workload.first { it.userId == "leo" }
        assertEquals(1, leo.pending)
        assertEquals(0, leo.doneThisWeek)
    }
}

private fun task(
    id: String,
    assigneeId: String?,
    status: TaskStatus,
    due: LocalDate?,
    completedBy: String? = null,
    completedAt: Instant? = null,
) = TaskItem(
    id = id,
    title = id,
    description = null,
    assigneeId = assigneeId,
    assigneeName = assigneeId,
    dueAt = due?.atStartOfDay(ZoneId.of("UTC"))?.toInstant(),
    priority = TaskPriority.MEDIUM,
    status = status,
    createdBy = "owner",
    createdByName = "Owner",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
    completedBy = completedBy,
    completedByName = completedBy,
    completedAt = completedAt,
)

private fun meal(id: String, cook: String?, participants: List<String>) = Meal(
    id = id,
    date = LocalDate.of(2026, 7, 25),
    slot = MealSlot.COMIDA,
    name = id,
    participantIds = participants,
    participantNames = participants,
    cookId = cook,
    cookName = cook,
    createdBy = "owner",
    createdByName = "Owner",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
)
