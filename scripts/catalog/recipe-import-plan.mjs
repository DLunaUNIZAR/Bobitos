// Plan de importación del catálogo de recetas. Puro: no importa firebase-admin (lo usan los tests de reglas).
// `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp). Las recetas comunes son GLOBAL y del
// admin del catálogo; las de los usuarios comparten colección y nunca se tocan.
import { CATALOG_ADMIN_UID, baseExisting, formatGlobalCatalogPlan, planGlobalCatalogImport, stampCatalogDoc } from "./import-core.mjs";
import { CATALOG_AUTHOR_NAME } from "./import-plan.mjs";

export const RECIPES_COLLECTION = "recipes";

/** Campos que gestiona el importador, con la forma que escribe la app (FirestoreRecipeRepository). */
export function recipeManagedFields(entry) {
  return {
    title: entry.title,
    description: entry.description,
    category: entry.category,
    servings: entry.servings,
    totalMinutes: entry.totalMinutes,
    ingredients: entry.ingredients,
    steps: entry.steps,
    source: { provider: "bobitos" },
  };
}

/** Documento de Firestore → `ExistingDoc` de `planRecipeImport` (con `visibility`, para las manuales). */
export function recipeDocToExisting(id, data, { updatedAtMillis, importedAtMillis }) {
  const s = data.source;
  return {
    ...baseExisting(id, data, { updatedAtMillis, importedAtMillis }, {
      title: data.title,
      description: data.description ?? null,
      category: data.category ?? null,
      servings: data.servings ?? null,
      totalMinutes: data.totalMinutes ?? null,
      ingredients: data.ingredients ?? [],
      steps: data.steps ?? [],
      source: s ? { provider: s.provider } : undefined,
    }),
    visibility: data.visibility,
  };
}

export function recipeToFirestoreDoc(entry, { now, create = false }) {
  const doc = stampCatalogDoc(recipeManagedFields(entry), { now, adminUid: CATALOG_ADMIN_UID });
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

/** Plan genérico + `manualGlobal` (ver `planGlobalCatalogImport`). */
export function planRecipeImport({ recipes, existing, adminUid }) {
  return planGlobalCatalogImport({ entries: recipes, existing, managedFields: recipeManagedFields, adminUid });
}

export function formatRecipePlan(plan) {
  return formatGlobalCatalogPlan(plan, "Plan de importación de recetas");
}
