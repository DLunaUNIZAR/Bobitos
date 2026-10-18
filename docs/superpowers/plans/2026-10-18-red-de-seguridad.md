# Red de seguridad (G1): plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans.
> - Es un **plan grande** (7 tareas): pasos 1 a 6, con `simplify`.
> - **Ninguna tarea cambia lo que ve el usuario.** La tarea 3 solo saca el estado de `ProfileScreen` a una función sin estado, sin tocar el aspecto. Por eso no hay revisión de diseño.
> - Cada tarea la hace un subagente `sonnet`, en serie y en el orden del plan.
> - La revisión final la hace `opus` (si falla, `fable`).
> - Los commits, el push y el PR los hace el coordinador.
> - **Instrucción del usuario para este plan (anótala como ruling al crear el ledger):** después del commit de la tarea 2, el coordinador sube la rama a `origin` y abre un **PR en borrador** contra `main` para que corra la CI. Vuelve a subirla antes de la pregunta del paso 6, y el plan no se cierra hasta ver la CI en verde en el último commit. En el paso 6, las cuatro opciones siguen siendo del usuario:
>   - «PR» = quitar el borrador;
>   - «descartar» = cerrar el PR y borrar también la rama remota.
> - **Ninguna tarea cambia `firestore.rules` ni `migrations/`.** Las mutaciones temporales de las tareas 4 a 7 (también la de `firestore.rules` en la tarea 7) se deshacen antes del informe.
> - Para la regla de re-revisión, cuenta como «permisos» cualquier arreglo que toque `firestore.rules`.
> - Los pasos usan casillas (`- [ ]`).

**Objetivo:** que la CI vuelva a comprobar Android en cada push y PR, que `lintDebug` pase y que los tests que hoy nunca fallan (o fallan siempre) protejan de verdad lo que dicen proteger.

**Arquitectura:**
- **CI:** se arregla el paso «Set up Android SDK». El job de Firebase pasa además las dos suites de Node que hoy solo corren en local.
- **Lint:** la navegación deja de leer `NavController.currentBackStack`, que es API restringida. Pregunta por la entrada con `getBackStackEntry`.
- **Tests:**
  - cada test que «nunca falla» se corrige y se demuestra con una **mutación temporal**: se rompe a propósito el código o el fixture, se ve fallar el test y se deshace la mutación;
  - dos piezas del producto se separan solo para poder probarlas: el contenido sin estado de «Perfil» y el registro de progreso de la instalación de betas.

**Tecnología:**
- Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.00), Hilt, Navigation Compose 2.9.8.
- Tests JUnit 4 (JVM), Compose instrumentados (`createAndroidComposeRule<ComponentActivity>()`) en el emulador `Pixel_10` (API 37) y `node --test` con el Emulator Suite de Firebase (`demo-bobitos`).
- GitHub Actions: `android-actions/setup-android@v3`.

**Spec:** la sección «G1» y los «Hallazgos de la comprobación» de `docs/superpowers/reports/2026-10-18-auditoria-deuda.md`. Las líneas Lnnn citadas son de `docs/superpowers/backlog.md` en `28dc784`.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).

**Entorno y comandos**
- **Java:** antes de cada `./gradlew`, ejecuta `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  - Necesita el emulador encendido. Si `adb devices` no lo lista: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
- **Suites de Node:** `npm run test:emulators` (reglas), `npm run test:scripts` (scripts del catálogo) y `npm run test:catalog-import` (importadores, con emulador). Las tres pasan hoy en local.
- **Puerta de cada tarea (`gate.sh`):** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug && npm run test:emulators && npm run test:scripts && npm run test:catalog-import`, más los tests instrumentados que nombre la tarea.
  - **Antes de la tarea 1**, `lintDebug` falla en `main` con un único error: `RestrictedApi` en `BobitosNavHost.kt:739`. Para la comprobación previa se tolera ese error y solo ese (anótalo como ruling). Desde la tarea 1, la puerta entera tiene que pasar.
  - Si un cambio rompe `config/detekt/baseline.xml`, regenera el baseline con `./gradlew detektBaseline` e inclúyelo en la tarea. Su diff solo puede cambiar las entradas de las funciones que la tarea mueve.

**Mutaciones (cómo se demuestra el RED de un test que nunca fallaba)**
- La tarea dice qué mutar. Aplica la mutación, ejecuta el test y copia en el informe la aserción que falla. Después deshaz la mutación y vuelve a ejecutar el test, que debe pasar.
- Antes del informe, `git diff` no puede contener la mutación. En las tareas que solo cambian tests, `git diff --stat` solo lista ficheros de test.

**Lo que no se hace**
- **Si un test corregido destapa un fallo real que el usuario vería** (por ejemplo, una etiqueta que no cabe), no lo arregles: para y dilo en el informe. El coordinador lo anota como menor con efecto visible.
- No se añaden tests instrumentados a la CI: está fuera de G1.
- Los nombres de test siguen el estilo de su fichero:
  - JVM: frases en inglés entre comillas invertidas;
  - instrumentados: camelCase;
  - Node: frases en español.

## Review Focus

1. **«Perfil», tras sacar su estado:** el selector de tema y el interruptor de recordatorios siguen llegando a sus ViewModels, y en API 33+ el permiso de notificaciones se sigue pidiendo antes de activarlos. Prueba de callbacks en la tarea 3; el cableado del envoltorio lo mira el revisor.
2. **Un test de Node o de reglas roto pone en rojo el PR:** ningún paso de la CI lleva `continue-on-error` ni `|| true`. Comprobación en la tarea 2.
3. **«Añadir a la compra» desde Ingredientes del menú principal sigue anclando «Hoy» del espacio antes de abrir la Compra.** Si no, las pestañas dejan de funcionar. Test en la tarea 1.
4. **Instalar una beta:** un fallo de descarga después de «Descargada» sigue siendo un error, no una cancelación. Test en la tarea 6.
5. **El dueño que reescribe su receta conservando dueño, autor y fecha de alta sigue pudiendo.** Es el camino legítimo, y el test nuevo de «no pisar» no puede prohibirlo. `assertSucceeds` en la tarea 7.

---

### Task 1: Navegación sin API restringida (`lintDebug` en verde)

Hallazgo 2 del informe.

**Ficheros:**
- Modificar: `J/core/navigation/BobitosNavHost.kt`:
  - `openShoppingWithName`, línea 739;
  - `needsSpaceHomeAnchor`, líneas 777-778.
- Borrar: `T/core/navigation/SpaceHomeAnchorTest.kt`. Su cobertura pasa al instrumentado nuevo.
- Crear: `A/core/navigation/SpaceHomeAnchorTest.kt`.

**Interfaces:**
- Produce: `internal fun NavController.needsSpaceHomeAnchor(): Boolean`. Sustituye a `needsSpaceHomeAnchor(backStackRoutes: List<String?>)`.

- [ ] **Paso 1: RED de lint.** Ejecuta `./gradlew lintDebug`. Esperado: `Lint found 1 error`, `BobitosNavHost.kt:739: Error: NavController.getCurrentBackStack can only be called from within the same library group … [RestrictedApi]`.

- [ ] **Paso 2: escribe el test instrumentado** `A/core/navigation/SpaceHomeAnchorTest.kt`.
  - **Montaje:** `createAndroidComposeRule<ComponentActivity>()`. En `setContent`, un `rememberNavController()` guardado en un `lateinit var` y un `NavHost` con `startDestination = BobitosDestination.Home.route` y cuatro `composable` vacíos: `Home`, `Ingredients`, `SpaceHome` y `Meals` (sus `.route`).
  - **Navegación:** desde el test, con `compose.runOnIdle { nav.navigate(...) }`.
  - **Tests:**
    - `fromTheMainMenuIngredientsTheSpaceHubMustBeAnchoredFirst`: navega a `Ingredients` y comprueba `assertTrue(nav.needsSpaceHomeAnchor())`.
    - `fromMealsTheSpaceHubIsAlreadyInTheStack`: navega a `SpaceHome` y después a `Meals`, y comprueba `assertFalse(nav.needsSpaceHomeAnchor())`.
    - `onlyTheStartDestinationNeedsTheAnchor`: sin navegar, comprueba `assertTrue(nav.needsSpaceHomeAnchor())`.

- [ ] **Paso 3: ejecútalo y comprueba que falla** con `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.core.navigation.SpaceHomeAnchorTest`. Esperado: error de compilación, porque la extensión `needsSpaceHomeAnchor()` no existe.

- [ ] **Paso 4: implementa `internal fun NavController.needsSpaceHomeAnchor(): Boolean`** en `BobitosNavHost.kt`, en lugar de la función de lista.
  - Devuelve `true` si `getBackStackEntry(BobitosDestination.SpaceHome.route)` lanza `IllegalArgumentException`, que es lo que lanza cuando la ruta no está en la pila. Captura solo esa excepción.
  - Si detekt marca `SwallowedException`, añade `@Suppress("SwallowedException")` con un comentario de una línea: la excepción es la respuesta «no está en la pila».
  - `openShoppingWithName` pasa a `if (needsSpaceHomeAnchor())`. Conserva su comentario.
  - Borra el `SpaceHomeAnchorTest` de JVM.

- [ ] **Paso 5: GREEN.** El test del paso 3 pasa y `./gradlew lintDebug` dice `0 errors`. Después, la puerta entera.

- [ ] **Paso 6: commit** (coordinador): `fix(navegacion): anclar «Hoy» sin la API restringida currentBackStack (lintDebug en verde)`.

### Task 2: La CI vuelve a ejecutar Android y las suites de Node

Hallazgo 1 del informe y L356.

**Ficheros:**
- Modificar: `.github/workflows/ci.yml`.

**Interfaces:** ninguna.

- [ ] **Paso 1: RED.**
  - Copia en el informe la causa de la última ejecución de `main` (`gh run view 39157308803 --log-failed | grep -E "Failed to find package|exit code"`). Esperado: `Warning: Failed to find package 'tools'` y `sdkmanager' failed with exit code 1` en «Set up Android SDK».
  - El motivo: la lista de paquetes por defecto de `setup-android@v3` (`tools platform-tools`) pide `tools`, que ya no existe en el repositorio del SDK.

- [ ] **Paso 2: arregla «Set up Android SDK».**
  - Añade al paso `with:` las entradas `packages: platform-tools` y `log-accepted-android-sdk-licenses: false`.
  - Actualiza el comentario del paso: sigue aceptando las licencias para que AGP descargue el platform de `compileSdk 37`, y sin `packages` pediría `tools`, retirado del SDK.

- [ ] **Paso 3: añade las dos suites al job `firebase-rules`.**
  - Van después de «Firestore rules tests» y en este orden:
    - `name: Catalog scripts tests`, con `run: npm run test:scripts`;
    - `name: Catalog import tests (emulator)`, con `run: npm run test:catalog-import`.
  - No cambies el id ni el `name` del job, por si una regla de rama exige ese check.

- [ ] **Paso 4: comprueba en local.**
  - `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.7` sin errores. Si Docker no responde, dilo en el informe y sigue.
  - `grep -nE "continue-on-error|\|\| true" .github/workflows/ci.yml` no da nada (Review Focus 2).
  - La puerta entera.
  - `TZ=UTC ./gradlew :app:testDebugUnitTest --rerun`, porque los runners de la CI están en UTC. Si algún test falla solo en UTC, el arreglo entra en esta tarea: fija la zona (`ZoneId`) o el idioma (`Locale`) en el propio test. Nunca cambies la CI para imitar este equipo.

- [ ] **Paso 5: commit, push y PR** (coordinador).
  - Commit: `ci: arreglar «Set up Android SDK» y ejecutar los tests de scripts e importadores del catálogo`.
  - `git push -u origin agent/red-de-seguridad`.
  - `gh pr create --draft --base main --title "Red de seguridad (G1): CI, lint y tests que nunca fallan"`. El cuerpo resume las tareas y acaba con las líneas de atribución de PR de la sesión.

- [ ] **Paso 6: GREEN en la CI.** El `task-done` de esta tarea es la puerta seguida de `gh pr checks --watch --fail-fast`: los dos jobs tienen que pasar.
  - La CI puede tardar más que el límite de 10 minutos de una llamada: lanza el `task-done` en segundo plano y espera su aviso.
  - Si la CI falla en un paso que en local pasa, es un fallo de la tarea («Atascos») con el log del paso (`gh run view <id> --log-failed`) como error.
  - El arreglo vuelve a seguir la regla del paso 4.

### Task 3: «Perfil» se prueba sin Hilt

L135, L295 y L326: es el mismo menor, apuntado tres veces.

`Frontend: sin skill de diseño (no cambia el aspecto: solo separa el estado)`.

**Ficheros:**
- Modificar: `J/feature/auth/ProfileScreen.kt`.
- Modificar: `A/feature/auth/ProfileScreenTest.kt`.
- Puede cambiar: `config/detekt/baseline.xml`. La entrada `LongMethod` de `ProfileScreen` pasa a `ProfileContent`.

**Interfaces:**
- Produce: `@Composable internal fun ProfileContent(...)`.
  - Recibe los parámetros actuales de `ProfileScreen` desde `user` hasta `onCheckForBetaUpdate`, sin cambiar sus nombres.
  - Añade estos seis, en este orden:
    - `themeMode: ThemeMode`;
    - `onThemeModeChange: (ThemeMode) -> Unit`;
    - `remindersEnabled: Boolean`;
    - `onRemindersChange: (Boolean) -> Unit`;
    - `remindersLeadTime: ReminderLeadTime`;
    - `onRemindersLeadTimeChange: (ReminderLeadTime) -> Unit`.
  - Acaba con `modifier: Modifier = Modifier`.
- `ProfileScreen` conserva su firma exacta, así que `BobitosNavHost` no cambia.

- [ ] **Paso 1: RED.** Ejecuta `ProfileScreenTest` tal como está (`-Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.feature.auth.ProfileScreenTest`). Esperado: falla al crear `hiltViewModel()` en una `ComponentActivity` que no es `GeneratedComponent`. Copia la excepción en el informe.

- [ ] **Paso 2: pasa el test a `ProfileContent`.**
  - `privacyPolicyAndAccountDeletionAreReachable` monta `ProfileContent` con los mismos valores que hoy, más `themeMode = ThemeMode.SYSTEM`, `remindersEnabled = false` y `remindersLeadTime = ReminderLeadTime.AT_TIME`, con callbacks vacíos. Sus aserciones no cambian.
  - Añade `themeAndRemindersReachTheirCallbacks` (Review Focus 1). Monta `ProfileContent` con `remindersEnabled = true` y callbacks que guardan lo que reciben, y comprueba:
    - pulsar «Oscuro» (`R.string.settings_theme_dark`, desplazándote hasta él) llama a `onThemeModeChange(ThemeMode.DARK)`;
    - pulsar el interruptor «Recordatorios» llama a `onRemindersChange(false)`. Localízalo con `isToggleable()`: la cabecera de la sección también puede decir «Recordatorios»;
    - «Avisar» (`R.string.profile_reminders_lead_label`) se muestra.
    - Con `remindersEnabled = false` (un segundo `setContent` no vale: usa un `mutableStateOf` que cambias en `runOnIdle`), «Avisar» no existe.
  - Esperado: no compila, porque `ProfileContent` no existe.

- [ ] **Paso 3: separa el contenido.**
  - `ProfileScreen` conserva:
    - la lectura de los dos ViewModels;
    - el `permissionLauncher`;
    - el `onRemindersChange` con la comprobación de `POST_NOTIFICATIONS`.
  - Después llama a `ProfileContent`, a la que pasa `themeViewModel::setThemeMode` y `remindersViewModel::setLeadTime`.
  - Todo lo demás (estado de `rememberSaveable`, `LaunchedEffect`, `ScreenScaffold` y diálogos) se mueve tal cual a `ProfileContent`.
  - No cambies ningún texto, orden, medida ni modificador.

- [ ] **Paso 4: GREEN.** Los dos tests de `ProfileScreenTest` pasan en `Pixel_10`. Después, la puerta entera.

- [ ] **Paso 5: commit** (coordinador): `test(perfil): ProfileContent sin estado para probar Perfil sin Hilt`.

### Task 4: Tests instrumentados que no medían lo que decían

L59, L168, L33 y L34. Esta tarea **solo cambia tests**.

**Ficheros:**
- Modificar: `A/core/navigation/WorkspaceNavigationBarTest.kt`.
- Modificar: `A/feature/common/IngredientReviewUiTest.kt`.
- Modificar: `A/LauncherIconTest.kt`.

**Interfaces:** ninguna.

- [ ] **Paso 1: `WorkspaceNavigationBarTest` (L59).**
  - La etiqueta lleva `maxLines = 1` con `overflow = Clip`, así que `isLineEllipsized(0)` nunca es `true`.
  - Sustituye las dos comprobaciones de las líneas 49 y 59 por un helper privado `assertCabeEntera(title: String, layout: TextLayoutResult)`, con dos aserciones:
    - `assertFalse("$title cortada", layout.hasVisualOverflow)`;
    - `assertEquals("$title cortada", title.length, layout.getLineEnd(0))`.
  - En `aLetraNormalLasEtiquetasVanEnLabelMedium`, llama también al helper con cada etiqueta.

- [ ] **Paso 2: RED de L59 por mutación.**
  - En `J/core/navigation/WorkspaceScaffold.kt`, haz que el `Text` de la etiqueta use `fontSize = labelStyle.fontSize` en lugar de `labelSize`. Así se desactiva el encogimiento compartido.
  - Ejecuta el test: `labelsAreNotEllipsizedAt130PercentFontScale` debe fallar con «… cortada».
  - Deshaz la mutación y comprueba que pasa.

- [ ] **Paso 3: `IngredientReviewUiTest` (L168).**
  - Mide a `232.dp` en lugar de `280.dp`.
  - El comentario pasa a «232 dp: los 280 dp mínimos de un AlertDialog menos sus 24 dp de margen a cada lado.»
  - Si a 232 dp la marca o la tienda ocupan dos líneas, para y dilo en el informe: es un fallo visible y no se arregla aquí.

- [ ] **Paso 4: `LauncherIconTest` (L33 y L34).**
  - `appIcon()` carga el recurso con `ContextCompat.getDrawable(context, R.mipmap.ic_launcher)`, que es el `android:icon` del manifiesto, en lugar de `packageManager.getApplicationIcon`, que una capa de fabricante puede envolver.
  - Los casts forzados pasan a `as?` con `assertNotNull("<qué falta>", …)`.
  - `appIconHasMonochromeLayer` pierde `assumeTrue` y lleva `@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)` (`androidx.test.filters.SdkSuppress`).
  - Sin mutación: no cambia lo que comprueba.

- [ ] **Paso 5: GREEN.**
  - Las tres clases pasan en `Pixel_10`: `-Pandroid.testInstrumentationRunnerArguments.class=` con las tres FQCN separadas por comas.
  - `git diff --stat` solo lista los tres tests.
  - Después, la puerta entera.

- [ ] **Paso 6: commit** (coordinador): `test: los tests de barra, revisión de ingredientes e icono miden lo que dicen`.

### Task 5: Tests JVM y de Node que pasaban con cualquier cosa

L293, L222 y la parte JVM de L223. Esta tarea **solo cambia tests**.

**Ficheros:**
- Modificar: `T/data/repository/RoutineExerciseFixtureTest.kt`.
- Modificar: `tests/catalog-routines.test.mjs`.
- Modificar: `T/core/model/ShoppingQuantityTest.kt`.
- Modificar: `T/data/repository/RecipeRestoreTest.kt`.

**Interfaces:** ninguna.

- [ ] **Paso 1: tabla compartida de rutinas (L293).**
  - En `RoutineExerciseFixtureTest`, añade `the shared table covers the four measures`: los `caso` de la tabla, en orden, son `listOf("repeticiones", "segundos", "cardio", "otros")`.
  - En `catalog-routines.test.mjs`, dentro de «expande cada caso…», añade al principio `assert.deepEqual(fixture.map((c) => c.caso), ["repeticiones", "segundos", "cardio", "otros"])`.
  - Añade el test «la tabla compartida coincide con el catálogo de ejercicios». Lee `data/catalog/exercises.json` y, para cada caso, comprueba `assert.deepEqual(indexExercises(catalogo).get(c.entry.exerciseId), c.exercise, c.caso)`.

- [ ] **Paso 2: RED de L293 por mutación.**
  - Sustituye el contenido de `tests/fixtures/routine-exercises.json` por `[]`.
  - Deben fallar el test nuevo de Kotlin (`--tests "*RoutineExerciseFixtureTest"`) y los dos de Node (`node --test tests/catalog-routines.test.mjs`).
  - Restaura el fichero con `git checkout -- tests/fixtures/routine-exercises.json` y comprueba que pasan.

- [ ] **Paso 3: `ShoppingQuantityTest` (L222).**
  - En `only the words before any preposition of the unit change in number`, sustituye la aserción de `"1/2 bolsas con asa"` por `assertEquals("1 bolsa para galletas", ShoppingQuantity.sum("1/2 bolsas para galletas", "1/2 bolsas para galletas"))`.
  - Renombra `every word of the unit agrees in number up to the first de` a `every word of the unit agrees in number up to the first preposition`.
  - **Mutación:** en `ShoppingQuantity`, haz que el cambio de número no se detenga en la primera preposición. La aserción nueva debe fallar (`1 bolsa para galleta`). Deshazla.

- [ ] **Paso 4: `RecipeRestoreTest` (L223, parte JVM).**
  - Añade `a common recipe whose owner and last editor are no longer admins comes back owned and edited by whoever undoes`.
  - Prepara `recipe(GLOBAL, importedAt = imported).copy(ownerUid = "ex-admin", createdBy = "ex-admin", updatedBy = "ex-admin")`.
  - `restoreAudit("second-admin", admins)` es igual a `RecipeRestoreAudit("second-admin", "second-admin", created, "second-admin", imported, imported)`.
  - **Mutación:** en `restoreAudit`, haz que el editor solo se sustituya si el dueño sigue siendo admin. El test nuevo debe fallar. Deshazla.

- [ ] **Paso 5: GREEN.** `git diff --stat` solo lista los cuatro tests. Después, la puerta entera.

- [ ] **Paso 6: commit** (coordinador): `test: la tabla de rutinas, las cantidades y el Deshacer de recetas fallan si se rompen`.

### Task 6: El progreso de la instalación de betas, con test

L22.

**Ficheros:**
- Modificar: `J/data/betaupdate/FirebaseBetaDistribution.kt`.
- Crear: `T/data/betaupdate/BetaUpdateProgressTrackerTest.kt`.

**Interfaces:**
- Produce `internal class BetaUpdateProgressTracker`, en `FirebaseBetaDistribution.kt`, junto a `afterDownload`.
  - Guarda en privado si la descarga ya terminó.
  - `fun onProgress(status: UpdateStatus, downloadedBytes: Long, totalBytes: Long): BetaUpdateProgress?` traduce con `toProgress`. Si el resultado es `AwaitingInstall`, apunta que ya se descargó. Aplica `afterDownload`.
  - `fun onFailure(status: FirebaseAppDistributionException.Status?): BetaUpdateProgress` traduce con `toFailureProgress()` (`Failed(UNKNOWN)` si es `null`) y aplica `afterDownload`.

- [ ] **Paso 1: escribe el test.** Cada caso usa un tracker nuevo:
  - `cancelling the installer after the download comes as install canceled`: `onProgress(DOWNLOADED, 10, 10)` da `AwaitingInstall`; después, `onFailure(INSTALLATION_FAILURE)` da `InstallCanceled`.
  - `an install failed status after the download comes as install canceled`: tras `DOWNLOADED`, `onProgress(INSTALL_FAILED, 10, 10)` da `InstallCanceled`.
  - `an install failure before the download stays a failure`: `onFailure(INSTALLATION_FAILURE)` da `Failed(BetaUpdateError.INSTALL_FAILED)`.
  - `a download failure after the download stays a failure` (Review Focus 4): tras `DOWNLOADED`, `onFailure(DOWNLOAD_FAILURE)` da `Failed(BetaUpdateError.DOWNLOAD_FAILED)`.
  - `an unknown failure is unknown`: `onFailure(null)` da `Failed(BetaUpdateError.UNKNOWN)`.

- [ ] **Paso 2: RED.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateProgressTrackerTest"` no compila, porque la clase no existe.

- [ ] **Paso 3: implementa el tracker y úsalo en `updateApp()`.**
  - Crea un tracker en cada `callbackFlow`, nunca como campo de la clase.
  - Los dos listeners solo llaman a `onProgress` y a `onFailure` y envían el resultado.
  - Desaparece el `var downloaded` local.

- [ ] **Paso 4: GREEN y mutación.**
  - El test pasa.
  - **Mutación:** quita `afterDownload` de `onFailure`. El primer test debe fallar. Deshazla.
  - Después, la puerta entera.

- [ ] **Paso 5: commit** (coordinador): `test(avisos-beta): el progreso de la instalación se traduce en una clase con test`.

### Task 7: Tests de reglas de recetas que fijan lo que protegen

L249 y la parte de reglas de L223. Esta tarea **solo cambia** `tests/firebase-emulators.test.mjs`.

**Ficheros:**
- Modificar: `tests/firebase-emulators.test.mjs`.

**Interfaces:** ninguna.

- [ ] **Paso 1: test nuevo (L249).** Añade «un setDoc de cliente no pisa una receta existente, propia o ajena» junto a los demás tests de recetas. Usa los helpers que ya hay (`verifiedFirestore`, `recipeData`, `seedImportedRecipe`, `RECIPE_ADMIN_UID`):
  - **Receta propia:** el dueño `recipe-overwrite-owner` crea `pisar-propia` con `recipeData(owner)` (`assertSucceeds`).
    - Después, `setDoc` de `recipeData(owner, { title: "Otra" })` sobre el mismo id da `assertFails`, porque cambia `createdAt`.
    - Otro usuario, `recipe-overwrite-other`, hace `setDoc` de `recipeData(other)` sobre ese id: `assertFails`.
  - **Reescritura legítima (Review Focus 5):** el dueño hace `setDoc` con los datos actuales (`getDoc`), `title: "Otra"`, `updatedBy: owner` y `updatedAt: serverTimestamp()`: `assertSucceeds`.
  - **Receta común:** `seedImportedRecipe("pisar-comun")`.
    - El admin hace `setDoc` de `recipeData(RECIPE_ADMIN_UID, { visibility: "GLOBAL" })` sobre ella: `assertFails`.
    - Un usuario normal hace `setDoc` de `recipeData(<uid>, { visibility: "GLOBAL" })`: `assertFails`.

- [ ] **Paso 2: RED por mutación.**
  - En `firestore.rules`, cambia temporalmente el `allow update` de `match /recipes/{recipeId}` a `allow update: if verifiedUser();`.
  - `npm run test:emulators` debe fallar en el test nuevo.
  - Deshazla y comprueba que `git diff firestore.rules` no da nada.

- [ ] **Paso 3: «Deshacer» de una común (L223).**
  - Borra `await assertFails(setDoc(ref, { ...dated, source: { ...before.source, importedAt: at(60) } }));`: no prueba nada aparte, porque `importedAt <= updatedAt <= request.time`.
  - El comentario de encima pasa a «La fecha de importación no puede ser posterior a la última edición (que ya no puede ser futura).»

- [ ] **Paso 4: reloj (L223).** En «solo un admin crea una receta con la fuente del catálogo…», `importedAt` pasa de `Timestamp.now()` a `Timestamp.fromMillis(Date.now() - 60_000)`.

- [ ] **Paso 5: GREEN.** `npm run test:emulators` pasa. Después, la puerta entera.

- [ ] **Paso 6: commit** (coordinador): `test(reglas): un setDoc de cliente no pisa recetas y los tests de Deshacer no dependen del reloj`.

---

## Cierre (paso 6): líneas del backlog que se marcan

En el commit del cierre, tacha cada línea con `~~…~~` y añade `**Resuelto** en \`agent/red-de-seguridad\` (tarea N)`, como en las secciones anteriores:

| Línea en `28dc784` | Sección | Tarea |
|---|---|---|
| L22 | `agent/avisos-beta` | 6 |
| L33, L34 | `agent/logo` | 4 |
| L59 | `agent/menores-evolucion-visual-fase-c` | 4 |
| L135 | `agent/evolucion-visual-fase-a` | 3 |
| L168 | `agent/menores-despensa-recetas` | 4 |
| L222, L223 | `agent/menores-compra-y-deshacer-comunes` | 5 y 7 (L223, ambas) |
| L249 | `agent/catalogo-recetas` | 7 |
| L293 | `agent/catalogo-rutinas` | 5 |
| L295 | `agent/catalogo-rutinas` | 3 |
| L326 | `agent/tema-oscuro-por-defecto` | 3 |
| L356 | `agent/catalogo-ingredientes` | 2 |

- Los dos hallazgos del informe no están en el backlog: no se marcan.
- Si las líneas se han movido por los menores nuevos de esta rama, búscalas por su texto.
- En la memoria, quita «pendiente arreglar el error de lintDebug» de `despensa-plan` y marca G1 como hecho en `auditoria-deuda-2026-10-18`.
