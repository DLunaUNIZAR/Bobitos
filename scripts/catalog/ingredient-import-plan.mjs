// Plan de importación del catálogo de ingredientes. Puro: no importa firebase-admin. `now` llega ya
// construido (FieldValue.serverTimestamp() o un Timestamp). El documento común no lleva dueño.
import { CATALOG_ADMIN_UID, baseExisting, formatImportPlan, planCatalogImport, stampCatalogDoc } from "./import-core.mjs";
import { ingredientKey } from "./ingredients.mjs";

export const INGREDIENTS_COLLECTION = "ingredients";
// Documento con la versión del catálogo de ingredientes (la caché del cliente caduca al cambiar).
export const INGREDIENTS_META_PATH = "catalogMeta/ingredients";

export function ingredientManagedFields(entry) {
  return {
    name: entry.name,
    nameLower: entry.name.toLowerCase(),
    category: entry.category,
    defaultUnit: entry.defaultUnit,
    source: { provider: "bobitos" },
  };
}

/**
 * Documento de Firestore (datos ya planos) → `ExistingDoc` de `planIngredientImport`.
 * Las marcas de tiempo llegan ya en milisegundos (null si faltan).
 */
export function ingredientDocToExisting(id, data, { updatedAtMillis, importedAtMillis }) {
  const s = data.source;
  return baseExisting(id, data, { updatedAtMillis, importedAtMillis }, {
    name: data.name,
    nameLower: data.nameLower,
    category: data.category,
    defaultUnit: data.defaultUnit,
    source: s ? { provider: s.provider } : undefined,
  });
}

export function ingredientToFirestoreDoc(entry, { now, create = false }) {
  const doc = stampCatalogDoc(ingredientManagedFields(entry), { now, adminUid: CATALOG_ADMIN_UID });
  return create ? { ...doc, createdAt: now } : doc;
}

export function planIngredientImport({ catalog, existing, adminUid }) {
  return planCatalogImport({
    entries: catalog.ingredients,
    existing,
    managedFields: ingredientManagedFields,
    isForeign: (d) => d.ownerUid != null && d.ownerUid !== adminUid,
    isNearDuplicate: (a, b) => ingredientKey(a) === ingredientKey(b),
  });
}

export function formatIngredientPlan(plan) {
  return formatImportPlan(plan, { title: "Plan de importación de ingredientes", metaPath: INGREDIENTS_META_PATH }).join("\n");
}
