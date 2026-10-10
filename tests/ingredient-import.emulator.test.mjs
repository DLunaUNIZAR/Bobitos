import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { beforeEach, test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { runIngredientImport } from "../scripts/catalog/import-ingredients.mjs";
import { quiet, setupEmulatorDb } from "./helpers/emulator-db.mjs";

const full = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
const small = { ...full, ingredients: full.ingredients.slice(0, 5) };

const db = setupEmulatorDb("ingredient-import-test");
beforeEach(async () => {
  await db.recursiveDelete(db.collection("ingredients"));
  await db.doc("catalogMeta/ingredients").delete();
  await db.doc("catalogMeta/exercises").delete();
});

const version = async () => (await db.doc("catalogMeta/ingredients").get()).get("version");

test("importa y una segunda pasada no escribe ni sube la versión", async () => {
  const first = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(first.create.length, 5);
  assert.equal((await db.collection("ingredients").get()).size, 5);
  assert.equal(await version(), 1);
  const d = (await db.doc(`ingredients/${small.ingredients[0].id}`).get()).data();
  assert.equal(d.name, small.ingredients[0].name);
  assert.equal(d.source.provider, "bobitos");
  assert.equal(d.updatedAt.toMillis(), d.source.importedAt.toMillis());
  assert.equal(d.ownerUid, undefined);
  const second = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(second.create.length + second.update.length, 0);
  assert.equal(second.versionBumped, false);
  assert.equal(await version(), 1);
});

test("la simulación no escribe nada", async () => {
  const r = await runIngredientImport({ db, catalog: small, apply: false, log: quiet });
  assert.equal(r.create.length, 5);
  assert.equal((await db.collection("ingredients").get()).size, 0);
  assert.equal((await db.doc("catalogMeta/ingredients").get()).exists, false);
});

test("un cambio en el JSON actualiza solo esa ficha y sube la versión", async () => {
  await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  const [first, ...rest] = small.ingredients;
  const changed = { ...small, ingredients: [{ ...first, defaultUnit: first.defaultUnit === "g" ? "ud" : "g" }, ...rest] };
  const r = await runIngredientImport({ db, catalog: changed, apply: true, log: quiet });
  assert.deepEqual(r.update.map((e) => e.id), [first.id]);
  assert.equal(await version(), 2);
});

test("no pisa una ficha editada en la app después de importarla", async () => {
  await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  const id = small.ingredients[0].id;
  await db.doc(`ingredients/${id}`).update({ category: "Congelados y otros", updatedAt: Timestamp.fromMillis(Date.now() + 60_000) });
  const r = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.deepEqual(r.skippedEditedInApp, [id]);
  assert.equal((await db.doc(`ingredients/${id}`).get()).get("category"), "Congelados y otros");
});

test("omite un documento antiguo de usuario con el mismo id", async () => {
  const e = small.ingredients[0];
  await db.doc(`ingredients/${e.id}`).set({ name: e.name, nameLower: e.name.toLowerCase(), ownerUid: "u1" });
  const r = await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.deepEqual(r.skippedUserOwned, [{ id: e.id, ownerUid: "u1" }]);
  assert.equal((await db.doc(`ingredients/${e.id}`).get()).get("ownerUid"), "u1");
});

test("rechaza un catálogo inválido sin escribir", async () => {
  const bad = { ...small, ingredients: [{ ...small.ingredients[0], defaultUnit: "kg" }] };
  await assert.rejects(runIngredientImport({ db, catalog: bad, apply: true, log: quiet }), /Catálogo inválido/);
  assert.equal((await db.collection("ingredients").get()).size, 0);
});

test("no toca la versión del catálogo de ejercicios", async () => {
  await runIngredientImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal((await db.doc("catalogMeta/exercises").get()).exists, false);
});

test("importa el catálogo completo en un solo pase", async () => {
  const r = await runIngredientImport({ db, catalog: full, apply: true, log: quiet });
  assert.equal(r.create.length, full.ingredients.length);
  assert.equal((await db.collection("ingredients").get()).size, full.ingredients.length);
});
