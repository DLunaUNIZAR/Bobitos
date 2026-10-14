# Menores de «Has cocinado …» y de la hoja «Nueva comida» — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (4 tareas, más una quinta solo si la Tarea 1 reproduce el fallo): pasos 1, 2, 3, 5 y 6, **sin `simplify`**. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). **Ninguna tarea toca `firestore.rules` ni `migrations/`.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** cerrar los 5 menores con efecto visible de la sección «2026-10-14 — `agent/menores-despensa-recetas`» de `docs/superpowers/backlog.md`. El de la hoja «Nueva comida» invisible, que está sin confirmar, primero se intenta reproducir en el emulador: si no se reproduce, se queda en el backlog. Los internos de esa sección se quedan en el backlog (salvo uno que se resuelve de paso; ver ruling 3).

**Arquitectura:**
- **Reproducción (Tarea 1):** un subagente intenta reproducir en el emulador `Pixel_10`, con la app real, la hoja «Nueva comida» invisible pero activa. No cambia código. Si lo reproduce, también busca la causa.
- **Comidas, «Has cocinado …» (Tareas 2 a 4):**
  - si «Listo» falla porque la lista ha cambiado, el mensaje dice que hay que volver a pulsar «Listo»;
  - el lector de pantalla anuncia el error del diálogo;
  - lo marcado sobrevive al giro (`rememberSaveable`);
  - «Listo» lee la lista mientras espera al catálogo, en vez de después.
- **Hoja «Nueva comida» (Tarea 5, condicional):** la escribe el coordinador a partir del informe de la Tarea 1, solo si se reproduce.

**Stack:** Kotlin + Jetpack Compose (Material 3, BOM `2026.06.00`) + Hilt + Firestore. Tests JVM (JUnit 4 + `kotlinx-coroutines-test`, dobles a mano) y tests de Compose instrumentados (`app/src/androidTest`, en el emulador).

**Spec:** `docs/superpowers/specs/2026-10-13-despensa-design.md` (§3c, el diálogo de cocinar). Plan anterior: `docs/superpowers/plans/2026-10-14-menores-despensa-recetas.md` (su ruling 3 dejó el diálogo abierto con el error dentro; su Tarea 6 hizo que el selector «Elegir receta» sobreviva al giro, y en su recorrido final se vio la hoja invisible). Backlog: `docs/superpowers/backlog.md`.

## Qué entra

| # | Menor (backlog, sección «2026-10-14 — `agent/menores-despensa-recetas`») | Tarea |
|---|---|---|
| M1 | **Sin confirmar:** tras girar a horizontal y volver a vertical con el selector «Elegir receta» abierto, al elegir la receta la hoja «Nueva comida» queda invisible pero activa (un toque abre «¿Descartar los cambios?») | 1 (y 5 si se reproduce) |
| M2 | En «Has cocinado …», el error «La lista de la compra ha cambiado… Vuelve a añadir los ingredientes.» da una instrucción equivocada: hay que volver a pulsar «Listo» | 2 |
| M3 | Cuando «Listo» falla, el lector de pantalla no anuncia el error del diálogo | 3 |
| M4 | Al girar el móvil con «Has cocinado …» abierto se pierde lo marcado | 3 |
| M5 | «Listo» espera al catálogo (hasta 5 s) y después lee la lista, una cosa detrás de otra | 4 |

## Qué no entra

- Los **internos** de la misma sección: el comentario de `CookedDialog` en `MealsScreen.kt`, el KDoc de `restockFromPantry`, la medida de `IngredientReviewUiTest` y la lógica de borrado repetida en `applyCooked`. **No toques** `PantryShopping.kt`, `FirestoreShoppingRepository.kt`, `IngredientReviewUiTest.kt` ni el comentario de `CookedReviewDialog` en `MealsScreen.kt`.
  - Excepción: el interno «`ItemNotFound` → "La lista ha cambiado" está en el mapeo general de Comidas» se resuelve de paso en la Tarea 2 (ruling 3).
- Los menores de las demás secciones del backlog.
- **Cualquier despliegue** (reglas, beta) y cualquier push: aplazados por el usuario.

## Rulings de planificación

1. **Reproducir antes de arreglar (M1).** La Tarea 1 hace al menos 20 intentos en la app real, repartidos en 5 variantes, y no cambia ningún fichero del repositorio.
   - **Reproducido** quiere decir que, después de elegir la receta, la captura no muestra la hoja «Nueva comida» y un toque en una zona vacía de la pantalla abre «¿Descartar los cambios?».
   - **No se reproduce:** no hay Tarea 5. Al cerrar, la línea del backlog sigue abierta, con «No se reprodujo en N intentos en `agent/menores-has-cocinado`».
   - **Se reproduce:** la Tarea 1 repite esa variante 5 veces más (frecuencia), busca la causa y propone el arreglo. Con ese informe, el coordinador escribe la Tarea 5 al final de este plan (commit en la rama) y la ejecuta después de la Tarea 4.
   - **Para y pregunta al usuario** antes de escribir la Tarea 5 si el arreglo exige cambiar versiones de dependencias (BOM de Compose, Material 3), quitar la confirmación «¿Descartar los cambios?» o cambiar el comportamiento de todas las hojas de la app (`BobitosFormSheet`).
2. **Mensaje propio de «Listo» (M2).** Texto nuevo `meals_error_cooked_shopping_changed`: «La lista de la compra ha cambiado mientras guardabas. No se ha guardado nada: vuelve a pulsar «Listo».»
   - Sale cuando `applyCooked` falla con `ItemChanged` o con `ItemNotFound`, como el valor nuevo `MealUiMessage.CookedShoppingChanged`.
   - Al volver a pulsar «Listo», la lista se vuelve a leer, así que el reintento funciona.
   - La revisión de «Añadir a la compra» de Comidas sigue con su texto de siempre, «…Vuelve a añadir los ingredientes.» (`MealUiMessage.ShoppingChanged`): allí la revisión ya se ha cerrado y sí hay que volver a añadir.
   - **Caso no tratado:** si se pulsa «Cancelar» mientras guarda y después falla por la lista, el mensaje sale en el aviso de la pantalla y el diálogo ya no está. Exige un cambio de otro miembro en milisegundos y, además, cancelar durante el guardado; no se trata.
3. **Dónde se elige el mensaje (M2).** El mensaje de «Listo» se elige en la llamada, como hace `applySeenWrites` en la revisión: `runAction` recibe el mapeo de errores (`toMessage`, por defecto el de siempre) y `confirmCooked` le pasa `Throwable::toCookedUiMessage`.
   - Con eso, en el mapeo general `ItemNotFound` ya no llega desde ningún camino de Comidas. Pasa al grupo de `UnexpectedError`, como en Recetas (`RecipesViewModel`).
   - Esto resuelve de paso el menor interno «`ItemNotFound` → … está en el mapeo general de Comidas». Al cerrar se marca así en el backlog.
4. **El error del diálogo se anuncia (M3).** El texto del error de `CookedDialog` lleva `semantics { liveRegion = LiveRegionMode.Polite }`, como el de `BobitosFormSheet`.
5. **Lo marcado sobrevive al giro (M4).** `CookedDialog` guarda la elección con `rememberSaveable(review)`, y `CookedChoice` pasa a ser `@Parcelize … : Parcelable`, como `MealDraft`.
   - Se conserva todo: lo que se tacha, lo que se ha acabado y «Apuntar a la compra lo que se ha acabado».
   - Si el sistema mata el proceso, el `ViewModel` se pierde y el diálogo no vuelve a salir; no hay nada que restaurar.
6. **Lista y catálogo a la vez (M5).** En `confirmCooked`, si hay algo que apuntar a la compra, la lectura de la lista (`currentItems`) se lanza con `async` antes de esperar al catálogo (`ingredientData()`), dentro de un `coroutineScope`.
   - Si la lista falla, el error sale enseguida y se deja de esperar al catálogo. No se escribe nada, ni entonces ni después.
   - Si no hay nada que apuntar, la lista no se lee, como hoy.
7. **Tests instrumentados.** La Tarea 3 amplía `CookedDialogUiTest`.
   - Se ejecutan en el emulador `Pixel_10`, que deja arrancado el coordinador (ver «Ejecución»).
   - Solo se ejecutan las clases nombradas: `ProfileScreenTest` falla desde antes (backlog, sección `agent/menores-compra-membresia-y-comunes`).
   - `connectedDebugAndroidTest` desinstala la app al acabar: después de la Tarea 3, la app ya no está instalada ni con sesión.

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `AT` = `app/src/androidTest/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
  - `ADB` = `~/Library/Android/sdk/platform-tools/adb`
  - `SCRATCH` = el directorio scratchpad de la sesión de ejecución (el coordinador lo pone en cada brief).
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **INSTR (una clase instrumentada):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<clase completa>`.
    - Antes, comprueba que hay emulador: `ADB devices` debe listar un `emulator-…  device`.
    - Si no lo hay, **no arranques ninguno**: para y devuelve BLOCKED.
    - Nunca lances `connectedDebugAndroidTest` sin `-P…class=`: `ProfileScreenTest` falla desde antes.
  - **NODE:** `npm run test:scripts` (solo el coordinador, al final).
  - **EMU (reglas):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && npm run test:emulators` (solo el coordinador, al final; este plan no cambia reglas).
    - Usa los puertos 8080 y 9099: antes de lanzarlo, el coordinador para los emuladores de Firebase que dejó arrancados.
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:**
  - cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`);
  - desplegar reglas, importar al proyecto real, distribuir betas o hacer push;
  - descargar nada;
  - tocar `firestore.rules`, `migrations/`, `tests/firebase-emulators.test.mjs` o `config/detekt/baseline.xml`;
  - parar o reiniciar los emuladores (Android o Firebase) que ha arrancado el coordinador.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:**
  - JVM puros (JUnit 4 + `kotlinx-coroutines-test`), con dobles a mano (`RecordingShoppingRepository`, `FakePantryRepository`, los de `MealsViewModelTest.kt` y los de `IngredientFakes.kt`); sin mockk ni Robolectric.
  - Los instrumentados, con `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<ComponentActivity>()`, como `AT/feature/meals/CookedDialogUiTest.kt`.
  - **Orden TDD obligatorio:** test primero y verlo en rojo, después el código. Un fallo de compilación por lo que aún no existe también vale como rojo; copia el fallo en el informe.
- **detekt:**
  - Límites: `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `CyclomaticComplexMethod` 15 y `TooManyFunctions` 25 (por clase y por fichero). `MagicNumber` está desactivado.
  - **No añadas funciones a la clase `MealsViewModel`** (está en el baseline de `TooManyFunctions`). La única función nueva del fichero es la de nivel superior `Throwable.toCookedUiMessage()`.
  - **No cambies estas firmas, que están en el baseline:** `MealsScreen(...)` y `MealCard(...)`.
  - No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; líneas de 120 caracteres como máximo.
- **Tests existentes:** solo se cambian los que la tarea nombra.

## Review Focus

1. **«Listo» cuando otro miembro cambia o borra a la vez algo que se vuelve a apuntar:** el diálogo sigue abierto, con lo marcado, y dice que se vuelva a pulsar «Listo». El reintento vuelve a leer la lista y escribe una sola vez. → `MealsCookedViewModelTest` (Tarea 2).
2. **La lista cambia en la revisión de «Añadir a la compra» de Comidas:** sigue saliendo «…Vuelve a añadir los ingredientes.», también si el artículo se ha borrado (`ItemNotFound` convertido por `applySeenWrites`), y no el mensaje nuevo. → `MealsViewModelTest`, tests `a shopping list changed while reviewing is reported` y `a shopping item deleted while reviewing is reported as a changed list`, sin cambios (Tarea 2 los ejecuta).
3. **Girar con todo marcado distinto de lo que viene por defecto** (algo sin tachar, algo acabado, «Apuntar a la compra…» desmarcado): tras el giro sigue igual. → `CookedDialogUiTest` (Tarea 3).
4. **Sin catálogo a tiempo y la lista no se puede leer:** el error sale enseguida, no a los 5 s. No se queda en «Guardando…» y no se escribe nada después. → `MealsCookedViewModelTest` (Tarea 4).
5. **«Listo» sin nada que apuntar a la compra:** no lee la lista; solo hace falta para «A la compra». → `MealsCookedViewModelTest` (Tarea 4).

---

## Ejecución (`delegating-plan-execution`, plan de menores)

1. Rama `agent/menores-has-cocinado`, desde `main` (38cba77), con este plan como primer commit.
2. **Antes de la Tarea 1, el coordinador prepara el entorno** y lo deja arrancado hasta el final (cada proceso largo, en segundo plano):
   1. Emulador Android: `~/Library/Android/sdk/emulator/emulator -avd Pixel_10 -no-snapshot-save`. Espera a `ADB wait-for-device` y a que `ADB shell getprop sys.boot_completed` devuelva `1`.
   2. Emuladores de Firebase: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && npm run emulators`. Espera a que respondan los puertos 8080 y 9099.
   3. `npm run android:connect-emulators` y después `npm run seed:emulators` (deja el usuario `demo-owner` en Firestore, el espacio «Casa de prueba» y «Leche» pendiente en la Compra).
   4. Escribe `SCRATCH/setup.mjs` con este contenido y ejecútalo con `node SCRATCH/setup.mjs`:

      ```js
      // Prepara los emuladores de Firebase (demo-bobitos): el usuario sembrado y la receta «Arroz con leche».
      import { createRequire } from "node:module";
      const require = createRequire("/Users/dluna/Developer/Bobitos/package.json");
      process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
      process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
      const { initializeApp } = require("firebase-admin/app");
      const { getAuth } = require("firebase-admin/auth");
      const { getFirestore, FieldValue } = require("firebase-admin/firestore");
      const app = initializeApp({ projectId: "demo-bobitos" });
      try { await getAuth(app).deleteUser("demo-owner"); } catch {}
      await getAuth(app).createUser({
        uid: "demo-owner", email: "david@bobitos.invalid", password: "bobitos123",
        displayName: "David Demo", emailVerified: true,
      });
      await getFirestore(app).collection("recipes").doc("arroz-con-leche").set({
        ownerUid: "demo-owner", visibility: "PRIVATE", title: "Arroz con leche", description: null, category: null,
        sourceRecipeId: null, sourceUrl: null,
        ingredients: [
          { name: "Arroz", quantity: "200", unit: "g" },
          { name: "Leche", quantity: "1", unit: "l" },
        ],
        steps: [], servings: null, totalMinutes: null,
        createdBy: "demo-owner", createdByName: "David Demo", createdAt: FieldValue.serverTimestamp(),
        updatedBy: "demo-owner", updatedAt: FieldValue.serverTimestamp(),
      });
      console.log("usuario demo-owner (david@bobitos.invalid / bobitos123) y receta «Arroz con leche» listos");
      ```

   5. Instala la app debug: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:installDebug`.
3. Las tareas 1 a 4, una tras otra, cada una con un `sonnet` que recibe su brief (con la ruta de `SCRATCH`). Después, la Tarea 5 solo si la Tarea 1 devuelve REPRODUCIDO (ruling 1).
   - **`task-done`:** las clases JVM nombradas en su paso de verde, las clases INSTR de la tarea (la 3 y, si existe, la 5) y GRADLE.
   - La Tarea 1 no tiene `task-done` de código: el coordinador guarda el informe en el ledger, con el resultado y el número de intentos.
4. **Revisión final** con `opus`, con este plan (Review Focus incluido) y los rulings del ledger.
   - Críticos e Importantes: los arregla un `sonnet`, cada uno con su test RED→GREEN.
   - **Re-revisión acotada** solo si un arreglo toca `migrations/` o alguno de los cuatro temas de seguridad del método. No se espera ninguno: no cambian las reglas.
5. **`code-review`** en nivel `high`, más los arreglos (misma regla de re-revisión).
6. **Cierre:**
   - en `docs/superpowers/backlog.md`, sección «2026-10-14 — `agent/menores-despensa-recetas`», tachar M2 a M5 y marcarlos **Resuelto** en `agent/menores-has-cocinado`;
   - marcar el interno «`ItemNotFound` → …» como **Resuelto de paso** en `agent/menores-has-cocinado` (ruling 3);
   - M1: si hubo Tarea 5, tacharlo como **Resuelto**; si no, añadir a su línea «No se reprodujo en N intentos en `agent/menores-has-cocinado`»;
   - añadir los menores aplazados en una sección nueva, con la fecha y la rama;
   - el usuario decide qué hacer con la rama. **Sin push ni despliegue.**

---

### Task 1: Reproducir la hoja «Nueva comida» invisible tras girar con el selector abierto

**Files:** ninguno del repositorio. Todo lo que produce va a `SCRATCH/repro-hoja/`: capturas, volcados de `uiautomator`, `logcat` y el informe `INFORME.md`.

**Interfaces:**
- Consumes: el entorno que deja el coordinador. El emulador `Pixel_10` está arrancado, con la app debug instalada y conectada a los emuladores de Firebase. El usuario es `david@bobitos.invalid` / `bobitos123`, el espacio «Casa de prueba» y la receta personal «Arroz con leche».
- Produces: `SCRATCH/repro-hoja/INFORME.md`, que empieza por una de estas líneas: `RESULTADO: REPRODUCIDO`, `RESULTADO: NO REPRODUCIDO` o `RESULTADO: BLOCKED`. La Tarea 5 (si existe) depende de este informe.

**Reglas de esta tarea:**
- **No cambies ningún fichero del repositorio** ni hagas commits. Si para confirmar una hipótesis haría falta cambiar código, describe el experimento en el informe: lo decide el coordinador.
- No pares ni reinicies los emuladores. Si algo del entorno falla (la app no conecta o no hay emulador), para y devuelve BLOCKED con lo que has visto.
- Textos de la app que vas a buscar (de `RES`): «Inicia sesión», «Correo electrónico», «Contraseña», «Entrar», «Comidas», «Nueva comida», «Elegir de una receta», «Elegir receta», «Buscar receta», «Guardar», «Cancelar» y «¿Descartar los cambios?».

- [ ] **Step 1: Sesión y pantalla de partida.**
  - Abre la app: `ADB shell monkey -p com.dlunaunizar.bobitos -c android.intent.category.LAUNCHER 1`. Si el `applicationId` de debug lleva sufijo, sácalo de `app/build.gradle.kts`.
  - Inicia sesión con `david@bobitos.invalid` / `bobitos123`. Para tocar y escribir, usa `ADB shell input tap X Y` y `ADB shell input text …`, con las coordenadas sacadas de `ADB shell uiautomator dump /sdcard/ui.xml` + `ADB pull /sdcard/ui.xml SCRATCH/repro-hoja/`.
  - Entra en «Casa de prueba» → Comidas.
  - Fija la rotación a mano: `ADB shell settings put system accelerometer_rotation 0` y `ADB shell settings put system user_rotation 0`.
  - Captura de partida: `ADB exec-out screencap -p > SCRATCH/repro-hoja/00-partida.png`. Mírala con la herramienta Read.

- [ ] **Step 2: Un intento (el procedimiento base).** Cada intento hace esto:
  1. Toca el botón flotante «Nueva comida»: se abre la hoja.
  2. Toca «Elegir de una receta»: se abre el selector «Elegir receta».
  3. Lo que diga la variante (buscar, teclado…).
  4. Gira a horizontal (`ADB shell settings put system user_rotation 1`), espera W1 y vuelve a vertical (`… user_rotation 0`). Después espera W2.
  5. Toca la fila «Arroz con leche» del selector (coordenadas del volcado hecho **después** del giro).
  6. Espera 1,5 s y guarda la captura `NN-tras-elegir.png` y el volcado `NN-tras-elegir.xml`, más `ADB shell dumpsys window windows | grep -E "Window #|mCurrentFocus|mFrame"` en `NN-ventanas.txt`.
  7. Clasifica el intento:
     - **Bien:** la captura muestra la hoja «Nueva comida» con «Arroz con leche» en el nombre y el botón «Guardar».
     - **Fallo (el del backlog):** la captura no muestra la hoja. Entonces toca una zona vacía de la pantalla (por ejemplo, la cabecera de la semana) y haz otra captura. Si sale «¿Descartar los cambios?», el fallo está **reproducido**.
     - **Otro:** cualquier otra cosa rara (la hoja a medias, el selector sigue abierto, un cierre de la app…). Apúntalo con sus capturas.
  8. Deja la pantalla limpia para el siguiente intento: «Cancelar» → «Descartar» (o atrás) hasta volver a Comidas sin hoja. Comprueba con una captura que no queda ninguna hoja ni diálogo.

- [ ] **Step 3: Las variantes.** 4 intentos de cada una (20 en total), en este orden:
  - **A (la del recorrido original):** escribe «Arroz» en «Buscar receta» y cierra el teclado (`ADB shell input keyevent 4` solo si el teclado está abierto; comprueba con una captura que el selector sigue abierto). W1 = 3 s, W2 = 3 s.
  - **B (giro rápido):** sin buscar. W1 = 0,3 s y W2 = 2 s: se vuelve a vertical antes de que termine de recrearse la actividad.
  - **C (elegir enseguida):** sin buscar. W1 = 3 s y W2 = 0,2 s: se elige la receta nada más volver a vertical.
  - **D (con el teclado abierto):** toca «Buscar receta», escribe «Arroz» y gira **con el teclado abierto**. W1 = 3 s, W2 = 3 s.
  - **E (doble giro):** sin buscar. Horizontal → vertical → horizontal → vertical, con 1 s entre cada uno. W2 = 2 s.

  Si un intento da **Fallo**, sigue con el Step 4 cuando acabes la variante. Si los 20 dan **Bien**, salta al Step 5.

- [ ] **Step 4: Solo si se reproduce: frecuencia y causa.**
  1. Repite 5 veces más la variante que falló. Anota cuántas fallan.
  2. En el estado del fallo, recoge:
     - el volcado de `uiautomator` (¿están los nodos de la hoja, «Nueva comida» y «Guardar»? ¿con qué `bounds`? ¿fuera de la pantalla?);
     - las ventanas de `dumpsys window` (¿hay una ventana de la hoja? ¿visible? ¿con qué marco?);
     - `ADB logcat -d -t 2000 > SCRATCH/repro-hoja/logcat.txt`, y busca excepciones y avisos de Compose.
  3. Lee el código que interviene:
     - `APP/core/designsystem/component/BobitosFormSheet.kt` (`ModalBottomSheet` con `rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = …)`);
     - `APP/feature/meals/MealsScreen.kt` (`MealEditorHost` y `MealEditor`; `pickerOpen` es `rememberSaveable(meal?.id)` desde `c796fe1`);
     - `APP/feature/meals/RecipePicker.kt` (`RecipePickerDialog`, un `AlertDialog`);
     - `APP/core/designsystem/component/EditorItem.kt` (`rememberEditorSlot`).
  4. Contrasta estas hipótesis con lo recogido, y añade otras si los datos apuntan a otra parte:
     - **H1:** al recrearse la actividad, la hoja restaura su estado `Expanded`, pero su desplazamiento se calcula cuando la ventana del selector ya está encima. Al cerrarse el selector, la hoja queda dibujada fuera de la pantalla mientras su ventana (con el velo transparente) sigue recogiendo los toques. Un toque llega a `onDismissRequest` → hay cambios → «¿Descartar los cambios?».
     - **H2:** el orden de las ventanas cambia tras el giro (el selector se crea antes que la hoja) y la animación `show()` de la hoja no llega a ejecutarse.
     - **H3:** es un problema de tiempos: solo pasa si se elige mientras la hoja aún está animándose tras el giro.
  5. Comprueba si pasa lo mismo con otra hoja que abra un diálogo encima: la Compra → editar un artículo → el selector de marca o tienda, si existe. Así se sabe si es de `BobitosFormSheet` en general o solo de Comidas.

- [ ] **Step 5: El informe.** Escribe `SCRATCH/repro-hoja/INFORME.md`:
  - primera línea: `RESULTADO: REPRODUCIDO`, `RESULTADO: NO REPRODUCIDO` o `RESULTADO: BLOCKED`;
  - una tabla con cada intento: variante, número, clasificación y ruta de su captura;
  - **si se reproduce**:
    - los pasos mínimos exactos y la frecuencia («falla 3 de 9 con la variante C»);
    - la causa, con la evidencia (volcado, ventanas, `logcat`, líneas de código);
    - el arreglo que propones, con el fichero, el código y su alcance (¿afecta a todas las hojas de la app?);
    - cómo escribir un test que falle antes del arreglo. Si no se puede (solo falla con giros reales y a veces), dilo, y propón como rojo la reproducción en el emulador: «falla N de M antes; 0 de M después»;
  - **si no se reproduce**: el número de intentos y las variantes probadas.

  Devuelve al coordinador el contenido del informe. **Sin commit.**

---

### Task 2: Si la lista cambia mientras «Listo» guarda, el mensaje dice que se vuelva a pulsar «Listo»

**Files:**
- Modify: `APP/feature/meals/MealUiMessage.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt` (`confirmCooked`, `runAction`, `ShoppingFailure.toUiMessage` y la función nueva `Throwable.toCookedUiMessage`)
- Modify: `APP/feature/meals/MealsScreen.kt` (solo `MealUiMessage.stringResourceId`)
- Modify: `RES`
- Modify: `docs/FUNCTIONAL_SPEC.md` (PANTRY-05)
- Modify (test): `UT/feature/meals/MealsCookedViewModelTest.kt`

**Interfaces:**
- Consumes: `shoppingRepository.cookedFailure`, `cookedCalls` y `currentItemsCalls` de `RecordingShoppingRepository` (`UT/feature/common/RecordingShoppingRepository.kt`). Un fallo de `applyCooked` no se apunta en `cookedCalls`.
- Produces:
  - `MealUiMessage.CookedShoppingChanged`;
  - `private fun runAction(successNotice: MealUiMessage?, editor: Boolean = false, toMessage: (Throwable) -> MealUiMessage = Throwable::toUiMessage, action: suspend () -> Unit)`;
  - `private fun Throwable.toCookedUiMessage(): MealUiMessage` (nivel superior de `MealsViewModel.kt`).

  La Tarea 4 reescribe el cuerpo de `confirmCooked` y mantiene `runAction(MealUiMessage.CookedReviewApplied, toMessage = Throwable::toCookedUiMessage) { … }`.

- [ ] **Step 1: Tests.** En `UT/feature/meals/MealsCookedViewModelTest.kt`:

  1. En el test `` `if an item to put back on the list was deleted meanwhile done says the list changed` ``, cambia **solo** la aserción del error:

  ```kotlin
              assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.error)
  ```

  2. Añade, justo después de ese test:

  ```kotlin
      @Test
      fun `if the list changes while done saves a retry reads it again and writes once`() =
          runTest(mainDispatcherRule.testDispatcher) {
              recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
              pantryRepository.serverIds = setOf(pantryId("Leche"))
              viewModel.observe("home")
              advanceUntilIdle()
              viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
              advanceUntilIdle()
              val review = viewModel.uiState.value.cooked!!
              val choice = review.defaultChoice().toggleFinished(pantryId("Leche"))
              shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

              viewModel.confirmCooked(choice)
              advanceUntilIdle()

              assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.error)
              assertSame(review, viewModel.uiState.value.cooked)
              assertFalse(viewModel.uiState.value.isSaving)
              val readsAfterFailure = shoppingRepository.currentItemsCalls

              shoppingRepository.cookedFailure = null
              viewModel.confirmCooked(choice)
              advanceUntilIdle()

              assertEquals(readsAfterFailure + 1, shoppingRepository.currentItemsCalls)
              assertEquals(1, shoppingRepository.cookedCalls.size)
              assertNull(viewModel.uiState.value.cooked)
              assertNull(viewModel.uiState.value.error)
              assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
          }

      @Test
      fun `any other failure of done keeps its own message`() = runTest(mainDispatcherRule.testDispatcher) {
          recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
          pantryRepository.serverIds = setOf(pantryId("Leche"))
          viewModel.observe("home")
          advanceUntilIdle()
          viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
          advanceUntilIdle()
          val review = viewModel.uiState.value.cooked!!
          shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.PermissionDenied)

          viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
          advanceUntilIdle()

          assertEquals(MealUiMessage.PermissionDenied, viewModel.uiState.value.error)
          assertSame(review, viewModel.uiState.value.cooked)
      }
  ```

  Si alguna línea pasa de 120 caracteres, pártela como las vecinas (ktlint lo marca).

- [ ] **Step 2: Rojo.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado: fallo de compilación, `Unresolved reference 'CookedShoppingChanged'`. Copia el fallo.

- [ ] **Step 3: El texto y el valor nuevos.**

  En `RES`, justo después de `meals_error_shopping_changed`:

  ```xml
      <string name="meals_error_cooked_shopping_changed">La lista de la compra ha cambiado mientras guardabas. No se ha guardado nada: vuelve a pulsar «Listo».</string>
  ```

  En `APP/feature/meals/MealUiMessage.kt`, justo después de `ShoppingChanged,`:

  ```kotlin
      CookedShoppingChanged,
  ```

  En `APP/feature/meals/MealsScreen.kt`, en `MealUiMessage.stringResourceId`, justo después de la línea de `ShoppingChanged`:

  ```kotlin
          MealUiMessage.CookedShoppingChanged -> R.string.meals_error_cooked_shopping_changed
  ```

- [ ] **Step 4: El mapeo en la llamada.** En `APP/feature/meals/MealsViewModel.kt`:

  1. `runAction` recibe el mapeo de errores. Sustituye la función entera por:

  ```kotlin
      private fun runAction(
          successNotice: MealUiMessage?,
          editor: Boolean = false,
          toMessage: (Throwable) -> MealUiMessage = Throwable::toUiMessage,
          action: suspend () -> Unit,
      ) {
          if (mutableUiState.value.isSaving) return
          mutableUiState.update {
              it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
          }
          viewModelScope.launch {
              runCatching { withEditorSaveTimeout(editor) { action() } }
                  .onSuccess {
                      mutableUiState.update {
                          it.copy(isSaving = false, notice = successNotice, editorSave = it.editorSave.succeeded(editor))
                      }
                  }
                  .onFailure { error ->
                      if (error is CancellationException) throw error
                      showError(toMessage(error), editor)
                  }
          }
      }
  ```

  2. En `confirmCooked`, cambia **solo** la línea de `runAction`:

  ```kotlin
          runAction(MealUiMessage.CookedReviewApplied, toMessage = Throwable::toCookedUiMessage) {
  ```

  Y añade al final del comentario que hay encima de `confirmCooked` esta línea:

  ```kotlin
      // Si la lista ha cambiado entretanto, basta con volver a pulsar «Listo» (toCookedUiMessage).
  ```

  3. Justo después de `private fun Throwable.toUiMessage(): MealUiMessage = when (this) { … }`, añade:

  ```kotlin
  // «Listo» en «Has cocinado …»: si un artículo que se vuelve a apuntar ha cambiado o se ha borrado, no se ha
  // escrito nada y basta con volver a pulsar «Listo», que vuelve a leer la lista. Los demás errores, como en el resto
  // de Comidas.
  private fun Throwable.toCookedUiMessage(): MealUiMessage {
      val failure = (this as? ShoppingRepositoryException)?.failure
      return if (failure == ShoppingFailure.ItemChanged || failure == ShoppingFailure.ItemNotFound) {
          MealUiMessage.CookedShoppingChanged
      } else {
          toUiMessage()
      }
  }
  ```

  4. Sustituye `private fun ShoppingFailure.toUiMessage(): MealUiMessage` entera por:

  ```kotlin
  private fun ShoppingFailure.toUiMessage(): MealUiMessage = when (this) {
      ShoppingFailure.NotAuthenticated -> MealUiMessage.NotAuthenticated
      ShoppingFailure.EmailNotVerified -> MealUiMessage.EmailNotVerified
      ShoppingFailure.SpaceNotFound -> MealUiMessage.SpaceNotFound
      ShoppingFailure.PermissionDenied -> MealUiMessage.PermissionDenied
      ShoppingFailure.Network -> MealUiMessage.NetworkError
      // La revisión de «Añadir a la compra» convierte ItemNotFound en ItemChanged (applySeenWrites); «Listo» al cocinar
      // elige su propio mensaje (toCookedUiMessage).
      ShoppingFailure.ItemChanged -> MealUiMessage.ShoppingChanged
      ShoppingFailure.ItemNotFound,
      ShoppingFailure.NameRequired,
      ShoppingFailure.NameTooLong,
      ShoppingFailure.QuantityTooLong,
      ShoppingFailure.NotesTooLong,
      ShoppingFailure.BrandTooLong,
      ShoppingFailure.Unknown,
      -> MealUiMessage.UnexpectedError
  }
  ```

- [ ] **Step 5: La spec funcional.** En `docs/FUNCTIONAL_SPEC.md`, PANTRY-05, sustituye la tercera viñeta por:

  ```markdown
  - «Listo» lo escribe todo a la vez: si algo falla (por ejemplo, sin conexión), no se escribe nada y el diálogo sigue abierto, con lo marcado y el motivo, para volver a intentarlo. Lo que otro miembro ya ha tachado o ha quitado de la lista se salta. Si a la vez otro miembro cambia algo de lo que se vuelve a apuntar, el diálogo avisa de que la lista ha cambiado y basta con volver a pulsar «Listo».
  ```

- [ ] **Step 6: Verde.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest` y de `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: PASS los dos; en `MealsViewModelTest` siguen en verde, sin tocarlos, `a shopping list changed while reviewing is reported` y `a shopping item deleted while reviewing is reported as a changed list`. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealUiMessage.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt \
  app/src/main/res/values/strings.xml \
  docs/FUNCTIONAL_SPEC.md \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt
git commit -m "fix(comidas): si la lista cambia mientras «Listo» guarda, el aviso dice que se vuelva a pulsar «Listo»"
```

---

### Task 3: En «Has cocinado …», el lector de pantalla anuncia el error y lo marcado sobrevive al giro

**Files:**
- Modify: `APP/feature/meals/CookedDialog.kt`
- Modify: `APP/feature/meals/CookedReview.kt` (solo `CookedChoice`)
- Modify (test): `AT/feature/meals/CookedDialogUiTest.kt`

**Interfaces:**
- Consumes: `CookedDialog(review, saving, errorMessage, onConfirm, onDismiss)`; `CookedReview(mealName, crossOff, finished)`; `CookedPantryRow(id, name)`; `CookedChoice(crossOffIds, finishedIds, restock)`.
- Produces: `@Parcelize data class CookedChoice(...) : Parcelable`, con los mismos campos y el mismo constructor. No cambia ninguna firma.

- [ ] **Step 1: Test del anuncio (instrumentado).** En `AT/feature/meals/CookedDialogUiTest.kt`, añade estos imports:

```kotlin
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
```

  Y añade este test después de `doneCannotBePressedWhileSaving`:

```kotlin
    @Test fun theScreenReaderAnnouncesTheError() {
        setDialog(saving = false, errorMessage = "Sin conexión.")
        compose.onNodeWithText("Sin conexión.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
```

- [ ] **Step 2: Rojo.** **INSTR** de `com.dlunaunizar.bobitos.feature.meals.CookedDialogUiTest`. Esperado: FAIL en `theScreenReaderAnnouncesTheError` (`Failed to assert the following: (LiveRegion = 'Polite')`). Copia el fallo.

- [ ] **Step 3: El anuncio.** En `APP/feature/meals/CookedDialog.kt`:

  Añade los imports:

```kotlin
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
```

  Y en el `Text` del error, sustituye `modifier = Modifier.padding(bottom = Spacing.sm),` por:

```kotlin
                        // Como en BobitosFormSheet: el lector de pantalla lo anuncia al aparecer.
                        modifier = Modifier
                            .padding(bottom = Spacing.sm)
                            .semantics { liveRegion = LiveRegionMode.Polite },
```

- [ ] **Step 4: Verde y commit.** **INSTR** de `…feature.meals.CookedDialogUiTest`. Esperado: PASS (los 3 tests).

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/CookedDialog.kt \
  app/src/androidTest/java/com/dlunaunizar/bobitos/feature/meals/CookedDialogUiTest.kt
git commit -m "fix(comidas): el lector de pantalla anuncia el error de «Has cocinado …»"
```

- [ ] **Step 5: Test del giro (instrumentado).** En `AT/feature/meals/CookedDialogUiTest.kt`, añade estos imports:

```kotlin
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performClick
```

  Y añade al final de la clase:

```kotlin
    private fun row(label: String) = compose.onNode(hasText(label) and isToggleable())

    @Test fun whatIsMarkedSurvivesRotating() {
        val withPantry = review.copy(finished = listOf(CookedPantryRow("leche", "Leche")))
        val restock = compose.activity.getString(R.string.meals_cooked_restock)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            BobitosTheme {
                CookedDialog(review = withPantry, saving = false, errorMessage = null, onConfirm = {}, onDismiss = {})
            }
        }
        // Todo lo contrario de lo que viene por defecto: sin tachar, acabado y sin apuntar a la compra.
        row("Lentejas").performClick()
        row("Leche").performClick()
        row(restock).performClick()
        row("Lentejas").assertIsOff()
        row("Leche").assertIsOn()
        row(restock).assertIsOff()

        // Lo que pasa al girar el móvil: se guarda el estado, se descarta la composición y se recrea.
        restoration.emulateSavedInstanceStateRestore()

        row("Lentejas").assertIsOff()
        row("Leche").assertIsOn()
        row(restock).assertIsOff()
    }
```

- [ ] **Step 6: Rojo.** **INSTR** de `…feature.meals.CookedDialogUiTest`. Esperado: FAIL en `whatIsMarkedSurvivesRotating`, en la primera aserción después de `emulateSavedInstanceStateRestore()`: «Lentejas» vuelve a estar marcada. Copia el fallo.

- [ ] **Step 7: Lo marcado se guarda.**

  En `APP/feature/meals/CookedReview.kt`, añade los imports `android.os.Parcelable` y `kotlinx.parcelize.Parcelize`, y sustituye la declaración de `CookedChoice` (con su KDoc) por:

```kotlin
/**
 * Lo marcado en el diálogo: los artículos a tachar, lo que se ha acabado y si se apunta a la compra. Parcelable para
 * que sobreviva a una rotación.
 */
@Parcelize
data class CookedChoice(val crossOffIds: Set<String>, val finishedIds: Set<String>, val restock: Boolean) : Parcelable
```

  En `APP/feature/meals/CookedDialog.kt`:
  - sustituye `var choice by remember(review) { mutableStateOf(review.defaultChoice()) }` por:

```kotlin
    // Sobrevive a una rotación; con otra revisión, vuelve a lo de por defecto.
    var choice by rememberSaveable(review) { mutableStateOf(review.defaultChoice()) }
```

  - añade el import `androidx.compose.runtime.saveable.rememberSaveable` y quita `androidx.compose.runtime.remember`, que se queda sin uso (`rememberScrollState` tiene su propio import);
  - en el comentario de cabecera de `CookedDialog`, cambia «si falla, el error sale dentro y lo marcado se conserva (el diálogo sigue con la misma revisión).» por «si falla, el error sale dentro y lo marcado se conserva (el diálogo sigue con la misma revisión), también al girar el móvil.».

- [ ] **Step 8: Verde.** **INSTR** de `…feature.meals.CookedDialogUiTest`. Esperado: PASS (los 4 tests). **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest` y de `com.dlunaunizar.bobitos.feature.meals.CookedReviewTest`. Esperado: PASS. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/CookedDialog.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/CookedReview.kt \
  app/src/androidTest/java/com/dlunaunizar/bobitos/feature/meals/CookedDialogUiTest.kt
git commit -m "fix(comidas): lo marcado en «Has cocinado …» sobrevive al giro del móvil"
```

---

### Task 4: «Listo» lee la lista mientras espera al catálogo

**Files:**
- Modify: `APP/feature/meals/MealsViewModel.kt` (solo `confirmCooked` y sus imports)
- Modify (test): `UT/feature/meals/MealsCookedViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `runAction(..., toMessage = Throwable::toCookedUiMessage) { … }` (Tarea 2);
  - `ingredientData()`, que espera 5 s como máximo (`CATALOG_TIMEOUT_MILLIS`);
  - `pantryRestock(names, items, data).writes`, `CookedWrites.restockNamesIn(data)` y `pantryEntryFor(name, data)`;
  - en los tests: `ingredientRepository.catalogOverride` (un `flow { awaitCancellation() }` simula el catálogo que no llega), `shoppingRepository.itemsFailure` (hace fallar `currentItems`) y `shoppingRepository.currentItemsCalls`.
- Produces: nada nuevo; misma firma de `confirmCooked(choice: CookedChoice)`.

- [ ] **Step 1: Tests.** En `UT/feature/meals/MealsCookedViewModelTest.kt`, añade al final de la clase:

```kotlin
    @Test
    fun `done reads the list while it waits for the catalog`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogOverride = flow { awaitCancellation() }
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        runCurrent()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
        runCurrent()
        val review = viewModel.uiState.value.cooked!!
        val readsBefore = shoppingRepository.currentItemsCalls

        viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
        runCurrent()

        assertEquals(readsBefore + 1, shoppingRepository.currentItemsCalls)
        assertTrue(shoppingRepository.cookedCalls.isEmpty())

        advanceTimeBy(5_001)
        runCurrent()
        val restock = shoppingRepository.cookedCalls.single().restock
        assertEquals(listOf("Leche"), restock.map { (it as ShoppingWrite.Add).name })
    }

    @Test
    fun `if the list cannot be read done fails at once without waiting for the catalog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow { awaitCancellation() }
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            runCurrent()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            runCurrent()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            runCurrent()

            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isSaving)
            assertSame(review, viewModel.uiState.value.cooked)

            advanceTimeBy(5_001)
            runCurrent()
            assertTrue(shoppingRepository.cookedCalls.isEmpty())
        }

    @Test
    fun `done with nothing to put on the list does not read the list`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
        shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
        advanceUntilIdle()
        val review = viewModel.uiState.value.cooked!!
        val readsBefore = shoppingRepository.currentItemsCalls

        viewModel.confirmCooked(review.defaultChoice())
        advanceUntilIdle()

        assertEquals(readsBefore, shoppingRepository.currentItemsCalls)
        assertEquals(1, shoppingRepository.cookedCalls.size)
    }
```

  Si alguna línea pasa de 120 caracteres, pártela como las vecinas.

- [ ] **Step 2: Rojo.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest`. Esperado:
  - FAIL en `done reads the list while it waits for the catalog`: `currentItemsCalls` no ha subido, porque la lista se lee después de esperar;
  - FAIL en `if the list cannot be read done fails at once without waiting for the catalog`: `error` es `null`, porque aún espera al catálogo;
  - PASS en `done with nothing to put on the list does not read the list`, que protege lo de hoy.

  Copia los fallos.

- [ ] **Step 3: La lectura a la vez.** En `APP/feature/meals/MealsViewModel.kt`, añade los imports `kotlinx.coroutines.async` y `kotlinx.coroutines.coroutineScope`, y sustituye el bloque de `runAction` de `confirmCooked` (de `runAction(MealUiMessage.CookedReviewApplied, …) {` a su `}`) por:

```kotlin
        runAction(MealUiMessage.CookedReviewApplied, toMessage = Throwable::toCookedUiMessage) {
            val needsCatalog = writes.crossOff.isNotEmpty() || writes.restockNames.isNotEmpty()
            // La lista se lee mientras se espera al catálogo: si no se puede leer, el error sale enseguida.
            coroutineScope {
                // «A la compra» sobre la lista del servidor: con una caché vieja se duplicaría el artículo.
                val items = if (writes.restockNames.isEmpty()) {
                    null
                } else {
                    async { shoppingRepository.currentItems(spaceId) }
                }
                val data = if (needsCatalog) ingredientData() else IngredientShoppingData()
                val restock = if (items == null) {
                    emptyList()
                } else {
                    pantryRestock(writes.restockNamesIn(data), items.await(), data).writes
                }
                shoppingRepository.applyCooked(
                    spaceId,
                    writes.crossOff.map { ShoppingCrossOff(it.id, pantryEntryFor(it.name, data)) },
                    writes.finishedIds,
                    restock,
                )
            }
            mutableUiState.update { it.copy(cooked = null) }
        }
```

  No cambies nada más de `confirmCooked`: ni la comprobación de `writes.isEmpty` ni el comentario de encima.

- [ ] **Step 4: Verde.** **JVM** de `com.dlunaunizar.bobitos.feature.meals.MealsCookedViewModelTest` y de `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: PASS los dos, incluido `crossing off without the catalog in time waits at most 5 seconds and marks without a pantry entry`, sin tocarlo. Después, **GRADLE**. Esperado: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsCookedViewModelTest.kt
git commit -m "fix(comidas): «Listo» lee la lista mientras espera al catálogo"
```

---

### Task 5 (condicional): la hoja «Nueva comida» sigue visible tras girar con el selector abierto

**Solo existe si la Tarea 1 devuelve `RESULTADO: REPRODUCIDO`.** Si devuelve `NO REPRODUCIDO`, esta tarea no se escribe ni se ejecuta, y M1 se queda en el backlog (ruling 1).

Si se reproduce, el coordinador sustituye esta sección por la tarea completa, en el mismo formato que las demás, a partir de `SCRATCH/repro-hoja/INFORME.md`, y hace commit del plan en la rama antes de lanzarla. La tarea debe llevar:
- **Files** e **Interfaces** concretos, sacados del arreglo que propone el informe;
- **un rojo antes del arreglo**:
  - un test (instrumentado si hace falta, en `AT/feature/meals/` o en `AT/core/designsystem/`) que falle por la causa encontrada;
  - si el informe dice que solo falla con giros reales y a veces, el rojo es la reproducción en el emulador con la variante que falló: «falla N de M antes; 0 de M después», con M ≥ 10;
- el código del arreglo, sin tocar las firmas del baseline (`MealsScreen(...)`, `MealCard(...)`);
- **INSTR** de `com.dlunaunizar.bobitos.feature.meals.MealEditorUiTest` (el selector sigue abierto tras el giro) y, si el arreglo toca `BobitosFormSheet`, de las clases de `AT` que la usen; después, **GRADLE**;
- un commit `fix(comidas): …` con los ficheros con nombre.

Antes de escribirla, el coordinador **para y pregunta al usuario** en los casos del ruling 1 (dependencias, la confirmación de descarte o el comportamiento de todas las hojas).

---

## Verificación de punta a punta (coordinador, al final)

- **Suites:**
  - GRADLE, NODE y EMU en verde. EMU no cambia, pero se pasa entera; antes, para los emuladores de Firebase del entorno, que ocupan los mismos puertos.
  - INSTR de `CookedDialogUiTest` y de `MealEditorUiTest`; si hubo Tarea 5, también sus clases.
- **Recorrido en la app real**, después de las INSTR (que desinstalan la app):
  - vuelve a arrancar los emuladores de Firebase y repite los puntos 3 a 5 de la preparación («Ejecución», paso 2);
  - inicia sesión con `david@bobitos.invalid` / `bobitos123`;
  - haz capturas de cada paso en `SCRATCH/e2e/`.

  1. **Giro con «Has cocinado …» abierto (M4):**
     - Comidas → «Nueva comida» → «Elegir de una receta» → «Arroz con leche» → «Guardar».
     - En la tarjeta de la comida, «Marcar como cocinada». Sale «Has cocinado Arroz con leche», con «Tachar de la compra» y «Leche» marcada (la «Leche» pendiente de la semilla).
     - Desmarca «Leche».
     - Gira a horizontal (`ADB shell settings put system accelerometer_rotation 0` y `… user_rotation 1`) y vuelve a vertical (`… user_rotation 0`).
     - El diálogo sigue abierto y «Leche» sigue desmarcada (captura).
     - «Cancelar».
  2. **La hoja «Nueva comida» (M1), solo si hubo Tarea 5:** repite M ≥ 10 veces la variante que falló en la Tarea 1. Esperado: 0 fallos.

## Despliegue (fuera de este plan)

Aplazado por el usuario. Este plan **no cambia `firestore.rules`**. Lo que ya estaba pendiente sigue igual: las reglas de la Despensa están sin desplegar. Hay que desplegarlas (`npx firebase deploy --only firestore:rules --project dev`) **antes** de distribuir una beta que lleve este trabajo, y desde el mismo commit que se distribuye.
