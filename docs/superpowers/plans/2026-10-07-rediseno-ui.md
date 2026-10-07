# Rediseño de la interfaz de Bobitos: plan maestro + plan detallado de la Fase 1

> **Para quien ejecute:** usar superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans, tarea a tarea. Al aprobar, copiar este plan a `docs/superpowers/plans/2026-10-07-rediseno-ui.md` (en modo plan solo se puede escribir aquí).

**Goal:** que la app sea más atractiva e intuitiva, evolucionando la identidad actual (teal `#0F766E` + Nunito), sin cambiar datos ni reglas de Firestore.

**Architecture:** primero una base de diseño compartida (colores por módulo claro/oscuro, wrappers, identidad), luego navegación/hub, luego editores en bottom sheet y accesibilidad, luego módulos. Un PR por fase; cada fase compila, pasa tests y se puede publicar sola.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Nunito variable, JUnit (tests en `app/src/test`), ktlint + detekt.

**Spec:** decisiones acordadas en la conversación (evolucionar identidad; incluir navegación del espacio, acciones descubribles, editores en bottom sheet, estados vacíos y accesibilidad; un PR por fase). Memoria de diseño: `mvp-design-direction.md`.

## Global Constraints
- Compilar con `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`; verificación: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:ktlintCheck :app:detekt`.
- Dynamic color sigue desactivado; marca = teal `#0F766E` (`Color.kt`), fuente Nunito (`Type.kt`), formas `BobitosShapes` (8/12/16/24/32 dp), rejilla `Spacing` de 4.
- Textos de UI en español en `res/values/strings.xml`; sentence case; la acción conserva el mismo nombre en todo el flujo.
- Los nombres de enums guardados en Firestore (`EventColor`, tipos de tarea, `SportType`, etc.) NO se renombran ni cambian de orden: los tokens solo cambian tonos, nunca el dato persistido. `firestore.rules` no se toca.
- Táctil mínimo 48 dp; estado nunca solo por color; respetar `rememberReduceMotion()` (`core/designsystem/Motion.kt`).
- Commits en español con convención `tipo(área): …` y `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; ramas `agent/<tema>`; PR por fase.

## Review Focus (casos que el plan implica y las pruebas deben cubrir)
1. **Tema oscuro elegido en la app con el sistema en claro:** `values-night` solo sigue al sistema, así que ese caso seguiría con ventana clara al arrancar. Esperado: sin flash perceptible; si no se puede evitar con recursos, fijar el fondo de ventana desde `MainActivity` según `ThemeMode` (Tarea 4).
2. **Contraste de acentos de módulo en oscuro:** cada par acento/contenedor/sobre-contenedor ≥ 4,5:1 (texto) y acento/fondo ≥ 3:1 (icono). Lo fija el test de la Tarea 1.
3. **Fuente al 200 %:** el título de `BobitosTopBar` debe truncarse con elipsis sin tapar el botón atrás ni las acciones.
4. **Destinos sin color** (`moduleColor()` devuelve `null` para Spaces, catálogos, etc.): deben seguir neutros, sin crash.
5. **Descripciones de accesibilidad** del botón atrás y de las acciones de las barras no deben perderse en la migración mecánica.

## Fases (cada una tendrá su propio plan detallado al llegar a ella)
Las fases 2–5 se escriben como planes separados cuando la anterior esté fusionada: dependen de lo que aprendamos (p. ej. cómo se comportan los sheets con IME) y cada una produce software funcional por sí sola.

| Fase | Contenido | Dependencias |
|---|---|---|
| 1 | Fundamentos: `ModuleColors` claro/oscuro, `Elevation`, `BobitosTopBar`, identidad (icono, fondo de ventana, `values-night`), reubicar componentes genéricos | — |
| 2 | Navegación y hub: cabecera «Hoy» (único gesto audaz), contadores reales, selector de espacio visible, ajustes con icono propio, resolver duplicación hub/barra (propuesta «Hoy» + «Más»; **confirmar con el usuario al empezar**), `RecipeDetailDialog` → pantalla. Archivos: `core/navigation/BobitosNavHost.kt` (885 líneas; `MainMenuScreen` y `SpaceHomeScreen` viven aquí), `BobitosDestination.kt`, `feature/spaces/*` | 1 |
| 3 | Editores largos a `BobitosBottomSheet`, confirmaciones a `BobitosDialog`, swipe con `customActions` + alternativa visible, FAB en Comidas, selector de color accesible. Se crean aquí los wrappers que solo esta fase usa (sheet, dialog, fab). Dividir 3a Tasks/Shopping, 3b Calendar/Meals, 3c Recipes/Settings | 1 |
| 4 | Calendar, PersonalCalendar, Meals, Sport: `DayCell` (34 dp, `CalendarScreen.kt:431`) y `DayChip` a ≥ 48 dp, estados vacíos, migrar a `ModuleColors`/`Spacing` | 1, 3 |
| 5 | Tasks, Shopping, Recipes, Profile, SpaceSettings, Spaces: `BobitosCard`, cabeceras, estados «todo hecho», prioridad con icono/texto, eliminar Material directo restante, regla lint contra `.dp` crudos | 1–4 |

Reutilizar siempre: `component/StateViews.kt` (`EmptyState`/`LoadingState`/`ErrorState`), `SearchField`, `SwipeActionsBox`, `UndoSnackbar`, `AppDatePickerDialog`, `theme/CategoryTint.kt` (`categoryCardColors`).

---

# Fase 1: Fundamentos — plan detallado

Rutas bajo `app/src/main/java/com/dlunaunizar/bobitos/` (`DS` = `core/designsystem`). Tests en `app/src/test/java/com/dlunaunizar/bobitos/`.

**Mapa de archivos**
- Crear `DS/theme/ModuleColors.kt` (acentos por módulo claro/oscuro; independiente de la navegación).
- Crear `DS/theme/Elevation.kt`.
- Crear `DS/component/BobitosTopBar.kt`.
- Modificar `DS/theme/Theme.kt` (proveer `LocalDarkTheme`), `core/navigation/BobitosDestination.kt` (`moduleColor()` pasa a leer `ModuleColors`) y sus 4 usos en `BobitosNavHost.kt` (líneas 673, 803, 815, 823).
- Modificar `res/drawable/ic_launcher.xml`, `res/values/colors.xml`, `res/values/themes.xml`; crear `res/values-night/{colors,themes}.xml`.
- Mover `AuthAvatar`/`FullScreenLoading` (`feature/auth/AuthComponents.kt`) y `feature/common/SyncStatusBanner.kt` a `DS/component/`.
- Tests: `core/designsystem/theme/ModuleColorsTest.kt`.

### Tarea 1: `ModuleColors` con test de contraste
**Files:** Create `DS/theme/ModuleColors.kt`; Test `core/designsystem/theme/ModuleColorsTest.kt`.
**Interfaces — Produces:** `enum class AppModule { SHOPPING, TASKS, CALENDAR, MEALS, SPORT }`; `data class ModuleColors(val accent: Color, val container: Color, val onContainer: Color)`; `object ModulePalette { fun colors(module: AppModule, dark: Boolean): ModuleColors }`; `val LocalDarkTheme: ProvidableCompositionLocal<Boolean>`; `@Composable fun moduleColors(module: AppModule): ModuleColors`.

- [ ] **Paso 1: test que falla.** Helpers WCAG en el propio test:
```kotlin
package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleColorsTest {
    private fun channel(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    private fun luminance(c: Color) = 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun todosLosParesCumplenContraste() {
        val backgrounds = mapOf(false to Color(0xFFFBFAF8), true to Color(0xFF141513))
        for (dark in listOf(false, true)) {
            for (module in AppModule.entries) {
                val c = ModulePalette.colors(module, dark)
                assertTrue("$module dark=$dark texto sobre contenedor", contrast(c.onContainer, c.container) >= 4.5f)
                assertTrue("$module dark=$dark acento sobre fondo", contrast(c.accent, backgrounds.getValue(dark)) >= 3f)
            }
        }
    }
}
```
- [ ] **Paso 2:** `./gradlew :app:testDebugUnitTest --tests '*ModuleColorsTest'` → FALLA (no compila: `AppModule` no existe).
- [ ] **Paso 3: implementación.** Valores de partida (los claros son los actuales de `moduleColor()`; los oscuros son aclarados para fondo `#141513`):
```kotlin
package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppModule { SHOPPING, TASKS, CALENDAR, MEALS, SPORT }

@Immutable
data class ModuleColors(val accent: Color, val container: Color, val onContainer: Color)

val LocalDarkTheme = compositionLocalOf { false }

object ModulePalette {
    fun colors(module: AppModule, dark: Boolean): ModuleColors = if (dark) darkColors.getValue(module) else lightColors.getValue(module)

    private val lightColors = mapOf(
        AppModule.SHOPPING to ModuleColors(Color(0xFFC05621), Color(0xFFFBE3D4), Color(0xFF3B1A08)),
        AppModule.TASKS to ModuleColors(Color(0xFF7E57C2), Color(0xFFE8DEF8), Color(0xFF241046)),
        AppModule.CALENDAR to ModuleColors(Color(0xFF00897B), Color(0xFFD0F0EB), Color(0xFF00201C)),
        AppModule.MEALS to ModuleColors(Color(0xFFAD1457), Color(0xFFF9DCE8), Color(0xFF3E0721)),
        AppModule.SPORT to ModuleColors(Color(0xFF1565C0), Color(0xFFD6E6FA), Color(0xFF05213F)),
    )
    private val darkColors = mapOf(
        AppModule.SHOPPING to ModuleColors(Color(0xFFF2A173), Color(0xFF5A2A0E), Color(0xFFFBE3D4)),
        AppModule.TASKS to ModuleColors(Color(0xFFB9A0EA), Color(0xFF3D2A66), Color(0xFFE8DEF8)),
        AppModule.CALENDAR to ModuleColors(Color(0xFF55CDBF), Color(0xFF004D44), Color(0xFFD0F0EB)),
        AppModule.MEALS to ModuleColors(Color(0xFFF08DB5), Color(0xFF63123A), Color(0xFFF9DCE8)),
        AppModule.SPORT to ModuleColors(Color(0xFF7FB2F0), Color(0xFF173F6E), Color(0xFFD6E6FA)),
    )
}

@Composable
fun moduleColors(module: AppModule): ModuleColors = ModulePalette.colors(module, LocalDarkTheme.current)
```
- [ ] **Paso 4:** ejecutar el test. Si algún par falla, ajustar SOLO ese tono (aclarar acento oscuro / oscurecer contenedor) en pasos de ~8 % y repetir hasta pasar; no relajar los umbrales.
- [ ] **Paso 5: commit** `feat(diseño): colores de módulo con variante clara y oscura`.

### Tarea 2: conectar el tema y `moduleColor()`
**Files:** Modify `DS/theme/Theme.kt:87-95`, `core/navigation/BobitosDestination.kt:113-122`, `core/navigation/BobitosNavHost.kt:673,803,815,823`.
**Interfaces — Consumes:** `AppModule`, `moduleColors()`, `LocalDarkTheme`. **Produces:** `@Composable fun BobitosDestination.moduleColor(): Color?` (misma firma de uso, ahora composable).

- [ ] **Paso 1:** en `BobitosTheme`, envolver el contenido: `CompositionLocalProvider(LocalDarkTheme provides darkTheme) { MaterialTheme(...) { content() } }`.
- [ ] **Paso 2:** reemplazar `moduleColor()` por:
```kotlin
@Composable
fun BobitosDestination.moduleColor(): Color? = when (this) {
    BobitosDestination.Shopping -> moduleColors(AppModule.SHOPPING).accent
    BobitosDestination.Tasks -> moduleColors(AppModule.TASKS).accent
    BobitosDestination.Calendar -> moduleColors(AppModule.CALENDAR).accent
    BobitosDestination.Meals -> moduleColors(AppModule.MEALS).accent
    BobitosDestination.Sport -> moduleColors(AppModule.SPORT).accent
    else -> null
}
```
  y quitar el `import androidx.compose.ui.graphics.Color` si queda sin uso solo en lo no necesario (lo sigue usando el retorno).
- [ ] **Paso 3:** `./gradlew :app:compileDebugKotlin` (los 4 usos de `BobitosNavHost.kt` ya están dentro de composables; si alguno no lo está, hoistear la llamada). Esperado: compila.
- [ ] **Paso 4:** comprobar en emulador el hub y la barra inferior en claro y en oscuro (capturas). Esperado: en claro idéntico a antes; en oscuro, acentos más claros.
- [ ] **Paso 5: commit** `refactor(diseño): moduleColor() lee la paleta por módulo según el tema`.

### Tarea 3: escala de elevación y `BobitosTopBar`
**Files:** Create `DS/theme/Elevation.kt`, `DS/component/BobitosTopBar.kt`; Modify los 8 archivos con `TopAppBar` (3 en `BobitosNavHost.kt:586,638,749`; localizar el resto con `grep -rln "TopAppBar(" app/src/main`).
**Interfaces — Produces:** `object Elevation { val none; val level1; val level2 }`; `@Composable fun BobitosTopBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})`.

- [ ] **Paso 1:** `grep -rn "ArrowBack" -B3 -A3 app/src/main` y anotar el `stringResource` usado hoy como `contentDescription` del botón atrás (16 usos); se reutiliza tal cual. Anotar también si alguna barra usa `scrollBehavior` o `colors` propios (esas se parametrizan o quedan fuera).
- [ ] **Paso 2:** crear `Elevation.kt` (`none = 0.dp`, `level1 = 1.dp`, `level2 = 3.dp`) y `BobitosTopBar` sobre `TopAppBar`: título con `maxLines = 1` + `TextOverflow.Ellipsis` (Review Focus 3), botón atrás con `Icons.AutoMirrored.Rounded.ArrowBack` y la descripción anotada, `actions` pasado tal cual.
- [ ] **Paso 3:** migrar archivo a archivo (un commit por 2–3 archivos); en cada uno compilar. Esperado: aspecto sin cambios.
- [ ] **Paso 4:** probar con fuente al 200 % una pantalla con título largo y varias acciones. Esperado: título truncado, acciones visibles.
- [ ] **Paso 5: commit** `refactor(diseño): BobitosTopBar compartida y escala de elevación`.

### Tarea 4: identidad (icono y fondo de ventana)
**Files:** Modify `res/drawable/ic_launcher.xml`, `res/values/colors.xml`, `res/values/themes.xml`; Create `res/values-night/colors.xml`, `res/values-night/themes.xml`; Modify `MainActivity.kt` solo si el Review Focus 1 no se resuelve con recursos.

- [ ] **Paso 1:** `ic_launcher.xml`: cambiar `#5C5AA7` por `#0F766E` (la «B» blanca se mantiene; contraste ≈ 5,5:1).
- [ ] **Paso 2:** `values/colors.xml`: `bobitos_window` → `#FFFBFAF8` (igual que `backgroundLight`). Crear `values-night/colors.xml` con `bobitos_window` = `#FF141513` (igual que el fondo oscuro).
- [ ] **Paso 3:** `values-night/themes.xml`: mismo estilo `Theme.Bobitos` con padre `android:style/Theme.Material.NoActionBar` y `android:windowLightStatusBar` = `false`.
- [ ] **Paso 4:** probar arranque en frío con (a) sistema oscuro + app «Sistema», (b) sistema claro + app «Oscuro», (c) sistema oscuro + app «Claro». En (b) y (c) el tema del sistema no coincide: si se ve un destello, leer `ThemeMode` y fijar `window.setBackgroundDrawable(ColorDrawable(...))` en `MainActivity` en cuanto se conozca. Esperado: sin destello perceptible en los tres casos.
- [ ] **Paso 5: commit** `fix(diseño): icono de marca en teal y fondo de ventana sin destello`.

### Tarea 5: reubicar componentes genéricos
**Files:** Move `AuthAvatar`, `FullScreenLoading` (`feature/auth/AuthComponents.kt:103,120`) y `feature/common/SyncStatusBanner.kt` a `DS/component/`.

- [ ] **Paso 1:** `grep -rn "FullScreenLoading\|AuthAvatar\|SyncStatusBanner" app/src` para listar usos.
- [ ] **Paso 2:** mover las declaraciones (paquete `core.designsystem.component`), reemplazar `FullScreenLoading()` por `LoadingState(Modifier.fillMaxSize())` solo si el resultado visual es idéntico; si no, conservarlo y mover tal cual. Actualizar imports.
- [ ] **Paso 3:** `./gradlew :app:compileDebugKotlin :app:ktlintCheck :app:detekt`. Esperado: sin errores.
- [ ] **Paso 4: commit** `refactor(diseño): componentes genéricos al design system`.

### Cierre de la Fase 1
- [ ] `./gradlew :app:testDebugUnitTest :app:ktlintCheck :app:detekt` en verde; capturas claro/oscuro de Home, SpaceHome y una lista; recorrido rápido con TalkBack de una pantalla con `BobitosTopBar`.
- [ ] PR «Fase 1: fundamentos del rediseño» con las capturas; después, beta si se quiere probar en dispositivo (`npm run beta:distribute`, `docs/BETA_DISTRIBUTION.md`).

## Autorrevisión
- **Cobertura:** cada decisión del usuario tiene fase (navegación → 2; acciones descubribles y bottom sheet → 3; vacíos y accesibilidad → 4–5; identidad → 1; fases con PR propio → estructura).
- **Sin placeholders en la Fase 1:** los valores de color son de partida y están protegidos por el test de contraste; las fases 2–5 son deliberadamente solo de alcance hasta que se detallen.
- **Consistencia de tipos:** `AppModule`, `ModuleColors`, `ModulePalette.colors`, `moduleColors`, `LocalDarkTheme`, `BobitosTopBar`, `Elevation` se usan con los mismos nombres en todas las tareas.
- **Cambio respecto al plan anterior:** los wrappers de sheet, dialog, FAB, card y botones pasan a crearse en la fase que los usa (3 y 5), no todos en la 1, para no dejar código sin consumidor.
