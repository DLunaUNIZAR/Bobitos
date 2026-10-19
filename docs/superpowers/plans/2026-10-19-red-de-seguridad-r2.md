# Red de seguridad, segunda vuelta (R2): plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans.
> - Es un **plan grande** (6 tareas): pasos 1 a 6, con `simplify`.
> - **Ninguna tarea cambia lo que ve el usuario.** Solo cambian la CI, tests y, para poder probar, la visibilidad o el sitio de tres piezas (`afterDownload`, `restoredFields` y la lectura de fechas de recetas). Por eso no hay revisión de diseño.
> - Cada tarea la hace un subagente `sonnet`, en serie y en el orden del plan.
> - La revisión final la hace `opus` (si falla, `fable`).
> - Los commits, el push y el PR los hace el coordinador.
> - **Instrucción del usuario para este plan (anótala como ruling al crear el ledger):** después del commit de la tarea 1, el coordinador sube la rama a `origin` y abre un **PR en borrador** contra `main` para que corran `ci.yml` e `instrumented.yml`. La vuelve a subir antes de la pregunta del paso 6, y el plan no se cierra hasta ver las dos en verde en el último commit. En el paso 6, las cuatro opciones siguen siendo del usuario:
>   - «PR» = quitar el borrador;
>   - «descartar» = cerrar el PR y borrar también la rama remota.
> - **Ninguna tarea cambia `firestore.rules` ni `migrations/`.** Las mutaciones temporales (también las de `firestore.rules` en la tarea 2) se deshacen antes del informe.
> - Para la regla de re-revisión, cuenta como «permisos» cualquier arreglo que toque `firestore.rules`.
> - Los pasos usan casillas (`- [ ]`).

**Paralelo:** no (la puerta arranca el Emulator Suite de Firebase en puertos fijos, y los instrumentados comparten el único emulador de Android).

**Objetivo:** que la CI ejecute los tests instrumentados en cada push a `main` y en cada PR que toquen la app, que las tres suites de Node se ejecuten aunque falle una, y que los tests de 12 líneas del backlog midan lo que dicen o cubran los huecos que dejaron G1, G2 y la compra.

**Arquitectura:**
- **CI:**
  - un workflow nuevo, `instrumented.yml`, con un emulador de `reactivecircus/android-emulator-runner`, según el ADR-0001;
  - la app de debug deja de caerse sin `google-services.json`, porque Crashlytics ya no exige el build ID en debug;
  - en `ci.yml`, las suites de Node dejan de cortarse unas a otras.
- **Tests:** cada test nuevo o corregido se demuestra con una **mutación temporal**: se rompe a propósito el código, el fixture o las reglas, se ve fallar el test y se deshace la mutación (como en G1).

**Tecnología:**
- Kotlin, Jetpack Compose, AGP 9.2.1 y Firebase BOM 34.15.0 (Crashlytics 20.0.6).
- Tests JUnit 4 (JVM), Compose instrumentados en el emulador `Pixel_10` (API 37) y `node --test` con el Emulator Suite de Firebase (`demo-bobitos`).
- GitHub Actions: `reactivecircus/android-emulator-runner@v2.38.0` y `rhysd/actionlint:1.7.12` (Docker, en local).

**Spec:** `docs/superpowers/specs/2026-10-19-red-de-seguridad-r2-design.md` y su ADR, `docs/adr/0001-tests-instrumentados-en-la-ci.md`. Las líneas Lnnn son de `docs/superpowers/backlog.md` en `2e417c6`.

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
- **Suites de Node:** `npm run test:emulators` (reglas), `npm run test:scripts` (scripts del catálogo) y `npm run test:catalog-import` (importadores, con emulador).
- **Puerta de cada tarea (`gate.sh`):** `./gradlew ktlintCheck detekt testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug lintDebug && npm run test:emulators && npm run test:scripts && npm run test:catalog-import`, más los tests instrumentados que nombre la tarea.
  - Si un cambio rompe `config/detekt/baseline.xml`, regenera el baseline con `./gradlew detektBaseline` e inclúyelo en la tarea. Su diff solo puede cambiar las entradas de las funciones que la tarea mueve.

**Mutaciones (cómo se demuestra el RED de un test sobre algo que ya funciona)**
- La tarea dice qué mutar. Aplica la mutación, ejecuta el test y copia en el informe la aserción que falla. Después deshaz la mutación y vuelve a ejecutar el test, que debe pasar.
- Antes del informe, `git diff` no puede contener ninguna mutación.

**Lo que no se hace**
- **Nunca se mueve, se borra ni se copia `app/google-services.json`.** Está en `.gitignore` y es del usuario. Para probar sin él se usa un `git worktree` aparte (tarea 1).
- **Ni `@Ignore`, ni `@FlakyTest`, ni filtros que quiten tests, ni reintentos ni `continue-on-error` en el paso del emulador.** Si un test falla solo en la CI, se corrige el test para que no dependa del dispositivo y siga midiendo lo mismo. Si no se puede, para y dilo en el informe.
- **Si un test corregido destapa un fallo real que el usuario vería, no lo arregles.** Para y dilo en el informe. El coordinador lo anota como menor con efecto visible.
- Los nombres de test siguen el estilo de su fichero:
  - JVM: frases en inglés entre comillas invertidas;
  - instrumentados: camelCase;
  - Node: frases en español.

## Review Focus

1. **El job del emulador ejecuta de verdad toda la suite:** el informe de la CI trae tantos `testcase` como `@Test` hay en `app/src/androidTest`, no 0 ni unos pocos por un filtro o un módulo equivocado. Comprobación en la tarea 1 y en el cierre.
2. **Un test de reglas roto sigue poniendo en rojo el job `firebase-rules`**, aunque ahora las suites de scripts e importadores se ejecuten después. Ningún paso lleva `continue-on-error` ni `|| true`. Comprobación en la tarea 1.
3. **El dueño que reescribe su receta conservando dueño, autor y fecha de alta sigue pudiendo**, mientras que otro usuario, con los mismos campos, ya no. `assertSucceeds` y `assertFails` en la tarea 2.
4. **Instalar una beta:** después de «Descargada», un fallo de instalación sigue contando como cancelación. Sin descarga, o con un fallo de descarga, sigue siendo un error. Lo cubre el tracker, tanto por `onProgress` como por `onFailure`, en la tarea 3.
5. **Deshacer el borrado de una común importada:** tras el redondeo a milisegundos, `source.importedAt` no queda después de `updatedAt`. Si quedara después, la regla rechazaría la restauración y la común se perdería. Test en la tarea 5.

---

### Task 1: Los instrumentados en la CI y las suites de Node sin cortarse

Hallazgo de la auditoría, L62 y L66.

Depende de: ninguna

**Ficheros:**
- Crear: `.github/workflows/instrumented.yml`.
- Crear (solo si el paso 1 reproduce la caída): `app/src/debug/res/values/crashlytics.xml`.
- Modificar: `.github/workflows/ci.yml`, job `firebase-rules`, pasos «Install dependencies», «Catalog scripts tests» y «Catalog import tests (emulator)».

**Interfaces:**
- Produce: el workflow `Instrumented tests`, con el job `connected-tests` («Instrumented tests (emulator)») y el artefacto `instrumented-test-reports`. El cierre del plan los usa para contar los tests.

- [ ] **Paso 1: RED. La app de debug se cae sin `google-services.json`.**
  - Crea un worktree limpio fuera del repo: `WT=$(mktemp -d)/r2-sin-google-services; git worktree add --detach "$WT" HEAD`. No tiene `app/google-services.json` porque está en `.gitignore`. No copies ese fichero ni `local.properties`.
  - En `$WT`, con `export ANDROID_HOME="$HOME/Library/Android/sdk"` y el emulador encendido, ejecuta `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.core.designsystem.component.SectionHeaderTest`.
  - Esperado: el proceso se cae y `adb logcat -d | grep -m1 "Crashlytics build ID is missing"` encuentra la excepción. Copia las dos salidas en el informe.
  - Si no se cae, dilo en el informe, sáltate el paso 2 y no crees `crashlytics.xml`.

- [ ] **Paso 2: GREEN. Crea `app/src/debug/res/values/crashlytics.xml`** con `<bool name="com.crashlytics.RequireBuildId">false</bool>` y un comentario de una línea: sin `google-services.json` (en la CI) no se aplica el plugin de Crashlytics, y sin esto la app de debug se cae al arrancar.
  - Copia ese fichero al mismo sitio de `$WT` y ejecuta allí la suite instrumentada **entera** (`./gradlew :app:connectedDebugAndroidTest`). Es lo más parecido a la CI que hay en local. Esperado: `BUILD SUCCESSFUL` y todos los tests en verde. Copia el resumen en el informe.
  - Después, `git worktree remove --force "$WT"`.

- [ ] **Paso 3: crea `.github/workflows/instrumented.yml`** con lo que fija el ADR:
  - `name: Instrumented tests`.
  - Disparo: `pull_request` y `push` a `[main]`, los dos con la misma lista `paths`: `app/**`, `gradle/**`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew` y `.github/workflows/instrumented.yml`.
  - `concurrency`: `group: instrumented-${{ github.ref }}` y `cancel-in-progress: true`.
  - Job `connected-tests`, con `name: Instrumented tests (emulator)`, `runs-on: ubuntu-latest` y `timeout-minutes: 45`. Pasos:
    1. Checkout, JDK 21, «Set up Android SDK» y «Set up Gradle», copiados de `android-quality` en `ci.yml`, con su comentario.
    2. «Build app and test APKs»: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`, para que el emulador no espere a la compilación.
    3. «Enable KVM», con el bloque del README del runner:
       ```bash
       echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
       sudo udevadm control --reload-rules
       sudo udevadm trigger --name-match=kvm
       ```
    4. «Instrumented tests» con `reactivecircus/android-emulator-runner@v2.38.0`:
       - `api-level: 37.0` (la imagen se llama `android-37.0`; con `37` el runner no la encuentra);
       - `target: google_apis`, `arch: x86_64`, `profile: pixel_6` (1080 × 2400 a 420 dpi, 411 dp de ancho) y `disable-animations: true`;
       - `script: ./gradlew :app:connectedDebugAndroidTest`;
       - sin caché de AVD ni reintentos.
       - Un comentario de una línea remite al ADR-0001.
    5. «Upload test reports», con `if: always()` y `actions/upload-artifact@v4`, nombre `instrumented-test-reports`, rutas `app/build/reports/androidTests/connected/debug/` y `app/build/outputs/androidTest-results/connected/debug/`, e `if-no-files-found: warn`.

- [ ] **Paso 4: L66 en `ci.yml`.**
  - Da `id: install` al paso «Install dependencies».
  - Añade `if: ${{ !cancelled() && steps.install.outcome == 'success' }}` a «Catalog scripts tests» y a «Catalog import tests (emulator)».
  - Pon encima de los dos un comentario de una línea: se ejecutan aunque falle la suite de reglas, y el job sigue en rojo igualmente.
  - No cambies el id ni el `name` de ningún job.

- [ ] **Paso 5: comprueba en local.**
  - `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.12` sin errores.
  - `grep -nE "continue-on-error|\|\| true|retry" .github/workflows/*.yml` no da nada (Review Focus 2).
  - La puerta entera.
  - En esta tarea no hay test RED de la CI: es configuración, y su prueba es el paso 7.

- [ ] **Paso 6: commit, push y PR** (coordinador).
  - Commit: `ci: ejecutar los tests instrumentados en un emulador y no cortar las suites de Node`.
  - `git push -u origin agent/red-de-seguridad-r2`.
  - `gh pr create --draft --base main --title "Red de seguridad, segunda vuelta (R2): instrumentados en la CI y tests que faltan"`. El cuerpo resume las tareas y acaba con las líneas de atribución de PR de la sesión.

- [ ] **Paso 7: GREEN en la CI.** El `task-done` de esta tarea es la puerta seguida de `gh pr checks --watch --fail-fast` (los tres jobs tienen que pasar) y del recuento de la Review Focus 1:
  ```bash
  RUN=$(gh run list --workflow instrumented.yml --branch agent/red-de-seguridad-r2 --limit 1 --json databaseId -q '.[0].databaseId')
  D=$(mktemp -d); gh run download "$RUN" -n instrumented-test-reports -D "$D"
  test "$(grep -rho '<testcase ' "$D" | wc -l)" -eq "$(grep -rho '@Test' app/src/androidTest | wc -l)"
  ```
  - La CI tarda más que el límite de 10 minutos de una llamada: lanza el `task-done` en segundo plano y espera su aviso.
  - Si la CI falla en algo que en local pasa, es un fallo de la tarea («Atascos»), con el log del paso (`gh run view <id> --log-failed`) como error. El arreglo sigue «Lo que no se hace».

### Task 2: Tests de reglas que fijan lo que protegen

L64 y L366. Solo cambia `tests/firebase-emulators.test.mjs`.

Depende de: ninguna

**Ficheros:**
- Modificar: `tests/firebase-emulators.test.mjs`:
  - el test «un setDoc de cliente no pisa una receta existente, propia o ajena» (~línea 1365);
  - el test «una marca personal admite tiendas de barrio y sigue limitada a 6» (~línea 1948).
- Mutar y restaurar: `firestore.rules`:
  - `ownsOrCuratesRecipe()` (~líneas 1043-1048);
  - `validStoreList` (~líneas 1312-1316).

**Interfaces:** ninguna.

- [ ] **Paso 1: comprueba el hueco de L64.**
  - En `firestore.rules`, dentro de `ownsOrCuratesRecipe()`, cambia `resource.data.ownerUid == request.auth.uid` (la rama de las recetas privadas, ~línea 1047) por `true` y ejecuta `npm run test:emulators`.
  - Esperado: el test de «no pisar» **sigue en verde**, porque hoy el `setDoc` ajeno falla por los campos inmutables. Copia la salida y deshaz la mutación.

- [ ] **Paso 2: corrige el test de L64.**
  - Sube `const current = (await getDoc(ref)).data();` (leída con `ownerDb`, porque la receta privada no la lee otro) a antes de los `assertFails`.
  - El `setDoc` del otro usuario pasa a `setDoc(doc(otherDb, "recipes", "pisar-propia"), { ...current, title: "Otra", updatedBy: otherId, updatedAt: serverTimestamp() })`. Conserva dueño, visibilidad, autor y fecha de alta, así que solo lo rechaza la comprobación de dueño.
  - Actualiza el comentario: el otro usuario conserva los campos inmutables y lo para no ser el dueño.
  - El `assertSucceeds` del dueño que reescribe conservando sus campos se queda como está (Review Focus 3).

- [ ] **Paso 3: RED de L64 por mutación.** Repite la mutación del paso 1. Esperado: el test de «no pisar» **falla** en el `assertFails` del otro usuario. Copia la aserción, deshaz la mutación y ejecuta la suite en verde.

- [ ] **Paso 4: L366.** En el test de tiendas de barrio, antes del `assertFails` de siete, añade `await assertSucceeds(setDoc(path("seis-barrio"), personalBrand({ stores: ["CARNICERIA", "PESCADERIA", "FRUTERIA", "PANADERIA", "DIA", "OTROS"] })));`. Son las cuatro de barrio más dos de las de antes.

- [ ] **Paso 5: RED de L366 por mutación,** con dos mutaciones por separado en `validStoreList`:
  - `stores.size() <= 6` → `<= 5`;
  - quitar `"PANADERIA"` de la lista de `hasOnly`.
  - Con cada una, el test nuevo falla en el `assertSucceeds` de `seis-barrio`. Copia las dos aserciones, deshaz las mutaciones y ejecuta la suite en verde.
  - `git diff firestore.rules` no muestra nada.

- [ ] **Paso 6: GREEN.** La puerta entera.

- [ ] **Paso 7: commit** (coordinador): `test(reglas): el setDoc ajeno solo lo para el dueño y seis tiendas con las de barrio`.

### Task 3: Tests repetidos de la barra de navegación y de las betas

L65 y L67.

Depende de: ninguna

**Ficheros:**
- Modificar: `A/core/navigation/WorkspaceNavigationBarTest.kt`:
  - borrar `labelsAreNotEllipsizedAt130PercentFontScale` (~líneas 30-51);
  - `assertCabeEntera` (~línea 77) y el comentario de ~línea 73.
- Mutar y restaurar: `J/core/navigation/WorkspaceScaffold.kt`:
  - `TabLabelMinSize` (~línea 176);
  - el `Text` de la etiqueta (~líneas 206-211).
- Modificar: `J/data/betaupdate/FirebaseBetaDistribution.kt:85`, donde `afterDownload` pasa de `internal` a `private`.
- Modificar: `T/data/betaupdate/BetaDistributionMappingTest.kt`, para borrar los dos tests de `afterDownload` (~líneas 83-98).
- Modificar: `T/data/betaupdate/BetaUpdateProgressTrackerTest.kt`.

**Interfaces:**
- Produce: `private fun BetaUpdateProgress.afterDownload(downloaded: Boolean): BetaUpdateProgress`. Solo la usa el tracker del mismo fichero.

- [ ] **Paso 1: L65.**
  - Añade como primera línea de `assertCabeEntera` `assertFalse("$title con puntos suspensivos", layout.isLineEllipsized(0))`.
  - Borra `labelsAreNotEllipsizedAt130PercentFontScale`: `todasLasEtiquetasTienenElMismoTamano` ya hace lo mismo a 130 %.
  - Corrige el comentario de ~línea 73 para que diga lo que comprueba ahora `assertCabeEntera`: que no hay salto de línea, ni desborde, ni puntos suspensivos.

- [ ] **Paso 2: RED de L65 por mutación.**
  - En `WorkspaceScaffold.kt`, pon `TabLabelMinSize = 14.sp` y añade `overflow = TextOverflow.Ellipsis` al `Text` de la etiqueta.
  - Ejecuta `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.core.navigation.WorkspaceNavigationBarTest`.
  - Esperado: `todasLasEtiquetasTienenElMismoTamano` falla con «… con puntos suspensivos». Copia la aserción, deshaz las dos mutaciones y vuelve a ejecutar la clase en verde (2 tests).

- [ ] **Paso 3: L67. Añade al tracker los dos casos que solo estaban en `BetaDistributionMappingTest`.** Mira cómo montan el tracker los tests de ese fichero.
  - `` `an install failure reported by progress without a download is still an error` ``: `onProgress(INSTALL_FAILED)` sin `AwaitingInstall` previo da `Failed(INSTALL_FAILED)`.
  - `` `a download failure after downloading is still a download error` ``: `onProgress(DOWNLOAD_FAILED)` tras `AwaitingInstall` da `Failed(DOWNLOAD_FAILED)`.

- [ ] **Paso 4: RED de L67 por mutación,** con dos mutaciones por separado en `afterDownload` (`FirebaseBetaDistribution.kt:86`):
  - quitar `downloaded &&`: falla el primer test nuevo;
  - cambiar `INSTALL_FAILED` por `DOWNLOAD_FAILED`: falla el segundo.
  - Copia las aserciones y deshaz las mutaciones.

- [ ] **Paso 5: deja `afterDownload` en `private`** y borra de `BetaDistributionMappingTest` los dos tests que la llaman: «tras descargar, un fallo de instalacion cuenta como cancelado» y «sin descargar…». El resto del fichero (`toFailureProgress`, `toProgress` y `releaseNotesOrNull`) no se toca.
  - Los cuatro casos que se borran (a-d) quedan cubiertos en el tracker. Nombra en el informe el test del tracker que cubre cada uno (Review Focus 4).

- [ ] **Paso 6: GREEN.** Los dos ficheros de test JVM, la clase instrumentada del paso 2 y la puerta entera.

- [ ] **Paso 7: commit** (coordinador): `test: la barra mide los puntos suspensivos y afterDownload se prueba solo por el tracker`.

### Task 4: Huecos de la revisión de «Añadir a la compra» y del editor

L15 y L346.

Depende de: ninguna

**Ficheros:**
- Modificar: `T/feature/common/IngredientReviewTest.kt`.
- Modificar: `T/feature/shopping/ShoppingViewModelTest.kt`.
- Mutar y restaurar:
  - `J/feature/common/IngredientReview.kt`: `inPantry` (~línea 205) y `quantityTooLong` (~línea 146);
  - `J/core/model/ShoppingMatch.kt:18`.

**Interfaces:**
- Consume:
  - `buildIngredientReviewRows(ingredients, currentItems, data, pantryIds): List<IngredientReviewRow>` (`IngredientReview.kt:191`);
  - `IngredientReviewRow.badges()`, `initialState()`, `ReviewRowState.withInclude(Boolean)` y `canConfirmReview(...)`;
  - `ShoppingViewModel.addItem(...)` (`ShoppingViewModel.kt:177`).

- [ ] **Paso 1: L15, en `IngredientReviewTest`:** `` `without a readable pantry no row carries the pantry badge but keeps the list badge` ``.
  - **Montaje:** ingredientes `Sal 200 g` y `Arroz`, la lista con `item("s1", "Sal", quantity = "1 kg")` e `ids = setOf(pantryId("Sal"), pantryId("Arroz"))`.
  - **Control:** con `ids`, `badges()` da `[[despensa, lista("1 kg")], [despensa]]`.
  - **Bajo prueba:** con `emptySet()`, `badges()` da `[[lista("1 kg")], []]` y ninguna fila tiene `inPantry`.
  - **Mutación:** `inPantry = true` en ~línea 205. Falla la parte de `emptySet()`.

- [ ] **Paso 2: L346 (1), en `IngredientReviewTest`:** `` `a long pending row blocks confirming only once Sumar is chosen` ``.
  - **Montaje:** una fila PENDING construida con `buildIngredientReviewRows`, con un pendiente de la lista y una cantidad de la receta, las dos libres, que juntas (`ShoppingQuantity.sum`) pasan de 40 caracteres. Una aserción fija que la suma pasa de 40.
  - **Aserciones:**
    - la fila es `PENDING`;
    - `canConfirmReview` es `true` con `initialState()`;
    - `canConfirmReview` es `false` con `initialState().withInclude(true)`.
  - **Mutación:** el umbral de `quantityTooLong` (~línea 146) a `> 100`.

- [ ] **Paso 3: L346 (3), en `ShoppingViewModelTest`:** `` `adding a product that is both purchased and pending asks about the pending one` ``.
  - **Montaje:**
    - la lista con «Tomates» comprado (`id = "b"`) y «tomate» pendiente (`id = "p"`), con `listItem(...).copy(id = …, name = …)`;
    - `observe("home")` y `advanceUntilIdle()`;
    - `addItem` con «Tomate» y la cantidad «2».
  - **Aserciones:**
    - `uiState.value.duplicate?.existing?.id == "p"`;
    - el fake no ha reapuntado ni añadido nada (`relisted` y `addedName` siguen `null`).
  - **Mutación:** `ShoppingMatch.kt:18`, `matches.firstOrNull { !it.purchased } ?: matches.firstOrNull()` → `matches.firstOrNull()`.

- [ ] **Paso 4: RED.** Aplica cada mutación por separado, ejecuta el test afectado y copia la aserción que falla. Deshaz cada mutación.

- [ ] **Paso 5: GREEN.** Las dos clases de test y la puerta entera.

- [ ] **Paso 6: commit** (coordinador): `test(compra): despensa sin leer, fila pendiente larga y comprado más pendiente en el editor`.

### Task 5: Huecos de recetas y marcas

L281, L312 y L406.

Depende de: ninguna

**Ficheros:**
- Modificar `J/data/repository/FirestoreRecipeRepository.kt` (solo extraer, sin cambiar el comportamiento):
  - `restoredFields` (~línea 221) sale de la clase a una función de nivel de fichero;
  - las constantes que usa (`FIELD_OWNER_UID`, `FIELD_CREATED_BY`, `FIELD_CREATED_AT`, `FIELD_UPDATED_BY`, `FIELD_UPDATED_AT`, `FIELD_SOURCE`, `SOURCE_PROVIDER`, `SOURCE_IMPORTED_AT` y `CATALOG_PROVIDER`) salen del `private companion object` a `private const val` de nivel de fichero;
  - las tres lecturas `toDate().toInstant()` de las recetas (~líneas 320, 324 y 326) pasan a la función nueva.
- Modificar: `T/data/repository/RecipeRestoreTest.kt`.
- Modificar: `T/core/model/RecipeUnitsFixtureTest.kt`.
- Modificar: `T/data/repository/IngredientBrandFirestoreTest.kt`.

**Interfaces:**
- Produce, en `FirestoreRecipeRepository.kt`:
  - `internal fun RecipeRestoreAudit.toRestoredFields(): Map<String, Any?>`, que sustituye a `restoredFields(audit)`;
  - `internal fun Timestamp.toMillisInstant(): Instant = toDate().toInstant()`. No se llama `toInstant`, porque `Timestamp` ya tiene un miembro con ese nombre, que conserva los nanosegundos y taparía la extensión;
  - `Instant.toTimestamp()` (~línea 314) pasa de `private` a `internal`.
- Consume: `internal fun BrandInput.toUpdateFields(): Map<String, Any?>` (`IngredientBrandFirestore.kt`).

- [ ] **Paso 1: L281, extracción.** Haz la extracción de «Ficheros» sin cambiar ningún valor. La puerta pasa igual que antes.

- [ ] **Paso 2: L281, tests en `RecipeRestoreTest`.**
  - `` `restored dates keep the millisecond the app read from Firestore` ``:
    - `stored = Timestamp(1_760_000_000L, 123_456_789)`, que tiene microsegundos como `serverTimestamp()` en el importador, y `read = stored.toMillisInstant()`;
    - una `RecipeRestoreAudit` con `createdAt`, `updatedAt` e `importedAt` iguales a `read`;
    - `toRestoredFields()` da `Timestamp(1_760_000_000L, 123_000_000)` en `createdAt`, `updatedAt` y `source.importedAt`, el mismo milisegundo que el `toMillis()` de `import-core.mjs`;
    - `source.importedAt` no queda después de `updatedAt` (Review Focus 5).
  - `` `restored fields use the server time for missing dates and carry the source only for imported recipes` ``:
    - con `createdAt` y `updatedAt` a `null`, los dos son `FieldValue.serverTimestamp()`;
    - sin `importedAt`, no hay clave `source`;
    - dueño, autor y editor salen de la `audit`.

- [ ] **Paso 3: L312, en `RecipeUnitsFixtureTest`,** dentro de `counted units add up and agree in number with the total`: añade `assertEquals(singular, "2 $plural", ShoppingQuantity.sum("1 $singular", "1 $singular"))`. Así se cubre, entre otras, «1 diente» + «1 diente» = «2 dientes».

- [ ] **Paso 4: L406, en `IngredientBrandFirestoreTest`.**
  - `` `update fields leave out the ingredient and delete the barcode and nutrients that are missing` ``:
    - con `input(barcode = null, nutrition = Nutrition(fat = 1.2), stores = listOf(Supermarket.DIA)).toUpdateFields()`, no hay clave `ingredientId`;
    - `barcode` es `FieldValue.delete()`, `fat` es `1.2` y los otros cinco nutrientes de `NUTRITION_FIELDS` son `FieldValue.delete()`;
    - `name` y `stores` llevan sus valores.
  - `` `update fields with every value present delete nothing` ``: con código y nutrición completa, ningún valor es `FieldValue.delete()`.

- [ ] **Paso 5: RED,** con cada mutación por separado. Copia la aserción que falla y deshaz la mutación.
  - `Instant.toTimestamp()` → `Timestamp(epochSecond, 0)`: falla el primer test de L281.
  - `toMillisInstant()` → `Instant.ofEpochSecond(seconds, nanoseconds.toLong())`: falla el primer test de L281.
  - En `toRestoredFields`, `createdAt` siempre `FieldValue.serverTimestamp()`: falla el primer test de L281.
  - `ShoppingQuantity.kt:105`, `UnitNumber::pluralOf` → `{ it }`: falla L312.
  - En `toUpdateFields`, quitar `fields.remove("ingredientId")`, y por separado cambiar `if (barcode == null)` por `if (false)`: falla L406.

- [ ] **Paso 6: GREEN.** Las tres clases de test y la puerta entera.

- [ ] **Paso 7: commit** (coordinador): `test(recetas): campos al deshacer y fechas a milisegundos, dos singulares y campos de marca al editar`.

### Task 6: Huecos de Deshacer en Deporte y del aviso de cocinado

L32 y L212.

Depende de: ninguna

**Ficheros:**
- Modificar: `T/data/repository/SportActivityFieldsTest.kt`.
- Modificar: `T/feature/meals/MealsCookedViewModelTest.kt`.
- Mutar y restaurar:
  - `J/data/repository/FirestoreSportActivityRepository.kt:531`;
  - `J/feature/meals/MealsViewModel.kt:328`.

**Interfaces:**
- Consume:
  - `internal fun eventDataForRestore(user: AuthUser, event: CalendarEvent, active: List<ActiveMember>): Map<String, Any?>` (`FirestoreSportActivityRepository.kt:530`);
  - `MealsViewModel.markCooked(...)` y `dismissCooked()`.

- [ ] **Paso 1: L32, en `SportActivityFieldsTest`:** `` `the organizer stays in the restored event although they do not take part in the activity` ``.
  - **Montaje:**
    - `linkedEvent(listOf("a", "owner"))`;
    - `activeMembers` con «a» (Ana) y «owner» (David), los dos `ACTIVE`;
    - la actividad con `activityData(...)` y solo «a» como participante.
  - **Aserciones:**
    - la actividad lleva `participantIds == listOf("a")`;
    - `eventDataForRestore` da `participantIds == listOf("a", "owner")` y `participantNames == listOf("Ana", "David")`.
  - **Mutación:** `:531`, `active.map(ActiveMember::id)` → `active.map(ActiveMember::id).filter { it != event.createdBy }`.

- [ ] **Paso 2: L212, en `MealsCookedViewModelTest`:** `` `cancelling the cooked dialog without a previous error keeps the cooked notice` ``.
  - **Montaje:** el mismo que el test de `dismissCooked` de ~línea 361, sin `confirmCooked`:
    - `markCooked` y después `advanceUntilIdle()`;
    - comprueba que el diálogo está abierto (`cooked != null`) y que el aviso es `MealUiMessage.MealCooked`.
  - **Aserciones tras `dismissCooked()`:**
    - `cooked` es `null`;
    - no hay error;
    - el aviso sigue siendo `MealUiMessage.MealCooked` (ruling 2 de `agent/menores-listo-has-cocinado`).
  - **Mutación:** `:328`, añadir `saver.consumeNotice()` después de `saver.consumeError()`.

- [ ] **Paso 3: RED.** Aplica cada mutación por separado, copia la aserción que falla y deshaz la mutación.

- [ ] **Paso 4: GREEN.** Las dos clases de test y la puerta entera.

- [ ] **Paso 5: commit** (coordinador): `test: el organizador sigue en el evento restaurado y «Cancelar» conserva el aviso de cocinado`.

## Cierre (paso 6): lo que comprueba y marca el coordinador

- **Antes de la pregunta del paso 6:** sube la rama y espera `gh pr checks --watch --fail-fast` en verde en el último commit.
  - Repite el recuento de la tarea 1 (paso 7) sobre la última ejecución de `instrumented.yml`: los `testcase` tienen que ser los `@Test` de `app/src/androidTest` (uno menos que al empezar, por L65).
  - Pon la cifra en el mensaje final.
- **Backlog:** marca como **Resuelto en `agent/red-de-seguridad-r2`** las 13 líneas, con su tarea:
  - L66 y L62 (tarea 1; L62 queda cubierta al correr `SpaceHomeAnchorTest` en la CI);
  - L64 y L366 (tarea 2);
  - L65 y L67 (tarea 3);
  - L15 y L346 (tarea 4);
  - L281, L312 y L406 (tarea 5);
  - L32 y L212 (tarea 6).
- **ADR-0001:** marca las dos casillas de «Puesta en marcha».
- Todo va en el mismo commit que los menores nuevos.
