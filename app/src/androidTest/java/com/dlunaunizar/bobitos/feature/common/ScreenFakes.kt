package com.dlunaunizar.bobitos.feature.common

import android.os.Handler
import android.os.Looper
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskDates
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModel
import com.dlunaunizar.bobitos.feature.tasks.TasksViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

// Repositorios falsos mínimos para montar las pantallas reales sin Hilt. Lo que un test no usa responde `error`;
// las lecturas (Flow) sin más respuesta salen vacías.
private object Unhandled

private inline fun <reified T : Any> fake(crossinline handler: (name: String, args: List<Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method: Method, args ->
        val list = args?.toList().orEmpty()
        when (val answer = handler(method.name, list)) {
            Unhandled -> when {
                Flow::class.java.isAssignableFrom(method.returnType) -> emptyFlow<Any>()
                method.name == "toString" -> T::class.java.simpleName
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === list.firstOrNull()
                else -> error("${method.name} no usado")
            }
            else -> answer
        }
    } as T

// Una escritura que tarda, como la de Firestore: la función suspendida (último argumento: su Continuation) se reanuda
// en el hilo principal pasado [WRITE_MILLIS]. Mientras tanto la escritura sigue en curso.
private const val WRITE_MILLIS = 400L

@Suppress("UNCHECKED_CAST")
private fun slowWrite(args: List<Any?>, result: Any?, onDone: () -> Unit): Any? {
    val continuation = args.last() as Continuation<Any?>
    Handler(Looper.getMainLooper()).postDelayed({
        onDone()
        continuation.resume(result)
    }, WRITE_MILLIS)
    return COROUTINE_SUSPENDED
}

/** Tareas falsas: la lista sale de [tasks] y completar una tarda y la deja en DONE al terminar. */
class FakeTasks(initial: List<TaskItem>) {
    val tasks = MutableStateFlow(initial)
    val completed = mutableListOf<String>()

    private val repository: TaskRepository = fake { name, args ->
        when (name) {
            "tasks" -> tasks
            "setCompleted" -> {
                val id = args[1] as String
                completed += id
                slowWrite(args, TaskDates(startAt = null, dueAt = null)) {
                    val done = { task: TaskItem -> task.copy(status = TaskStatus.DONE, completedAt = Instant.now()) }
                    tasks.update { list -> list.map { if (it.id == id) done(it) else it } }
                }
            }
            else -> Unhandled
        }
    }

    fun viewModel() = TasksViewModel(repository, fake<SpaceRepository> { _, _ -> Unhandled })
}

/** Compra falsa: la lista sale de [items] y comprar uno tarda y lo deja comprado al terminar. */
class FakeShopping(initial: List<ShoppingItem>) {
    val items = MutableStateFlow(initial)
    val purchased = mutableListOf<String>()

    private val repository: ShoppingRepository = fake { name, args ->
        when (name) {
            "items" -> items
            "setPurchased" -> {
                val id = args[1] as String
                purchased += id
                slowWrite(args, Unit) {
                    items.update { list -> list.map { if (it.id == id) it.copy(purchased = true) else it } }
                }
            }
            else -> Unhandled
        }
    }

    fun viewModel(): ShoppingViewModel {
        val ingredients = fake<IngredientRepository> { _, _ -> Unhandled }
        val personal = fake<PersonalIngredientRepository> { _, _ -> Unhandled }
        val source = IngredientShoppingSource(
            ingredients,
            personal,
            fake<IngredientBrandRepository> { _, _ -> Unhandled },
            fake<IngredientPrefsRepository> { _, _ -> Unhandled },
        )
        val pantry = fake<PantryRepository> { _, _ -> Unhandled }
        return ShoppingViewModel(repository, ingredients, personal, source, pantry)
    }
}
