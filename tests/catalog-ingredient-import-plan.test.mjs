import assert from "node:assert/strict";
import { test } from "node:test";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-plan.mjs";
import {
  INGREDIENTS_META_PATH, formatIngredientPlan, ingredientDocToExisting, ingredientManagedFields,
  ingredientToFirestoreDoc, planIngredientImport,
} from "../scripts/catalog/ingredient-import-plan.mjs";

const ing = (over = {}) => ({ id: "chocolate-negro", name: "Chocolate negro", category: "Dulces y chocolate", defaultUnit: "g", ...over });
const cat = (...ingredients) => ({ schemaVersion: 1, ingredients });
const imported = (e, over = {}) => ({
  id: e.id, ownerUid: undefined, hasSource: true, updatedAtMillis: 1000, importedAtMillis: 1000,
  fields: ingredientManagedFields(e), ...over,
});
const plan = (catalog, existing = []) => planIngredientImport({ catalog, existing, adminUid: CATALOG_ADMIN_UID });

test("la versión vive en catalogMeta/ingredients", () => {
  assert.equal(INGREDIENTS_META_PATH, "catalogMeta/ingredients");
});

test("el documento al crear lleva fuente, fechas y autor del catálogo y nada de dueño", () => {
  const now = { marker: true };
  const d = ingredientToFirestoreDoc(ing(), { now, create: true });
  assert.deepEqual(d, {
    name: "Chocolate negro", nameLower: "chocolate negro", category: "Dulces y chocolate", defaultUnit: "g",
    source: { provider: "bobitos", importedAt: now }, updatedBy: CATALOG_ADMIN_UID, updatedAt: now, createdAt: now,
  });
  assert.equal("createdAt" in ingredientToFirestoreDoc(ing(), { now }), false);
});

test("un documento importado sin cambios queda igual", () => {
  const e = ing();
  const data = { ...ingredientManagedFields(e), source: { provider: "bobitos", importedAt: "t" } };
  const existing = [ingredientDocToExisting(e.id, data, { updatedAtMillis: 5, importedAtMillis: 5 })];
  assert.deepEqual(plan(cat(e), existing).unchanged, ["chocolate-negro"]);
});

test("cambiar la categoría o la unidad actualiza", () => {
  const e = ing();
  assert.deepEqual(plan(cat(ing({ defaultUnit: "ud" })), [imported(e)]).update.map((x) => x.id), ["chocolate-negro"]);
});

test("un documento antiguo de usuario con el mismo id se omite", () => {
  const e = ing();
  const p = plan(cat(e), [imported(e, { ownerUid: "u1", hasSource: false })]);
  assert.deepEqual(p.skippedUserOwned, [{ id: "chocolate-negro", ownerUid: "u1" }]);
  assert.deepEqual(p.create, []);
});

test("una ficha manual del admin (sin fuente) y una editada en la app no se pisan", () => {
  const e = ing({ defaultUnit: "ud" });
  assert.deepEqual(plan(cat(e), [imported(ing(), { hasSource: false })]).skippedAdminManual, ["chocolate-negro"]);
  assert.deepEqual(plan(cat(e), [imported(ing(), { updatedAtMillis: 2000 })]).skippedEditedInApp, ["chocolate-negro"]);
});

test("se informa de huérfanas y de casi duplicados", () => {
  const viejo = ing({ id: "cacao", name: "Cacao" });
  const nuevo = ing({ id: "chocolates-negros", name: "Chocolates negros" });
  const p = plan(cat(nuevo), [imported(viejo), imported(ing())]);
  assert.deepEqual(p.orphaned.sort(), ["cacao", "chocolate-negro"]);
  assert.deepEqual(p.nearDuplicates, [{ id: "chocolates-negros", existingId: "chocolate-negro" }]);
});

test("el texto del plan habla de ingredientes y de su versión", () => {
  const text = formatIngredientPlan(plan(cat(ing())));
  assert.match(text, /^Plan de importación de ingredientes/);
  assert.match(text, /catalogMeta\/ingredients/);
});
