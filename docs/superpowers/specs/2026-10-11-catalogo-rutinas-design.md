# Catálogo común de rutinas — Spec de diseño (subproyecto 1 de 2)

> Resultado de `superpowers:brainstorming` (camino **arquitectónico**), aprobado por el usuario el 2026-10-11. El plan de implementación sale de esta spec con `superpowers:writing-plans` y se ejecuta con `delegating-plan-execution`. El subproyecto 2 (catálogo de **recetas**) tendrá su propia spec después.

## Contexto

Las secciones «Comunes» de Rutinas y «Recetas comunes» del Recetario están vacías. Ya existen los catálogos de **ejercicios** (214, wger) e **ingredientes** (356, lista propia; desplegados en la beta 19). El usuario quiere bases de datos de **rutinas y recetas que casen con esos catálogos**. Se divide en dos subproyectos; se empieza por rutinas (el modelo ya sirve y los ejercicios se enlazan por id).

**Búsqueda de fuentes (2026-10-11):** no hay base de datos abierta de rutinas (free-exercise-db y similares son solo ejercicios; las plantillas públicas de wger exigen autenticación). La estructura de un programa de entrenamiento no tiene derechos de autor → **lista propia**. Para recetas, el usuario también eligió lista propia (TheMealDB prohíbe publicar apps en el plan gratuito; Wikilibros es CC BY-SA sin estructura; los volcados de Zenodo tienen licencia dudosa).

**Hoy (código):**
- `Routine` (`core/model/Routine.kt`): título, descripción, `visibility GLOBAL|PRIVATE`, `exercises: List<RoutineExercise>`; sin nivel, días ni descanso.
- `RoutineExercise` (`core/model/RoutineExercise.kt`): `name`, `exerciseId?` (slug del catálogo), `type`, `sets` (reps/weight/seconds), `durationMinutes`, `level`, `notes`, `measure`. Serialización única en `data/repository/RoutineExerciseFirestore.kt` (`toFirestoreExercises`, límites 30 ejercicios / 20 series).
- Comunes y personales comparten la colección `routines`; «Comunes» (`RoutinesScreen.kt`) y el selector de la sesión de gimnasio (`SportScreen.kt`, `RoutinePickerDialog`) ya muestran las GLOBAL, ordenadas por título. Al elegir una rutina en una sesión se copian sus ejercicios y ahí se rellenan los pesos.
- No hay «Guardar como mía» para rutinas (sí para recetas: `RecipesViewModel.fork()`, `sourceRecipeId`).
- `validRoutineShape` (`firestore.rules` ~l.1015) no admite `source` ni `sourceRoutineId`.

## Decisiones del usuario

1. Dos subproyectos con su spec/plan/rama; **rutinas primero**.
2. Fuente: **lista propia** (rutinas y recetas).
3. Contenido: las cuatro familias — gimnasio (fuerza), en casa, abdomen y core, cardio/HIIT.
4. **Solo sesiones sueltas**: nada de programas de varios días ni series A/B; cada rutina se entiende sola.
5. **«Guardar como mía»** en rutinas, como en recetas.
6. Enfoque **A**: `routines.json` en el repo + validación contra `exercises.json` + revisión + importador.
7. Despliegue propio (la beta 19 ya salió): reglas → importación → **beta 20**.
8. **Comunes siempre en «Comunes»:** el admin del catálogo es la cuenta del usuario y las importadas son suyas (`ownerUid`). «Mis rutinas» muestra solo las privadas del usuario; todas las GLOBAL van a «Comunes» (el admin conserva ahí Editar/Eliminar) y «Guardar como mía» sale en cualquier común, también para el admin.

## 1. Contenido y formato

~30 rutinas, títulos descriptivos, solo ejercicios del catálogo:

| Familia | Nº | Ejemplos |
|---|---|---|
| Gimnasio · fuerza | ~12 | Cuerpo completo para empezar, Cuerpo completo intermedio, Torso, Pierna, Empuje, Tirón, Pierna y glúteo, Circuito de máquinas, Espalda y bíceps, Pecho y tríceps, Hombros |
| En casa | ~6 | Peso corporal para empezar / intermedio, Mancuernas cuerpo completo, Mancuernas torso y pierna, Kettlebell |
| Abdomen y core | ~5 | Abdomen exprés 10 min, Core con planchas, Abdomen en polea y máquina |
| Cardio / HIIT | ~6 | Cinta suave, Elíptica moderada, Intervalos en remo, HIIT con comba, HIIT con peso corporal |

Sin estiramientos ni movilidad (el catálogo no tiene esos ejercicios). La descripción sigue siempre la plantilla: **nivel · duración aprox. · material · descanso entre series · frecuencia sugerida** (≤1000 caracteres). Intervalos de HIIT y rangos de repeticiones van en `notes` del ejercicio.

`data/catalog/routines.json` (`{schemaVersion: 1, routines: [...]}`), con abreviatura de series:
```json
{ "title": "Cuerpo completo para empezar",
  "description": "Principiante · 45 min · máquinas y mancuernas · descansa 90 s · 2–3 veces por semana.",
  "exercises": [
    { "exerciseId": "prensa-de-piernas", "series": 3, "reps": 12 },
    { "exerciseId": "plancha", "series": 3, "seconds": 30 },
    { "exerciseId": "cinta-de-correr", "durationMinutes": 10, "level": "Suave", "notes": "Para terminar, ritmo cómodo" } ] }
```

`scripts/catalog/routines.mjs` (validación + construcción) y `build-routines.mjs` (CLI):
- `id = slug(title)` (reutilizar `slug` de `ingredients.mjs`/`normalize.mjs`); títulos e ids únicos; título 1–120.
- Cada `exerciseId` existe en `exercises.json`; se **copian del catálogo** `name`, `type` y `measure`.
- Medida REPS → `reps` obligatorio; SECONDS → `seconds`; CARDIO → `durationMinutes` y sin series; `series` 1–20; 1–30 ejercicios por rutina.
- Expande `series` a `sets` (`{reps, weight: null}` o `{reps: null, weight: null, seconds}`); **nunca pesos**.
- Genera `data/catalog/routines-review.md` («no se edita a mano»): cada rutina con descripción y ejercicios por nombre (p. ej. «Prensa de piernas — 3 × 12»). **Parada:** el usuario lo revisa antes de importar.

## 2. Firestore, reglas e importador

**Documento** `routines/{slug}`: `visibility: "GLOBAL"`, `ownerUid`/`createdBy`/`updatedBy` = `CATALOG_ADMIN_UID`, `createdByName: "Catálogo Bobitos"`, `title`, `description`, `exercises`, `createdAt`/`updatedAt`, `source: {provider: "bobitos", importedAt}`. Los ejercicios se escriben **idénticos** a `toFirestoreExercises()` (`measure` solo si SECONDS; `seconds` solo si hay valor; `weight: null`) para que «sin cambios» se detecte bien.

**Reglas** (`firestore.rules`):
- `validRoutineShape`: `hasOnly` añade `source` y `sourceRoutineId`; `sourceRoutineId` string ≤128.
- `validNewRoutine`: el cliente no puede crear `source`.
- `validRoutineUpdate`: sin cambios en `affectedKeys` (ya excluye `source` y `sourceRoutineId`); `FirestoreRoutineRepository.updateRoutine` usa `update()` campo a campo, así que conserva `source`.

**Importador** `scripts/catalog/import-routines.mjs` + `routine-import-plan.mjs` sobre `planCatalogImport`/`formatImportPlan`/`commitCatalogOps` (`import-core.mjs`) y `resolveTarget`/`parseImportArgs` (`admin-cli.mjs`):
- Simulación por defecto («Simulación: no se ha escrito nada»); `--apply` escribe.
- Nunca borra (huérfanas se informan); se salta rutinas de usuarios (`ownerUid` ≠ admin), comunes manuales sin `source` y comunes editadas en la app (`updatedAt > source.importedAt`).
- **Sin `catalogMeta`**: las rutinas se leen con listener en vivo; `commitCatalogOps` admite `metaPath` nulo (sin incremento de versión). Exercises/ingredients no cambian de comportamiento.
- npm: `catalog:routines` (valida + revisión) y `catalog:import-routines`.

## 3. App

- `Routine.sourceRoutineId: String?`, leído/escrito en `FirestoreRoutineRepository` (`toRoutine`, `createRoutine(..., sourceRoutineId)`); la interfaz `RoutineRepository` y los fakes de test se actualizan.
- `RoutinesViewModel.fork(source)` calcado de `RecipesViewModel.fork()`: copia PRIVATE con título, descripción, ejercicios y `sourceRoutineId`; aviso «Guardada en «Mis rutinas»» (nuevo `RoutineUiMessage`).
- Botón «Guardar como mía» en `RoutineDetailSheet` (`RoutinesScreen.kt` ~l.371) para **cualquier** rutina GLOBAL si `canWrite` (decisión 8); reutilizar el estilo de `RecipeSheets.kt:133`. Cadenas en `strings.xml`.
- Secciones (decisión 8): «Mis rutinas» = solo las PRIVATE del usuario; «Comunes» = todas las GLOBAL, sin excluir las del propio usuario. El selector de la sesión de gimnasio no cambia.

## 4. Documentación

- `data/catalog/LICENSE.md`: rutinas = lista propia, sin derivar de ninguna base de datos.
- `docs/ROUTINE_CATALOG.md` (nuevo, al estilo de `docs/INGREDIENT_CATALOG.md`): formato, validación, revisión, importación.
- `docs/DATA_MODEL.md`: `source` y `sourceRoutineId` en rutinas.
- Créditos (`CreditsDialog.kt`): sin cambios; el bloque «Catálogo Bobitos» ya cubre el contenido propio.

## 5. Pruebas

| Suite | Qué |
|---|---|
| NODE (`npm run test:scripts`, nuevo `tests/catalog-routines.test.mjs` + `catalog-routine-import-plan.test.mjs`) | exerciseId desconocido, medida incoherente, cardio con series, >30 ejercicios / >20 series, título repetido, expansión de `series`, forma idéntica a `toFirestoreExercises`, plan de importación; `routines.json` real valida |
| IMP (`test:catalog-import`, nuevo `tests/routine-import.emulator.test.mjs`) | crear, actualizar, sin cambios, saltar editadas en la app, manuales y de usuarios; sin `catalogMeta` |
| EMU (`tests/firebase-emulators.test.mjs`) | copia con `sourceRoutineId`; admin edita común con `source`; cliente no crea `source` ni cambia `sourceRoutineId` |
| JVM | `fork()`, mapeo de `sourceRoutineId`, botón visible solo en comunes ajenas |
| Punta a punta (emulador) | importar → «Comunes» → «Guardar como mía» → sesión de gimnasio con una común; capturas claro/oscuro |

## 6. Plan y despliegue

- **Un plan grande** en la rama `agent/catalogo-rutinas` (revisión final con Opus + simplify + code-review high). Parada del usuario: `routines-review.md` antes de cualquier importación.
- **Despliegue propio**, cada paso confirmado por el usuario (ver trampas de beta: `--project dev`, JAVA_HOME de Android Studio, `beta:distribute` sube el APK viejo si falla el build): reglas → simulación → `--apply` (clave en `~/.config/bobitos/bobitos-dev-adminsdk.json`) → beta 20 con notas en `distribution/release-notes.txt`.

## Fuera de alcance

Programas de varios días o campo «programa»; estiramientos/movilidad; nivel, descanso o etiquetas como campos del modelo; filtros nuevos en Rutinas; abrir la ficha del ejercicio desde la rutina; recetas (subproyecto 2, que tendrá el mismo problema de «Mis recetas» del admin).

## Verificación de esta spec

Cada decisión (1–8) tiene sección: 1→§6, 2→Contexto, 3–4→§1, 5→§3, 6→§1–2, 7→§6, 8→§3. Piezas reutilizadas: `planCatalogImport`, `commitCatalogOps`, `resolveTarget`, `parseImportArgs`, `slug`, `toFirestoreExercises`, `RecipesViewModel.fork()`, `CATALOG_ADMIN_UID`/`RecipeAdmins`.

