# Tema oscuro por defecto — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan pequeño** (1 tarea): pasos 1, 2, 3, 5 y 6, sin `simplify`. La tarea la hace un subagente `sonnet`; la revisión final, `opus` (si falla, `fable`). Los pasos usan casillas (`- [ ]`).

**Objetivo:** que la app use el tema **oscuro** cuando el usuario no ha elegido ninguno, también en la ventana de arranque.

**Arquitectura:**
- El modo por defecto vive en un solo sitio (`DEFAULT_THEME_MODE`), y lo usan el repositorio de DataStore (cuando no hay valor guardado o no es válido) y el valor inicial del `ThemeViewModel`.
- La ventana previa a Compose (`themes.xml`) pasa a ser oscura, para que el caso por defecto no destelle en claro al arrancar.

**Stack:** Kotlin, Jetpack Compose, DataStore Preferences, Hilt, recursos XML de Android.

**Spec:** no hay documento de spec. El diseño se aprobó en la conversación el 11/10/2026:
- quien no haya elegido tema pasa a ver la app en oscuro; quien eligió Claro o Sistema lo conserva (es un valor guardado);
- el usuario eligió «Arranque también oscuro»: la ventana de arranque es oscura, y quien elija Claro verá un destello oscuro breve al abrir (el mismo coste que hoy pagan, al revés, quienes eligen Oscuro);
- revierte la decisión del PR #57 («tema claro por defecto»).

## Restricciones globales

- **Rutas:** `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`; `RES` = `app/src/main/res/`.
- **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
- Tests JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. Orden TDD: test primero, en rojo, después el código.
- detekt: extraer código antes que tocar el baseline. ktlint: sin imports sin usar.
- Prohibido: nada contra `bobitos-dev`, desplegar, distribuir betas, push.

## Review Focus

1. **Primer fotograma:** con el valor por defecto, del arranque al primer fotograma de Compose no debe verse nada claro (ventana, barra de estado, valor inicial del ViewModel antes de que DataStore responda).
2. **Barras del sistema:** con la ventana oscura, los iconos de la barra de estado y de navegación deben verse claros sobre oscuro antes y después de cargar Compose; con Claro elegido, oscuros sobre claro tras cargar.
3. **Valores guardados:** `LIGHT`, `DARK` y `SYSTEM` guardados se respetan; un valor desconocido o ausente cae en `DARK`.
4. **Sistema:** con `SYSTEM` elegido y el móvil en claro, la app se ve clara (solo el arranque es oscuro).

---

### Task 1: Oscuro como modo por defecto y ventana de arranque oscura

**Ficheros:**
- Modificar:
  - `APP/core/designsystem/theme/ThemeMode.kt`: KDoc (hoy dice «por defecto la app usa [LIGHT]»), `val DEFAULT_THEME_MODE = ThemeMode.DARK` e `internal fun parseThemeMode(stored: String?): ThemeMode` (nombre → modo; `null` o desconocido → `DEFAULT_THEME_MODE`).
  - `APP/data/repository/DataStoreThemePreferenceRepository.kt:19-23`: usar `parseThemeMode`.
  - `APP/data/repository/ThemePreferenceRepository.kt:7`: el comentario dice «Por defecto [ThemeMode.LIGHT]».
  - `APP/feature/auth/ThemeViewModel.kt:23`: `initialValue = DEFAULT_THEME_MODE`.
  - `APP/MainActivity.kt:57-58`: el comentario dice que la ventana previa «sigue al sistema»; ahora es oscura y `setBackgroundDrawable` la alinea si el tema elegido es otro.
  - `RES/values/themes.xml`: padre `android:style/Theme.Material.NoActionBar` (oscuro) y `android:windowLightStatusBar` a `false`.
  - `RES/values/colors.xml`: `bobitos_window` pasa a `#FF141513` (el `backgroundDark` de `core/designsystem/theme/Color.kt:62`), con el comentario «fondo del tema oscuro».
  - `PROJECT_PLAN.md`: una fila en la tabla de decisiones (tras la del 11/10/2026 del catálogo de ingredientes): `| 11/10/2026 | Tema oscuro por defecto | Quien no ha elegido tema ve la app en oscuro, también en la ventana de arranque; Claro y Sistema siguen disponibles en Perfil y se respetan si ya estaban elegidos. Revierte la decisión del PR #57 (claro por defecto). Quien elige Claro ve un destello oscuro breve al abrir |`
- Crear:
  - `UT/core/designsystem/theme/ThemeModeTest.kt`
  - `UT/feature/auth/ThemeViewModelTest.kt`

**Interfaces:**
- **Produce:** `val DEFAULT_THEME_MODE: ThemeMode` (público, en `core/designsystem/theme`) e `internal fun parseThemeMode(stored: String?): ThemeMode`.

- [ ] **Paso 1: tests en rojo.**
  - `ThemeModeTest`:
    - `a missing preference defaults to dark`: `parseThemeMode(null) == ThemeMode.DARK`;
    - `an unknown value defaults to dark`: `parseThemeMode("SEPIA") == ThemeMode.DARK`;
    - `stored choices are kept`: `"LIGHT"`, `"DARK"` y `"SYSTEM"` dan su modo.
  - `ThemeViewModelTest` (con un doble de `ThemePreferenceRepository` cuyo `themeMode` es un `MutableSharedFlow` sin emitir, y la regla de dispatcher principal que usen los demás tests de ViewModel del proyecto, p. ej. `MainDispatcherRule`):
    - `before the preference loads the theme is dark`: `themeMode.value == ThemeMode.DARK`;
    - `a stored light choice is reflected`: tras emitir `LIGHT`, `themeMode.value == ThemeMode.LIGHT`.
- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA (`parseThemeMode`/`DEFAULT_THEME_MODE` sin resolver).
- [ ] **Paso 3: implementar** según «Ficheros».
- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(tema): el tema oscuro pasa a ser el de por defecto, también al arrancar`.

---

## Ejecución (`delegating-plan-execution`, plan pequeño)

1. Rama `agent/tema-oscuro-por-defecto` desde `main`; commit de este plan.
2. Task 1 con un `sonnet`; el coordinador hace `task-done` con GRADLE.
3. Revisión final con `opus`, más arreglos (sin re-revisión: no toca migraciones ni seguridad).
4. `code-review` en nivel `high`, más arreglos.
5. Cierre: backlog, rulings y la decisión sobre la rama, que es del usuario.

## Verificación de punta a punta (coordinador, al final)

En el emulador Android (`Pixel_10`), app debug sin datos (`pm clear`):
1. Arranque en frío con el móvil en claro: la ventana y la app salen oscuras, sin destello claro (captura justo tras el arranque y otra con la app cargada).
2. En Perfil, elegir Claro y reabrir en frío: la app sale clara (destello oscuro breve aceptado).
3. Elegir Sistema con el móvil en claro: la app sale clara; con el móvil en oscuro, oscura.
