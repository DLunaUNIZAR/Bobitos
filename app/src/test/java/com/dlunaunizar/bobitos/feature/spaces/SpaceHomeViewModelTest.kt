package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CurrentDayProvider
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SportActivityRepository
import com.dlunaunizar.bobitos.data.repository.TaskFailure
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepositoryException
import com.dlunaunizar.bobitos.feature.tasks.TaskUiMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceHomeViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val me = "me"
    private val members = mutableMapOf<String, MutableSharedFlow<List<SpaceMember>>>()
    private val tasks = mutableMapOf<String, MutableSharedFlow<List<TaskItem>>>()
    private val meals = mutableMapOf<String, MutableSharedFlow<List<Meal>>>()
    private val events = mutableMapOf<String, MutableSharedFlow<List<CalendarEvent>>>()
    private val activities = mutableMapOf<String, MutableSharedFlow<List<SportActivity>>>()
    private val shopping = mutableMapOf<String, MutableSharedFlow<List<ShoppingItem>>>()

    // Escuchas abiertas ahora mismo en las seis fuentes.
    private var active = 0
    private var failTasksOf: String? = null
    private var completionGate: CompletableDeferred<Unit>? = null
    private var failCompletion = false
    private var failRestoration: Throwable? = null
    private val completions = mutableListOf<Triple<String, String, Boolean>>()
    private val restorations = mutableListOf<Pair<String, TaskItem>>()

    private fun <T> MutableMap<String, MutableSharedFlow<T>>.source(space: String) =
        getOrPut(space) { MutableSharedFlow(replay = 1) }

    // Lo que ve el ViewModel: la fuente del espacio, contando las suscripciones activas.
    private fun <T> MutableMap<String, MutableSharedFlow<T>>.of(space: String): Flow<T> =
        source(space).onStart { active++ }.onCompletion { active-- }

    private val fakeToday = MutableStateFlow(LocalDate.now())

    // Perezoso: su viewModelScope debe crearse con el dispatcher de la regla ya puesto.
    private val viewModel by lazy {
        SpaceHomeViewModel(
            taskRepository = object : TaskRepository by NoTasks {
                override fun tasks(spaceId: String): Flow<List<TaskItem>> =
                    if (spaceId == failTasksOf) flow { throw IOException("sin conexión") } else tasks.of(spaceId)

                override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean) {
                    completions += Triple(spaceId, taskId, completed)
                    completionGate?.await()
                    if (failCompletion) throw IOException("sin conexión")
                }

                override suspend fun restoreCompletion(spaceId: String, previous: TaskItem) {
                    failRestoration?.let { throw it }
                    restorations += spaceId to previous
                }
            },
            mealRepository = object : MealRepository by NoMeals {
                override fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate) =
                    meals.of(spaceId)
            },
            calendarRepository = object : CalendarRepository by NoEvents {
                override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant) =
                    events.of(spaceId)
            },
            sportRepository = object : SportActivityRepository by stubOf() {
                override fun activities(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate) =
                    activities.of(spaceId)
            },
            shoppingRepository = object : ShoppingRepository by stubOf() {
                override fun items(spaceId: String) = shopping.of(spaceId)
            },
            spaceRepository = object : SpaceRepository by NoSpaces {
                override fun members(spaceId: String): Flow<List<SpaceMember>> = members.of(spaceId)
            },
            dayProvider = object : CurrentDayProvider() {
                override fun today(zone: ZoneId): Flow<LocalDate> = fakeToday
            },
        )
    }

    private fun emitAll(space: String, taskList: List<TaskItem> = emptyList()) {
        members.source(space).tryEmit(listOf(SpaceMember(me, "Yo", SpaceRole.OWNER)))
        tasks.source(space).tryEmit(taskList)
        meals.source(space).tryEmit(emptyList())
        events.source(space).tryEmit(emptyList())
        activities.source(space).tryEmit(emptyList())
        shopping.source(space).tryEmit(emptyList())
    }

    @Test
    fun soloEscuchaMientrasSeRecoge() = runTest {
        emitAll("a")
        viewModel.show("a", me)
        assertEquals(0, active)

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.agenda.collect {} }
        runCurrent()
        assertEquals(6, active)

        collector.cancel()
        advanceTimeBy(4_000)
        assertEquals(6, active)
        advanceTimeBy(1_100)
        assertEquals(0, active)
    }

    @Test
    fun cambiarDeEspacioNoMezclaDatos() = runTest {
        val seen = mutableListOf<UiState<TodayAgenda>>()
        val collector =
            launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.agenda.collect { seen += it.state } }
        emitAll("a", listOf(taskDueToday("de-a")))
        viewModel.show("a", me)
        runCurrent()
        assertEquals(listOf("de-a"), (seen.last() as UiState.Content).value.tasks.map { it.task.id })

        viewModel.show("b", me)
        runCurrent()
        assertEquals(UiState.Loading, seen.last())

        val switchedAt = seen.size
        emitAll("b", listOf(taskDueToday("de-b")))
        runCurrent()
        val after = seen.drop(switchedAt)
        assertTrue(after.isNotEmpty())
        after.forEach { state ->
            assertEquals(listOf("de-b"), (state as UiState.Content).value.tasks.map { it.task.id })
        }
        collector.cancel()
    }

    @Test
    fun completarDesdeHoyOfreceDeshacerYDeshacerLaDevuelve() = runTest {
        viewModel.show("a", me)
        val task = taskDueToday("t1")
        var offered: TaskItem? = null

        viewModel.complete(task) { offered = it }
        runCurrent()

        assertEquals(listOf(Triple("a", "t1", true)), completions)
        assertEquals(task, offered)

        viewModel.undoCompletion(offered!!)
        runCurrent()

        assertEquals(listOf("a" to task), restorations)
    }

    @Test
    fun deshacerUnaTareaQueOtroCambioAvisa() = runTest {
        viewModel.show("a", me)
        failRestoration = TaskRepositoryException(TaskFailure.TaskChanged)

        viewModel.undoCompletion(taskDueToday("t1"))
        runCurrent()

        assertEquals(TaskUiMessage.TaskChanged, viewModel.error.value)
        assertTrue(restorations.isEmpty())
    }

    @Test
    fun volverTrasMasDeCincoSegundosNoPasaPorCargando() = runTest {
        emitAll("a", listOf(taskDueToday("de-a")))
        viewModel.show("a", me)
        val first = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.agenda.collect {} }
        runCurrent()
        first.cancel()
        advanceTimeBy(6_000)
        assertEquals(0, active)

        // Hoy -> Tareas -> Hoy: se conserva lo último y se vuelve a escuchar sin pasar por «Cargando».
        val seen = mutableListOf<UiState<TodayAgenda>>()
        val second = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.agenda.collect { seen += it.state } }
        runCurrent()

        assertEquals(6, active)
        assertTrue(seen.isNotEmpty())
        seen.forEach { assertTrue("pasó por $it", it is UiState.Content) }
        second.cancel()
    }

    @Test
    fun reintentarSiVuelveAPasarPorCargando() = runTest {
        emitAll("a")
        viewModel.show("a", me)
        val seen = mutableListOf<UiState<TodayAgenda>>()
        val collector =
            launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.agenda.collect { seen += it.state } }
        runCurrent()
        val before = seen.size

        viewModel.retry()
        runCurrent()

        assertEquals(UiState.Loading, seen.drop(before).first())
        collector.cancel()
    }

    @Test
    fun laAgendaSabeDeQueEspacioEs() = runTest {
        emitAll("a", listOf(taskDueToday("de-a")))
        viewModel.show("a", me)
        val seen = mutableListOf<UiState<TodayAgenda>>()
        val collector =
            launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.agenda.collect { seen += it.state } }
        runCurrent()

        assertEquals("a", (seen.last() as UiState.Content).value.spaceId)
        collector.cancel()
    }

    @Test
    fun unaAgendaDeOtroEspacioSeMuestraComoCargando() {
        val deA = ScopedAgenda(
            "a",
            UiState.Content(
                TodayAgenda(emptyList(), 0, emptyList(), ShoppingGlance(0, emptyList()), emptyList(), spaceId = "a"),
            ),
        )

        assertEquals(deA.state, deA.forSpace("a"))
        assertEquals(UiState.Loading, deA.forSpace("b"))
        assertEquals(UiState.Loading, deA.forSpace(null))
        assertEquals(UiState.Loading, ScopedAgenda("a", UiState.Loading).forSpace("b"))
    }

    @Test
    fun unErrorDeOtroEspacioSeMuestraComoCargando() {
        val errorDeA = ScopedAgenda("a", UiState.Error(FailureKind.OFFLINE))

        assertEquals(UiState.Error(FailureKind.OFFLINE), errorDeA.forSpace("a"))
        assertEquals(UiState.Loading, errorDeA.forSpace("b"))
    }

    @Test
    fun completarLaMismaTareaDosVecesMientrasEscribeSoloEscribeUna() = runTest {
        viewModel.show("a", me)
        completionGate = CompletableDeferred()
        val task = taskDueToday("t1")
        val offered = mutableListOf<TaskItem>()

        viewModel.complete(task) { offered += it }
        viewModel.complete(task) { offered += it }
        runCurrent()
        completionGate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf(Triple("a", "t1", true)), completions)
        assertEquals(listOf(task), offered)

        // Ya terminó: se puede volver a completar (por ejemplo, una repetición del día siguiente).
        completionGate = null
        viewModel.complete(task) { offered += it }
        runCurrent()
        assertEquals(2, completions.size)
    }

    @Test
    fun completarSinEscribirDevuelveFalseYNoTocaElRepositorio() = runTest {
        val task = taskDueToday("t1")
        // Sin espacio fijado no hay a dónde escribir.
        assertFalse(viewModel.complete(task) {})

        viewModel.show("a", me)
        completionGate = CompletableDeferred()
        assertTrue(viewModel.complete(task) {})
        // Con una escritura en curso para esa tarea, el segundo toque no escribe ni lo dice como lanzado.
        assertFalse(viewModel.complete(task) {})
        runCurrent()

        assertEquals(listOf(Triple("a", "t1", true)), completions)
    }

    @Test
    fun unaEscrituraCanceladaLiberaLaTareaYAvisaDeQueNoTermino() = runTest {
        viewModel.show("a", me)
        val gate = CompletableDeferred<Unit>()
        completionGate = gate
        val task = taskDueToday("t1")
        val resultados = mutableListOf<Boolean>()

        assertTrue(viewModel.complete(task, { resultados += it }) {})
        runCurrent()
        gate.cancel()
        runCurrent()

        assertEquals(listOf(false), resultados)
        completionGate = null
        assertTrue(viewModel.complete(task, { resultados += it }) {})
        runCurrent()
        // La primera escritura llegó a empezar; la segunda, ya libre, también.
        assertEquals(listOf(Triple("a", "t1", true), Triple("a", "t1", true)), completions)
    }

    @Test
    fun completarAvisaDeSuResultadoYSiFallaNoOfreceDeshacer() = runTest {
        viewModel.show("a", me)
        failCompletion = true
        val resultados = mutableListOf<Boolean>()
        val offered = mutableListOf<TaskItem>()

        viewModel.complete(taskDueToday("t1"), { resultados += it }) { offered += it }
        runCurrent()

        assertEquals(listOf(false), resultados)
        assertTrue(offered.isEmpty())
    }

    @Test
    fun unFalloDaErrorYReintentarVuelveASuscribir() = runTest {
        emitAll("bad")
        failTasksOf = "bad"
        viewModel.show("bad", me)
        val seen = mutableListOf<UiState<TodayAgenda>>()
        val collector =
            launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.agenda.collect { seen += it.state } }
        runCurrent()
        assertEquals(UiState.Error(FailureKind.OFFLINE), seen.last())
        assertEquals("bad", viewModel.agenda.value.spaceId)

        failTasksOf = null
        viewModel.retry()
        runCurrent()

        assertTrue(seen.last() is UiState.Content)
        collector.cancel()
    }

    private fun taskDueToday(id: String): TaskItem {
        val zone = ZoneId.systemDefault()
        return TaskItem(
            id = id,
            title = id,
            description = null,
            assigneeId = me,
            assigneeName = "Yo",
            dueAt = LocalDate.now().atStartOfDay(zone).toInstant(),
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.TODO,
            createdBy = me,
            createdByName = "Yo",
            createdAt = Instant.EPOCH,
            updatedBy = me,
            updatedAt = Instant.EPOCH,
            completedBy = null,
            completedByName = null,
            completedAt = null,
        )
    }
}

// Repositorio que falla en cualquier método que el test no sobrescribe.
private inline fun <reified T : Any> stubOf(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { _, method, _ -> error("no usado: ${method.name}") } as T

// Los fakes solo implementan la lectura que usa el ViewModel; el resto no se llama.
private object NoTasks : TaskRepository {
    override fun tasks(spaceId: String): Flow<List<TaskItem>> = error("no usado")
    override suspend fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: com.dlunaunizar.bobitos.core.model.TaskType?,
        recurrence: com.dlunaunizar.bobitos.core.model.TaskRecurrence?,
        startAt: Instant?,
    ): String = error("no usado")

    override suspend fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: com.dlunaunizar.bobitos.core.model.TaskType?,
        recurrence: com.dlunaunizar.bobitos.core.model.TaskRecurrence?,
        startAt: Instant?,
    ): Unit = error("no usado")

    override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean): Unit = error("no usado")
    override suspend fun deleteTask(spaceId: String, taskId: String): Unit = error("no usado")
    override suspend fun restoreTask(spaceId: String, task: TaskItem): Unit = error("no usado")
    override suspend fun restoreCompletion(spaceId: String, previous: TaskItem): Unit = error("no usado")
}

private object NoMeals : MealRepository {
    override fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate): Flow<List<Meal>> {
        error("no usado")
    }

    override suspend fun addMeal(
        spaceId: String,
        date: LocalDate,
        slot: com.dlunaunizar.bobitos.core.model.MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ): String = error("no usado")

    override suspend fun updateMeal(
        spaceId: String,
        mealId: String,
        date: LocalDate,
        slot: com.dlunaunizar.bobitos.core.model.MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ): Unit = error("no usado")

    override suspend fun setCooked(spaceId: String, mealId: String, cooked: Boolean): Unit = error("no usado")
    override suspend fun restoreMeal(spaceId: String, meal: Meal): Unit = error("no usado")
    override suspend fun deleteMeal(spaceId: String, mealId: String): Unit = error("no usado")
}

private object NoEvents : CalendarRepository {
    override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>> {
        error("no usado")
    }

    override suspend fun createEvent(spaceId: String, input: EventInput, eventId: String?): String = error("no usado")
    override suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput): Unit = error("no usado")
    override suspend fun deleteEvent(spaceId: String, eventId: String): Unit = error("no usado")
}

private object NoSpaces : SpaceRepository {
    override fun spaces(): Flow<List<SpaceSummary>> = error("no usado")
    override fun space(spaceId: String): Flow<SpaceSummary?> = error("no usado")
    override fun members(spaceId: String): Flow<List<SpaceMember>> = error("no usado")
    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> = error("no usado")
    override suspend fun createSpace(name: String): String = error("no usado")
    override suspend fun renameSpace(spaceId: String, name: String): Unit = error("no usado")
    override suspend fun leaveSpace(spaceId: String): Unit = error("no usado")
    override suspend fun removeMember(spaceId: String, userId: String): Unit = error("no usado")
    override suspend fun transferOwnership(spaceId: String, newOwnerId: String): Unit = error("no usado")
    override suspend fun deleteSpace(spaceId: String): Unit = error("no usado")
    override suspend fun createInvitation(spaceId: String): SpaceInvitation = error("no usado")
    override suspend fun revokeInvitation(invitationId: String): Unit = error("no usado")
    override suspend fun acceptInvitation(code: String): String = error("no usado")
}
