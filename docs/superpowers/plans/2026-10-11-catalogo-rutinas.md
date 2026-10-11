# Catálogo común de rutinas — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (6 tareas), así que lleva los pasos 1 a 6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). Toca `firestore.rules` (permisos): un arreglo que la cambie lleva re-revisión acotada. Los pasos usan casillas (`- [ ]`).

**Objetivo:** la sección «Comunes» de Rutinas trae 29 sesiones sueltas hechas solo con ejercicios del catálogo, importadas con un importador seguro, y cualquier común se puede copiar con «Guardar como mía». Además, los filtros de tipo y grupo muscular de Ejercicios (y del selector de ejercicios de Rutinas) pasan a ser dos desplegables a la misma altura, uno al lado del otro, debajo del buscador.

**Arquitectura:**
- **Datos:** `data/catalog/routines.json` (lista propia, series abreviadas) se valida contra `data/catalog/exercises.json` y se expande a la forma exacta que escribe la app (`toFirestoreExercises()`). `routines-review.md` es la revisión del usuario.
- **Importador:** `import-routines.mjs` sobre el núcleo genérico `import-core.mjs` (simulación por defecto, nunca borra, respeta lo editado en la app), sin `catalogMeta` (las rutinas se leen en vivo).
- **Reglas:** `routines` admite `source` (solo Admin SDK) y `sourceRoutineId` (copias).
- **App:** «Mis rutinas» = solo privadas; «Comunes» = todas las GLOBAL; «Guardar como mía» en el detalle de cualquier común.
- **Filtros de ejercicios:** `ExerciseFilterChips` se sustituye por `ExerciseFilterMenus` (misma firma): una `Row` con dos `ExposedDropdownMenuBox` de Material 3 con `weight(1f)`, compartida por la pantalla Ejercicios y el selector.

**Stack:** Node 22 (`node --test`, firebase-admin), reglas de Firestore (`@firebase/rules-unit-testing`), Kotlin + Jetpack Compose + Hilt.

**Spec:** `docs/superpowers/specs/2026-10-11-catalogo-rutinas-design.md` (decisiones 1–8). El despliegue (§6) va después del cierre del plan, con el usuario.

**Tarea 6 (filtros), decisiones del usuario:** (2026-10-10) «No me gusta la interfaz. Quiero que salgan uno al lado del otro y que sean desplegables», en Ejercicios y en el selector de Rutinas con un solo componente (en el diálogo, más estrecho, un valor largo como «Peso corporal» puede verse cortado en la casilla, aunque en el menú se lee entero); (2026-10-11) «Añade que los filtros de ejercicios que están debajo del buscador estén a la misma altura y sean desplegables» → se incorpora aquí el plan sin commitear `2026-10-10-filtros-desplegables-ejercicios.md`.

## Rulings de planificación (desviaciones conscientes de la spec)

1. **Contenido: 29 rutinas** (la spec dice «~30»). «Mancuernas torso y pierna» se divide en «Mancuernas torso» y «Mancuernas pierna»; «Kettlebell» pasa a «Kettlebell y peso corporal» (el catálogo solo tiene 2 ejercicios de kettlebell); se añaden «Fuerza con básicos», «Core avanzado», «Core suave en casa» y «Cardio variado en el gimnasio». Coste si mal: el usuario lo corrige en la parada de la Tarea 2.
2. **`level` solo en cardio y «otros»**, y `OTROS` se trata como `CARDIO` (minutos, sin series), igual que el editor de la app (`ExerciseListEditor.kt:290`, `recordsSets`).
3. **Límites propios** del contenido (la app no los tiene): `notes` ≤ 200, `level` ≤ 30, `reps` 1–100, `seconds` 1–600, `durationMinutes` 1–180, `series` 1–20 (= `MAX_ROUTINE_SETS`).
4. **El importador informa de las «Comunes creadas a mano en la app»** (GLOBAL del admin sin `source` y con otro id) para que el usuario decida si borrarlas tras importar; no las toca. La spec no lo pide.
5. **`sourceRoutineId` solo se escribe si hay valor**; las reglas aceptan ausente, `null` o texto de 1 a 128.
6. **`source` reutiliza `validIngredientSource`** en las reglas: misma forma `{provider: "bobitos", importedAt}`.
7. **El mapeo de `sourceRoutineId` en `toRoutine()`** no tiene test JVM (es privado y depende de `DocumentSnapshot`, sin Robolectric ni mockk): lo cubre la verificación de punta a punta.
8. **Tras «Guardar como mía» se cierra el detalle**, como en recetas (`RecipesScreen.kt:227-230`), para que se vea el aviso.

## Restricciones globales

- **Rutas:** `SC` = `scripts/catalog/`; `T` = `tests/`; `DC` = `data/catalog/`; `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`; `RES` = `app/src/main/res/values/strings.xml`.
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **NODE:** `npm run test:scripts`
  - **IMP:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:catalog-import`
  - **EMU** (reglas): `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:emulators`
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
- **Prohibido:** cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`), `npm run catalog:fetch`, desplegar, distribuir betas, push. No se descarga nada.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Ejercicios e ingredientes sin cambios de comportamiento:** los tests que ya existen no se modifican (solo se añaden tests nuevos) y siguen en verde; `import-plan.mjs` e `ingredient-import-plan.mjs` mantienen exportaciones, firmas y salida.
- **Slug:** `slug()` de `SC/normalize.mjs` (idéntico al de `APP/core/model/CatalogIngredient.kt`). El id de cada rutina es `slug(title)`.
- **Tipos de fuerza:** `STRENGTH_TYPES` de `SC/normalize.mjs` (`MAQUINA`, `PESO_LIBRE`, `PESO_CORPORAL`) = `recordsSets` de la app. `CARDIO` y `OTROS` llevan minutos.
- **Documento común `routines/{slug}`** (lo escribe el importador): `visibility: "GLOBAL"`, `ownerUid` = `createdBy` = `updatedBy` = `CATALOG_ADMIN_UID`, `createdByName: "Catálogo Bobitos"` (`CATALOG_AUTHOR_NAME`), `title`, `description`, `exercises`, `createdAt` (solo al crear), `updatedAt`, `source: {provider: "bobitos", importedAt}`. **Nunca pesos** (`weight: null`).
- **Forma de cada ejercicio** = `toFirestoreExercises()` (`APP/data/repository/RoutineExerciseFirestore.kt`): `{name, exerciseId, type, sets, durationMinutes, level, notes}` con `null` explícitos; `measure: "SECONDS"` solo en series por tiempo; serie de repeticiones `{reps, weight: null}`; serie por tiempo `{reps: null, weight: null, seconds}`; cardio/otros `sets: []`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano; sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (copiar el fallo en el informe), después el código.
- **detekt:** extraer código antes que tocar el baseline. **ktlint:** sin imports sin usar.

## Review Focus

1. **Una rutina importada se comporta en la app como una creada en la app:** la plancha conserva sus segundos (`measure: SECONDS`), el cardio sus minutos y su nivel, y los pesos salen vacíos; abrirla en el editor y guardarla, o usarla en una sesión, no pierde nada. → tabla compartida `T/fixtures/routine-exercises.json`: Node (Tarea 1) comprueba que el importador la produce y la JVM (Tarea 5) que la app la lee y la reescribe igual; EMU (Tarea 4) comprueba que el admin puede editar una importada.
2. **Reimportar no pisa nada:** sin cambios en el JSON no escribe; una común editada en la app o una ficha de usuario con el mismo id se omiten. → IMP (Tarea 3).
3. **El admin (cuenta del usuario) ve las importadas en «Comunes», no en «Mis rutinas»,** y puede «Guardar como mía». → JVM `personalOnly`/`canForkRoutine` (Tarea 5).
4. **«Guardar como mía» con doble toque o sin permiso:** una sola copia; sin escritura no aparece; un error de permisos se muestra. → JVM (Tarea 5).
5. **Un cliente no puede hacerse pasar por el catálogo** (crear con `source`) ni cambiar `sourceRoutineId` de su copia. → EMU (Tarea 4).

**Review Focus de la Tarea 6 (filtros):**
1. **Misma altura y diálogo estrecho:** las dos casillas miden lo mismo y quedan alineadas en una fila; en el selector, un valor largo («Peso corporal») no rompe la fila ni hace crecer la casilla en altura (como mucho, se corta en una línea).
2. **Teclado y foco:** tocar un desplegable no abre el teclado ni deja el buscador en un estado raro; al cerrar el menú (elegir o tocar fuera), la casilla no queda con el foco pegado.
3. **Catálogo vacío en el selector** (carga diferida): el tipo funciona, el grupo sale deshabilitado y no falla nada; cuando llega el catálogo, el grupo se habilita solo.
4. **Grupo elegido que desaparece del catálogo:** se sigue viendo en la casilla y en el menú, y «Todos los grupos» lo quita.
5. **Rotación y tema oscuro:** los filtros elegidos sobreviven a una rotación y el menú y las casillas se leen bien en tema oscuro.

---

### Task 1: Validación, construcción y revisión de rutinas (Node)

**Ficheros:**
- Crear: `SC/routines.mjs`, `SC/build-routines.mjs`, `T/catalog-routines.test.mjs`, `T/fixtures/routine-exercises.json`.
- Modificar: `package.json` (scripts `catalog:routines` y `test:scripts`).

**Interfaces:**
- Consume: `slug`, `STRENGTH_TYPES` de `SC/normalize.mjs`.
- Produce (las usan las Tareas 2, 3 y 5):
  - `ROUTINE_LIMITS` (objeto con `title: 120, description: 1000, exercises: 30, series: 20, reps: 100, seconds: 600, minutes: 180, level: 30, notes: 200`);
  - `indexExercises(exerciseCatalog) → Map<id, {name, type, measure}>`;
  - `validateRoutineCatalog(catalog, exercisesById) → string[]` (vacío = válido);
  - `expandRoutineExercise(entry, exercise) → objeto Firestore`;
  - `buildRoutines(catalog, exercisesById) → [{id, title, description, exercises}]`;
  - `renderRoutineReview(routines) → string`;
  - `T/fixtures/routine-exercises.json`: lista de `{caso, entry, exercise, firestore}`.

- [ ] **Step 1: Crear la tabla compartida** `T/fixtures/routine-exercises.json` (la usa también la JVM en la Tarea 5):

```json
[
  {
    "caso": "repeticiones",
    "entry": { "exerciseId": "prensa-de-piernas", "series": 2, "reps": 12 },
    "exercise": { "name": "Prensa de piernas", "type": "MAQUINA", "measure": "REPS" },
    "firestore": {
      "name": "Prensa de piernas", "exerciseId": "prensa-de-piernas", "type": "MAQUINA",
      "sets": [{ "reps": 12, "weight": null }, { "reps": 12, "weight": null }],
      "durationMinutes": null, "level": null, "notes": null
    }
  },
  {
    "caso": "segundos",
    "entry": { "exerciseId": "plancha", "series": 2, "seconds": 30, "notes": "Espalda recta" },
    "exercise": { "name": "Plancha", "type": "PESO_CORPORAL", "measure": "SECONDS" },
    "firestore": {
      "name": "Plancha", "exerciseId": "plancha", "type": "PESO_CORPORAL",
      "sets": [{ "reps": null, "weight": null, "seconds": 30 }, { "reps": null, "weight": null, "seconds": 30 }],
      "durationMinutes": null, "level": null, "notes": "Espalda recta", "measure": "SECONDS"
    }
  },
  {
    "caso": "cardio",
    "entry": { "exerciseId": "cinta-de-correr", "durationMinutes": 10, "level": "Suave" },
    "exercise": { "name": "Cinta de correr", "type": "CARDIO", "measure": "REPS" },
    "firestore": {
      "name": "Cinta de correr", "exerciseId": "cinta-de-correr", "type": "CARDIO",
      "sets": [], "durationMinutes": 10, "level": "Suave", "notes": null
    }
  },
  {
    "caso": "otros",
    "entry": { "exerciseId": "patada-de-gluteo-con-banda-elastica", "durationMinutes": 5 },
    "exercise": { "name": "Patada de glúteo con banda elástica", "type": "OTROS", "measure": "REPS" },
    "firestore": {
      "name": "Patada de glúteo con banda elástica", "exerciseId": "patada-de-gluteo-con-banda-elastica", "type": "OTROS",
      "sets": [], "durationMinutes": 5, "level": null, "notes": null
    }
  }
]
```

- [ ] **Step 2: Escribir los tests** `T/catalog-routines.test.mjs`:

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import {
  ROUTINE_LIMITS, buildRoutines, expandRoutineExercise, indexExercises, renderRoutineReview, validateRoutineCatalog,
} from "../scripts/catalog/routines.mjs";

const fixture = JSON.parse(await readFile(new URL("./fixtures/routine-exercises.json", import.meta.url), "utf8"));
const exercises = indexExercises({
  exercises: [
    { id: "prensa-de-piernas", name: "Prensa de piernas", type: "MAQUINA", measure: "REPS" },
    { id: "plancha", name: "Plancha", type: "PESO_CORPORAL", measure: "SECONDS" },
    { id: "cinta-de-correr", name: "Cinta de correr", type: "CARDIO" },
    { id: "patada-de-gluteo-con-banda-elastica", name: "Patada de glúteo con banda elástica", type: "OTROS" },
  ],
});
const routine = (over = {}) => ({
  title: "Pierna para empezar",
  description: "Principiante · 30 min · máquinas · descansa 90 s entre series · 2 veces por semana.",
  exercises: [{ exerciseId: "prensa-de-piernas", series: 3, reps: 12 }],
  ...over,
});
const cat = (...routines) => ({ schemaVersion: 1, routines });
const problems = (...routines) => validateRoutineCatalog(cat(...routines), exercises);
const withExercise = (e) => problems(routine({ exercises: [e] }));

test("indexExercises toma REPS si la ficha no dice medida", () => {
  assert.deepEqual(exercises.get("cinta-de-correr"), { name: "Cinta de correr", type: "CARDIO", measure: "REPS" });
});

test("expande cada caso de la tabla compartida a la forma de la app", () => {
  for (const c of fixture) {
    assert.deepEqual(expandRoutineExercise(c.entry, { ...c.exercise }), c.firestore, c.caso);
  }
});

test("una rutina correcta no da problemas y se construye con id = slug(title)", () => {
  assert.deepEqual(problems(routine()), []);
  const [built] = buildRoutines(cat(routine()), exercises);
  assert.equal(built.id, "pierna-para-empezar");
  assert.equal(built.exercises[0].sets.length, 3);
  assert.ok(built.exercises[0].sets.every((s) => s.weight === null));
});

test("rechaza un exerciseId que no está en el catálogo", () => {
  assert.match(withExercise({ exerciseId: "no-existe", series: 3, reps: 10 }).join("\n"), /no-existe/);
});

test("exige la medida del ejercicio: reps en repeticiones, seconds en tiempo", () => {
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: 3, seconds: 30 }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "plancha", series: 3, reps: 10 }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "plancha", series: 3, seconds: 30, reps: 10 }), []);
  assert.deepEqual(withExercise({ exerciseId: "plancha", series: 3, seconds: 30 }), []);
});

test("cardio y otros llevan minutos y nunca series; fuerza nunca lleva minutos ni nivel", () => {
  assert.notDeepEqual(withExercise({ exerciseId: "cinta-de-correr", series: 3, reps: 10 }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "cinta-de-correr" }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "patada-de-gluteo-con-banda-elastica", series: 2, reps: 10 }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: 3, reps: 12, durationMinutes: 5 }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: 3, reps: 12, level: "Suave" }), []);
  assert.deepEqual(withExercise({ exerciseId: "cinta-de-correr", durationMinutes: 20, level: "Moderado" }), []);
});

test("respeta los límites de series, ejercicios y textos", () => {
  const L = ROUTINE_LIMITS;
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: L.series + 1, reps: 10 }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: 0, reps: 10 }), []);
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: 2.5, reps: 10 }), []);
  const many = Array.from({ length: L.exercises + 1 }, () => ({ exerciseId: "prensa-de-piernas", series: 1, reps: 10 }));
  assert.notDeepEqual(problems(routine({ exercises: many })), []);
  assert.notDeepEqual(problems(routine({ exercises: [] })), []);
  assert.notDeepEqual(problems(routine({ title: "x".repeat(L.title + 1) })), []);
  assert.notDeepEqual(problems(routine({ description: "x".repeat(L.description + 1) })), []);
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: 3, reps: 12, notes: "x".repeat(L.notes + 1) }), []);
});

test("rechaza claves desconocidas, títulos mal escritos y títulos repetidos", () => {
  assert.notDeepEqual(problems(routine({ nivel: "Principiante" })), []);
  assert.notDeepEqual(withExercise({ exerciseId: "prensa-de-piernas", series: 3, reps: 12, weight: 40 }), []);
  assert.notDeepEqual(problems(routine({ title: "pierna" })), []);
  assert.notDeepEqual(problems(routine({ title: "Pierna  doble" })), []);
  assert.match(problems(routine(), routine({ title: "Pierna para empezar" })).join("\n"), /id repetido/);
  assert.match(problems(routine(), routine({ title: "Pierna para émpezar" })).join("\n"), /id repetido/);
});

test("rechaza un catálogo sin schemaVersion 1 o sin rutinas", () => {
  assert.notDeepEqual(validateRoutineCatalog({ schemaVersion: 2, routines: [routine()] }, exercises), []);
  assert.notDeepEqual(validateRoutineCatalog({ schemaVersion: 1, routines: [] }, exercises), []);
});

test("la revisión enseña cada rutina con su pauta legible", () => {
  const built = buildRoutines(cat(routine({
    exercises: [
      { exerciseId: "prensa-de-piernas", series: 3, reps: 12 },
      { exerciseId: "plancha", series: 3, seconds: 30, notes: "Espalda recta" },
      { exerciseId: "cinta-de-correr", durationMinutes: 10, level: "Suave" },
    ],
  })), exercises);
  const md = renderRoutineReview(built);
  assert.match(md, /^# Revisión del catálogo de rutinas/);
  assert.match(md, /Total: 1 rutinas, 3 ejercicios\./);
  assert.match(md, /## Pierna para empezar/);
  assert.match(md, /`pierna-para-empezar`/);
  assert.match(md, /\| Prensa de piernas \| 3 × 12 \|  \|/);
  assert.match(md, /\| Plancha \| 3 × 30 s \| Espalda recta \|/);
  assert.match(md, /\| Cinta de correr \| 10 min · Suave \|  \|/);
});
```

- [ ] **Step 3: Añadir el test a NODE** en `package.json`: al final de `test:scripts` añade ` tests/catalog-routines.test.mjs`.

- [ ] **Step 4: Ejecutar NODE y verlo en rojo** (no existe `routines.mjs`). Copia el fallo en el informe.

- [ ] **Step 5: Implementar** `SC/routines.mjs`:

```js
// Catálogo común de rutinas de Bobitos (lista propia): validación contra el catálogo de ejercicios,
// construcción de los documentos y revisión. Funciones puras y sin dependencias de red.
import { STRENGTH_TYPES, slug } from "./normalize.mjs";

// Límites del contenido. `exercises` y `series` son los de la app (MAX_ROUTINE_EXERCISES/SETS); title y
// description, los de las reglas; el resto son propios del catálogo.
export const ROUTINE_LIMITS = {
  title: 120, description: 1000, exercises: 30, series: 20, reps: 100, seconds: 600, minutes: 180, level: 30, notes: 200,
};
const L = ROUTINE_LIMITS;
const ROUTINE_KEYS = ["title", "description", "exercises"];
const EXERCISE_KEYS = ["exerciseId", "series", "reps", "seconds", "durationMinutes", "level", "notes"];

const isObject = (x) => x != null && typeof x === "object" && !Array.isArray(x);
const isIntIn = (x, min, max) => Number.isInteger(x) && x >= min && x <= max;
const isTextIn = (x, max) =>
  typeof x === "string" && x.length >= 1 && x.length <= max && x === x.trim() && !/\s{2,}/.test(x);

/** Índice id → {name, type, measure} de `exercises.json` (medida REPS si la ficha no la dice). */
export function indexExercises(exerciseCatalog) {
  return new Map(exerciseCatalog.exercises.map((e) => [e.id, { name: e.name, type: e.type, measure: e.measure ?? "REPS" }]));
}

function validateRoutineExercise(entry, exercisesById) {
  if (!isObject(entry)) return ["El ejercicio no es un objeto."];
  const problems = [];
  const extra = Object.keys(entry).filter((k) => !EXERCISE_KEYS.includes(k));
  if (extra.length) problems.push(`Claves no permitidas: ${extra.join(", ")}.`);
  const exercise = exercisesById.get(entry.exerciseId);
  if (!exercise) return [...problems, `exerciseId «${entry.exerciseId}» no está en exercises.json.`];
  const has = (k) => k in entry;
  if (STRENGTH_TYPES.includes(exercise.type)) {
    if (!isIntIn(entry.series, 1, L.series)) problems.push(`series tiene que ser un entero entre 1 y ${L.series}.`);
    if (exercise.measure === "SECONDS") {
      if (!isIntIn(entry.seconds, 1, L.seconds)) problems.push(`seconds tiene que ser un entero entre 1 y ${L.seconds}.`);
      if (has("reps")) problems.push("Un ejercicio por tiempo no lleva reps.");
    } else {
      if (!isIntIn(entry.reps, 1, L.reps)) problems.push(`reps tiene que ser un entero entre 1 y ${L.reps}.`);
      if (has("seconds")) problems.push("Un ejercicio de repeticiones no lleva seconds.");
    }
    for (const k of ["durationMinutes", "level"]) if (has(k)) problems.push(`Un ejercicio de fuerza no lleva ${k}.`);
  } else {
    if (!isIntIn(entry.durationMinutes, 1, L.minutes)) {
      problems.push(`durationMinutes tiene que ser un entero entre 1 y ${L.minutes}.`);
    }
    for (const k of ["series", "reps", "seconds"]) if (has(k)) problems.push(`Un ejercicio de cardio u otros no lleva ${k}.`);
    if (has("level") && !isTextIn(entry.level, L.level)) {
      problems.push(`level tiene que tener entre 1 y ${L.level} caracteres, sin espacios de más.`);
    }
  }
  if (has("notes") && !isTextIn(entry.notes, L.notes)) {
    problems.push(`notes tiene que tener entre 1 y ${L.notes} caracteres, sin espacios de más.`);
  }
  return problems;
}

function validateRoutine(routine, exercisesById) {
  if (!isObject(routine)) return ["La rutina no es un objeto."];
  const problems = [];
  const extra = Object.keys(routine).filter((k) => !ROUTINE_KEYS.includes(k));
  const missing = ROUTINE_KEYS.filter((k) => !(k in routine));
  if (extra.length) problems.push(`Claves no permitidas: ${extra.join(", ")}.`);
  if (missing.length) problems.push(`Faltan claves: ${missing.join(", ")}.`);
  const { title, description, exercises } = routine;
  if (!isTextIn(title, L.title)) problems.push(`title tiene que tener entre 1 y ${L.title} caracteres, sin espacios de más.`);
  else if (!/\p{Lu}/u.test(title[0])) problems.push("title tiene que empezar por una letra mayúscula.");
  if (!isTextIn(description, L.description)) {
    problems.push(`description tiene que tener entre 1 y ${L.description} caracteres, sin espacios de más.`);
  }
  if (!Array.isArray(exercises) || exercises.length < 1 || exercises.length > L.exercises) {
    problems.push(`exercises tiene que ser una lista de 1 a ${L.exercises} ejercicios.`);
  } else {
    exercises.forEach((e, i) => {
      for (const p of validateRoutineExercise(e, exercisesById)) problems.push(`#${i + 1} ${e?.exerciseId ?? "?"}: ${p}`);
    });
  }
  return problems;
}

export function validateRoutineCatalog(catalog, exercisesById) {
  const problems = [];
  if (!catalog || catalog.schemaVersion !== 1) problems.push("schemaVersion tiene que ser 1.");
  const list = catalog?.routines;
  if (!Array.isArray(list) || list.length === 0) {
    problems.push("routines tiene que ser una lista no vacía.");
    return problems;
  }
  const ids = new Set();
  list.forEach((r, i) => {
    for (const p of validateRoutine(r, exercisesById)) problems.push(`${r?.title ?? `#${i + 1}`}: ${p}`);
    if (typeof r?.title !== "string") return;
    const id = slug(r.title);
    if (ids.has(id)) problems.push(`id repetido: ${id}`);
    else ids.add(id);
  });
  return problems;
}

/**
 * Ejercicio de `routines.json` → el mismo mapa que escribe `toFirestoreExercises()` en la app
 * (RoutineExerciseFirestore.kt): null explícitos, `measure` solo si es SECONDS y nunca pesos.
 */
export function expandRoutineExercise(entry, exercise) {
  const strength = STRENGTH_TYPES.includes(exercise.type);
  const timed = strength && exercise.measure === "SECONDS";
  const set = () => (timed ? { reps: null, weight: null, seconds: entry.seconds } : { reps: entry.reps, weight: null });
  const out = {
    name: exercise.name,
    exerciseId: entry.exerciseId,
    type: exercise.type,
    sets: strength ? Array.from({ length: entry.series }, set) : [],
    durationMinutes: strength ? null : entry.durationMinutes,
    level: strength ? null : (entry.level ?? null),
    notes: entry.notes ?? null,
  };
  return timed ? { ...out, measure: "SECONDS" } : out;
}

/** Rutinas listas para importar, en el orden del fichero. Exige un catálogo ya validado. */
export function buildRoutines(catalog, exercisesById) {
  return catalog.routines.map((r) => ({
    id: slug(r.title),
    title: r.title,
    description: r.description,
    exercises: r.exercises.map((e) => expandRoutineExercise(e, exercisesById.get(e.exerciseId))),
  }));
}

// «3 × 12», «3 × 30 s» o «10 min · Suave».
function pauta(e) {
  if (e.sets.length) {
    const s = e.sets[0];
    return e.measure === "SECONDS" ? `${e.sets.length} × ${s.seconds} s` : `${e.sets.length} × ${s.reps}`;
  }
  return [`${e.durationMinutes} min`, e.level].filter(Boolean).join(" · ");
}

export function renderRoutineReview(routines) {
  const total = routines.reduce((n, r) => n + r.exercises.length, 0);
  const lines = [
    "# Revisión del catálogo de rutinas",
    "",
    "Generado con `npm run catalog:routines` a partir de `data/catalog/routines.json`; no se edita a mano.",
    "",
    `Total: ${routines.length} rutinas, ${total} ejercicios.`,
    "",
  ];
  for (const r of routines) {
    lines.push(`## ${r.title}`, "", `\`${r.id}\``, "", r.description, "", "| Ejercicio | Pauta | Notas |", "|---|---|---|");
    for (const e of r.exercises) lines.push(`| ${e.name} | ${pauta(e)} | ${e.notes ?? ""} |`);
    lines.push("");
  }
  return lines.join("\n");
}
```

- [ ] **Step 6: Implementar la CLI** `SC/build-routines.mjs`:

```js
// Valida data/catalog/routines.json contra exercises.json y genera data/catalog/routines-review.md.
import { readFile, writeFile } from "node:fs/promises";
import { buildRoutines, indexExercises, renderRoutineReview, validateRoutineCatalog } from "./routines.mjs";

const ROOT = new URL("../../", import.meta.url);
const SOURCE = new URL("data/catalog/routines.json", ROOT);
const EXERCISES = new URL("data/catalog/exercises.json", ROOT);
const REVIEW = new URL("data/catalog/routines-review.md", ROOT);

const catalog = JSON.parse(await readFile(SOURCE, "utf8"));
const exercisesById = indexExercises(JSON.parse(await readFile(EXERCISES, "utf8")));
const problems = validateRoutineCatalog(catalog, exercisesById);
if (problems.length) {
  for (const p of problems) console.error(`- ${p}`);
  console.error(`${problems.length} problemas en data/catalog/routines.json.`);
  process.exit(1);
}
const routines = buildRoutines(catalog, exercisesById);
await writeFile(REVIEW, renderRoutineReview(routines));
console.log(`Catálogo de rutinas: ${routines.length} rutinas. Revisión en data/catalog/routines-review.md`);
```

Y en `package.json`, junto a `catalog:ingredients`: `"catalog:routines": "node scripts/catalog/build-routines.mjs",`.

- [ ] **Step 7: Ejecutar NODE y verlo en verde** (todas las suites, también las de ejercicios e ingredientes).

- [ ] **Step 8: Commit**

```bash
git add scripts/catalog/routines.mjs scripts/catalog/build-routines.mjs tests/catalog-routines.test.mjs tests/fixtures/routine-exercises.json package.json
git commit -m "feat(catalogo): validación, construcción y revisión del catálogo de rutinas"
```

---

### Task 2: Contenido: las 29 rutinas comunes

**Ficheros:**
- Crear: `DC/routines.json`, `DC/routines-review.md` (generado).
- Modificar: `T/catalog-routines.test.mjs` (añadir un test al final).

**Interfaces:**
- Consume: `validateRoutineCatalog`, `buildRoutines`, `indexExercises` (Tarea 1); `npm run catalog:routines`.
- Produce: `DC/routines.json` con `{ "schemaVersion": 1, "routines": [...] }` en el orden de abajo (lo usan la Tarea 3 y el despliegue).

**Notación de la lista** (transcríbela **exactamente** a JSON, en este orden):
- `# Título` → `title`. La línea siguiente → `description`.
- `id 3x12` → `{ "exerciseId": "id", "series": 3, "reps": 12 }`.
- `id 3x30s` → `{ "exerciseId": "id", "series": 3, "seconds": 30 }`.
- `id 10' Suave` → `{ "exerciseId": "id", "durationMinutes": 10, "level": "Suave" }`.
- `… | Texto` → añade `"notes": "Texto"`.

```text
# Cuerpo completo para empezar
Principiante · 45 min · máquinas · descansa 90 s entre series · 2 o 3 veces por semana.
prensa-de-piernas 3x12
press-de-pecho-en-maquina 3x12
jalon-al-pecho 3x12
remo-en-maquina 3x12
press-de-hombros-en-maquina 3x12
curl-femoral-sentado 3x12
plancha 3x30s

# Cuerpo completo intermedio
Intermedio · 60 min · barra, mancuernas y barra de dominadas · descansa 2 min en los básicos y 90 s en el resto · 3 veces por semana.
sentadilla-trasera-con-barra 4x8
press-de-banca 4x8
remo-con-barra 4x8
peso-muerto-rumano-con-barra 3x10
press-de-hombros-con-mancuernas 3x10
dominadas 3x8 | Si aún no llegas, hazlas asistidas con banda
plancha-lateral 3x30s | Por lado

# Circuito de máquinas
Principiante · 40 min · solo máquinas · descansa 60 s entre series · 2 o 3 veces por semana.
extension-de-cuadriceps-en-maquina 3x15
curl-femoral-tumbado 3x15
press-de-pecho-en-maquina 3x15
remo-en-maquina 3x15
elevacion-lateral-en-maquina 3x15
extension-de-triceps-en-maquina 3x15
crunch-en-maquina 3x15

# Torso
Intermedio · 60 min · barra, mancuernas y polea · descansa 90 s entre series · 2 veces por semana.
press-de-banca 4x8
remo-con-barra 4x8
press-militar-con-barra 3x8
jalon-al-pecho 3x10
aperturas-con-mancuernas 3x12
curl-con-barra 3x10
extension-de-triceps-con-cuerda 3x12

# Pierna
Intermedio · 60 min · barra y máquinas · descansa 2 min en la sentadilla y 90 s en el resto · 2 veces por semana.
sentadilla-trasera-con-barra 4x8
peso-muerto-rumano-con-barra 3x10
prensa-de-piernas 3x12
curl-femoral-sentado 3x12
extension-de-cuadriceps-en-maquina 3x12
elevacion-de-gemelos-de-pie-en-maquina 4x15

# Empuje
Intermedio · 60 min · barra, mancuernas, paralelas y polea · descansa 90 s entre series · 1 o 2 veces por semana.
press-de-banca 4x8
press-de-banca-inclinado-con-mancuernas 3x10
press-de-hombros-con-mancuernas 3x10
elevacion-lateral-con-mancuernas 3x15
fondos-en-paralelas 3x10
extension-de-triceps-en-polea 3x12

# Tirón
Intermedio · 60 min · barra de dominadas, barra, polea y mancuernas · descansa 90 s entre series · 1 o 2 veces por semana.
dominadas 4x8 | Si aún no llegas, hazlas asistidas con banda
remo-con-barra 4x8
jalon-al-pecho-con-agarre-neutro 3x10
remo-en-polea-baja 3x12
face-pull 3x15
curl-con-barra 3x10
curl-martillo-con-mancuernas 3x12

# Pierna y glúteo
Intermedio · 55 min · barra, mancuernas, polea y máquinas · descansa 90 s entre series · 1 o 2 veces por semana.
hip-thrust-con-barra 4x10
sentadilla-bulgara-con-mancuernas 3x10 | Por pierna
peso-muerto-rumano-con-mancuernas 3x12
abduccion-de-cadera-en-maquina 3x15
patada-de-gluteo-en-polea 3x12 | Por pierna
prensa-de-piernas 3x12

# Espalda y bíceps
Intermedio · 50 min · polea, mancuernas y barra Z · descansa 75 s entre series · 1 o 2 veces por semana.
jalon-al-pecho 4x10
remo-con-mancuerna-a-una-mano 3x10 | Por brazo
remo-en-polea-baja-con-agarre-estrecho 3x12
jalon-con-brazos-rectos 3x12
curl-con-barra-z 3x10
curl-inclinado-con-mancuernas 3x12
curl-en-polea 3x12

# Pecho y tríceps
Intermedio · 50 min · barra, mancuernas, máquinas y polea · descansa 75 s entre series · 1 o 2 veces por semana.
press-de-banca-con-mancuernas 4x10
press-de-banca-inclinado 3x8
aperturas-en-maquina-pec-deck 3x12
cruce-de-poleas 3x12
press-frances-con-barra-sz 3x10
extension-de-triceps-con-cuerda 3x12

# Hombros
Intermedio · 45 min · barra, mancuernas, máquina y polea · descansa 75 s entre series · 1 o 2 veces por semana.
press-militar-con-barra 4x8
press-arnold 3x10
elevacion-lateral-con-mancuernas 4x12
pajaro-en-maquina 3x15
face-pull 3x15
encogimientos-de-hombros-con-mancuernas 3x12

# Fuerza con básicos
Intermedio o avanzado · 60 min · barra y banco · descansa 3 min entre series · 2 o 3 veces por semana.
sentadilla-trasera-con-barra 5x5
press-de-banca 5x5
remo-pendlay 5x5
peso-muerto-convencional 3x5
press-militar-con-barra 3x5

# Peso corporal para empezar
Principiante · 25 min · sin material, solo una silla · descansa 60 s entre series · 3 veces por semana.
sentadilla-con-peso-corporal 3x12
flexiones-inclinadas 3x10 | Con las manos en una silla estable o en el banco
puente-de-gluteos 3x15
zancada-con-peso-corporal 3x10 | Por pierna
bird-dog 3x10 | Por lado
plancha 3x20s

# Peso corporal intermedio
Intermedio · 35 min · sin material, solo una silla y una barra baja · descansa 60 s entre series · 3 veces por semana.
sentadilla-pistol 3x6 | Por pierna; agárrate a algo si lo necesitas
flexiones 4x15
remo-invertido 3x10 | Con una barra baja y firme
sentadilla-cosaca 3x8 | Por lado
fondos-en-banco 3x12 | Con las manos en una silla estable o en el banco
curl-nordico 3x5 | Sujeta los pies bajo algo firme
hollow-hold 3x30s

# Mancuernas cuerpo completo
Principiante o intermedio · 40 min · un par de mancuernas y un banco o una silla · descansa 75 s entre series · 2 o 3 veces por semana.
zancadas-con-mancuernas 3x10 | Por pierna
press-de-suelo-con-mancuernas 3x10
remo-con-mancuerna-a-una-mano 3x10 | Por brazo
peso-muerto-rumano-con-mancuernas 3x12
press-de-hombros-con-mancuernas 3x10
curl-alterno-con-mancuernas 2x12
crunch-con-peso 3x15

# Mancuernas torso
Intermedio · 40 min · un par de mancuernas y un banco o una silla · descansa 75 s entre series · 2 veces por semana.
press-de-suelo-con-mancuernas 4x10
remo-con-mancuerna-a-una-mano 4x10 | Por brazo
press-de-hombros-con-mancuernas 3x10
elevacion-lateral-con-mancuernas 3x15
pullover-con-mancuerna 3x12
curl-martillo-con-mancuernas 3x12
extension-de-triceps-por-encima-de-la-cabeza-con-mancuerna 3x12

# Mancuernas pierna
Intermedio · 40 min · un par de mancuernas y un banco o una silla · descansa 75 s entre series · 2 veces por semana.
sentadilla-frontal-con-mancuernas 4x10
sentadilla-bulgara-con-mancuernas 3x10 | Por pierna
peso-muerto-rumano-con-mancuernas 3x12
hip-thrust-con-mancuerna 3x12
zancada-hacia-atras-con-mancuernas 3x10 | Por pierna
elevacion-de-gemelos-sentado-con-mancuerna 3x15

# Kettlebell y peso corporal
Intermedio · 30 min · una kettlebell · descansa 60 s entre series · 2 o 3 veces por semana.
swing-con-kettlebell 5x15
flexiones 4x12
zancada-con-peso-corporal 3x12 | Por pierna
giro-ruso-con-pesa-rusa 3x20
puente-de-gluteos 3x15
plancha 3x40s

# Abdomen exprés 10 min
Todos los niveles · 10 min · esterilla · descansa 30 s entre series · 3 o 4 veces por semana.
crunch-abdominal 2x20
crunch-inverso 2x15
crunch-bicicleta 2x20
toques-de-talon 2x20
plancha 2x40s

# Core con planchas
Principiante o intermedio · 20 min · esterilla · descansa 45 s entre series · 2 o 3 veces por semana.
plancha 3x45s
plancha-lateral 3x30s | Por lado
hollow-hold 3x30s
bicho-muerto 3x10 | Por lado
bird-dog 3x10 | Por lado

# Abdomen en polea y máquina
Intermedio · 25 min · polea, máquina y barra de dominadas · descansa 60 s entre series · 2 veces por semana.
crunch-en-polea 3x15
crunch-en-maquina 3x15
lenador-en-polea 3x12 | Por lado
pallof-press 3x12 | Por lado
elevaciones-de-rodillas-colgado 3x12

# Core avanzado
Avanzado · 25 min · barra de dominadas, rueda abdominal y banco · descansa 60 s entre series · 2 veces por semana.
toes-to-bar 3x8
rueda-abdominal 3x10
dragon-flag 3x5
l-sit 3x15s
limpiaparabrisas-colgado 3x8 | Por lado

# Core suave en casa
Principiante · 15 min · esterilla · descansa 45 s entre series · 3 veces por semana.
bird-dog 3x10 | Por lado
bicho-muerto 3x10 | Por lado
puente-de-gluteos 3x15
superman 3x12
plancha-lateral 2x20s | Por lado

# Cinta suave
Principiante · 30 min · cinta de correr · sin pausas · 2 a 4 veces por semana.
cinta-de-correr 5' Suave | Calentamiento caminando
cinta-de-correr 20' Moderado | A un ritmo que te deje hablar
cinta-de-correr 5' Suave | Vuelta a la calma

# Elíptica moderada
Principiante o intermedio · 30 min · elíptica · sin pausas · 2 a 4 veces por semana.
eliptica 5' Suave | Calentamiento
eliptica 25' Moderado | Ritmo constante; sube la resistencia si se hace fácil

# Intervalos en remo
Intermedio · 25 min · remo ergómetro · pausas activas · 2 veces por semana.
remo-ergometro 5' Suave | Calentamiento
remo-ergometro 16' Intenso | 8 rondas: 1 min fuerte y 1 min suave
remo-ergometro 4' Suave | Vuelta a la calma

# HIIT con comba
Intermedio · 20 min · comba · pausas dentro de cada bloque · 2 veces por semana.
saltar-a-la-comba 3' Suave | Calentamiento
saltar-a-la-comba 10' Intenso | 10 rondas: 30 s rápido y 30 s de descanso
montaneros 4' Intenso | 8 rondas: 20 s de trabajo y 10 s de descanso
caminata 3' Suave | Vuelta a la calma

# HIIT con peso corporal
Intermedio · 20 min · sin material · pausas dentro de cada bloque · 2 veces por semana.
jumping-jacks 3' Suave | Calentamiento
burpees 4' Intenso | 8 rondas: 20 s de trabajo y 10 s de descanso
montaneros 4' Intenso | 8 rondas: 20 s de trabajo y 10 s de descanso
rodillas-elevadas 4' Intenso | 8 rondas: 20 s de trabajo y 10 s de descanso
jumping-jacks 3' Suave | Vuelta a la calma

# Cardio variado en el gimnasio
Principiante o intermedio · 35 min · elíptica, bicicleta estática y remo ergómetro · sin pausas entre máquinas · 2 o 3 veces por semana.
eliptica 10' Moderado
bicicleta-estatica 10' Moderado
remo-ergometro 10' Moderado
caminata 5' Suave | Vuelta a la calma, en la cinta o fuera
```

- [ ] **Step 1: Añadir el test** al final de `T/catalog-routines.test.mjs`:

```js
test("el routines.json real es válido: 29 rutinas, sin pesos ni ids repetidos", async () => {
  const real = JSON.parse(await readFile(new URL("../data/catalog/routines.json", import.meta.url), "utf8"));
  const byId = indexExercises(JSON.parse(await readFile(new URL("../data/catalog/exercises.json", import.meta.url), "utf8")));
  assert.deepEqual(validateRoutineCatalog(real, byId), []);
  const built = buildRoutines(real, byId);
  assert.equal(built.length, 29);
  assert.equal(new Set(built.map((r) => r.id)).size, 29);
  assert.equal(built.reduce((n, r) => n + r.exercises.length, 0), 162);
  assert.ok(built.every((r) => r.exercises.every((e) => e.sets.every((s) => s.weight === null))));
});
```

- [ ] **Step 2: Ejecutar NODE y verlo en rojo** (falta `routines.json`). Copia el fallo.
- [ ] **Step 3: Escribir `DC/routines.json`** a partir de la lista (JSON con 2 espacios de sangría y salto de línea final).
- [ ] **Step 4:** `npm run catalog:routines` → «Catálogo de rutinas: 29 rutinas.» y se genera `DC/routines-review.md`.
- [ ] **Step 5: Ejecutar NODE y verlo en verde.**
- [ ] **Step 6: Commit**

```bash
git add data/catalog/routines.json data/catalog/routines-review.md tests/catalog-routines.test.mjs
git commit -m "feat(catalogo): 29 rutinas comunes con ejercicios del catálogo"
```

> **PARADA DEL USUARIO (tras la Tarea 2).** El coordinador comprueba que `routines.json` coincide con la lista de arriba (script de comparación propio, fuera del repo), enseña `DC/routines-review.md` al usuario y **espera su aprobación**. Los cambios que pida los aplica un subagente `sonnet` (editar `routines.json`, regenerar la revisión, ajustar los recuentos del test, NODE en verde, commit). No se sigue con la Tarea 3 hasta tener el visto bueno.

---

### Task 3: Importador de rutinas y documentación del catálogo

**Ficheros:**
- Modificar: `SC/import-core.mjs` (`formatImportPlan` y `commitCatalogOps` con `metaPath` opcional), `package.json`, `data/catalog/LICENSE.md`.
- Crear: `SC/routine-import-plan.mjs`, `SC/import-routines.mjs`, `T/catalog-routine-import-plan.test.mjs`, `T/routine-import.emulator.test.mjs`, `docs/ROUTINE_CATALOG.md`.
- Test (añadir, sin cambiar los existentes): `T/catalog-import-core.test.mjs`.

**Interfaces:**
- Consume: `validateRoutineCatalog`, `buildRoutines`, `indexExercises` (Tarea 1); `CATALOG_ADMIN_UID`, `baseExisting`, `stampCatalogDoc`, `planCatalogImport`, `formatImportPlan`, `section`, `loadExisting`, `commitCatalogOps` (`SC/import-core.mjs`); `CATALOG_AUTHOR_NAME` (`SC/import-plan.mjs`); `connectAdmin`, `parseImportArgs`, `runMain` (`SC/admin-cli.mjs`).
- Produce (la Tarea 4 usa `routineToFirestoreDoc`):
  - `ROUTINES_COLLECTION = "routines"`;
  - `routineManagedFields(entry)`, `routineDocToExisting(id, data, times)`, `routineToFirestoreDoc(entry, {now, create})`;
  - `planRoutineImport({routines, existing, adminUid}) → plan` (con `manualGlobal: string[]`), `formatRoutinePlan(plan) → string`;
  - `runRoutineImport({db, catalog, exercises, apply, log}) → plan` (`SC/import-routines.mjs`).

- [ ] **Step 1: Tests del núcleo** (añadir al final de `T/catalog-import-core.test.mjs`, sin tocar lo existente):

```js
test("formatImportPlan sin metaPath no habla de la versión del catálogo", () => {
  const p = planCatalogImport({ entries: [{ id: "a", name: "A" }], existing: [], managedFields: (e) => ({ name: e.name }) });
  const lines = formatImportPlan(p, { title: "Plan de importación de rutinas", metaPath: null });
  assert.equal(lines[0], "Plan de importación de rutinas");
  assert.ok(lines.every((l) => !l.includes("versión")));
});
```

- [ ] **Step 2: Tests del plan de rutinas** `T/catalog-routine-import-plan.test.mjs`:

```js
import assert from "node:assert/strict";
import { test } from "node:test";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-core.mjs";
import {
  ROUTINES_COLLECTION, formatRoutinePlan, planRoutineImport, routineDocToExisting, routineManagedFields, routineToFirestoreDoc,
} from "../scripts/catalog/routine-import-plan.mjs";

const r = (over = {}) => ({
  id: "torso", title: "Torso", description: "Intermedio · 60 min",
  exercises: [{ name: "Press de banca", exerciseId: "press-de-banca", type: "PESO_LIBRE",
    sets: [{ reps: 8, weight: null }], durationMinutes: null, level: null, notes: null }],
  ...over,
});
const imported = (e, over = {}) => ({
  id: e.id, ownerUid: CATALOG_ADMIN_UID, hasSource: true, updatedAtMillis: 1000, importedAtMillis: 1000,
  visibility: "GLOBAL", fields: routineManagedFields(e), ...over,
});
const plan = (routines, existing = []) => planRoutineImport({ routines, existing, adminUid: CATALOG_ADMIN_UID });

test("la colección es routines", () => assert.equal(ROUTINES_COLLECTION, "routines"));

test("al crear lleva visibilidad, dueño y autor del catálogo; al actualizar, solo lo gestionado", () => {
  const now = { marker: true };
  const e = r();
  assert.deepEqual(routineToFirestoreDoc(e, { now, create: true }), {
    title: "Torso", description: "Intermedio · 60 min", exercises: e.exercises,
    source: { provider: "bobitos", importedAt: now }, updatedBy: CATALOG_ADMIN_UID, updatedAt: now,
    visibility: "GLOBAL", ownerUid: CATALOG_ADMIN_UID, createdBy: CATALOG_ADMIN_UID, createdByName: "Catálogo Bobitos", createdAt: now,
  });
  assert.deepEqual(Object.keys(routineToFirestoreDoc(e, { now })).sort(),
    ["description", "exercises", "source", "title", "updatedAt", "updatedBy"]);
});

test("un documento importado sin cambios queda igual", () => {
  const e = r();
  const data = { ...routineToFirestoreDoc(e, { now: "t", create: true }) };
  const existing = [routineDocToExisting(e.id, data, { updatedAtMillis: 5, importedAtMillis: 5 })];
  assert.deepEqual(plan([e], existing).unchanged, ["torso"]);
});

test("cambiar la descripción o una serie actualiza", () => {
  assert.deepEqual(plan([r({ description: "Otra" })], [imported(r())]).update.map((x) => x.id), ["torso"]);
  const more = r({ exercises: [{ ...r().exercises[0], sets: [{ reps: 10, weight: null }] }] });
  assert.deepEqual(plan([more], [imported(r())]).update.map((x) => x.id), ["torso"]);
});

test("omite la rutina de un usuario, la manual del admin y la editada en la app", () => {
  assert.deepEqual(plan([r()], [imported(r(), { ownerUid: "u1", hasSource: false })]).skippedUserOwned,
    [{ id: "torso", ownerUid: "u1" }]);
  assert.deepEqual(plan([r({ description: "Otra" })], [imported(r(), { hasSource: false })]).skippedAdminManual, ["torso"]);
  assert.deepEqual(plan([r({ description: "Otra" })], [imported(r(), { updatedAtMillis: 2000 })]).skippedEditedInApp, ["torso"]);
});

test("informa de huérfanas y de comunes creadas a mano, sin contar las privadas del admin", () => {
  const p = plan([r()], [
    imported(r({ id: "vieja" })),
    imported(r({ id: "manual" }), { hasSource: false }),
    imported(r({ id: "privada" }), { hasSource: false, visibility: "PRIVATE" }),
    imported(r({ id: "de-usuario" }), { ownerUid: "u1", hasSource: false }),
  ]);
  assert.deepEqual(p.orphaned, ["vieja"]);
  assert.deepEqual(p.manualGlobal, ["manual"]);
});

test("el texto del plan no menciona versión y lista las comunes manuales", () => {
  const p = plan([r()], [imported(r({ id: "manual" }), { hasSource: false })]);
  const text = formatRoutinePlan(p);
  assert.match(text, /^Plan de importación de rutinas/);
  assert.match(text, /Crear: 1/);
  assert.doesNotMatch(text, /versión/);
  assert.match(text, /Comunes creadas a mano en la app \(no se tocan\): 1\n  - manual/);
});
```

- [ ] **Step 3: Test del emulador** `T/routine-import.emulator.test.mjs`:

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { beforeEach, test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-core.mjs";
import { runRoutineImport } from "../scripts/catalog/import-routines.mjs";
import { quiet, setupEmulatorDb } from "./helpers/emulator-db.mjs";

const exercises = JSON.parse(await readFile(new URL("../data/catalog/exercises.json", import.meta.url), "utf8"));
const full = JSON.parse(await readFile(new URL("../data/catalog/routines.json", import.meta.url), "utf8"));
const small = { schemaVersion: 1, routines: [
  { title: "Prueba fuerza", description: "Principiante · 10 min · máquinas · descansa 60 s · 1 vez.",
    exercises: [{ exerciseId: "prensa-de-piernas", series: 2, reps: 12 }, { exerciseId: "plancha", series: 2, seconds: 30 }] },
  { title: "Prueba cardio", description: "Principiante · 10 min · cinta · sin pausas · 1 vez.",
    exercises: [{ exerciseId: "cinta-de-correr", durationMinutes: 10, level: "Suave" }] },
] };

const db = setupEmulatorDb("routine-import-test");
beforeEach(async () => {
  await db.recursiveDelete(db.collection("routines"));
  await db.recursiveDelete(db.collection("catalogMeta"));
});
const run = (catalog, apply = true) => runRoutineImport({ db, catalog, exercises, apply, log: quiet });

test("importa como comunes del catálogo y una segunda pasada no escribe", async () => {
  const first = await run(small);
  assert.equal(first.create.length, 2);
  const d = (await db.doc("routines/prueba-fuerza").get()).data();
  assert.equal(d.visibility, "GLOBAL");
  assert.equal(d.ownerUid, CATALOG_ADMIN_UID);
  assert.equal(d.createdByName, "Catálogo Bobitos");
  assert.equal(d.source.provider, "bobitos");
  assert.equal(d.updatedAt.toMillis(), d.source.importedAt.toMillis());
  assert.equal(d.exercises[1].measure, "SECONDS");
  assert.deepEqual(d.exercises[1].sets[0], { reps: null, weight: null, seconds: 30 });
  const second = await run(small);
  assert.equal(second.create.length + second.update.length, 0);
  assert.equal(second.unchanged.length, 2);
  assert.equal((await db.collection("catalogMeta").get()).size, 0);
});

test("la simulación no escribe nada", async () => {
  const r = await run(small, false);
  assert.equal(r.create.length, 2);
  assert.equal((await db.collection("routines").get()).size, 0);
});

test("un cambio en el JSON actualiza solo esa rutina", async () => {
  await run(small);
  const [a, b] = small.routines;
  const r = await run({ ...small, routines: [{ ...a, description: `${a.description} Cambio.` }, b] });
  assert.deepEqual(r.update.map((e) => e.id), ["prueba-fuerza"]);
});

test("no pisa una común editada en la app ni la rutina de un usuario con el mismo id", async () => {
  await run(small);
  await db.doc("routines/prueba-fuerza").update({ title: "Editada", updatedAt: Timestamp.fromMillis(Date.now() + 60_000) });
  await db.doc("routines/prueba-cardio").delete();
  await db.doc("routines/prueba-cardio").set({ ownerUid: "u1", visibility: "PRIVATE", title: "Mía" });
  const r = await run(small);
  assert.deepEqual(r.skippedEditedInApp, ["prueba-fuerza"]);
  assert.deepEqual(r.skippedUserOwned, [{ id: "prueba-cardio", ownerUid: "u1" }]);
  assert.equal((await db.doc("routines/prueba-fuerza").get()).get("title"), "Editada");
  assert.equal((await db.doc("routines/prueba-cardio").get()).get("title"), "Mía");
});

test("informa de las comunes creadas a mano y no las toca", async () => {
  await db.doc("routines/manual1").set({ ownerUid: CATALOG_ADMIN_UID, visibility: "GLOBAL", title: "Full body" });
  const r = await run(small);
  assert.deepEqual(r.manualGlobal, ["manual1"]);
  assert.equal((await db.doc("routines/manual1").get()).get("title"), "Full body");
});

test("rechaza un catálogo inválido sin escribir", async () => {
  const bad = { ...small, routines: [{ ...small.routines[0], exercises: [{ exerciseId: "no-existe", series: 1, reps: 1 }] }] };
  await assert.rejects(run(bad), /Catálogo inválido/);
  assert.equal((await db.collection("routines").get()).size, 0);
});

test("importa el catálogo completo en un solo pase", async () => {
  const r = await run(full);
  assert.equal(r.create.length, full.routines.length);
  assert.equal((await db.collection("routines").get()).size, full.routines.length);
});
```

- [ ] **Step 4: Registrar los tests** en `package.json`: añade ` tests/catalog-routine-import-plan.test.mjs` al final de `test:scripts`, y ` tests/routine-import.emulator.test.mjs` dentro de las comillas escapadas de `test:catalog-import`, tras `tests/ingredient-reset.emulator.test.mjs`. Añade también `"catalog:import-routines": "node scripts/catalog/import-routines.mjs",` junto a `catalog:import-ingredients`.

- [ ] **Step 5: Ejecutar NODE e IMP y verlos en rojo.** Copia los fallos.

- [ ] **Step 6: Núcleo con `metaPath` opcional** en `SC/import-core.mjs`:
  - En `formatImportPlan`, la línea de la versión solo aparece si `metaPath != null`:

```js
  const writes = plan.create.length + plan.update.length > 0;
  // Sin metaPath (rutinas: se leen en vivo, sin catálogo versionado) no hay versión que mencionar.
  const version = metaPath == null
    ? []
    : [writes ? `La versión del catálogo subirá (${metaPath}).` : "La versión del catálogo no cambia."];
```

  y en el array devuelto sustituye la línea del ternario por `...version,`.
  - En `commitCatalogOps`, envuelve el `batch.set(db.doc(metaPath), …)` en `if (metaPath) { … }` y actualiza el comentario de la función: «Con `metaPath` nulo no toca ninguna versión (rutinas)».

- [ ] **Step 7: Implementar** `SC/routine-import-plan.mjs`:

```js
// Plan de importación del catálogo de rutinas. Puro: no importa firebase-admin (lo usan los tests de
// reglas). `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp). Las rutinas comunes
// son GLOBAL y del admin del catálogo; las de los usuarios comparten colección y nunca se tocan.
import { CATALOG_ADMIN_UID, baseExisting, formatImportPlan, planCatalogImport, section, stampCatalogDoc } from "./import-core.mjs";
import { CATALOG_AUTHOR_NAME } from "./import-plan.mjs";

export const ROUTINES_COLLECTION = "routines";

export function routineManagedFields(entry) {
  return {
    title: entry.title,
    description: entry.description,
    exercises: entry.exercises,
    source: { provider: "bobitos" },
  };
}

/** Documento de Firestore → `ExistingDoc` de `planRoutineImport` (con `visibility`, para las manuales). */
export function routineDocToExisting(id, data, { updatedAtMillis, importedAtMillis }) {
  const s = data.source;
  return {
    ...baseExisting(id, data, { updatedAtMillis, importedAtMillis }, {
      title: data.title,
      description: data.description ?? null,
      exercises: data.exercises ?? [],
      source: s ? { provider: s.provider } : undefined,
    }),
    visibility: data.visibility,
  };
}

export function routineToFirestoreDoc(entry, { now, create = false }) {
  const doc = stampCatalogDoc(routineManagedFields(entry), { now, adminUid: CATALOG_ADMIN_UID });
  if (!create) return doc;
  return {
    ...doc,
    visibility: "GLOBAL",
    ownerUid: CATALOG_ADMIN_UID,
    createdBy: CATALOG_ADMIN_UID,
    createdByName: CATALOG_AUTHOR_NAME,
    createdAt: now,
  };
}

/**
 * Plan genérico + `manualGlobal`: comunes del admin sin `source` y con un id que no está en el JSON
 * (creadas a mano en la app). Se informa de ellas para que el usuario decida; nunca se tocan.
 */
export function planRoutineImport({ routines, existing, adminUid }) {
  const isForeign = (d) => d.ownerUid !== adminUid;
  const plan = planCatalogImport({ entries: routines, existing, managedFields: routineManagedFields, isForeign });
  const ids = new Set(routines.map((r) => r.id));
  plan.manualGlobal = existing
    .filter((d) => !isForeign(d) && !d.hasSource && d.visibility === "GLOBAL" && !ids.has(d.id))
    .map((d) => d.id);
  return plan;
}

export function formatRoutinePlan(plan) {
  return [
    ...formatImportPlan(plan, { title: "Plan de importación de rutinas", metaPath: null }),
    ...section("Comunes creadas a mano en la app (no se tocan)", plan.manualGlobal),
  ].join("\n");
}
```

- [ ] **Step 8: Implementar** `SC/import-routines.mjs`:

```js
// Importa data/catalog/routines.json a Firestore (colección routines, comunes GLOBAL) con firebase-admin.
// Idempotente; nunca borra; sin catalogMeta (la app lee las rutinas en vivo).
// Uso: node scripts/catalog/import-routines.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, commitCatalogOps, loadExisting } from "./import-core.mjs";
import { connectAdmin, parseImportArgs, runMain } from "./admin-cli.mjs";
import {
  ROUTINES_COLLECTION, formatRoutinePlan, planRoutineImport, routineDocToExisting, routineToFirestoreDoc,
} from "./routine-import-plan.mjs";
import { buildRoutines, indexExercises, validateRoutineCatalog } from "./routines.mjs";

const EXERCISES = new URL("../../data/catalog/exercises.json", import.meta.url);

/** `exercises`: el contenido de exercises.json (para validar y copiar nombre, tipo y medida). */
export async function runRoutineImport({ db, catalog, exercises, apply, log = console.log }) {
  // Antes de leer nada de Firestore.
  const exercisesById = indexExercises(exercises);
  const problems = validateRoutineCatalog(catalog, exercisesById);
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);
  const routines = buildRoutines(catalog, exercisesById);

  const { existing, updateTimes } = await loadExisting(db, ROUTINES_COLLECTION, routineDocToExisting);
  const plan = planRoutineImport({ routines, existing, adminUid: CATALOG_ADMIN_UID });
  log(formatRoutinePlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return plan;
  }

  // Nunca se borra ninguna rutina.
  await commitCatalogOps({
    db, FieldValue, collection: ROUTINES_COLLECTION, plan, toDoc: routineToFirestoreDoc, updateTimes,
    metaPath: null, adminUid: CATALOG_ADMIN_UID, now: FieldValue.serverTimestamp(),
  });
  log(`\nAplicado: ${plan.create.length} creadas, ${plan.update.length} actualizadas.`);
  return plan;
}

await runMain(import.meta.url, async () => {
  const args = parseImportArgs(process.argv.slice(2), { defaultCatalog: "data/catalog/routines.json" });
  const app = connectAdmin(args.project);
  const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
  const exercises = JSON.parse(readFileSync(EXERCISES, "utf8"));
  await runRoutineImport({ db: getFirestore(app), catalog, exercises, apply: args.apply });
});
```

- [ ] **Step 9: Ejecutar NODE e IMP y verlos en verde** (también las suites de ejercicios e ingredientes).

- [ ] **Step 10: Documentación.**
  - `data/catalog/LICENSE.md`: al final, una sección nueva:

```markdown
## Rutinas

`routines.json` es una lista propia de Bobitos: la selección de ejercicios, las series, las repeticiones, los tiempos y los textos se han redactado para este catálogo y no derivan de ninguna base de datos. Cada rutina enlaza fichas del catálogo de ejercicios por su id y repite su nombre; la licencia CC BY-SA del principio de este fichero se refiere a las fichas de ejercicios, no a las rutinas.
```

  - `docs/ROUTINE_CATALOG.md`, nuevo, con el estilo de `docs/INGREDIENT_CATALOG.md` y estas secciones:
    1. **Qué es:** unas 29 rutinas comunes (`routines/{slug}`, `visibility: GLOBAL`, dueño el admin del catálogo) en la misma colección que las rutinas personales; enlace a la spec `superpowers/specs/2026-10-11-catalogo-rutinas-design.md`.
    2. **Fuente y licencia:** lista propia; ver `data/catalog/LICENSE.md` («Rutinas»).
    3. **Estructura de `data/catalog/`:** tabla con `routines.json` (se edita a mano) y `routines-review.md` (no; `npm run catalog:routines`).
    4. **Formato:** ejemplo de `routines.json` con una serie de repeticiones, una por tiempo y un cardio; `id = slug(title)` (cambiar el título crea otra rutina y deja huérfana la anterior); plantilla de la descripción «nivel · duración · material · descanso · frecuencia».
    5. **Validación:** las reglas de la Tarea 1 (exerciseId del catálogo, medida, fuerza frente a cardio/otros, límites de `ROUTINE_LIMITS`), y que nombre, tipo y medida se copian de `exercises.json`.
    6. **Importar:** simulación y `--apply` en el emulador (`FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-routines -- --project demo-bobitos [--apply]`) y en `bobitos-dev` (`GOOGLE_APPLICATION_CREDENTIALS=… npm run catalog:import-routines -- --project dev [--apply]`); qué omite (rutinas de usuarios, comunes sin `source`, comunes editadas en la app), que nunca borra (huérfanas y «Comunes creadas a mano» solo se informan) y que no usa `catalogMeta`.

- [ ] **Step 11: Commit**

```bash
git add scripts/catalog/import-core.mjs scripts/catalog/routine-import-plan.mjs scripts/catalog/import-routines.mjs tests/catalog-import-core.test.mjs tests/catalog-routine-import-plan.test.mjs tests/routine-import.emulator.test.mjs package.json data/catalog/LICENSE.md docs/ROUTINE_CATALOG.md
git commit -m "feat(catalogo): importador del catálogo de rutinas (sin catalogMeta) y su documentación"
```

---

### Task 4: Reglas de `routines`: `source` del importador y `sourceRoutineId` de las copias

**Ficheros:**
- Modificar: `firestore.rules` (funciones `validRoutineShape` y `validNewRoutine`, ~l.1015-1050), `T/firebase-emulators.test.mjs` (añadir tests tras «la rutina valida la forma…»), `docs/DATA_MODEL.md`.

**Interfaces:**
- Consume: `routineToFirestoreDoc` (Tarea 3, `SC/routine-import-plan.mjs`); `validIngredientSource` (`firestore.rules`); helpers del test `routineData`, `verifiedFirestore`, `RECIPE_ADMIN_UID`, `testEnvironment`.
- Produce: documento `routines` que admite `source` (`{provider: "bobitos", importedAt}`, nunca desde un cliente) y `sourceRoutineId` (ausente, `null` o texto 1–128, inmutable). La Tarea 5 escribe `sourceRoutineId` al copiar.

- [ ] **Step 1: Escribir los tests** en `T/firebase-emulators.test.mjs`: añade el import `import { routineToFirestoreDoc } from "../scripts/catalog/routine-import-plan.mjs";` junto al de `toFirestoreDoc`, y estos tests después del test «la rutina valida la forma y rechaza campos ajenos o una lista de ejercicios enorme»:

```js
const importedRoutine = {
  id: "torso",
  title: "Torso",
  description: "Intermedio · 60 min · barra · descansa 90 s entre series · 2 veces por semana.",
  exercises: [{ name: "Press de banca", exerciseId: "press-de-banca", type: "PESO_LIBRE",
    sets: [{ reps: 8, weight: null }], durationMinutes: null, level: null, notes: null }],
};

async function seedImportedRoutine(id) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "routines", id),
      routineToFirestoreDoc({ ...importedRoutine, id }, { now: Timestamp.now(), create: true }));
  });
}

test("el admin edita una rutina importada pero no su fuente, y cualquiera la lee", async () => {
  await seedImportedRoutine("torso-importada");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "routines", "torso-importada");
  await assertSucceeds(updateDoc(ref, {
    title: "Torso (editada)", description: "Otra", exercises: [], updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref, {
    source: { provider: "bobitos", importedAt: Timestamp.now() }, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(verifiedFirestore("routine-imported-reader"), "routines", "torso-importada")));
});

test("ningún cliente crea una rutina con la fuente del catálogo", async () => {
  const source = { provider: "bobitos", importedAt: Timestamp.now() };
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertFails(setDoc(doc(admin, "routines", "falsa-global"),
    routineData(RECIPE_ADMIN_UID, { visibility: "GLOBAL", source })));
  const user = verifiedFirestore("routine-fake-source");
  await assertFails(setDoc(doc(user, "routines", "falsa"), routineData("routine-fake-source", { source })));
});

test("una copia guarda el id de la común, válido e inmutable", async () => {
  const user = verifiedFirestore("routine-forker");
  const ref = doc(user, "routines", "copia");
  await assertSucceeds(setDoc(ref, routineData("routine-forker", { sourceRoutineId: "torso" })));
  await assertSucceeds(updateDoc(ref, { title: "Mi torso", updatedBy: "routine-forker", updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(ref, { sourceRoutineId: "otra", updatedBy: "routine-forker", updatedAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(user, "routines", "origen-nulo"), routineData("routine-forker", { sourceRoutineId: null })));
  await assertFails(setDoc(doc(user, "routines", "origen-numero"), routineData("routine-forker", { sourceRoutineId: 123 })));
  await assertFails(setDoc(doc(user, "routines", "origen-vacio"), routineData("routine-forker", { sourceRoutineId: "" })));
  await assertFails(setDoc(doc(user, "routines", "origen-largo"),
    routineData("routine-forker", { sourceRoutineId: "x".repeat(129) })));
});
```

- [ ] **Step 2: Ejecutar EMU y verlo en rojo.** Copia los fallos (el primero y el tercero fallan por `hasOnly`).

- [ ] **Step 3: Cambiar las reglas** en `firestore.rules`:
  - En `validRoutineShape()`, la lista de `hasOnly` pasa a terminar en `"exercises", "source", "sourceRoutineId"`, y antes de `&& request.resource.data.ownerUid is string` se añaden:

```
        // Rutinas del catálogo: `source` lo escribe solo el importador (Admin SDK), con la forma de ingredientes.
        && (!request.resource.data.keys().hasAny(["source"]) || validIngredientSource(request.resource.data.source))
        // Copia de una común («Guardar como mía»): id de la original.
        && (!request.resource.data.keys().hasAny(["sourceRoutineId"])
          || request.resource.data.sourceRoutineId == null
          || (request.resource.data.sourceRoutineId is string
            && request.resource.data.sourceRoutineId.size() > 0
            && request.resource.data.sourceRoutineId.size() <= 128))
```

  - En `validNewRoutine()`, añade `&& !request.resource.data.keys().hasAny(["source"])` tras `validRoutineShape()`.
  - `validRoutineUpdate()` no cambia: su `affectedKeys().hasOnly([...])` ya impide tocar `source` y `sourceRoutineId`.
  - `validIngredientSource` está definida más abajo en el mismo bloque `match`; las funciones de reglas se resuelven al evaluar, así que no hace falta moverla. Si el emulador diera error de función desconocida, mueve `validIngredientSource` encima de `validRoutineShape` sin cambiarla.

- [ ] **Step 4: Ejecutar EMU y verlo en verde** (toda la suite).

- [ ] **Step 5: `docs/DATA_MODEL.md`:** antes de `#### Ejercicios embebidos en \`routines\` y \`activities.session\``, una subsección `### Rutinas (colección \`routines\`)` que describa: campos (`ownerUid`, `visibility` GLOBAL|PRIVATE, `title` ≤120, `description` ≤1000, `exercises` ≤30, autoría y fechas), `source` (`{provider: "bobitos", importedAt}`, solo en las comunes del catálogo, lo escribe el importador; ver `ROUTINE_CATALOG.md`) y `sourceRoutineId` (id de la común de la que es copia; ausente, `null` o texto ≤128, inmutable); y las reglas: crea el propio usuario (GLOBAL solo el admin; ningún cliente con `source`), edita el dueño o el admin en las GLOBAL (solo `title`, `description`, `exercises` y los campos de actualización), borra el dueño o el admin.

- [ ] **Step 6: Commit**

```bash
git add firestore.rules tests/firebase-emulators.test.mjs docs/DATA_MODEL.md
git commit -m "feat(reglas): rutinas con fuente del catálogo y copias con sourceRoutineId"
```

---

### Task 5: App: «Guardar como mía» y comunes siempre en «Comunes»

**Ficheros:**
- Modificar: `APP/core/model/Routine.kt`, `APP/data/repository/RoutineRepository.kt`, `APP/data/repository/FirestoreRoutineRepository.kt`, `APP/feature/routines/RoutinesUiState.kt`, `APP/feature/routines/RoutinesViewModel.kt`, `APP/feature/routines/RoutinesScreen.kt`, `RES`, `UT/feature/routines/RoutinesViewModelTest.kt`, `UT/feature/sport/SportViewModelTest.kt` (solo la firma del doble).
- Crear: `APP/feature/routines/RoutineSections.kt`, `UT/feature/routines/RoutineSectionsTest.kt`, `UT/data/repository/RoutineExerciseFixtureTest.kt`.

**Interfaces:**
- Consume: `T/fixtures/routine-exercises.json` (Tarea 1); `parseRoutineExercises`, `toFirestoreExercises` (`internal`, `APP/data/repository/RoutineExerciseFirestore.kt`); reglas de la Tarea 4.
- Produce:
  - `Routine.sourceRoutineId: String? = null`;
  - `RoutineRepository.createRoutine(visibility, title, description, exercises, sourceRoutineId: String? = null)`;
  - `RoutinesViewModel.fork(source: Routine)` y `RoutineUiMessage.Forked`;
  - `internal fun List<Routine>.personalOnly(): List<Routine>` y `internal fun canForkRoutine(routine: Routine, canWrite: Boolean): Boolean` (`RoutineSections.kt`).

- [ ] **Step 1: Test de la tabla compartida** `UT/data/repository/RoutineExerciseFixtureTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineExerciseFixtureTest {
    // La misma tabla que tests/catalog-routines.test.mjs: lo que escribe el importador, la app lo lee y lo
    // vuelve a escribir igual (segundos, minutos, nivel y pesos vacíos incluidos).
    private val cases = File("../tests/fixtures/routine-exercises.json").also {
        assertTrue("falta la tabla compartida en ${it.absolutePath}", it.exists())
    }.readText().let(::JSONArray)

    @Test
    fun `the app reads and rewrites imported exercises unchanged`() {
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val stored = case.getJSONObject("firestore").toPlainMap()
            val parsed = parseRoutineExercises(listOf(stored)).orEmpty()
            assertEquals(case.getString("caso"), listOf(stored), parsed.toFirestoreExercises())
        }
    }
}

private fun JSONObject.toPlainMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key -> get(key).toPlain() }

private fun Any?.toPlain(): Any? = when (this) {
    JSONObject.NULL -> null
    is JSONObject -> toPlainMap()
    is JSONArray -> (0 until length()).map { get(it).toPlain() }
    else -> this
}
```

- [ ] **Step 2: Test de las secciones** `UT/feature/routines/RoutineSectionsTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoutineSectionsTest {
    @Test
    fun `my routines only lists private ones, also for the catalog admin`() {
        val mine = listOf(
            routine("p1", RoutineVisibility.PRIVATE),
            routine("torso", RoutineVisibility.GLOBAL),
            routine("p2", RoutineVisibility.PRIVATE),
        )
        assertEquals(listOf("p1", "p2"), mine.personalOnly().map(Routine::id))
    }

    @Test
    fun `any common routine can be saved as mine when writing is allowed`() {
        assertTrue(canForkRoutine(routine("torso", RoutineVisibility.GLOBAL), canWrite = true))
        assertFalse(canForkRoutine(routine("torso", RoutineVisibility.GLOBAL), canWrite = false))
        assertFalse(canForkRoutine(routine("p1", RoutineVisibility.PRIVATE), canWrite = true))
    }

    private fun routine(id: String, visibility: RoutineVisibility) = Routine(
        id = id,
        ownerUid = "admin",
        visibility = visibility,
        title = id,
        createdBy = "admin",
        createdByName = "Catálogo Bobitos",
        createdAt = Instant.EPOCH,
        updatedBy = "admin",
        updatedAt = Instant.EPOCH,
    )
}
```

- [ ] **Step 3: Tests del ViewModel** en `UT/feature/routines/RoutinesViewModelTest.kt`:
  - En `FakeRoutineRepository`: `var lastSourceRoutineId: String? = null`; `createRoutine` recibe un quinto parámetro `sourceRoutineId: String?` y lo guarda en `lastSourceRoutineId` junto al resto.
  - En `UT/feature/sport/SportViewModelTest.kt`, `FakeSportRoutineRepository.createRoutine` recibe también `sourceRoutineId: String?` (sin más cambios).
  - Tests nuevos (usa el helper `routine(id, visibility, title)` que ya existe en el fichero; añade los imports que falten):

```kotlin
    @Test
    fun `fork saves a private copy linked to the common routine`() = runTest(mainDispatcherRule.testDispatcher) {
        val exercises = listOf(
            RoutineExercise(
                name = "Press de banca",
                exerciseId = "press-de-banca",
                type = ExerciseType.PESO_LIBRE,
                sets = listOf(ExerciseSet(reps = 8)),
            ),
        )
        val common = routine("torso", RoutineVisibility.GLOBAL, "Torso")
            .copy(description = "Intermedio · 60 min", exercises = exercises)

        viewModel.fork(common)
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals(RoutineVisibility.PRIVATE, repository.lastVisibility)
        assertEquals("Torso", repository.lastTitle)
        assertEquals("Intermedio · 60 min", repository.lastDescription)
        assertEquals(exercises, repository.lastExercises)
        assertEquals("torso", repository.lastSourceRoutineId)
        assertEquals(RoutineUiMessage.Forked, viewModel.uiState.value.notice)
    }

    @Test
    fun `a double tap on fork saves a single copy`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        val common = routine("torso", RoutineVisibility.GLOBAL, "Torso")

        viewModel.fork(common)
        runCurrent()
        viewModel.fork(common)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
    }

    @Test
    fun `fork shows a permission error`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failure = RoutineFailure.PermissionDenied

        viewModel.fork(routine("torso", RoutineVisibility.GLOBAL, "Torso"))
        advanceUntilIdle()

        assertEquals(RoutineUiMessage.PermissionDenied, viewModel.uiState.value.error)
        assertEquals(0, repository.createCount)
    }
```

- [ ] **Step 4: Ejecutar GRADLE y verlo en rojo** (no compilan `personalOnly`, `canForkRoutine`, `fork`, `Forked` ni el parámetro nuevo). Copia el fallo.

- [ ] **Step 5: Modelo y repositorio.**
  - `Routine.kt`: tras `exercises`, `val sourceRoutineId: String? = null, // común de la que es copia («Guardar como mía»)`.
  - `RoutineRepository.kt`: `createRoutine(..., exercises: List<RoutineExercise>, sourceRoutineId: String? = null)`.
  - `FirestoreRoutineRepository.kt`:
    - `createRoutine(..., sourceRoutineId: String?)`: el `mapOf(...)` del `set` pasa a `buildMap { … }` con las mismas entradas y, al final, `sourceRoutineId?.let { put(FIELD_SOURCE_ROUTINE_ID, it) }` (solo si hay valor; Ruling 5).
    - `const val FIELD_SOURCE_ROUTINE_ID = "sourceRoutineId"` en el `companion`.
    - `toRoutine()`: `sourceRoutineId = getString("sourceRoutineId"),` tras `exercises`.

- [ ] **Step 6: Secciones** `APP/feature/routines/RoutineSections.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility

// «Mis rutinas» enseña solo las privadas del usuario: las GLOBAL (también las del catálogo, cuyo dueño es
// el admin) van siempre a «Comunes».
internal fun List<Routine>.personalOnly(): List<Routine> = filter { it.visibility == RoutineVisibility.PRIVATE }

// «Guardar como mía» sale en cualquier común, también para el admin, si se puede escribir.
internal fun canForkRoutine(routine: Routine, canWrite: Boolean): Boolean =
    canWrite && routine.visibility == RoutineVisibility.GLOBAL
```

- [ ] **Step 7: ViewModel y mensajes.**
  - `RoutinesUiState.kt`: `Forked,` al final de `RoutineUiMessage`.
  - `RoutinesViewModel.kt`, tras `deleteRoutine`:

```kotlin
    // Copia privada de una común («Guardar como mía»), enlazada con la original por sourceRoutineId.
    fun fork(source: Routine) {
        runAction(RoutineUiMessage.Forked) {
            repository.createRoutine(
                visibility = RoutineVisibility.PRIVATE,
                title = source.title,
                description = source.description,
                exercises = source.exercises.orEmpty(),
                sourceRoutineId = source.id,
            )
        }
    }
```

- [ ] **Step 8: Pantalla** `RoutinesScreen.kt`:
  - `RoutinesCatalog`: quita `mineIds` y su comentario; la sección «Mis rutinas» recibe `routines = state.mine.let { m -> if (m is UiState.Content) UiState.Content(m.value.personalOnly()) else m }`; la de «Comunes» recibe `state.global` tal cual. Quita el parámetro `excludeIds` de `routinesSection` (y su filtro: `visible = routines.value`).
  - `RoutineSheetsHost`: nuevo parámetro `onFork: (Routine) -> Unit`; el detalle se pinta con `RoutineDetailSheet(routine = it, canFork = canForkRoutine(it, canWrite), onFork = { onFork(it) }, onDismiss = onCloseDetail)`.
  - `RoutineDetailSheet(routine, canFork: Boolean, onFork: () -> Unit, onDismiss)`: pasa a `BobitosInfoSheet` `actions = { if (canFork) TextButton(onClick = onFork) { Text(stringResource(R.string.routines_fork)) } }` (mismo estilo que `RecipeSheets.kt:132-134`).
  - En `RoutinesScreen`, al llamar a `RoutineSheetsHost`: `onFork = { routine -> viewModel.fork(routine); detailRoutineId = null }` (Ruling 8).
  - `stringResourceId`: `RoutineUiMessage.Forked -> R.string.routines_notice_forked`.
  - `RES`, tras `routines_notice_deleted`:

```xml
    <string name="routines_fork">Guardar como mía</string>
    <string name="routines_notice_forked">Guardada en «Mis rutinas»</string>
```

- [ ] **Step 9: Ejecutar GRADLE y verlo en verde.**

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/Routine.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/RoutineRepository.kt app/src/main/java/com/dlunaunizar/bobitos/data/repository/FirestoreRoutineRepository.kt app/src/main/java/com/dlunaunizar/bobitos/feature/routines/RoutineSections.kt app/src/main/java/com/dlunaunizar/bobitos/feature/routines/RoutinesUiState.kt app/src/main/java/com/dlunaunizar/bobitos/feature/routines/RoutinesViewModel.kt app/src/main/java/com/dlunaunizar/bobitos/feature/routines/RoutinesScreen.kt app/src/main/res/values/strings.xml app/src/test/java/com/dlunaunizar/bobitos/data/repository/RoutineExerciseFixtureTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/routines/RoutineSectionsTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/routines/RoutinesViewModelTest.kt app/src/test/java/com/dlunaunizar/bobitos/feature/sport/SportViewModelTest.kt
git commit -m "feat(rutinas): «Guardar como mía» y comunes siempre en «Comunes»"
```

---

### Task 6: Filtros de ejercicios: desplegables de tipo y grupo a la misma altura

**Restricciones propias de esta tarea** (además de las globales):
- **Textos nuevos** en `RES`, junto a `exercises_filter_all`: `exercises_filter_type_label` = «Tipo»; `exercises_filter_group_label` = «Grupo muscular»; `exercises_filter_any` = «Todos». Se mantienen `exercises_filter_all` («Todos los tipos») y `exercises_filter_all_groups` («Todos los grupos»).
- **API de Material 3:** si `ExposedDropdownMenuAnchorType` no existe en la versión del BOM (`composeBom = "2026.06.00"`), usar `MenuAnchorType.PrimaryNotEditable` y anotarlo en el informe.
- **Misma altura:** las dos casillas son `OutlinedTextField` de una línea (`singleLine = true`) con `weight(1f)` dentro de la misma `Row`; no se añade ningún alto fijo.

**Comportamiento:**

- **Casilla izquierda:**
  - etiqueta «Tipo»;
  - valor «Todos» o el tipo elegido;
  - menú: «Todos los tipos» y los 5 tipos en el orden de `ExerciseType.entries`.
- **Casilla derecha:**
  - etiqueta «Grupo muscular»;
  - valor «Todos» o el grupo elegido;
  - menú: «Todos los grupos» y `groupFilterOptions(groups, filter.muscleGroup)`.
- **Al elegir:**
  - elegir una opción la aplica y cierra el menú;
  - «Todos los …» quita el filtro;
  - elegir la opción ya elegida no cambia nada. Ya no existe «tocar otra vez para quitar», como con los chips.
- **Opción elegida:** se marca con `Icons.Rounded.Check` como `trailingIcon` y con `Modifier.semantics { selected = true }`; las demás, sin icono.
- **Sin grupos:** si no hay grupos ni grupo elegido (por ejemplo, el selector con el catálogo aún sin cargar), el desplegable de grupo se ve deshabilitado (`enabled = false`), para que la fila no cambie de forma. El de tipo funciona siempre.
- **Grupo elegido que ya no está en el catálogo:** aparece al final del menú, como hacían los chips, para que se vea cuál es y se pueda quitar.
- **Casillas:**
  - `OutlinedTextField` con `readOnly = true`, `singleLine = true` y `onValueChange = {}`;
  - `trailingIcon = ExposedDropdownMenuDefaults.TrailingIcon(expanded)`;
  - `Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)`, que no abre el teclado;
  - colores por defecto, los mismos que el buscador.
- **Menú:** `ExposedDropdownMenu`, con el ancho de su casilla (valor por defecto).

**Ficheros:**
- Renombrar con `git mv`: `APP/feature/exercises/ExerciseFilterChips.kt` → `APP/feature/exercises/ExerciseFilterMenus.kt`, y reescribirlo.
- Renombrar con `git mv`: `UT/feature/exercises/ExerciseFilterChipKeysTest.kt` → `UT/feature/exercises/ExerciseFilterMenusTest.kt`, y reescribirlo.
- Modificar:
  - `APP/feature/exercises/ExercisesScreen.kt:216-224`: llamar a `ExerciseFilterMenus`;
  - `APP/feature/exercises/ExercisePicker.kt:55-60`: llamar a `ExerciseFilterMenus` y actualizar el comentario de cabecera si menciona chips;
  - `app/src/main/res/values/strings.xml`: los 3 textos nuevos.

**Interfaces:**
- Consume:
  - `ExerciseFilter(query, type, muscleGroup)`;
  - `ExerciseType.labelRes` (`ExerciseTokens.kt`);
  - `String.foldForSearch()` (`core/common/TextSearch.kt`);
  - `Spacing.sm`.
- Produce:
  - `@Composable internal fun ExerciseFilterMenus(groups: List<String>, filter: ExerciseFilter, onTypeChange: (ExerciseType?) -> Unit, onGroupChange: (String?) -> Unit, modifier: Modifier = Modifier)`
  - `internal fun groupFilterOptions(groups: List<String>, selected: String?): List<String>`
  - `internal fun isSelectedGroup(option: String, selected: String?): Boolean`
  - `@StringRes internal fun typeFilterValueRes(type: ExerciseType?): Int`

- [ ] **Paso 1: escribir los tests en rojo.** Sustituyen a los de las claves de chips. Contenido completo de `UT/feature/exercises/ExerciseFilterMenusTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFilterMenusTest {
    @Test
    fun `group options keep the catalog order without folded duplicates`() {
        assertEquals(listOf("Pecho", "Bíceps"), groupFilterOptions(listOf("Pecho", "Bíceps", "biceps"), null))
    }

    @Test
    fun `a selected group missing from the catalog is added at the end`() {
        assertEquals(listOf("Pecho", "Cuello"), groupFilterOptions(listOf("Pecho"), "Cuello"))
    }

    @Test
    fun `a selected group already present with other accents or case is not repeated`() {
        assertEquals(listOf("Bíceps", "Pecho"), groupFilterOptions(listOf("Bíceps", "Pecho"), "BICEPS"))
    }

    @Test
    fun `no groups and nothing selected gives no options`() {
        assertEquals(emptyList<String>(), groupFilterOptions(emptyList(), null))
    }

    @Test
    fun `the selected group is matched ignoring accents and case`() {
        assertTrue(isSelectedGroup("Bíceps", "biceps"))
        assertFalse(isSelectedGroup("Pecho", "biceps"))
        assertFalse(isSelectedGroup("Pecho", null))
    }

    @Test
    fun `the type value is Todos without a type and the type label with one`() {
        assertEquals(R.string.exercises_filter_any, typeFilterValueRes(null))
        assertEquals(R.string.exercise_type_machine, typeFilterValueRes(ExerciseType.MAQUINA))
    }
}
```

- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA al compilar los tests, porque no existen `groupFilterOptions`, `isSelectedGroup`, `typeFilterValueRes` ni `R.string.exercises_filter_any`.

- [ ] **Paso 3: implementar.**
  - Añadir los 3 textos a `strings.xml`.
  - Reescribir `ExerciseFilterMenus.kt` con este contenido, ajustando solo lo que pidan la compilación, ktlint o detekt:

```kotlin
package com.dlunaunizar.bobitos.feature.exercises

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.foldForSearch
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.ExerciseType

// Desplegables de tipo y de grupo muscular, uno al lado del otro, compartidos por la pantalla Ejercicios y el
// selector. «Todos los …» quita el filtro. Si el grupo elegido ya no está en el catálogo, se sigue ofreciendo
// para que se vea cuál es.
@Composable
internal fun ExerciseFilterMenus(
    groups: List<String>,
    filter: ExerciseFilter,
    onTypeChange: (ExerciseType?) -> Unit,
    onGroupChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedGroup = filter.muscleGroup
    val groupOptions = remember(groups, selectedGroup) { groupFilterOptions(groups, selectedGroup) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterDropdown(
            label = stringResource(R.string.exercises_filter_type_label),
            value = stringResource(typeFilterValueRes(filter.type)),
            options = listOf(
                FilterOption(stringResource(R.string.exercises_filter_all), filter.type == null) { onTypeChange(null) },
            ) + ExerciseType.entries.map { type ->
                FilterOption(stringResource(type.labelRes), filter.type == type) { onTypeChange(type) }
            },
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.exercises_filter_group_label),
            value = selectedGroup ?: stringResource(R.string.exercises_filter_any),
            options = listOf(
                FilterOption(stringResource(R.string.exercises_filter_all_groups), selectedGroup == null) {
                    onGroupChange(null)
                },
            ) + groupOptions.map { group ->
                FilterOption(group, isSelectedGroup(group, selectedGroup)) { onGroupChange(group) }
            },
            enabled = groupOptions.isNotEmpty(),
            modifier = Modifier.weight(1f),
        )
    }
}

private class FilterOption(val text: String, val selected: Boolean, val onSelect: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    value: String,
    options: List<FilterOption>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text) },
                    onClick = {
                        expanded = false
                        option.onSelect()
                    },
                    trailingIcon = if (option.selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.semantics { selected = option.selected },
                )
            }
        }
    }
}

// Grupos del menú: los del catálogo sin repetidos (plegados), en su orden, y al final el elegido si ya no está.
internal fun groupFilterOptions(groups: List<String>, selected: String?): List<String> {
    val shown = groups.distinctBy { it.foldForSearch() }
    return if (selected != null && shown.none { isSelectedGroup(it, selected) }) shown + selected else shown
}

internal fun isSelectedGroup(option: String, selected: String?): Boolean =
    selected != null && option.foldForSearch() == selected.foldForSearch()

@StringRes
internal fun typeFilterValueRes(type: ExerciseType?): Int = type?.labelRes ?: R.string.exercises_filter_any
```

  - En `ExercisesScreen.kt` y `ExercisePicker.kt`, cambiar `ExerciseFilterChips(` por `ExerciseFilterMenus(` con los mismos argumentos. En el selector, el comentario de cabecera ya dice «filtros de tipo y grupo»: no hace falta tocarlo si no menciona chips.
  - Comprobar con `grep -rn "ExerciseFilterChips\|typeChipKeys\|groupChipKeys" app/src` que no queda ninguna referencia.

- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde (compila, ktlint y detekt sin errores, todos los tests pasan, incluidos los 6 nuevos).

- [ ] **Paso 5:** commit.

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/exercises/ExerciseFilterMenus.kt app/src/main/java/com/dlunaunizar/bobitos/feature/exercises/ExercisesScreen.kt app/src/main/java/com/dlunaunizar/bobitos/feature/exercises/ExercisePicker.kt app/src/test/java/com/dlunaunizar/bobitos/feature/exercises/ExerciseFilterMenusTest.kt app/src/main/res/values/strings.xml
git status --short   # los renombrados con git mv ya están en el índice; no debe quedar nada más de esta tarea
git commit -m "feat(ejercicios): filtros de tipo y grupo como desplegables en una fila"
```

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. Crear la rama `agent/catalogo-rutinas` desde `main` y hacer commit de este plan (solo este fichero).
2. Las tareas 1 a 6, una tras otra, cada una con un `sonnet` que recibe su brief. `task-done` con la suite de cada tarea:
   - NODE en las tareas 1, 2 y 3; IMP en la 3; EMU en la 4; GRADLE en la 5 y en la 6.
   - **Parada del usuario tras la Tarea 2** (revisión de `routines-review.md`).
3. **Revisión final** con `opus`, más los arreglos. **Re-revisión acotada** si algún arreglo toca `firestore.rules` (permisos) o el aislamiento de las rutinas privadas.
4. **`simplify`:** un revisor `sonnet` con los 4 ángulos y un aplicador `sonnet` de la lista (a).
5. **`code-review`** en nivel `high`, más los arreglos.
6. **Cierre:** menores al backlog y decisión del usuario sobre la rama. El despliegue va después (sección siguiente).

## Verificación de punta a punta (coordinador, al final)

- NODE, IMP, EMU y GRADLE en verde.
- **En el emulador:** `npm run emulators`; importar ejercicios y rutinas: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-exercises.mjs --project demo-bobitos --apply` y `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-routines.mjs --project demo-bobitos --apply`; instalar la app debug con un usuario normal y con el admin.
- **Recorrido**, con capturas en tema claro y oscuro:
  1. usuario normal: «Comunes» muestra las 29; abrir «Core con planchas»: la plancha sale con segundos y «Cinta suave» con minutos y nivel;
  2. «Guardar como mía» en «Torso»: se cierra el detalle, aviso «Guardada en «Mis rutinas»» y la copia aparece en «Mis rutinas»; editarla (poner pesos) y guardar;
  3. Deporte → nueva actividad de gimnasio → elegir «Pierna» (común): se copian sus ejercicios sin pesos; guardar la sesión;
  4. admin: las 29 salen en «Comunes» (con Editar/Eliminar) y no en «Mis rutinas»; «Guardar como mía» también le aparece; editar una común y reimportar en simulación: sale en «Omitidas (editadas en la app…)»;
  5. Ejercicios: bajo «Buscar ejercicio», «Tipo: Todos» y «Grupo muscular: Todos» en la misma fila y a la misma altura; elegir «Peso libre» y «Pecho» filtra (los tres filtros se suman con la búsqueda) y «Todos los …» los quita; tocar las casillas no abre el teclado; captura en tema oscuro con el menú abierto;
  6. selector de ejercicios del editor de rutinas: las dos casillas, una al lado de la otra, y los menús funcionan; con el catálogo aún sin cargar, «Grupo muscular» sale deshabilitado.

## Despliegue (tras el cierre; cada paso lo confirma el usuario)

1. Reglas: `firebase deploy --only firestore:rules --project dev`.
2. Rutinas: `GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-routines -- --project dev` (simulación; revisar «Comunes creadas a mano») y después con `--apply`.
3. Beta 20 (`VERSION_CODE=20`, `0.1.0-beta.20`) con notas en `distribution/release-notes.txt`, según `docs/BETA_DISTRIBUTION.md` y las trampas conocidas (JAVA_HOME de Android Studio; `beta:distribute` sube el APK viejo si falla el build).
