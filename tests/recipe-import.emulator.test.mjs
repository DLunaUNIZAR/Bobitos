import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { beforeEach, test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-core.mjs";
import { runRecipeImport } from "../scripts/catalog/import-recipes.mjs";
import { quiet, setupEmulatorDb } from "./helpers/emulator-db.mjs";

const ingredients = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
const full = JSON.parse(await readFile(new URL("../data/catalog/recipes.json", import.meta.url), "utf8"));
const small = { schemaVersion: 1, recipes: [
  { title: "Prueba lentejas", category: "Legumbres", description: "Receta de prueba.", servings: 4, totalMinutes: 60,
    ingredients: [
      { name: "Lentejas pardinas", quantity: "300", unit: "g" },
      { name: "Ajos", quantity: "2", unit: "dientes" },
      { name: "Sal" },
    ],
    steps: ["Cuece las lentejas.", "Sirve."] },
  { title: "Prueba tortilla", category: "Huevos", description: "Receta de prueba.", servings: 2, totalMinutes: 20,
    ingredients: [
      { name: "Huevos", quantity: "4", unit: "ud" },
      { name: "Aceite de oliva virgen extra", quantity: "1/2", unit: "cucharadita" },
    ],
    steps: ["Bate los huevos.", "Cuaja la tortilla."] },
] };

const db = setupEmulatorDb("recipe-import-test");
beforeEach(async () => {
  await db.recursiveDelete(db.collection("recipes"));
  await db.recursiveDelete(db.collection("catalogMeta"));
});
const run = (catalog, apply = true) => runRecipeImport({ db, catalog, ingredients, apply, log: quiet });

test("importa como comunes del catálogo, con la forma de la app, y una segunda pasada no escribe", async () => {
  const first = await run(small);
  assert.equal(first.create.length, 2);
  const d = (await db.doc("recipes/prueba-lentejas").get()).data();
  assert.equal(d.visibility, "GLOBAL");
  assert.equal(d.ownerUid, CATALOG_ADMIN_UID);
  assert.equal(d.createdBy, CATALOG_ADMIN_UID);
  assert.equal(d.createdByName, "Catálogo Bobitos");
  assert.equal(d.source.provider, "bobitos");
  assert.equal(d.updatedAt.toMillis(), d.source.importedAt.toMillis());
  assert.equal(d.category, "Legumbres");
  assert.equal(d.servings, 4);
  assert.ok(Number.isInteger(d.totalMinutes));
  // Cantidades como texto y nombre canónico («Ajos» → «Ajo»); la sal, sin cantidad ni unidad.
  assert.deepEqual(d.ingredients, [
    { name: "Lentejas pardinas", quantity: "300", unit: "g" },
    { name: "Ajo", quantity: "2", unit: "dientes" },
    { name: "Sal", quantity: null, unit: null },
  ]);
  assert.deepEqual(d.steps, ["Cuece las lentejas.", "Sirve."]);
  assert.equal("sourceRecipeId" in d, false);
  const second = await run(small);
  assert.equal(second.create.length + second.update.length, 0);
  assert.equal(second.unchanged.length, 2);
  assert.equal((await db.collection("catalogMeta").get()).size, 0);
});

test("la simulación no escribe nada", async () => {
  const r = await run(small, false);
  assert.equal(r.create.length, 2);
  assert.equal((await db.collection("recipes").get()).size, 0);
});

test("un cambio en el JSON actualiza solo esa receta", async () => {
  await run(small);
  const [a, b] = small.recipes;
  const r = await run({ ...small, recipes: [{ ...a, steps: [...a.steps, "Reposa 5 minutos."] }, b] });
  assert.deepEqual(r.update.map((e) => e.id), ["prueba-lentejas"]);
});

test("no pisa una común editada en la app ni la receta de un usuario con el mismo id", async () => {
  await run(small);
  await db.doc("recipes/prueba-lentejas").update({ title: "Editada", updatedAt: Timestamp.fromMillis(Date.now() + 60_000) });
  await db.doc("recipes/prueba-tortilla").delete();
  await db.doc("recipes/prueba-tortilla").set({ ownerUid: "u1", visibility: "PRIVATE", title: "Mía" });
  const r = await run(small);
  assert.deepEqual(r.skippedEditedInApp, ["prueba-lentejas"]);
  assert.deepEqual(r.skippedUserOwned, [{ id: "prueba-tortilla", ownerUid: "u1" }]);
  assert.equal((await db.doc("recipes/prueba-lentejas").get()).get("title"), "Editada");
  assert.equal((await db.doc("recipes/prueba-tortilla").get()).get("title"), "Mía");
});

test("una común borrada y recreada en la app con el mismo id («Deshacer») se omite y no se duplica", async () => {
  await run(small);
  const ref = db.doc("recipes/prueba-lentejas");
  const { source: _source, ...withoutSource } = (await ref.get()).data();
  await ref.delete();
  await ref.set({ ...withoutSource, createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
  const r = await run(small);
  assert.deepEqual(r.skippedAdminManual, ["prueba-lentejas"]);
  assert.equal(r.create.length, 0);
  assert.equal((await db.collection("recipes").get()).size, 2);
});

test("informa de las comunes creadas a mano y no las toca", async () => {
  await db.doc("recipes/manual1").set({ ownerUid: CATALOG_ADMIN_UID, visibility: "GLOBAL", title: "Cocido" });
  const r = await run(small);
  assert.deepEqual(r.manualGlobal, ["manual1"]);
  assert.equal((await db.doc("recipes/manual1").get()).get("title"), "Cocido");
});

test("rechaza un catálogo inválido sin escribir", async () => {
  const bad = { ...small, recipes: [{ ...small.recipes[0], ingredients: [{ name: "Unicornio", quantity: "1", unit: "ud" }] }] };
  await assert.rejects(run(bad), /Catálogo inválido/);
  assert.equal((await db.collection("recipes").get()).size, 0);
});

test("importa el catálogo completo en un solo pase", async () => {
  const r = await run(full);
  assert.equal(r.create.length, full.recipes.length);
  assert.equal((await db.collection("recipes").get()).size, full.recipes.length);
});
