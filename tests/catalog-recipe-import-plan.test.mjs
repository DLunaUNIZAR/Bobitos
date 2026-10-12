import assert from "node:assert/strict";
import { test } from "node:test";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-core.mjs";
import {
  RECIPES_COLLECTION, formatRecipePlan, planRecipeImport, recipeDocToExisting, recipeManagedFields, recipeToFirestoreDoc,
} from "../scripts/catalog/recipe-import-plan.mjs";

const r = (over = {}) => ({
  id: "lentejas", title: "Lentejas", description: "De diario.", category: "Legumbres", servings: 4, totalMinutes: 60,
  ingredients: [{ name: "Lentejas pardinas", quantity: "300", unit: "g" }, { name: "Sal", quantity: null, unit: null }],
  steps: ["Cuece las lentejas."],
  ...over,
});
const imported = (e, over = {}) => ({
  id: e.id, ownerUid: CATALOG_ADMIN_UID, hasSource: true, updatedAtMillis: 1000, importedAtMillis: 1000,
  visibility: "GLOBAL", fields: recipeManagedFields(e), ...over,
});
const plan = (recipes, existing = []) => planRecipeImport({ recipes, existing, adminUid: CATALOG_ADMIN_UID });

test("la colección es recipes", () => assert.equal(RECIPES_COLLECTION, "recipes"));

test("al crear lleva visibilidad, dueño y autor del catálogo; al actualizar, solo lo gestionado", () => {
  const now = { marker: true };
  const e = r();
  assert.deepEqual(recipeToFirestoreDoc(e, { now, create: true }), {
    title: "Lentejas", description: "De diario.", category: "Legumbres", servings: 4, totalMinutes: 60,
    ingredients: e.ingredients, steps: e.steps,
    source: { provider: "bobitos", importedAt: now }, updatedBy: CATALOG_ADMIN_UID, updatedAt: now,
    visibility: "GLOBAL", ownerUid: CATALOG_ADMIN_UID, createdBy: CATALOG_ADMIN_UID, createdByName: "Catálogo Bobitos", createdAt: now,
  });
  assert.deepEqual(Object.keys(recipeToFirestoreDoc(e, { now })).sort(), [
    "category", "description", "ingredients", "servings", "source", "steps", "title", "totalMinutes", "updatedAt", "updatedBy",
  ]);
});

test("un documento importado sin cambios queda igual", () => {
  const e = r();
  const data = { ...recipeToFirestoreDoc(e, { now: "t", create: true }) };
  const existing = [recipeDocToExisting(e.id, data, { updatedAtMillis: 5, importedAtMillis: 5 })];
  assert.deepEqual(plan([e], existing).unchanged, ["lentejas"]);
});

test("cambiar un paso, una cantidad, las raciones o la categoría actualiza", () => {
  const changes = [
    r({ steps: ["Cuece las lentejas 40 minutos."] }),
    r({ ingredients: [{ name: "Lentejas pardinas", quantity: "400", unit: "g" }, { name: "Sal", quantity: null, unit: null }] }),
    r({ servings: 6 }),
    r({ category: "Verduras" }),
  ];
  for (const changed of changes) assert.deepEqual(plan([changed], [imported(r())]).update.map((x) => x.id), ["lentejas"]);
});

test("omite la receta de un usuario, la manual del admin y la editada en la app", () => {
  assert.deepEqual(plan([r()], [imported(r(), { ownerUid: "u1", hasSource: false })]).skippedUserOwned,
    [{ id: "lentejas", ownerUid: "u1" }]);
  assert.deepEqual(plan([r({ servings: 6 })], [imported(r(), { hasSource: false })]).skippedAdminManual, ["lentejas"]);
  assert.deepEqual(plan([r({ servings: 6 })], [imported(r(), { updatedAtMillis: 2000 })]).skippedEditedInApp, ["lentejas"]);
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
  const text = formatRecipePlan(p);
  assert.match(text, /^Plan de importación de recetas/);
  assert.match(text, /Crear: 1/);
  assert.doesNotMatch(text, /versión/);
  assert.match(text, /Comunes creadas a mano en la app \(no se tocan\): 1\n  - manual/);
});
