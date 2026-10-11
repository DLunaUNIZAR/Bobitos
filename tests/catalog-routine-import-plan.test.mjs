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
