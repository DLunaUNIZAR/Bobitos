# Red de seguridad, segunda vuelta (R2): diseño

Fecha: 2026-10-19 · Base: `main` en `2e417c6` · Origen: sección «R2» y «Hallazgo de la comprobación» de `docs/superpowers/reports/2026-10-19-auditoria-deuda.md` · Decisión de la CI: `docs/adr/0001-tests-instrumentados-en-la-ci.md`.

Las líneas «Lnnn» son de `docs/superpowers/backlog.md` en `2e417c6` (la misma numeración que en `main` hoy).

## Para qué

Hoy la CI no ejecuta ninguno de los 203 tests instrumentados. Las dos últimas ramas apoyan buena parte de su Review Focus en ellos (`UndoOfferEffectTest`, `TasksScreenCompletingTest`, `IngredientReviewUiTest`, `SpacesSavingUiTest`), y si una rama posterior los rompe, nada avisa. Además, algunos tests no miden lo que dicen y hay huecos sobre arreglos recientes de G1, G2 y la compra.

R2 deja la red lista antes de G10, cuyos tests nuevos serán de Compose.

**Éxito:**
- el job del emulador pasa en GitHub en el PR en borrador de la rama, y su informe trae todos los instrumentados que haya al final de la rama (hoy 203; L65 borra uno, y R2 puede añadir otros). La cifra se comprueba contra los `@Test` de `app/src/androidTest` en el último commit;
- las 13 líneas del backlog quedan resueltas y marcadas;
- no cambia nada de lo que ve el usuario.

## Objetivos

1. **Instrumentados en la CI**, tal como fija el ADR-0001: workflow aparte `instrumented.yml`, push a `main` y PR con filtro de rutas, `reactivecircus/android-emulator-runner`, x86_64, API 37 si hay imagen, perfil de 411 dp de ancho, animaciones desactivadas, sin reintentos e informes como artefacto. Con esto se resuelve también **L62**.
   - **La app de debug tiene que arrancar sin `app/google-services.json`.** Ese fichero está en `.gitignore` y la CI no lo tiene, así que no se aplica el plugin de Crashlytics, aunque la dependencia `firebase-crashlytics` sí entra en el debug. Cuando `FirebaseInitializer` crea la app `demo-bobitos`, Crashlytics puede caerse con «The Crashlytics build ID is missing», y caería con él todo el proceso de los tests. La CI nunca ha arrancado el APK de debug: solo lo compila.
     - Primero se reproduce desde un `git worktree` limpio, que no tiene el fichero. **Nunca se mueve, se borra ni se copia el `app/google-services.json` del usuario.**
     - Si se cae, el arreglo es un recurso en `app/src/debug/res/values/` con `<bool name="com.crashlytics.RequireBuildId">false</bool>`. Solo afecta al debug; con el fichero presente, el plugin sigue generando el build ID como hoy.
     - No se genera un `google-services.json` ficticio en la CI ni se cambia `build.gradle.kts`.
     - Si no se cae, no se añade nada.
2. **Los 12 internos restantes**, con lo que se considera hecho en cada uno:

| Línea | Hecho cuando |
|---|---|
| **L66** (`ci.yml`) | En el job `firebase-rules`, si falla la suite de reglas, las de scripts e importadores se ejecutan igualmente, y el job sigue en rojo si falla cualquiera. Si falla la preparación (`npm ci`, Java o Node), no se ejecuta ninguna. |
| **L64** (`tests/firebase-emulators.test.mjs`) | En «un setDoc de cliente no pisa una receta existente, propia o ajena», el `setDoc` del otro usuario conserva los campos inmutables de la receta (dueño, autor y fecha de alta), así que solo lo rechaza la comprobación de dueño. Se demuestra con una mutación temporal de esa comprobación en `firestore.rules`, deshecha antes del informe. |
| **L65** (`WorkspaceNavigationBarTest`) | Se borra `labelsAreNotEllipsizedAt130PercentFontScale`, que repite a `todasLasEtiquetasTienenElMismoTamano` a 130 %. Si `assertCabeEntera` no detecta los puntos suspensivos (`isLineEllipsized`), se le añade, para que el test que queda cubra el recorte y los puntos suspensivos. |
| **L67** (`BetaUpdateProgressTrackerTest`, `BetaDistributionMappingTest`) | `afterDownload` pasa a `private` en `FirebaseBetaDistribution.kt`. Cada caso de `afterDownload` se prueba una sola vez, a través del tracker. Los de `BetaDistributionMappingTest` que no estén ya en el tracker se mueven allí; los repetidos se borran. |
| **L15** (`IngredientReview.kt`) | Un test JVM construye las filas de la revisión con `pantryIds` vacío (despensa no leída) para un ingrediente que, con la despensa leída, llevaría el aviso de despensa. La fila no lleva ese aviso y conserva el de la lista si lo tiene. |
| **L32** (`SportActivityFieldsTest`) | Un test prueba que el organizador sigue en el evento restaurado aunque no participe en la actividad (ruling R6 de `agent/deshacer`). |
| **L212** (`MealsCookedViewModelTest`) | Un test prueba que «Cancelar» sin error previo conserva el aviso «Comida marcada como cocinada» (ruling 2 de `agent/menores-listo-has-cocinado`). |
| **L281** (`FirestoreRecipeRepository.restoredFields`) | Un test JVM fija el contrato de `restoredFields`: dueño, autor y editor; `serverTimestamp()` cuando la fecha es `null`; `source` solo si hay `importedAt`. También fija el viaje `Timestamp` → `Instant` → `Timestamp` con una fecha con microsegundos, como las que escribe el importador con `serverTimestamp()`. La fecha vuelve igual **en milisegundos** (`toMillis()`, lo que compara `import-core.mjs`), no exacta: la app lee con `toDate()`, que trunca, y cambiarlo sería cambiar el comportamiento. Además, la relación entre `importedAt` y `updatedAt` no se invierte. Para probarla se puede sacar a una función `internal` sin cambiar su comportamiento. |
| **L312** (`RecipeUnitsFixtureTest`) | Cubre singular + singular: «1 diente» + «1 diente» = «2 dientes», que sale de `pluralOf`. |
| **L346** (revisión de «Añadir a la compra») | Hay dos tests. (1) Una fila PENDING larga, construida por el camino real y no con un `ReviewRowState` sintético, llega a `withInclude(true)` como dice el punto 1 del Review Focus de `agent/compra-sin-duplicados`. (2) El ViewModel del editor recibe el mismo producto comprado y pendiente (punto 3). |
| **L366** (reglas de tiendas) | Un test de reglas acepta justo 6 tiendas, entre ellas las de barrio nuevas, y sigue rechazando 7. |
| **L406** (`BrandInput.toUpdateFields()`) | Un test JVM prueba que no escribe `ingredientId` y que borra (`FieldValue.delete()`) el código y los nutrientes ausentes. |

## No objetivos

- **L63** (mover el ancla de «Hoy» a `navigateToWorkspace`), el resto de «Sueltos», G4 (documentación) y los tests de L31 y L51 (van en G10).
- Cambiar el comportamiento de la app. En `app/src/main` solo se permiten cambios de visibilidad o de extracción para poder probar: `afterDownload` a `private` (L67) y `restoredFields` a función `internal` (L281). Si otro test los pide, valen con la misma condición.
- Cambiar `firestore.rules` o `migrations/`. Las mutaciones temporales para ver fallar un test se deshacen antes del informe.
- Gradle Managed Devices, Firebase Test Lab o ejecutar instrumentados dentro del job `android-quality`.
- Repartir la suite en varios emuladores.

## Anti-objetivos

Si pasa algo de esto, R2 ha fallado aunque todo esté en verde:

- **Un job que se pone en verde quitando tests.** Ni `@Ignore`, ni `@FlakyTest`, ni filtros de clase, ni anotaciones que excluyan tests sin una decisión explícita del usuario. Si un test falla solo en la CI (por el tamaño, la densidad, la API o el idioma del emulador), se corrige el test para que no dependa del dispositivo y siga midiendo lo mismo, o se para y se pregunta.
- **Reintentos automáticos** (`retry`, varias pasadas, `continue-on-error` en el paso del emulador) que escondan la inestabilidad.
- **Tests nuevos o corregidos que pasan con cualquier cosa.** Cada uno se ve fallar antes de darlo por bueno: o es RED de verdad, o se demuestra con una mutación temporal del código o del fixture, deshecha antes del informe (como en G1).
- **Un job que empieza en rojo en `main`.** Por eso se valida en el PR en borrador antes del merge.

## Instrucción del usuario para este plan

Como en G1: después del commit de la tarea del job, el coordinador sube la rama a `origin` y abre un **PR en borrador** contra `main` para que corran `ci.yml` e `instrumented.yml`. Vuelve a subirla antes de la pregunta del cierre. El plan no se cierra hasta ver las dos en verde en el último commit. En el cierre, las cuatro opciones siguen siendo del usuario: «PR» quita el borrador y «descartar» cierra el PR y borra también la rama remota.

Si el job sale en rojo en GitHub por un test que pasa en local, se trata como una tarea fallida, según el anti-objetivo de arriba.

## Lo que decide quien construye

- El número y el orden de las tareas: unas 5 o 6, con el job primero para que el PR en borrador lo pruebe pronto.
- Los detalles del workflow: la versión exacta de la imagen y del perfil, si se cachea el AVD, el `timeout-minutes`, cómo se habilita KVM y las rutas exactas de los informes.
- Cómo se valida en local la sintaxis del workflow antes de subirlo.
- El nombre y el sitio de cada test nuevo, dentro del fichero de test que ya cubre esa pieza cuando lo hay.
- Si cada test nuevo es JVM o de Compose (por ejemplo, el (1) de L346). R2 no tiene que añadir instrumentados.

## Decisiones del coordinador (no del usuario)

- Workflow aparte en lugar de un job dentro de `ci.yml`, para usar el filtro `paths` de GitHub sin acciones de terceros.
- Lo técnico del ADR: el runner, la imagen, el perfil, sin reintentos y los informes como artefacto.
- L65: borrar el test redundante en lugar de reescribirlo.
- L66: pasos con condición en el mismo job, en lugar de tres jobs que repetirían la preparación.
- L67: probar `afterDownload` a través del tracker y hacerla privada.
- Si la app de debug se cae sin `google-services.json`: el recurso `com.crashlytics.RequireBuildId` en `app/src/debug`, en lugar de un fichero ficticio en la CI o de tocar `build.gradle.kts`.
- L281: la fecha se compara en milisegundos, no exacta.
- El criterio de éxito cuenta los instrumentados que haya al final de la rama, no una cifra fija.
