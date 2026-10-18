package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TodayAgendaTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 17) // sábado
    private val me = "me"
    private val members = listOf(
        SpaceMember(me, "Yo", SpaceRole.OWNER),
        SpaceMember("leo", "Leo", SpaceRole.MEMBER),
    )

    private fun build(
        tasks: List<TaskItem> = emptyList(),
        meals: List<Meal> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
        activities: List<SportActivity> = emptyList(),
        shopping: List<ShoppingItem> = emptyList(),
    ) = buildTodayAgenda("casa", me, today, zone, tasks, meals, events, activities, shopping, members)

    @Test
    fun tareasVencidasPrimeroYLuegoLasDeHoy() {
        val agenda = build(
            tasks = listOf(
                task("hoy-tarde", due = at(today, 18)),
                task("ayer", due = at(today.minusDays(1), 10)),
                task("hoy-b", due = at(today, 9)),
                task("hoy-a", due = at(today, 9)),
                task("hace-una-semana", due = at(today.minusDays(7), 10)),
                task("mañana", due = at(today.plusDays(1), 10)),
                task("hecha", due = at(today, 8), status = TaskStatus.DONE),
                task("sin-fecha", due = null),
            ),
        )

        assertEquals(
            listOf("hace-una-semana", "ayer", "hoy-a", "hoy-b", "hoy-tarde"),
            agenda.tasks.map { it.task.id },
        )
        assertEquals(
            listOf(today.minusDays(7), today.minusDays(1), null, null, null),
            agenda.tasks.map { it.overdueSince },
        )
    }

    @Test
    fun tareasDeCualquieraIncluidasSinAsignarYLasTuyasComoTu() {
        val agenda = build(
            tasks = listOf(
                task("mia", due = at(today, 8), assignee = me),
                task("de-leo", due = at(today, 9), assignee = "leo", assigneeName = "Leo"),
                task("libre", due = at(today, 10)),
            ),
        )

        assertEquals(
            listOf(AgendaPerson.You, AgendaPerson.Member("Leo"), AgendaPerson.Nobody),
            agenda.tasks.map { it.owner },
        )
    }

    @Test
    fun unaTareaDeUnExMiembroMuestraSuNombreGuardado() {
        val agenda = build(
            tasks = listOf(task("huerfana", due = at(today, 8), assignee = "ex", assigneeName = "Marta")),
        )

        assertEquals(AgendaPerson.Member("Marta"), agenda.tasks.single().owner)
    }

    @Test
    fun masDeCincoTareasDejaLasDemasOcultas() {
        val agenda = build(tasks = (1..8).map { task("t$it", due = at(today, it)) })

        assertEquals(AGENDA_MAX_TASKS, agenda.tasks.size)
        assertEquals(3, agenda.hiddenTasks)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), agenda.tasks.map { it.task.id })
    }

    @Test
    fun eventoDeTodoElDiaVaPrimero() {
        val agenda = build(
            events = listOf(event("con-hora", at(today, 9)), event("fiesta", allDay = true)),
        )

        val first = agenda.entries.first() as AgendaEntry.Event
        assertEquals("fiesta", first.event.id)
        assertEquals(AgendaTime.AllDay, first.time)
    }

    @Test
    fun eventoEmpezadoAyerSigueHoy() {
        val agenda = build(
            events = listOf(
                event("viaje", at(today.minusDays(1), 20), end = at(today, 12)),
                event("desayuno-tarde", at(today, 7)),
            ),
        )

        // «Sigue hoy» va antes de lo que tiene hora, aunque empezara antes.
        val ordered = agenda.entries.filterIsInstance<AgendaEntry.Event>()
        assertEquals(listOf("viaje", "desayuno-tarde"), ordered.map { it.event.id })
        assertEquals(AgendaTime.Continues, ordered.first().time)
        assertEquals(agenda.entries.indexOf(ordered.first()) < agenda.entries.indexOf(ordered.last()), true)
    }

    @Test
    fun eventoConHora() {
        val agenda = build(events = listOf(event("cita", at(today, 17, 30), people = listOf(me, "leo"))))

        val entry = agenda.entries.filterIsInstance<AgendaEntry.Event>().single()
        assertEquals(AgendaTime.At(LocalTime.of(17, 30)), entry.time)
        assertEquals(listOf(AgendaPerson.You, AgendaPerson.Member("leo")), entry.people)
    }

    @Test
    fun actividadConEventoEnlazadoNoSeDuplica() {
        val agenda = build(
            events = listOf(event("ev", allDay = true)),
            activities = listOf(activity("pádel", eventId = "ev")),
        )

        assertEquals(0, agenda.entries.count { it is AgendaEntry.Event })
        assertEquals(1, agenda.entries.count { it is AgendaEntry.Activity })
    }

    @Test
    fun actividadConEventoDeTodoElDiaVaSinHora() {
        val agenda = build(
            events = listOf(event("ev", allDay = true)),
            activities = listOf(activity("pádel", eventId = "ev")),
        )

        assertEquals(AgendaTime.NoTime, agenda.entries.filterIsInstance<AgendaEntry.Activity>().single().time)
    }

    @Test
    fun actividadConEventoConHoraVaASuHora() {
        val agenda = build(
            events = listOf(event("ev", at(today, 19, 15))),
            activities = listOf(activity("pádel", eventId = "ev")),
        )

        assertEquals(
            AgendaTime.At(LocalTime.of(19, 15)),
            agenda.entries.filterIsInstance<AgendaEntry.Activity>().single().time,
        )
    }

    @Test
    fun actividadSinEventoVaSinHora() {
        val agenda = build(activities = listOf(activity("correr", eventId = null)))

        assertEquals(AgendaTime.NoTime, agenda.entries.filterIsInstance<AgendaEntry.Activity>().single().time)
    }

    @Test
    fun franjasPlanificadasYSinPlanificarOrdenadasPorHora() {
        val agenda = build(
            meals = listOf(meal("cena", MealSlot.CENA, cook = me, participants = listOf(me, "leo", "ana"))),
            events = listOf(event("cita", at(today, 14)), event("tarde", at(today, 16))),
            activities = listOf(activity("yoga", eventId = null)),
        )

        val labels = agenda.entries.map {
            when (it) {
                is AgendaEntry.Meal -> "meal:${it.slot}:${it.meal?.id}"
                is AgendaEntry.Event -> "event:${it.event.id}"
                is AgendaEntry.Activity -> "activity:${it.activity.id}"
            }
        }
        assertEquals(
            listOf(
                "activity:yoga",
                "meal:DESAYUNO:null",
                "meal:COMIDA:null", // a las 14:00 gana la comida al evento
                "event:cita",
                "event:tarde",
                "meal:CENA:cena",
            ),
            labels,
        )
        val cena = agenda.entries.last() as AgendaEntry.Meal
        assertEquals(AgendaPerson.You, cena.cook)
        assertEquals(3, cena.eaters)
        assertEquals(AgendaTime.Slot(MealSlot.CENA), cena.time)
        assertNull((agenda.entries[1] as AgendaEntry.Meal).cook)
    }

    @Test
    fun compraConCeroDosYCincoPendientes() {
        assertEquals(ShoppingGlance(0, emptyList()), build().shopping)

        val two = build(shopping = listOf(item("Leche"), item("Pan"), item("Hecho", purchased = true)))
        assertEquals(ShoppingGlance(2, listOf("Leche", "Pan")), two.shopping)

        val five = build(shopping = listOf("a", "b", "c", "d", "e").map { item(it) })
        assertEquals(ShoppingGlance(5, listOf("a", "b", "c")), five.shopping)
    }

    @Test
    fun diaVacioTieneSoloLasTresFranjasSinPlanificar() {
        val agenda = build()

        assertEquals(0, agenda.tasks.size)
        assertEquals(0, agenda.hiddenTasks)
        assertEquals(
            listOf(MealSlot.DESAYUNO, MealSlot.COMIDA, MealSlot.CENA),
            agenda.entries.map { (it as AgendaEntry.Meal).also { meal -> assertNull(meal.meal) }.slot },
        )
    }

    @Test
    fun elRepartoSeCalculaComoAntes() {
        val thisWeek = today.atStartOfDay(zone).toInstant()
        val lastWeek = today.minusDays(9).atStartOfDay(zone).toInstant()
        val agenda = build(
            tasks = listOf(
                task("p1", due = null, assignee = me),
                task("p2", due = null, assignee = me),
                task(
                    "d1",
                    due = null,
                    assignee = me,
                    status = TaskStatus.DONE,
                    completedBy = me,
                    completedAt = thisWeek,
                ),
                task(
                    "old",
                    due = null,
                    assignee = me,
                    status = TaskStatus.DONE,
                    completedBy = me,
                    completedAt = lastWeek,
                ),
                task("p3", due = null, assignee = "leo"),
            ),
        )

        val mine = agenda.workload.first { it.userId == me }
        assertEquals(2, mine.pending)
        assertEquals(1, mine.doneThisWeek)
        val leo = agenda.workload.first { it.userId == "leo" }
        assertEquals(1, leo.pending)
        assertEquals(0, leo.doneThisWeek)
    }

    @Test
    fun unaRecurrenteAdelantadaNoCuentaComoPendienteDeHoy() {
        val diaria = TaskRecurrence(RecurrenceUnit.DAY, 1)
        val tareas = listOf(
            task("vencida", due = at(today.minusDays(1), 10)),
            task("hoy", due = at(today, 9)).copy(recurrence = diaria),
            task("mañana", due = at(today.plusDays(1), 9)),
            task("hecha", due = at(today, 8), status = TaskStatus.DONE),
            task("sin-fecha", due = null),
        )

        assertEquals(setOf("vencida", "hoy"), pendingDueTodayOrEarlier(tareas, today, zone))
        // Al completar la recurrente de hoy, su vencimiento pasa a mañana y sale del conjunto.
        val adelantada = tareas.map { if (it.id == "hoy") it.copy(dueAt = at(today.plusDays(1), 9)) else it }
        assertEquals(setOf("vencida"), pendingDueTodayOrEarlier(adelantada, today, zone))
    }

    @Test
    fun unNombreVacioNoDejaHuecos() {
        val agenda = build(
            tasks = listOf(
                task("sin-nombre", due = at(today, 8), assignee = "ex", assigneeName = ""),
                task("con-id", due = at(today, 9), assignee = "leo", assigneeName = ""),
            ),
            events = listOf(
                event("e", start = at(today, 10), people = listOf(me, "leo")).copy(
                    participantIds = listOf("ana", "otra"),
                    participantNames = listOf("Ana", ""),
                ),
            ),
            meals = listOf(meal("m", MealSlot.COMIDA, cook = "ex", participants = emptyList()).copy(cookName = "")),
        )

        assertEquals(listOf(AgendaPerson.Unknown, AgendaPerson.Member("Leo")), agenda.tasks.map { it.owner })
        assertEquals(
            listOf(AgendaPerson.Member("Ana")),
            agenda.entries.filterIsInstance<AgendaEntry.Event>().single().people,
        )
        assertNull(agenda.entries.filterIsInstance<AgendaEntry.Meal>().single { it.meal != null }.cook)
    }

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant =
        day.atTime(hour, minute).atZone(zone).toInstant()

    private fun task(
        id: String,
        due: Instant?,
        assignee: String? = null,
        assigneeName: String? = assignee,
        status: TaskStatus = TaskStatus.TODO,
        completedBy: String? = null,
        completedAt: Instant? = null,
    ) = TaskItem(
        id = id,
        title = id,
        description = null,
        assigneeId = assignee,
        assigneeName = assigneeName,
        dueAt = due,
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

    private fun event(
        id: String,
        start: Instant = Instant.EPOCH,
        end: Instant = start.plusSeconds(3600),
        allDay: Boolean = false,
        people: List<String> = emptyList(),
    ) = CalendarEvent(
        id = id,
        title = id,
        description = null,
        allDay = allDay,
        startAt = if (allDay) today.atStartOfDay(zone).toInstant() else start,
        endAt = if (allDay) today.plusDays(1).atStartOfDay(zone).toInstant() else end,
        startDate = if (allDay) today else null,
        endDateExclusive = if (allDay) today.plusDays(1) else null,
        timeZone = "UTC",
        color = EventColor.BLUE,
        participantIds = people,
        participantNames = people,
        createdBy = "owner",
        createdByName = "Owner",
        createdAt = Instant.EPOCH,
        updatedBy = "owner",
        updatedAt = Instant.EPOCH,
    )

    private fun activity(id: String, eventId: String?) = SportActivity(
        id = id,
        date = today,
        type = SportType.OTROS,
        name = id,
        participantIds = listOf(me),
        participantNames = listOf("Yo"),
        eventId = eventId,
        createdBy = "owner",
        createdByName = "Owner",
        createdAt = Instant.EPOCH,
        updatedBy = "owner",
        updatedAt = Instant.EPOCH,
    )

    private fun meal(id: String, slot: MealSlot, cook: String?, participants: List<String>) = Meal(
        id = id,
        date = today,
        slot = slot,
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

    private fun item(name: String, purchased: Boolean = false) = ShoppingItem(
        id = name,
        name = name,
        quantity = null,
        notes = null,
        purchased = purchased,
        createdBy = "owner",
        createdByName = "Owner",
        createdAt = Instant.EPOCH,
        updatedBy = "owner",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
    )
}
