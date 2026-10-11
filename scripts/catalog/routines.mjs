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
