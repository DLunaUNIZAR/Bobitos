package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoField

// Resumen del hub: «Mi día» (D1) para el usuario + reparto de tareas por miembro (D2).
data class SpaceHomeDigest(
    val myTasksToday: List<TaskItem>,
    val myCookingToday: List<Meal>,
    val myEatingToday: List<Meal>,
    val myEventsToday: List<CalendarEvent>,
    val workload: List<MemberWorkload>,
) {
    // Cierto si «Mi día» no tiene nada que mostrar (para ocultar el bloque).
    val myDayEmpty: Boolean
        get() = myTasksToday.isEmpty() &&
            myCookingToday.isEmpty() &&
            myEatingToday.isEmpty() &&
            myEventsToday.isEmpty()
}

// Carga de un miembro: pendientes asignadas y completadas esta semana.
data class MemberWorkload(val userId: String, val displayName: String, val pending: Int, val doneThisWeek: Int)

/**
 * Construye el resumen a partir de listas YA leídas (una snapshot cada una): las [meals] y [events]
 * vienen acotados a hoy; [tasks] es la lista del espacio (tope 250). Todo se agrega en cliente, sin
 * consultas extra ni índices.
 */
fun buildHomeDigest(
    userId: String,
    today: LocalDate,
    zone: ZoneId,
    tasks: List<TaskItem>,
    meals: List<Meal>,
    events: List<CalendarEvent>,
    members: List<SpaceMember>,
): SpaceHomeDigest {
    val weekStart = today.with(ChronoField.DAY_OF_WEEK, 1L).atStartOfDay(zone).toInstant()
    val myTasksToday = tasks.filter { task ->
        task.status == TaskStatus.TODO &&
            task.assigneeId == userId &&
            task.dueAt?.atZone(zone)?.toLocalDate() == today
    }
    val myCookingToday = meals.filter { it.cookId == userId }
    val myEatingToday = meals.filter { it.cookId != userId && userId in it.participantIds }
    val myEventsToday = events.filter { userId in it.participantIds }
    val workload = members.map { member ->
        MemberWorkload(
            userId = member.userId,
            displayName = member.displayName,
            pending = tasks.count { it.status == TaskStatus.TODO && it.assigneeId == member.userId },
            doneThisWeek = tasks.count {
                it.status == TaskStatus.DONE &&
                    it.completedBy == member.userId &&
                    (it.completedAt?.isBefore(weekStart) == false)
            },
        )
    }
    return SpaceHomeDigest(myTasksToday, myCookingToday, myEatingToday, myEventsToday, workload)
}
