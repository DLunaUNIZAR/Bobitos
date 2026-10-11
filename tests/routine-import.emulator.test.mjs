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
