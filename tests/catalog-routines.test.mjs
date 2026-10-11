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
