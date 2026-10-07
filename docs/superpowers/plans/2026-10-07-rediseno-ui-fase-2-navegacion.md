# Rediseño UI · Fase 2: navegación y hub — plan detallado

> **Para quien ejecute:** usar superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans, tarea a tarea. Pasos con casillas `- [ ]`.

**Goal:** que moverse por un espacio sea evidente: «Hoy» pasa a ser una pestaña (con cabecera viva), los módulos secundarios se agrupan en «Más», y cambiar de espacio y gestionar el espacio dejan de estar escondidos en un menú ⋮.

**Architecture:** el hub deja de ser una pantalla aparte con tarjetas duplicadas de la barra inferior. Todas las pantallas del espacio comparten un `WorkspaceScaffold` (barra superior con chip del espacio + barra inferior de 5 pestañas: Hoy, Calendario, Tareas, Compra, Más). La lógica de «qué pestaña está activa» es una función pura y testeada. Se conserva el ancla `SpaceHome` en la pila (`popUpTo(SpaceHome){saveState}`), por lo que los deep links y el flujo del calendario personal no cambian.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, Hilt (`SpaceHomeViewModel` sin cambios), JUnit.

**Spec:** decisiones del usuario (2026-10-07): pestañas «Hoy + Calendario + Tareas + Compra + Más» (Más = Comidas, Deporte, Notas); cambio de espacio con chip en la barra superior y icono de ajustes propio, sin menú ⋮. Plan maestro: `docs/superpowers/plans/2026-10-07-rediseno-ui.md`.

## Global Constraints
- Los del plan maestro (JAVA_HOME del JBR, `compileDebugKotlin testDebugUnitTest ktlintCheck detekt`, textos en `strings.xml` en español y sentence case, táctil ≥ 48 dp, `rememberReduceMotion()`, commits `tipo(área): …` con `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`).
- Rama: `agent/ui-fase2-navegacion` desde `main`. Sin cambios en `firestore.rules`, repositorios ni ViewModels.
- Rutas existentes que no cambian: `space-home`, `calendar`, `tasks`, `shopping`, `meals`, `sport`, `notes`, `calendar-event/{eventId}/{date}`, `space-settings`, `spaces`, `home`. Solo se añade `space-more`.

## Fuera de alcance (decidido tras explorar el código)
- **Insignias con contadores en las pestañas:** `SpaceHomeViewModel.load()` hace una lectura puntual y se cachea por entrada de pila; en una barra siempre visible se quedarían desfasadas al editar dentro de un módulo. Los contadores van en fichas dentro de «Hoy», que se recarga al entrar.
- **Barra inferior en los catálogos globales** (Recetario, Ingredientes, Ejercicios, Rutinas): también se abren desde Home sin espacio, así que no pueden llevar la barra del espacio.
- **`RecipeDetailDialog` → pantalla:** se hace en la Fase 3c junto al resto de editores de `RecipesScreen`.
- **Selector de espacio en bottom sheet:** el chip lleva a la pantalla `Spaces` actual (lista con crear y unirse). El sheet llega con `BobitosBottomSheet` en la Fase 3.

## Review Focus
1. **Atrás desde Comidas/Deporte abiertos desde «Más»** vuelve a «Más»; atrás desde «Más» vuelve a «Hoy»; atrás desde «Hoy» va al menú principal (como hoy).
2. **Tocar «Más» estando en Comidas o Deporte** vuelve a «Más» sin apilar otra copia.
3. **Evento del calendario personal** (`calendar-event/…`): resalta «Calendario» y, al cambiar de pestaña, se comporta como hoy (ancla `SpaceHome`).
4. **Digest/contadores aún nulos** (carga o sin conexión): «Hoy» muestra solo saludo y fecha, sin fichas ni crash.
5. **Fuente al 200 %:** cinco etiquetas en la barra (la más larga, «Calendario»): una línea con elipsis, sin romper la barra.
6. **Cambio de espacio:** tras elegir otro espacio, «Hoy» muestra los datos del nuevo y no los del anterior.
7. **TalkBack:** el chip se anuncia como botón «Cambiar de espacio», con el nombre del espacio; las pestañas indican cuál está seleccionada.
8. **Usuario sin nombre** (`displayName` vacío): el saludo no queda con coma colgando.

## Mapa de archivos (rutas bajo `app/src/main/java/com/dlunaunizar/bobitos/`)
- Modificar `core/navigation/BobitosDestination.kt`: añadir `SpaceMore`, `workspaceTabs`, `workspaceTabFor()`, mover `CALENDAR_EVENT_ROUTE` aquí.
- Crear `core/navigation/WorkspaceScaffold.kt`: scaffold común (barra superior con chip, barra inferior de pestañas). Sale de `BobitosNavHost.kt` (hoy líneas 621–731).
- Modificar `core/navigation/BobitosNavHost.kt`: usar el scaffold en `SpaceHome`/`SpaceMore`, helper de pestañas, `protectedRoutes`; retirar `SpaceActions`, `SpaceHomeScreen`.
- Crear `feature/spaces/SpaceTodayScreen.kt` («Hoy»: cabecera + fichas + `MyDayCard` + `WorkloadSection`) y `feature/spaces/TodayGreeting.kt` (lógica pura).
- Crear `feature/spaces/SpaceMoreScreen.kt` («Más»).
- Crear `feature/spaces/ModuleCard.kt`: `SpaceHomeCard`/`SpaceHomeCountBadge` movidas desde `BobitosNavHost.kt` (las sigue usando `MainMenuScreen`).
- Modificar `res/values/strings.xml`.
- Tests (`app/src/test/java/com/dlunaunizar/bobitos/`): `core/navigation/WorkspaceTabsTest.kt`, `feature/spaces/TodayGreetingTest.kt`.

---

### Task 1: modelo de pestañas (puro y testeado)
**Files:** Modify `core/navigation/BobitosDestination.kt`; Test `core/navigation/WorkspaceTabsTest.kt`.
**Interfaces — Produces:** `BobitosDestination.SpaceMore`; `BobitosDestination.workspaceTabs: List<BobitosDestination>`; `fun workspaceTabFor(route: String?): BobitosDestination?`; `internal const val CALENDAR_EVENT_ROUTE`.

- [ ] **Paso 1: test que falla**
```kotlin
package com.dlunaunizar.bobitos.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceTabsTest {
    @Test
    fun laBarraTieneLasCincoPestanasEnOrden() {
        assertEquals(
            listOf(
                BobitosDestination.SpaceHome,
                BobitosDestination.Calendar,
                BobitosDestination.Tasks,
                BobitosDestination.Shopping,
                BobitosDestination.SpaceMore,
            ),
            BobitosDestination.workspaceTabs,
        )
    }

    @Test
    fun cadaRutaDelEspacioResaltaSuPestana() {
        assertEquals(BobitosDestination.SpaceHome, workspaceTabFor("space-home"))
        assertEquals(BobitosDestination.Calendar, workspaceTabFor("calendar"))
        assertEquals(BobitosDestination.Calendar, workspaceTabFor(CALENDAR_EVENT_ROUTE))
        assertEquals(BobitosDestination.Tasks, workspaceTabFor("tasks"))
        assertEquals(BobitosDestination.Shopping, workspaceTabFor("shopping"))
    }

    @Test
    fun comidasYDeporteResaltanMas() {
        assertEquals(BobitosDestination.SpaceMore, workspaceTabFor("space-more"))
        assertEquals(BobitosDestination.SpaceMore, workspaceTabFor("meals"))
        assertEquals(BobitosDestination.SpaceMore, workspaceTabFor("sport"))
    }

    @Test
    fun lasRutasFueraDelEspacioNoTienenPestana() {
        assertNull(workspaceTabFor(null))
        assertNull(workspaceTabFor("home"))
        assertNull(workspaceTabFor("recipes"))
        assertNull(workspaceTabFor("profile"))
    }
}
```
- [ ] **Paso 2:** `./gradlew :app:testDebugUnitTest --tests '*WorkspaceTabsTest'` → FALLA (no compila).
- [ ] **Paso 3: implementación** en `BobitosDestination.kt`. Añadir el valor al enum (entre `Sport` y `Notes`, o tras `SpaceHome`; el orden del enum no se usa para la barra):
```kotlin
    SpaceMore(
        route = "space-more",
        titleRes = R.string.space_more_title,
        icon = Icons.Rounded.MoreHoriz,
    ),
```
  y en el `companion object` y a nivel de archivo:
```kotlin
        // Pestañas de la barra inferior dentro de un espacio. Comidas y Deporte cuelgan de «Más».
        val workspaceTabs = listOf(SpaceHome, Calendar, Tasks, Shopping, SpaceMore)
```
```kotlin
internal const val CALENDAR_EVENT_ROUTE = "calendar-event/{eventId}/{date}"

/** Pestaña que se resalta para la ruta actual, o null si la ruta no es una pantalla del espacio. */
fun workspaceTabFor(route: String?): BobitosDestination? = when (route) {
    BobitosDestination.SpaceHome.route -> BobitosDestination.SpaceHome
    BobitosDestination.Calendar.route, CALENDAR_EVENT_ROUTE -> BobitosDestination.Calendar
    BobitosDestination.Tasks.route -> BobitosDestination.Tasks
    BobitosDestination.Shopping.route -> BobitosDestination.Shopping
    BobitosDestination.SpaceMore.route,
    BobitosDestination.Meals.route,
    BobitosDestination.Sport.route,
    -> BobitosDestination.SpaceMore
    else -> null
}
```
  Añadir `import androidx.compose.material.icons.rounded.MoreHoriz`. Quitar `private const val CALENDAR_EVENT_ROUTE` de `BobitosNavHost.kt:883`. La cadena `R.string.space_more_title` se añade en la Tarea 5; para compilar ya, añadir en `strings.xml` `<string name="space_more_title">Más</string>`.
- [ ] **Paso 4:** ejecutar el test → PASA. `./gradlew :app:compileDebugKotlin` → compila.
- [ ] **Paso 5: commit** `feat(nav): modelo de pestañas del espacio con «Más»`.

### Task 2: saludo y resumen de «Hoy» (lógica pura)
**Files:** Create `feature/spaces/TodayGreeting.kt`; Test `feature/spaces/TodayGreetingTest.kt`.
**Interfaces — Produces:** `enum class DayPeriod { MORNING, AFTERNOON, EVENING }`; `fun dayPeriodFor(hour: Int): DayPeriod`; `fun SpaceHomeDigest.todayItemCount(): Int`; `fun firstName(displayName: String): String`.

- [ ] **Paso 1: test que falla**
```kotlin
package com.dlunaunizar.bobitos.feature.spaces

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayGreetingTest {
    @Test
    fun elSaludoDependeDeLaHora() {
        assertEquals(DayPeriod.MORNING, dayPeriodFor(5))
        assertEquals(DayPeriod.MORNING, dayPeriodFor(11))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodFor(12))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodFor(19))
        assertEquals(DayPeriod.EVENING, dayPeriodFor(20))
        assertEquals(DayPeriod.EVENING, dayPeriodFor(3))
    }

    @Test
    fun elNombreEsLaPrimeraPalabra() {
        assertEquals("David", firstName("David Luna"))
        assertEquals("Ana", firstName("  Ana  "))
        assertEquals("", firstName("   "))
    }

    @Test
    fun cuentaLosElementosDeHoy() {
        val empty = SpaceHomeDigest(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(0, empty.todayItemCount())
    }
}
```
- [ ] **Paso 2:** ejecutar → FALLA (no compila).
- [ ] **Paso 3: implementación**
```kotlin
package com.dlunaunizar.bobitos.feature.spaces

enum class DayPeriod { MORNING, AFTERNOON, EVENING }

fun dayPeriodFor(hour: Int): DayPeriod = when (hour) {
    in 5..11 -> DayPeriod.MORNING
    in 12..19 -> DayPeriod.AFTERNOON
    else -> DayPeriod.EVENING
}

fun firstName(displayName: String): String = displayName.trim().substringBefore(' ')

/** Tareas, comidas y eventos de hoy del usuario, para el resumen de la cabecera. */
fun SpaceHomeDigest.todayItemCount(): Int =
    myTasksToday.size + myCookingToday.size + myEatingToday.size + myEventsToday.size
```
- [ ] **Paso 4:** ejecutar → PASA.
- [ ] **Paso 5: commit** `feat(hoy): saludo por franja del día y resumen de elementos de hoy`.

### Task 3: `WorkspaceScaffold` con chip del espacio y barra de 5 pestañas
**Files:** Create `core/navigation/WorkspaceScaffold.kt`; Modify `core/navigation/BobitosNavHost.kt` (retirar `WorkspaceScaffold` y `SpaceActions`, líneas 621–731).
**Interfaces — Consumes:** `workspaceTabs`, `moduleColor()`, `BobitosTopBar(titleContent, actions)`, `SyncStatusBanner`, `LocalSnackbarHostState`. **Produces:** `internal fun WorkspaceScaffold(selectedTab: BobitosDestination, screenTitle: String?, spaceName: String, onTabSelected: (BobitosDestination) -> Unit, onSwitchSpace: () -> Unit, onSpaceSettings: () -> Unit, onProfile: () -> Unit, syncStatus: SyncStatus, content: @Composable () -> Unit)`.

- [ ] **Paso 1:** mover `WorkspaceScaffold` a `WorkspaceScaffold.kt` conservando el `Scaffold`, el `SnackbarHost` y el `CompositionLocalProvider(LocalSnackbarHostState …)`. Cambiar la firma a la de arriba. `screenTitle` es el subtítulo de módulo (null en «Hoy» y «Más»).
- [ ] **Paso 2: título con chip.** En `titleContent`, una `Column` con:
```kotlin
Row(
    modifier = Modifier
        .clip(MaterialTheme.shapes.small)
        .clickable(
            onClickLabel = stringResource(R.string.change_space),
            role = Role.Button,
            onClick = onSwitchSpace,
        )
        .padding(vertical = Spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = spaceName,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
}
screenTitle?.let {
    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```
- [ ] **Paso 3: acciones.** Sustituir `SpaceActions` por dos `IconButton` de 48 dp: ajustes (`Icons.Rounded.Settings`, `contentDescription = stringResource(R.string.space_settings)`) y perfil (`Icons.Rounded.AccountCircle`, `R.string.profile_open`). No queda menú ⋮.
- [ ] **Paso 4: barra inferior.**
```kotlin
NavigationBar {
    BobitosDestination.workspaceTabs.forEach { tab ->
        val accent = tab.moduleColor()
        NavigationBarItem(
            selected = tab == selectedTab,
            onClick = { onTabSelected(tab) },
            icon = { Icon(tab.icon, contentDescription = null) },
            label = { Text(stringResource(tab.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = accent?.let {
                NavigationBarItemDefaults.colors(
                    selectedIconColor = it,
                    selectedTextColor = it,
                    indicatorColor = it.copy(alpha = 0.2f),
                )
            } ?: NavigationBarItemDefaults.colors(),
        )
    }
}
```
  «Hoy» (`SpaceHome`) y «Más» (`SpaceMore`) no tienen color de módulo (`moduleColor()` da null) y usan los colores por defecto. El título de `SpaceHome` en la barra es `R.string.space_today_title` («Hoy», Tarea 5).
- [ ] **Paso 5:** `./gradlew :app:compileDebugKotlin :app:ktlintCheck :app:detekt` (los `composable(...)` que usan el scaffold se ajustan en la Tarea 6; hasta entonces dejar compilando con una llamada temporal por pantalla que pase `selectedTab = workspaceTabFor(route)`). Esperado: compila.
- [ ] **Paso 6: commit** `refactor(nav): WorkspaceScaffold con chip del espacio y pestañas Hoy/Calendario/Tareas/Compra/Más`.

### Task 4: pantalla «Hoy»
**Files:** Create `feature/spaces/SpaceTodayScreen.kt`, `feature/spaces/ModuleCard.kt`; Modify `BobitosNavHost.kt` (mover `SpaceHomeCard`/`SpaceHomeCountBadge` a `ModuleCard.kt`).
**Interfaces — Consumes:** `SpaceHomeDigest`, `SpaceModuleCounts`, `dayPeriodFor`, `firstName`, `todayItemCount`, `MyDayCard`, `WorkloadSection`, `categoryCardColors`, `moduleColors`. **Produces:** `@Composable fun SpaceTodayScreen(displayName: String, counts: SpaceModuleCounts?, digest: SpaceHomeDigest?, onOpen: (BobitosDestination) -> Unit)`.

- [ ] **Paso 1:** contenido (columna con scroll, padding `Spacing.lg`, separación `Spacing.md`):
  1. **Cabecera (único gesto audaz):** `Card` con `containerColor = primaryContainer`, forma `MaterialTheme.shapes.extraLarge`; dentro, el saludo en `headlineLarge` (p. ej. «Buenos días, David»; si `firstName` está vacío, solo «Buenos días»), la fecha larga en español en `bodyLarge` (`DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)` con `Locale.getDefault()`, `LocalDate.now()`), y una línea de resumen solo si `digest != null`: `pluralStringResource(R.plurals.space_today_summary, n, n)` o `R.string.space_today_nothing` cuando `todayItemCount() == 0`.
  2. **Fichas de contadores** (cuadrícula 2 × 2, solo si `counts != null`): Compra (`pendingShopping`), Tareas (`pendingTasks`), Calendario (`upcomingEvents`), Comidas (`todayMeals`). Cada ficha es `Card(onClick)` con `categoryCardColors(moduleColor)`, el icono del módulo, el número en `headlineMedium` y el nombre en `labelLarge`; área ≥ 48 dp; el número y el nombre forman un único elemento semántico («3 Tareas»).
  3. `digest?.let { MyDayCard(it) }`.
  4. `digest?.workload?.let { WorkloadSection(it) }`.
- [ ] **Paso 2:** `ModuleCard.kt` recibe tal cual `SpaceHomeCard` y `SpaceHomeCountBadge` (con `internal`), sin cambiar su aspecto, porque `MainMenuScreen` las sigue usando.
- [ ] **Paso 3:** `./gradlew :app:compileDebugKotlin :app:ktlintCheck :app:detekt` → compila.
- [ ] **Paso 4: commit** `feat(hoy): cabecera viva con saludo, fichas de contadores y resumen de hoy`.

### Task 5: pantalla «Más» y textos
**Files:** Create `feature/spaces/SpaceMoreScreen.kt`; Modify `res/values/strings.xml`.
**Interfaces — Produces:** `@Composable fun SpaceMoreScreen(todayMeals: Int, onOpen: (BobitosDestination) -> Unit)`.

- [ ] **Paso 1:** `SpaceMoreScreen`: columna con scroll con tres `ModuleCard`: `Meals` (con `count = todayMeals`), `Sport` y `Notes`. `todayMeals` sale del mismo `SpaceHomeViewModel` si ya está cargado; si no, 0.
- [ ] **Paso 2: textos** (`strings.xml`; la clave `space_more_title` ya existe desde la Tarea 1):
```xml
<string name="space_today_title">Hoy</string>
<string name="space_today_greeting_morning">Buenos días</string>
<string name="space_today_greeting_afternoon">Buenas tardes</string>
<string name="space_today_greeting_evening">Buenas noches</string>
<string name="space_today_greeting_named">%1$s, %2$s</string>
<string name="space_today_nothing">Hoy no tienes nada pendiente</string>
<plurals name="space_today_summary">
    <item quantity="one">Tienes %1$d cosa para hoy</item>
    <item quantity="other">Tienes %1$d cosas para hoy</item>
</plurals>
```
  Quitar `space_home_subtitle` (ya no se usa); `more_options` solo si `grep -rn "more_options" app/src/main` ya no lo encuentra.
- [ ] **Paso 3:** compilar + ktlint + detekt. **Paso 4: commit** `feat(nav): pantalla «Más» y textos de Hoy`.

### Task 6: cableado en `BobitosNavHost`
**Files:** Modify `core/navigation/BobitosNavHost.kt`.
**Interfaces — Consumes:** todo lo anterior. **Produces:** helper `NavHostController.navigateToTab(tab)` y `NavHostController.openFromMore(destination)`.

- [ ] **Paso 1:** `protectedRoutes` incluye `BobitosDestination.SpaceMore.route`.
- [ ] **Paso 2: helpers**
```kotlin
private fun NavHostController.navigateToTab(tab: BobitosDestination) {
    when (tab) {
        // «Hoy» es el ancla de la pila: basta con volver a ella.
        BobitosDestination.SpaceHome -> popBackStack(BobitosDestination.SpaceHome.route, inclusive = false)
        // Estando en Comidas/Deporte, «Más» vuelve a su pantalla en vez de apilar otra.
        BobitosDestination.SpaceMore ->
            if (!popBackStack(BobitosDestination.SpaceMore.route, inclusive = false)) navigateToWorkspace(tab)
        else -> navigateToWorkspace(tab)
    }
}

// Comidas, Deporte y Notas se apilan sobre «Más» para que atrás vuelva a «Más».
private fun NavHostController.openFromMore(destination: BobitosDestination) {
    navigate(destination.route) { launchSingleTop = true }
}
```
- [ ] **Paso 3: rutas.** `SpaceHome` y `SpaceMore` usan `WorkspaceScaffold`; el `BackHandler { navigateToHome() }` de `SpaceHome` se conserva; `SpaceHomeViewModel` sigue en `SpaceHome` (se recarga al entrar, como hoy) y `SpaceMore` recibe `counts?.todayMeals`. Las rutas `Shopping`, `Tasks`, `Calendar`, `calendar-event`, `Meals`, `Sport` pasan `selectedTab = workspaceTabFor(route)` y `onTabSelected = navController::navigateToTab`. Para no repetir las 5 lambdas idénticas (`onSwitchSpace`, `onSpaceSettings`, `onProfile`), extraer un `@Composable` local `spaceScaffold(selectedTab, screenTitle, content)` dentro de `BobitosNavHost`.
- [ ] **Paso 4:** `Notes` se abre con `openFromMore`; `Meals`/`Sport` desde las fichas de «Hoy» y desde «Más» con `openFromMore`.
- [ ] **Paso 5:** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:ktlintCheck :app:detekt` → verde. **Paso 6: commit** `feat(nav): «Hoy» como pestaña, «Más» para Comidas/Deporte/Notas y chip de espacio`.

### Cierre de la Fase 2
- [ ] Recorrido manual en dispositivo (no hay emulador en esta máquina): abrir espacio → Hoy; cada pestaña; Más → Comidas → atrás (a Más) → atrás (a Hoy) → atrás (menú principal); Más → Notas → atrás; tocar «Más» estando en Deporte; chip → cambiar de espacio → Hoy del nuevo espacio; icono de ajustes; evento desde el calendario personal (resalta Calendario); fuente al 200 %; TalkBack en chip y pestañas; claro y oscuro.
- [ ] PR «Rediseño UI · Fase 2: navegación y hub» con capturas.

## Autorrevisión
- **Cobertura del plan maestro (Fase 2):** hero «Hoy» (T4), contadores reales (fichas, T4), selector de espacio visible (T3), ajustes con icono propio (T3), duplicación hub/barra resuelta (T1, T3, T4, T6). Lo que queda fuera está justificado arriba.
- **Placeholders:** ninguno; los pasos de UI describen estructura y estilos concretos y las piezas con lógica llevan test y código.
- **Consistencia de nombres:** `workspaceTabs`, `workspaceTabFor`, `SpaceMore`, `CALENDAR_EVENT_ROUTE`, `navigateToTab`, `openFromMore`, `SpaceTodayScreen`, `SpaceMoreScreen`, `ModuleCard`, `dayPeriodFor`, `firstName`, `todayItemCount` se usan igual en todas las tareas.
- **Riesgo principal:** la pila de navegación. Mitigado con la función pura testeada (T1) y el recorrido manual del cierre.
