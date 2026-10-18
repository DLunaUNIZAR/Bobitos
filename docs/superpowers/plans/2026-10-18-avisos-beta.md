# Avisos e instalación de betas nuevas: plan de implementación

> **Para agentes:** se ejecuta con la skill `delegating-plan-execution` del usuario. El marco es superpowers:executing-plans.
> - Es un **plan grande** (5 tareas): pasos 1 a 6, con `simplify`.
> - Después del paso 3 va la revisión de diseño (`impeccable audit`) de los ficheros de la tarea 4.
> - Cada tarea la hace un subagente `sonnet`, en serie y en el orden del plan.
> - La revisión final la hace `opus` (si falla, `fable`).
> - Los commits los hace el coordinador.
> - **Ninguna tarea toca `firestore.rules` ni `migrations/`.**
> - Para la regla de re-revisión, cuentan como temas de seguridad:
>   - como «autenticación», un arreglo que cambie el inicio de sesión de tester;
>   - como «permisos», un arreglo que cambie `REQUEST_INSTALL_PACKAGES` o su uso.
> - Los pasos usan casillas (`- [ ]`).

**Objetivo:** que la app avise de las betas nuevas de App Distribution al abrirse (como mucho una vez al día y sin insistir con una versión pospuesta) y que, desde «Perfil», se puedan buscar, leer sus notas, descargarlas e instalarlas.

**Arquitectura:**
- **`J/data/betaupdate/`:** la interfaz `BetaDistribution` envuelve el SDK. En `release` la implementa Firebase; en `debug`, una versión desactivada. Aquí van también los tipos propios: versión, error y progreso.
- **`J/data/repository/`:** las preferencias en DataStore, con el patrón de los demás repositorios de preferencias.
- **`J/feature/betaupdate/`:**
  - las reglas puras del aviso automático;
  - `BetaUpdateViewModel`, creado por `MainActivity` (de nivel de actividad);
  - tres composables sin estado: el aviso, la hoja y la fila.
- **El cableado:**
  - `MainActivity` llama a `onAppForeground()` en su `onStart()`.
  - `BobitosApp` pinta el aviso y la hoja encima de cualquier pantalla.
  - «Perfil» recibe el estado y la acción por parámetros, como ya recibe `authActionState`.

**Tecnología:**
- Kotlin 2.3.10, Jetpack Compose (BOM 2026.06.00), Hilt, DataStore Preferences y corrutinas (`kotlinx-coroutines-play-services` para `Task.await()`).
- Firebase App Distribution `16.0.0-beta21`.
- Tests con JUnit 4 (JVM) y Compose instrumentados (`createAndroidComposeRule<ComponentActivity>()`) en el emulador `Pixel_10`.

**Spec:** `docs/superpowers/specs/2026-10-18-avisos-beta-design.md`. Incluye la «Dirección visual», que es el brief de `/impeccable shape` confirmado. Contexto de producto: `PRODUCT.md`.

## Restricciones globales

**Rutas**
- `J/` = `app/src/main/java/com/dlunaunizar/bobitos/`.
- `T/` = `app/src/test/java/com/dlunaunizar/bobitos/` (JVM).
- `A/` = `app/src/androidTest/java/com/dlunaunizar/bobitos/` (instrumentados).
- `S` = `app/src/main/res/values/strings.xml`.

**Entorno y comandos**
- **Java:** antes de cada `./gradlew`, ejecuta `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Test JVM suelto:** `./gradlew :app:testDebugUnitTest --tests "<FQCN>"`.
- **Test instrumentado suelto:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
  - Necesita el emulador encendido. Si `adb devices` no lo lista: `export PATH="$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/platform-tools:$PATH"; nohup emulator -avd Pixel_10 -no-boot-anim >/dev/null 2>&1 & adb wait-for-device`.
  - El emulador está en inglés (`en-US`). Los textos de la app siguen en español porque solo hay `values/strings.xml`.
- **Compilación de release:** `./gradlew :app:assembleRelease`. Usa `app/google-services.json` y `keystore.properties`, que existen en este equipo.
  - Sale con `versionCode` 1 y `beta:distribute` lo rechaza.
  - **No se ejecuta nunca `npm run beta:distribute`.**
- **Puerta de cada tarea:** `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug`, más los tests instrumentados de la propia tarea.
  - Si `lintDebug` ya falla en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`, se tolera ese error y solo ese.
  - Si un cambio de firma rompe `config/detekt/baseline.xml`, se regenera con `./gradlew detektBaseline` y se incluye en la tarea.
  - Los tests instrumentados que ya existen y tocan un fichero de la tarea se actualizan en esa tarea.

**Textos (spec, «Dirección visual»)**
- Los nuevos van en `S`, en español de España, con tuteo, frases cortas y punto final en las frases completas.
- Fijados por la spec:
  - «Hay una beta nueva», «Actualizar» y «Más tarde» (el aviso);
  - «Versión», «Buscar actualizaciones» y «Tienes la última versión» (la fila);
  - «Descargar e instalar», «Instalar», «Reintentar», «Novedades» y «Cerrar» (la hoja).
- Se reutilizan `R.string.close` («Cerrar») y `R.string.retry` («Reintentar»).
- Los mensajes de error, la línea de ayuda y la línea de la descarga los escribe el implementador con esa voz. Los errores, con palabras normales y sin el texto de la excepción.

**Componentes y accesibilidad (spec y `PRODUCT.md`)**
- Componentes reales de Material 3. Tema oscuro por defecto. Todo se comprueba en claro y en oscuro.
- Contraste AA en los dos temas.
- Con la letra al 130 % nada se corta.
- Destinos táctiles de 48 dp.
- TalkBack lee el progreso y cada cambio de estado de la fila y de la hoja (`liveRegion` en los textos de estado).
- Sin animaciones nuevas. Si alguna hace falta, comprueba `rememberReduceMotion()`.

**Alcance (spec)**
- Las únicas dependencias nuevas son `firebase-appdistribution-api` y `firebase-appdistribution`.
- La app no tiene notificaciones propias. `signInTester` solo se llama desde `checkManually()`.
- La versión `debug` no llama nunca al SDK.

**Frontend**
- **La skill de diseño** la invoca el implementador de la tarea 4, con su línea `Frontend:`.
- **No hay nadie a quien preguntar.** Si la skill pide elegir o confirmar algo, decide con la «Dirección visual» de la spec y `PRODUCT.md`, y anota en el informe lo que decidiste.
- **Límite de ficheros:** la skill solo cambia los ficheros de la tarea.
- **No hay trabajo nuevo de impeccable** (R1), así que no se crea `DESIGN.md`. Las carpetas de trabajo de las skills ya están en `.gitignore`.

## Rulings de la planificación

- **R1. `harden` en lugar de trabajo nuevo de impeccable.** El trabajo nuevo abre el descubrimiento del mundo visual (`WORLD_DISCOVERY_REQUIRED`). Ese paso pregunta al usuario y se degrada dentro de un subagente. La dirección ya está en la spec y casi todo el trabajo son estados, errores y textos largos.
- **R2. Los botones de la hoja van al pie, junto a «Cerrar», como en `BobitosInfoSheet` (`primaryAction`).**
  - El botón es «Descargar e instalar», «Instalar» o «Reintentar», según el estado.
  - La zona de estado (progreso, línea de ayuda o error) va arriba, fija sobre las notas. Para eso, `BobitosInfoSheet` gana un hueco opcional `header`, que no cambia sus tres usos actuales.
- **R3. Un aviso fijo, no un snackbar.** «Cerrar no cancela la descarga» es una línea fija del estado de descarga, del estilo de «Puedes cerrar esta hoja: Android te pedirá instalar al terminar.». No es un snackbar después de cerrar, porque la raíz de la app no tiene `SnackbarHost`.
- **R4. Disponibilidad:** un campo nuevo `BuildConfig.BETA_UPDATES_ENABLED`, `false` en `debug` y `true` en `release`.
- **R5. El primer plano es `MainActivity.onStart()`.** También salta al girar el móvil y al volver del inicio de sesión de tester o del instalador. El ViewModel lo ignora en cuatro casos:
  - hay una comprobación en curso;
  - el aviso está abierto;
  - la hoja está abierta;
  - hay una comprobación manual en curso.
- **R6. «Perfil» recibe el estado por parámetros**, no con `hiltViewModel()`, porque el ViewModel es de la actividad. Las acciones viajan como la interfaz `BetaUpdateActions`, que implementa el ViewModel, para no añadir seis lambdas a `BobitosApp`.
- **R7. Buscar con una descarga en curso.** Si `checkManually()` llega mientras se descarga, reabre la hoja con el progreso actual sin volver a llamar al SDK.
- **R8. Título de la hoja:** «Versión %1$s», con `displayVersion`. Mensaje del aviso: «La versión %1$s está lista para instalar.», el de la maqueta aprobada.
- **R9. `BobitosDialog` gana `onDismissRequest`.** Es un parámetro opcional (`onDismissRequest: () -> Unit = onDismiss`) para que «Más tarde» (botón) y cerrar con Atrás o tocando fuera hagan cosas distintas, como pide la spec.
- **R10. `ProfileScreenTest` ya falla en `main`.** Lo comprobé el 2026-10-18 en el emulador: `IllegalStateException: Given component holder class androidx.activity.ComponentActivity does not implement … GeneratedComponent`. Pasa porque `ProfileScreen` pide `ThemeViewModel` y `RemindersViewModel` con `hiltViewModel()` y el test usa una actividad sin Hilt.
  - La tarea 4 solo lo actualiza para que compile con los parámetros nuevos. No le añade casos, y su fallo previo se tolera, ese y solo ese.
  - La fila se prueba aislada, en `AppVersionRow`.
  - Arreglar ese test queda fuera del plan.

## Review Focus

1. **Girar el móvil con el aviso o la hoja abiertos, o durante la descarga.** El estado se conserva (ViewModel de la actividad) y no salen un segundo aviso ni una segunda comprobación. Lo prueba la tarea 3 (`dos aperturas seguidas con la comprobación en curso llaman una vez al SDK`).
2. **Volver del navegador del inicio de sesión o del instalador de Android dispara `onStart()`.** El aviso automático no debe salir encima de la hoja ni durante una comprobación manual. Lo prueba la tarea 3 (`con la hoja abierta o una comprobación manual en curso abrir la app no comprueba`).
3. **Tamaño total de la descarga desconocido (0 o negativo).** Progreso indeterminado, sin «NaN %» ni división por cero. Lo prueban las tareas 1 (`fraction es null si no se conoce el total`) y 4 (`sinTotalNoSeMuestraPorcentaje`).
4. **Cuenta de Google que no está en `bobitos-beta`, o API sin activar.** La comprobación manual lo explica con palabras normales y la automática no dice nada ni gasta el día. Lo prueban las tareas 1 (traducción de errores) y 3 (`una comprobación automática fallida no gasta el día ni muestra nada`).
5. **Notas vacías o nulas.** No sale un «Novedades» vacío. Lo prueban las tareas 1 (`notas en blanco cuentan como sin notas`) y 4 (`sinNotasNoSeMuestraNovedades`).

---

### Tarea 1: dependencia y envoltorio del SDK

**Ficheros:**
- Modificar `gradle/libs.versions.toml`:
  - versión `appDistribution = "16.0.0-beta21"`;
  - librerías `firebase-appdistribution-api` y `firebase-appdistribution` (`com.google.firebase:…`, `version.ref = "appDistribution"`). No vienen en `firebase-bom` 34.15.0.
- Modificar `app/build.gradle.kts`:
  - `implementation(libs.firebase.appdistribution.api)`;
  - `releaseImplementation(libs.firebase.appdistribution)`;
  - `buildConfigField("boolean", "BETA_UPDATES_ENABLED", …)`: `"false"` en `debug` y `"true"` en `release`.
- Crear `J/data/betaupdate/BetaDistribution.kt`: los tipos, la interfaz y `DisabledBetaDistribution`.
- Crear `J/data/betaupdate/FirebaseBetaDistribution.kt`: la implementación y las funciones de traducción.
- Crear `J/data/di/BetaUpdateModule.kt`.
- Test: crear `T/data/betaupdate/BetaDistributionMappingTest.kt`.

**Interfaces:**
- Consume: nada nuevo.
- Produce (paquete `com.dlunaunizar.bobitos.data.betaupdate`):
  - `data class BetaRelease(val displayVersion: String, val versionCode: Long, val releaseNotes: String?)`.
  - `enum class BetaUpdateError { SIGN_IN_CANCELED, SIGN_IN_FAILED, OFFLINE, API_DISABLED, DOWNLOAD_FAILED, INSTALL_FAILED, NOT_AVAILABLE, UNKNOWN }`.
  - `class BetaUpdateException(val error: BetaUpdateError, cause: Throwable? = null) : Exception(cause)`.
  - El progreso:
    - `sealed interface BetaUpdateProgress`, con estos casos:
      - `data class Downloading(val downloadedBytes: Long, val totalBytes: Long)`, con `val fraction: Float?`. Es `null` si `totalBytes <= 0`; si no, `downloadedBytes / totalBytes`, acotado a `0f..1f`.
      - `data object AwaitingInstall`: descargada; Android pide confirmar.
      - `data object InstallCanceled`.
      - `data class Failed(val error: BetaUpdateError)`.
  - `interface BetaDistribution`:
    - `val isAvailable: Boolean`;
    - `val isTesterSignedIn: Boolean`;
    - `suspend fun signInTester()`;
    - `suspend fun checkForNewRelease(): BetaRelease?`;
    - `fun updateApp(): Flow<BetaUpdateProgress>`.
  - Contrato de `BetaDistribution`:
    - `signInTester` y `checkForNewRelease` lanzan `BetaUpdateException`.
    - `updateApp()` emite los estados hasta que acaba la tarea del SDK y entonces termina.
    - Un fallo de `updateApp()` llega como `Failed` o `InstallCanceled`, nunca como excepción.
  - `object DisabledBetaDistribution : BetaDistribution`:
    - `isAvailable` e `isTesterSignedIn` son `false`;
    - `signInTester` y `checkForNewRelease` lanzan `BetaUpdateException(NOT_AVAILABLE)`;
    - `updateApp()` emite `Failed(NOT_AVAILABLE)`.
  - `class FirebaseBetaDistribution : BetaDistribution`, sobre `Firebase.appDistribution`. `isAvailable` es `true`.
  - Las funciones de traducción, en `FirebaseBetaDistribution.kt`:
    - `internal fun FirebaseAppDistributionException.Status.toBetaUpdateError(): BetaUpdateError`;
    - `internal fun FirebaseAppDistributionException.Status.toFailureProgress(): BetaUpdateProgress`;
    - `internal fun UpdateStatus.toProgress(downloadedBytes: Long, totalBytes: Long): BetaUpdateProgress?`;
    - `internal fun releaseNotesOrNull(raw: String?): String?`.
  - `BetaUpdateModule` (`@InstallIn(SingletonComponent::class)`) da un `BetaDistribution` `@Singleton`: `FirebaseBetaDistribution()` si `BuildConfig.BETA_UPDATES_ENABLED` y `DisabledBetaDistribution` si no.

**Tablas de traducción** (comprueba los nombres de las constantes en el jar de `16.0.0-beta21` y anota en el informe cualquier diferencia):

| `Status` | `toBetaUpdateError()` |
|---|---|
| `AUTHENTICATION_CANCELED` | `SIGN_IN_CANCELED` |
| `AUTHENTICATION_FAILURE` | `SIGN_IN_FAILED` |
| `NETWORK_FAILURE` | `OFFLINE` |
| `API_DISABLED` | `API_DISABLED` |
| `DOWNLOAD_FAILURE` | `DOWNLOAD_FAILED` |
| `INSTALLATION_FAILURE` | `INSTALL_FAILED` |
| `NOT_IMPLEMENTED` | `NOT_AVAILABLE` |
| cualquier otro | `UNKNOWN` |

- `toFailureProgress()`: `INSTALLATION_CANCELED` da `InstallCanceled`; el resto, `Failed(toBetaUpdateError())`.

| `UpdateStatus` | `toProgress(d, t)` |
|---|---|
| `PENDING` | `Downloading(0, t)` |
| `DOWNLOADING` | `Downloading(d, t)` |
| `DOWNLOADED` | `AwaitingInstall` |
| `INSTALL_CANCELED` | `InstallCanceled` |
| `DOWNLOAD_FAILED` | `Failed(DOWNLOAD_FAILED)` |
| `INSTALL_FAILED` | `Failed(INSTALL_FAILED)` |
| cualquier otro | `null` (no cambia nada) |

- [ ] **Paso 1: Tests que fallan.** En `BetaDistributionMappingTest`:
  - `cada Status del SDK se traduce a su error`: la primera tabla, incluido un `Status` que no está en ella (`UNKNOWN`) y da `UNKNOWN`.
  - `cancelar la instalación no es un error`: `INSTALLATION_CANCELED.toFailureProgress() == InstallCanceled` y `NETWORK_FAILURE.toFailureProgress() == Failed(OFFLINE)`.
  - `cada UpdateStatus se traduce a su progreso`: la segunda tabla con `d = 5`, `t = 10`.
  - `fraction es la parte descargada`: `Downloading(25, 100).fraction == 0.25f`.
  - `fraction es null si no se conoce el total`: con `totalBytes` 0 y −1.
  - `notas en blanco cuentan como sin notas`:
    - `releaseNotesOrNull(null) == null`;
    - `releaseNotesOrNull("  \n") == null`;
    - `releaseNotesOrNull("Hola") == "Hola"`.
  - `la distribución desactivada no está disponible`:
    - `DisabledBetaDistribution.isAvailable` y `isTesterSignedIn` son `false`;
    - `checkForNewRelease()` lanza `BetaUpdateException` con `error == NOT_AVAILABLE` (`runTest`).
- [ ] **Paso 2: Comprobar que fallan.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.data.betaupdate.BetaDistributionMappingTest"` falla al compilar, porque no existen los tipos ni la dependencia.
- [ ] **Paso 3: Implementar.**
  - `FirebaseBetaDistribution`:
    - usa `Task.await()`;
    - captura `FirebaseAppDistributionException` y la relanza como `BetaUpdateException(errorCode.toBetaUpdateError(), e)`; otras excepciones van como `UNKNOWN`;
    - `toBetaRelease()` usa `releaseNotesOrNull`.
  - `updateApp()` es un `callbackFlow` sobre el `UpdateTask`:
    - `addOnProgressListener` con `toProgress(apkBytesDownloaded, apkFileTotalBytes)`, ignorando los `null`;
    - el fallo se traduce con `toFailureProgress()`;
    - el fin de la tarea cierra el flujo.
  - Esta clase no tiene test JVM: la cubren `assembleRelease` y la prueba manual de la tarea 5.
- [ ] **Paso 4: Comprobar que pasan** el test y la puerta.
- [ ] **Paso 5: El SDK completo solo está en `release`.**
  - `./gradlew :app:assembleRelease` termina con `BUILD SUCCESSFUL`.
  - `find app/build/intermediates -path '*release*' -name AndroidManifest.xml | xargs grep -l REQUEST_INSTALL_PACKAGES` lista al menos un fichero.
  - Con `*debug*` en lugar de `*release*`, no lista ninguno.
- [ ] **Paso 6: Informe.** Incluye las constantes reales de `Status` y `UpdateStatus` y las diferencias con las tablas.

### Tarea 2: preferencias y reglas del aviso automático

**Ficheros:**
- Crear `J/data/repository/BetaUpdatePreferenceRepository.kt` (la interfaz).
- Crear `J/data/repository/DataStoreBetaUpdatePreferenceRepository.kt`:
  - DataStore `"beta_update"`, con el patrón de `DataStoreOnboardingPreferenceRepository`;
  - claves `last_auto_check_epoch_day` y `postponed_version_code`, las dos `longPreferencesKey`.
- Modificar `J/data/di/DataModule.kt`: `@Binds @Singleton` de la interfaz.
- Crear `J/feature/betaupdate/BetaUpdatePolicy.kt`.
- Test: crear `T/feature/betaupdate/BetaUpdatePolicyTest.kt`.

**Interfaces:**
- Consume: `BetaRelease` (tarea 1).
- Produce:
  - `interface BetaUpdatePreferenceRepository`:
    - `val lastAutoCheckDate: Flow<LocalDate?>`;
    - `val postponedVersionCode: Flow<Long?>`;
    - `suspend fun setLastAutoCheckDate(date: LocalDate)`;
    - `suspend fun setPostponedVersionCode(versionCode: Long)`.
  - `internal fun shouldAutoCheck(available: Boolean, testerSignedIn: Boolean, today: LocalDate, lastAutoCheck: LocalDate?): Boolean`. Es `available && testerSignedIn && lastAutoCheck != today`.
  - `internal fun shouldPrompt(release: BetaRelease?, postponedVersionCode: Long?): Boolean`. Es `release != null && (postponedVersionCode == null || release.versionCode > postponedVersionCode)`.

- [ ] **Paso 1: Tests que fallan.** En `BetaUpdatePolicyTest`, con `today = 2026-10-18`:
  - `comprueba si hay sesión y hoy no se ha comprobado`: `lastAutoCheck` `null` y `2026-10-17` dan `true`.
  - `no comprueba dos veces el mismo día`: `lastAutoCheck = today` da `false`.
  - `no comprueba sin sesión de tester`: `testerSignedIn = false` da `false`.
  - `no comprueba en desarrollo`: `available = false` da `false`.
  - `avisa de una versión nueva sin posponer`: versión 25 y pospuesta `null` dan `true`.
  - `no avisa de la versión pospuesta`: 25 y 25 dan `false`.
  - `avisa de una versión más nueva que la pospuesta`: 26 y 25 dan `true`.
  - `sin versión no avisa`: `release = null` da `false`.
- [ ] **Paso 2: Comprobar que fallan.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdatePolicyTest"` falla al compilar.
- [ ] **Paso 3: Implementar.**
  - Las dos funciones.
  - El repositorio: la fecha se guarda como `toEpochDay()` y se lee con `LocalDate.ofEpochDay`.
  - El `@Binds`.
- [ ] **Paso 4: Comprobar que pasan** el test y la puerta.

### Tarea 3: `BetaUpdateViewModel`

**Ficheros:**
- Crear `J/feature/betaupdate/BetaUpdateViewModel.kt`: el ViewModel, su estado y `BetaUpdateActions`.
- Test: crear `T/feature/betaupdate/BetaUpdateViewModelTest.kt`.

**Interfaces:**
- Consume:
  - de la tarea 1: `BetaDistribution`, `BetaRelease`, `BetaUpdateError`, `BetaUpdateException` y `BetaUpdateProgress`;
  - de la tarea 2: `BetaUpdatePreferenceRepository`, `shouldAutoCheck` y `shouldPrompt`;
  - `CurrentDayProvider.today(zone: ZoneId): Flow<LocalDate>` (`J/core/common/CurrentDayProvider.kt`; es `open`, así que en el test se sustituye por una subclase).
- Produce (paquete `com.dlunaunizar.bobitos.feature.betaupdate`):
  - `sealed interface ManualCheckState`, con los casos `data object Idle`, `data object Checking`, `data object UpToDate` y `data class Failed(val error: BetaUpdateError)`.
  - `data class BetaUpdateSheetState(val release: BetaRelease, val progress: BetaUpdateProgress? = null)`. Con `progress == null`, la versión está lista para descargar.
  - `data class BetaUpdateUiState`:
    - `val installedVersion: String`;
    - `val available: Boolean`;
    - `val testerSignedIn: Boolean`;
    - `val manualCheck: ManualCheckState = ManualCheckState.Idle`;
    - `val prompt: BetaRelease? = null`;
    - `val sheet: BetaUpdateSheetState? = null`.
  - `interface BetaUpdateActions`, con `fun checkManually()`, `fun acceptPrompt()`, `fun postponePrompt()`, `fun dismissPrompt()`, `fun startDownload()` y `fun closeSheet()`.
  - `@HiltViewModel class BetaUpdateViewModel @Inject constructor(distribution: BetaDistribution, preferences: BetaUpdatePreferenceRepository, currentDay: CurrentDayProvider) : ViewModel(), BetaUpdateActions`, con:
    - `val uiState: StateFlow<BetaUpdateUiState>`;
    - `fun onAppForeground()`.
- **Valores:**
  - `installedVersion` = `BuildConfig.VERSION_NAME`;
  - `available` = `distribution.isAvailable`;
  - `testerSignedIn` arranca con `distribution.isTesterSignedIn`;
  - la zona horaria es `ZoneId.systemDefault()`.

**Comportamiento** (spec, «Comportamiento», y R5 y R7):
- **`onAppForeground()` no hace nada** en estos casos (R5):
  - hay una comprobación en curso (automática o manual);
  - `prompt != null`;
  - `sheet != null`;
  - `!shouldAutoCheck(…)`.
- **Si sigue**, comprueba en silencio:
  - **Al terminar bien:** guarda la fecha de hoy y, si `shouldPrompt(release, pospuesta)`, pone `prompt = release`.
  - **Al fallar:** no guarda la fecha ni cambia el estado visible.
- **`postponePrompt()`** guarda el `versionCode` del aviso y lo cierra. **`dismissPrompt()`** lo cierra sin guardar nada. **`acceptPrompt()`** cierra el aviso, abre la hoja con esa versión y llama a `startDownload()`.
- **`checkManually()`:**
  - Si `!available`, no hace nada.
  - Si hay una descarga en curso, reabre la hoja con el último progreso (R7).
  - Si no, el estado pasa a `Checking`.
  - Sin sesión, llama primero a `signInTester()`:
    - si falla, termina en `Failed(error)`;
    - si va bien, `testerSignedIn = true`.
  - Después comprueba:
    - sin versión nueva, `UpToDate`;
    - con versión, `Idle` y la hoja abierta con `progress = null`, sin mirar la versión pospuesta;
    - si falla, `Failed(error)`.
- **`startDownload()`:**
  - recoge `updateApp()` en `viewModelScope`, sin cancelar una recogida anterior que siga viva;
  - cada estado nuevo se guarda como último progreso y, si la hoja está abierta, también en `sheet.progress`.
- **`closeSheet()`:** `sheet = null`, y la descarga sigue.

- [ ] **Paso 1: Tests que fallan.** En `BetaUpdateViewModelTest`, con `MainDispatcherRule`.
  - **Dobles:**
    - `FakeBetaDistribution`:
      - `available` y `signedIn` configurables;
      - resultados de `signInTester` y `checkForNewRelease` configurables, con la opción de quedarse suspendidos en un `CompletableDeferred`;
      - `updateApp()` sobre un `MutableSharedFlow`;
      - contadores de llamadas.
    - `FakeBetaUpdatePreferenceRepository`, sobre `MutableStateFlow`.
    - `FakeCurrentDayProvider`, una subclase de `CurrentDayProvider` con el día cambiable.
  - **Datos:**
    - `r25 = BetaRelease("0.1.0-beta.25", 25, "Notas")`;
    - `r26 = BetaRelease("0.1.0-beta.26", 26, null)`.
  - **Aviso automático:**
    - `sin sesión de tester abrir la app no comprueba`: 0 llamadas a `checkForNewRelease` y a `signInTester`.
    - `en desarrollo abrir la app no comprueba`.
    - `al abrir con una versión nueva avisa y gasta el día`: `prompt == r25` y la fecha guardada es hoy.
    - `al abrir sin versión nueva gasta el día sin avisar`.
    - `solo comprueba una vez al día`: dos aperturas el mismo día hacen 1 llamada; al cambiar el día, 2.
    - `una comprobación automática fallida no gasta el día ni muestra nada`:
      - con `OFFLINE` y con `API_DISABLED`;
      - después del fallo, `prompt == null`, `manualCheck == Idle` y no hay fecha guardada;
      - al volver a abrir, hace la segunda llamada.
    - `más tarde pospone esa versión`:
      - la preferencia queda en 25 y el aviso se cierra;
      - al día siguiente, con `r25` no avisa y con `r26` sí.
    - `cerrar el aviso no pospone`: no se guarda nada y al día siguiente `r25` vuelve a avisar.
    - `actualizar abre la hoja y empieza la descarga`:
      - `prompt == null`, `sheet.release == r25` y hay 1 llamada a `updateApp()`;
      - tras emitir `Downloading(5, 10)`, `sheet.progress` es ese valor.
    - `dos aperturas seguidas con la comprobación en curso llaman una vez al SDK`, con la comprobación suspendida.
    - `con la hoja abierta o una comprobación manual en curso abrir la app no comprueba`: hoy sin fecha guardada, y ninguna llamada de más.
  - **Comprobación manual:**
    - `sin sesión buscar inicia sesión y luego comprueba`: 1 inicio de sesión, 1 comprobación y `testerSignedIn == true`.
    - `si se cancela el inicio de sesión se muestra y no se comprueba`: `Failed(SIGN_IN_CANCELED)` y 0 comprobaciones.
    - `mientras busca el estado es Checking`.
    - `sin versión nueva dice que tienes la última`: `UpToDate`.
    - `un error al buscar se muestra`: `Failed(OFFLINE)`.
    - `buscar enseña la versión pospuesta y no descarga sola`: con 25 pospuesta, `sheet == BetaUpdateSheetState(r25, null)` y 0 llamadas a `updateApp()`.
    - `en desarrollo buscar no hace nada`.
  - **Hoja:**
    - `cancelar la instalación permite instalar otra vez`: tras `InstallCanceled`, `startDownload()` hace la segunda llamada a `updateApp()`.
    - `un fallo de descarga permite reintentar`: lo mismo, tras `Failed(DOWNLOAD_FAILED)`.
    - `cerrar la hoja no corta la descarga y buscar la reabre con su progreso`:
      - tras `closeSheet()`, `sheet == null`;
      - después se emite `Downloading(7, 10)`;
      - `checkManually()` da `sheet.progress == Downloading(7, 10)` sin ninguna llamada nueva a `checkForNewRelease`.
  - **`la versión instalada es la de la compilación`:** `installedVersion == BuildConfig.VERSION_NAME`.
- [ ] **Paso 2: Comprobar que fallan.** `./gradlew :app:testDebugUnitTest --tests "com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdateViewModelTest"` falla al compilar.
- [ ] **Paso 3: Implementar** `BetaUpdateViewModel` según «Comportamiento».
- [ ] **Paso 4: Comprobar que pasan** el test y la puerta.

### Tarea 4: aviso, hoja y fila en la interfaz

**Frontend:** `impeccable:impeccable harden J/feature/betaupdate/BetaUpdatePrompt.kt J/feature/betaupdate/BetaUpdateSheet.kt J/feature/betaupdate/AppVersionRow.kt`

**Ficheros:**
- Crear `J/feature/betaupdate/BetaUpdatePrompt.kt`, `J/feature/betaupdate/BetaUpdateSheet.kt` y `J/feature/betaupdate/AppVersionRow.kt`.
- Modificar `J/core/designsystem/component/BobitosDialog.kt` (R9) y `J/core/designsystem/component/BobitosInfoSheet.kt` (R2).
- Modificar `J/MainActivity.kt`, `J/app/BobitosApp.kt`, `J/core/navigation/BobitosNavHost.kt` y `J/feature/auth/ProfileScreen.kt`.
- Modificar `S`.
- Tests:
  - crear `A/feature/betaupdate/BetaUpdateUiTest.kt`;
  - modificar `A/feature/auth/ProfileScreenTest.kt`, solo para pasarle los parámetros nuevos (R10).

**Interfaces:**
- Consume: `BetaUpdateUiState`, `BetaUpdateSheetState`, `ManualCheckState`, `BetaUpdateActions` y `BetaUpdateViewModel` (tarea 3); `BetaRelease` y `BetaUpdateProgress` (tarea 1).
- Produce:
  - `@Composable fun BetaUpdatePrompt(release: BetaRelease, onUpdate: () -> Unit, onLater: () -> Unit, onDismiss: () -> Unit)`:
    - sobre `BobitosDialog`;
    - «Actualizar» llama a `onUpdate` y «Más tarde» a `onLater`;
    - Atrás y tocar fuera llaman a `onDismiss`.
  - `@Composable fun BetaUpdateSheet(state: BetaUpdateSheetState, onStartDownload: () -> Unit, onClose: () -> Unit)`:
    - sobre `BobitosInfoSheet`, con título `stringResource(R.string.beta_update_sheet_title, displayVersion)`;
    - en el `header`, el estado;
    - en el contenido, «Novedades» y las notas;
    - `primaryAction` según `progress`, con `onStartDownload`:
      - `null`: «Descargar e instalar»;
      - `InstallCanceled`: «Instalar»;
      - `Failed`: «Reintentar»;
      - el resto: sin acción.
  - `@Composable fun AppVersionRow(state: BetaUpdateUiState, onCheck: () -> Unit, modifier: Modifier = Modifier)`.
  - `BobitosDialog(…, onDismissRequest: () -> Unit = onDismiss)` y `BobitosInfoSheet(…, header: (@Composable ColumnScope.() -> Unit)? = null, …)`. El `header` va entre la fila del título y la columna con scroll.
  - `ProfileScreen(…, betaUpdateState: BetaUpdateUiState, onCheckForBetaUpdate: () -> Unit, …)`. La fila va en «Información», encima de `LegalButtons()`.
  - `BobitosNavHost(…, betaUpdateState: BetaUpdateUiState, onCheckForBetaUpdate: () -> Unit, …)`, que se los pasa a `ProfileScreen`.
  - `BobitosApp(…, betaUpdateState: BetaUpdateUiState, betaUpdateActions: BetaUpdateActions, …)`:
    - después del `when` principal pinta `BetaUpdatePrompt` si `prompt != null` y `BetaUpdateSheet` si `sheet != null`, enlazados a las acciones;
    - pasa `betaUpdateActions::checkManually` a `BobitosNavHost`.
  - `MainActivity`:
    - `private val betaUpdateViewModel: BetaUpdateViewModel by viewModels()`;
    - recoge `uiState` con `collectAsStateWithLifecycle()` y pasa el ViewModel como `betaUpdateActions`;
    - `override fun onStart()` llama a `super.onStart()` y después a `betaUpdateViewModel.onAppForeground()`.
- **Cadenas** (`S`; las dos últimas son R8):
  - `beta_update_prompt_title` «Hay una beta nueva»;
  - `beta_update_prompt_update` «Actualizar»;
  - `beta_update_prompt_later` «Más tarde»;
  - `beta_update_version_label` «Versión»;
  - `beta_update_check` «Buscar actualizaciones»;
  - `beta_update_up_to_date` «Tienes la última versión.»;
  - `beta_update_download_install` «Descargar e instalar»;
  - `beta_update_install` «Instalar»;
  - `beta_update_notes_title` «Novedades»;
  - `beta_update_prompt_message` «La versión %1$s está lista para instalar.»;
  - `beta_update_sheet_title` «Versión %1$s».
- **Lo que escribe el implementador:** los errores (uno por `BetaUpdateError`), la línea de ayuda sin sesión (que mencione la cuenta de Google en la que llegan las invitaciones de la beta), la línea de la descarga (R3) y el texto de «Android te pide confirmar la instalación».
- **El porcentaje:** con `fraction != null`, la barra es determinada y el texto lleva «N %» y los MB descargados de los totales, con una cifra decimal y coma (por ejemplo, «50 % · 5,0 de 10,0 MB»). Con `fraction == null`, la barra es indeterminada y no hay «%».

- [ ] **Paso 1: Tests que fallan.**
  - En `BetaUpdateUiTest`, cada uno con `BobitosTheme`:
    - **`elAvisoMuestraLaVersionYSusDosBotones`:**
      - salen «Hay una beta nueva» y «0.1.0-beta.25» (`substring = true`);
      - «Actualizar» llama a `onUpdate` y «Más tarde» a `onLater`.
    - **`laHojaListaOfreceDescargarYEnseñaLasNotas`:** con `progress = null`, salen «Descargar e instalar», «Novedades» y el texto de las notas.
    - **`sinNotasNoSeMuestraNovedades`:** con `releaseNotes = null`, «Novedades» no existe.
    - **`laDescargaMuestraSuPorcentaje`:** con `Downloading(5_242_880, 10_485_760)`, existe un texto que contiene «50 %».
    - **`sinTotalNoSeMuestraPorcentaje`:** con `Downloading(100, 0)`, ningún nodo contiene «%».
    - **`instalacionCanceladaOfreceInstalar`:** sale «Instalar» y, al pulsarlo, llama a `onStartDownload`.
    - **`unFalloOfreceReintentar`:** con `Failed(DOWNLOAD_FAILED)`, sale «Reintentar».
    - **`laFilaEnDesarrolloSoloMuestraLaVersion`:** con `available = false`, salen «Versión» y `installedVersion`, y «Buscar actualizaciones» no existe.
    - **`laFilaSinSesionAvisaDeLaCuentaDeGoogle`:** con `available = true` y `testerSignedIn = false`, sale «Buscar actualizaciones» y un texto que contiene «cuenta de Google».
    - **`laFilaMientrasBuscaDesactivaElBoton`:** con `Checking`, «Buscar actualizaciones» no está activo.
    - **`laFilaAlDiaDiceQueTienesLaUltima`:** con `UpToDate`, sale «Tienes la última versión» (`substring = true`).
    - **`laFilaConErrorPermiteVolverABuscar`:** con `Failed(OFFLINE)`, «Buscar actualizaciones» está activo.
- [ ] **Paso 2: Comprobar que fallan.** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdateUiTest` falla al compilar.
- [ ] **Paso 3: Implementar.**
  - Antes de escribir la interfaz, invoca la skill de la línea `Frontend:`.
  - Las piezas: los dos huecos opcionales de los componentes comunes, los tres composables, las cadenas y el cableado de `MainActivity` → `BobitosApp` → `BobitosNavHost` → `ProfileScreen`.
  - Los textos de estado de la fila y de la hoja llevan `liveRegion = LiveRegionMode.Polite`, y la barra, su `progressBarRangeInfo`.
- [ ] **Paso 4: Comprobar que pasan.**
  - `BetaUpdateUiTest` pasa.
  - Los tests instrumentados de `BobitosDialog` y `BobitosInfoSheet` que ya existen en `A/core/designsystem/component/` pasan.
  - `ProfileScreenTest` compila. Su fallo previo por Hilt se tolera (R10).
  - Pasa la puerta.
- [ ] **Paso 5: Informe.** Incluye las decisiones que tomó la skill y los textos que escribiste.

### Tarea 5: documentación y prueba manual

**Skill:** `engineering:documentation` (guía de operación de la beta).

**Ficheros:**
- Modificar `docs/BETA_DISTRIBUTION.md`, `PRIVACY_POLICY.md` y `docs/BETA_TEST_PLAN.md`.

**Interfaces:**
- Consume: lo que hacen las tareas 1 a 4.
- Produce: nada para el código.

**Contenido (spec, «Documentación» y «Configuración que hace el usuario»):**
- **`docs/BETA_DISTRIBUTION.md`:** una sección nueva, «Actualizar desde la app», que cuenta:
  - Hay que activar una vez «Firebase App Testers API» en Google Cloud Console, en el proyecto `bobitos-dev`. Sin ella, la comprobación falla con un error de API desactivada.
  - La primera beta que trae la función se instala como siempre, por correo o App Tester.
  - Después, en «Perfil», «Buscar actualizaciones»:
    - la primera vez se inicia sesión con la cuenta de Google de la invitación;
    - desde entonces avisa al abrir la app, como mucho una vez al día;
    - «Más tarde» silencia esa versión.
  - Las notas de `distribution/release-notes.txt` se ven en la app.
  - Lo que hace Android:
    - la primera vez pide permitir las instalaciones desde Bobitos;
    - siempre pide confirmar la instalación;
    - Play Protect analiza el APK.
  - El SDK completo solo está en `release`. Su auto-actualización incumple la política de Google Play, así que si algún día se publicara allí haría falta una variante de beta propia.
  - Además, se actualiza la sección 6 («Actualización y caducidad»).
- **`PRIVACY_POLICY.md`:** una viñeta en la lista de tratamientos, con el estilo de la de Crashlytics:
  - solo en las compilaciones de distribución, y si el tester inicia sesión desde «Perfil», la app usa Firebase App Distribution con su cuenta de Google para comprobar y descargar betas nuevas;
  - las compilaciones de desarrollo no lo usan.
- **`docs/BETA_TEST_PLAN.md`:** en «Recorrido mínimo», los pasos de la prueba manual:
  - la comprobación manual: inicio de sesión, notas, descarga con progreso e instalación;
  - el aviso automático al día siguiente;
  - «Más tarde» y la versión siguiente;
  - cerrar el aviso con Atrás;
  - sin conexión;
  - cerrar la hoja durante la descarga.

- [ ] **Paso 1: Comprobación que falla.** `grep -q "Firebase App Testers API" docs/BETA_DISTRIBUTION.md && grep -q "Buscar actualizaciones" docs/BETA_DISTRIBUTION.md && grep -q "App Distribution" PRIVACY_POLICY.md && grep -q "Buscar actualizaciones" docs/BETA_TEST_PLAN.md; echo $?` imprime `1`.
- [ ] **Paso 2: Escribir.** Invoca la skill `engineering:documentation` y sigue sus criterios para una guía de operación. Escribe en español, sin nombres, correos ni identificadores personales, como el resto de esos documentos.
- [ ] **Paso 3: Comprobar.**
  - La misma orden imprime `0`.
  - Pasa la puerta.
