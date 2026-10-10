import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { parseResetArgs, runReset } from "../scripts/catalog/reset-ingredients.mjs";

const quiet = () => {};
let app;
let db;

before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "falta FIRESTORE_EMULATOR_HOST");
  app = initializeApp({ projectId: "demo-bobitos" }, "ingredient-reset-test");
  db = getFirestore(app);
});
after(() => deleteApp(app));

beforeEach(async () => {
  for (const path of ["ingredients", "ingredientPrefs", "users", "exercises", "catalogMeta"]) {
    await db.recursiveDelete(db.collection(path));
  }
  for (const id of ["arroz", "leche", "tomate"]) {
    await db.doc(`ingredients/${id}`).set({ name: id, ownerUid: "u1" });
    await db.doc(`ingredients/${id}/brands/b1`).set({ name: "Marca 1", ownerUid: "u1" });
    await db.doc(`ingredients/${id}/brands/b2`).set({ name: "Marca 2", ownerUid: "u2" });
  }
  // Padre sin datos: solo tiene una marca colgando.
  await db.doc("ingredients/fantasma/brands/b1").set({ name: "Huérfana", ownerUid: "u1" });
  await db.doc("ingredientPrefs/u1").set({ entries: { arroz: { supermarket: "DIA" } } });
  await db.doc("ingredientPrefs/u2").set({ entries: {} });
  // Lo que no se toca nunca.
  await db.doc("users/u1/brands/p1").set({ name: "Personal", ingredientId: "arroz" });
  await db.doc("users/u1/ingredients/salsa-casera").set({ name: "Salsa casera" });
  await db.doc("exercises/press-de-banca").set({ name: "Press de banca" });
  await db.doc("catalogMeta/exercises").set({ version: 3 });
  await db.doc("catalogMeta/ingredients").set({ version: 1 });
});

test("la simulación cuenta ingredientes, marcas y preferencias sin borrar nada", async () => {
  const r = await runReset({ db, apply: false, log: quiet });
  assert.deepEqual(r, { ingredients: 4, brands: 7, prefs: 2, deleted: false });
  assert.equal((await db.collection("ingredients").listDocuments()).length, 4);
  assert.equal((await db.collection("ingredientPrefs").get()).size, 2);
});

test("con apply borra ingredientes, marcas (también bajo padres sin datos) y preferencias", async () => {
  const r = await runReset({ db, apply: true, log: quiet });
  assert.deepEqual(r, { ingredients: 4, brands: 7, prefs: 2, deleted: true });
  assert.equal((await db.collection("ingredients").listDocuments()).length, 0);
  assert.equal((await db.collection("ingredients/fantasma/brands").get()).size, 0);
  assert.equal((await db.collection("ingredientPrefs").get()).size, 0);
});

test("no toca users, exercises ni catalogMeta", async () => {
  await runReset({ db, apply: true, log: quiet });
  assert.equal((await db.doc("users/u1/brands/p1").get()).exists, true);
  assert.equal((await db.doc("users/u1/ingredients/salsa-casera").get()).exists, true);
  assert.equal((await db.doc("exercises/press-de-banca").get()).exists, true);
  assert.equal((await db.doc("catalogMeta/exercises").get()).get("version"), 3);
  assert.equal((await db.doc("catalogMeta/ingredients").get()).get("version"), 1);
});

test("una segunda pasada no encuentra nada", async () => {
  await runReset({ db, apply: true, log: quiet });
  assert.deepEqual(await runReset({ db, apply: true, log: quiet }), { ingredients: 0, brands: 0, prefs: 0, deleted: true });
});

test("parseResetArgs solo admite --project y --apply", () => {
  assert.deepEqual(parseResetArgs(["--project", "demo-bobitos"]), { apply: false, project: "demo-bobitos" });
  assert.deepEqual(parseResetArgs(["--apply", "--project", "dev"]), { apply: true, project: "dev" });
  assert.throws(() => parseResetArgs(["--catalog", "x"]), /Argumento desconocido: --catalog/);
});
