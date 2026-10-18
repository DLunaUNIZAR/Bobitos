package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoField
import com.dlunaunizar.bobitos.core.model.Meal as MealModel

/** Tareas que caben en «Tareas para hoy»; el resto se cuenta en [TodayAgenda.hiddenTasks]. */
const val AGENDA_MAX_TASKS = 5

private const val SHOPPING_GLANCE_NAMES = 3

/** Lo de hoy en toda la casa: tareas, agenda (eventos, deporte y comidas), compra pendiente y reparto. */
data class TodayAgenda(
    val tasks: List<AgendaTask>,
    val hiddenTasks: Int,
    val entries: List<AgendaEntry>,
    val shopping: ShoppingGlance,
    val workload: List<MemberWorkload>,
    /** De qué espacio es esta agenda. */
    val spaceId: String,
)

/**
 * Lo que debe pintar la pantalla de [spaceId]: una agenda que es de otro espacio (el que acaba de dejarse atrás, hasta
 * que el ViewModel empieza a leer el nuevo) se ve como `Loading`, nunca bajo el nombre del espacio nuevo.
 */
fun UiState<TodayAgenda>.forSpace(spaceId: String?): UiState<TodayAgenda> =
    if (this is UiState.Content && value.spaceId != spaceId) UiState.Loading else this

/** Una tarea de la agenda; [overdueSince] es el día en que venció si ya venció, y null si vence hoy. */
data class AgendaTask(val task: TaskItem, val overdueSince: LocalDate?, val owner: AgendaPerson)

sealed interface AgendaPerson {
    data object You : AgendaPerson

    data class Member(val name: String) : AgendaPerson

    data object Nobody : AgendaPerson
}

sealed interface AgendaTime {
    data object AllDay : AgendaTime

    data object NoTime : AgendaTime

    // Evento con hora que empezó antes de hoy y sigue hoy.
    data object Continues : AgendaTime

    data class At(val time: LocalTime) : AgendaTime

    data class Slot(val slot: MealSlot) : AgendaTime
}

sealed interface AgendaEntry {
    val time: AgendaTime

    data class Event(val event: CalendarEvent, override val time: AgendaTime, val people: List<AgendaPerson>) :
        AgendaEntry

    data class Activity(val activity: SportActivity, override val time: AgendaTime, val people: List<AgendaPerson>) :
        AgendaEntry

    // [meal] null es una franja sin planificar.
    data class Meal(val slot: MealSlot, val meal: MealModel?, val cook: AgendaPerson?, val eaters: Int) : AgendaEntry {
        override val time: AgendaTime = AgendaTime.Slot(slot)
    }
}

/** La compra pendiente: cuántos productos y los primeros nombres, en el orden de la lista. */
data class ShoppingGlance(val pending: Int, val firstNames: List<String>)

// Carga de un miembro: pendientes asignadas y completadas esta semana.
data class MemberWorkload(val userId: String, val displayName: String, val pending: Int, val doneThisWeek: Int)

/**
 * Construye la agenda de hoy a partir de listas YA leídas (una snapshot cada una): [meals], [events] y
 * [activities] vienen acotados a hoy; [tasks] es la lista del espacio (tope 250) y [shopping] la de la compra. Todo
 * se agrega en cliente, sin consultas extra ni índices.
 */
fun buildTodayAgenda(
    spaceId: String,
    userId: String,
    today: LocalDate,
    zone: ZoneId,
    tasks: List<TaskItem>,
    meals: List<MealModel>,
    events: List<CalendarEvent>,
    activities: List<SportActivity>,
    shopping: List<ShoppingItem>,
    members: List<SpaceMember>,
): TodayAgenda {
    val dueTasks = tasks
        .filter { it.isPendingDueTodayOrEarlier(today, zone) }
        // Las vencidas van antes que las de hoy porque su fecha es anterior.
        .sortedWith(compareBy<TaskItem>({ it.dueAt }, { it.title }))
        .map { task ->
            val dueDay = requireNotNull(task.dueAt).atZone(zone).toLocalDate()
            AgendaTask(task, overdueSince = dueDay.takeIf { it < today }, owner = ownerOf(task, userId))
        }
    return TodayAgenda(
        tasks = dueTasks.take(AGENDA_MAX_TASKS),
        hiddenTasks = (dueTasks.size - AGENDA_MAX_TASKS).coerceAtLeast(0),
        entries = agendaEntries(userId, today, zone, meals, events, activities),
        shopping = shopping.filterNot { it.purchased }.let { pending ->
            ShoppingGlance(pending.size, pending.take(SHOPPING_GLANCE_NAMES).map { it.name })
        },
        workload = workloadOf(today, zone, tasks, members),
        spaceId = spaceId,
    )
}

/**
 * Ids de las tareas pendientes que vencen hoy o antes. Una que se repite y se ha adelantado más allá de hoy al
 * completarla ya no cuenta. Sirve para saber cuándo se ha dejado «todo hecho por hoy».
 */
fun pendingDueTodayOrEarlier(tasks: List<TaskItem>, today: LocalDate, zone: ZoneId): Set<String> =
    tasks.filter { it.isPendingDueTodayOrEarlier(today, zone) }.map { it.id }.toSet()

private fun TaskItem.isPendingDueTodayOrEarlier(today: LocalDate, zone: ZoneId): Boolean =
    status == TaskStatus.TODO && dueAt?.atZone(zone)?.toLocalDate()?.let { it <= today } == true

private fun ownerOf(task: TaskItem, userId: String): AgendaPerson = when {
    task.assigneeId == null -> AgendaPerson.Nobody
    task.assigneeId == userId -> AgendaPerson.You
    // Aunque ya no esté en el espacio: se usa el nombre guardado en la tarea.
    else -> AgendaPerson.Member(task.assigneeName.orEmpty())
}

private fun peopleOf(userId: String, ids: List<String>, names: List<String>): List<AgendaPerson> =
    ids.mapIndexed { index, id ->
        if (id == userId) AgendaPerson.You else AgendaPerson.Member(names.getOrNull(index).orEmpty())
    }

private fun agendaEntries(
    userId: String,
    today: LocalDate,
    zone: ZoneId,
    meals: List<MealModel>,
    events: List<CalendarEvent>,
    activities: List<SportActivity>,
): List<AgendaEntry> {
    val eventsById = events.associateBy { it.id }
    // El evento enlazado de una actividad sale una sola vez, como actividad.
    val linkedEventIds = activities.mapNotNull { it.eventId }.toSet()
    val eventEntries = events.filter { it.id !in linkedEventIds }.map { event ->
        val time = when {
            event.allDay -> AgendaTime.AllDay
            event.startAt.atZone(zone).toLocalDate() < today -> AgendaTime.Continues
            else -> AgendaTime.At(event.startAt.atZone(zone).toLocalTime())
        }
        AgendaEntry.Event(event, time, peopleOf(userId, event.participantIds, event.participantNames))
    }
    val activityEntries = activities.map { activity ->
        val linked = activity.eventId?.let(eventsById::get)
        val time = if (linked != null && !linked.allDay) {
            AgendaTime.At(linked.startAt.atZone(zone).toLocalTime())
        } else {
            AgendaTime.NoTime
        }
        AgendaEntry.Activity(activity, time, peopleOf(userId, activity.participantIds, activity.participantNames))
    }
    val todayMeals = meals.filter { it.date == today }
    val mealEntries = MealSlot.entries.flatMap { slot ->
        val inSlot = todayMeals.filter { it.slot == slot }
        if (inSlot.isEmpty()) {
            listOf(AgendaEntry.Meal(slot, meal = null, cook = null, eaters = 0))
        } else {
            inSlot.map { AgendaEntry.Meal(slot, it, cookOf(it, userId), it.participantIds.size) }
        }
    }
    return (eventEntries + activityEntries + mealEntries).sortedWith(
        compareBy<AgendaEntry>({ groupRank(it.time) }, { sortTime(it.time) }, { kindRank(it) }, { sortTitle(it) }),
    )
}

private fun cookOf(meal: MealModel, userId: String): AgendaPerson? = when {
    meal.cookId == null -> null
    meal.cookId == userId -> AgendaPerson.You
    else -> meal.cookName?.let { AgendaPerson.Member(it) }
}

// Primero todo el día, después sin hora, después lo que sigue de ayer y al final lo que tiene hora.
private fun groupRank(time: AgendaTime): Int = when (time) {
    AgendaTime.AllDay -> 0
    AgendaTime.NoTime -> 1
    AgendaTime.Continues -> 2
    is AgendaTime.At, is AgendaTime.Slot -> 3
}

// Las franjas cuentan como las 08:00, las 14:00 y las 21:00.
private fun sortTime(time: AgendaTime): LocalTime = when (time) {
    is AgendaTime.At -> time.time
    is AgendaTime.Slot -> when (time.slot) {
        MealSlot.DESAYUNO -> LocalTime.of(8, 0)
        MealSlot.COMIDA -> LocalTime.of(14, 0)
        MealSlot.CENA -> LocalTime.of(21, 0)
    }
    else -> LocalTime.MIN
}

// A la misma hora: primero la comida, luego el evento y luego la actividad.
private fun kindRank(entry: AgendaEntry): Int = when (entry) {
    is AgendaEntry.Meal -> 0
    is AgendaEntry.Event -> 1
    is AgendaEntry.Activity -> 2
}

private fun sortTitle(entry: AgendaEntry): String = when (entry) {
    is AgendaEntry.Meal -> entry.meal?.name.orEmpty()
    is AgendaEntry.Event -> entry.event.title
    is AgendaEntry.Activity -> entry.activity.name
}

private fun workloadOf(today: LocalDate, zone: ZoneId, tasks: List<TaskItem>, members: List<SpaceMember>) =
    today.with(ChronoField.DAY_OF_WEEK, 1L).atStartOfDay(zone).toInstant().let { weekStart ->
        members.map { member ->
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
    }
