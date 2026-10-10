// Plan de importación del catálogo de ingredientes. Puro: no importa firebase-admin. `now` llega ya
// construido (FieldValue.serverTimestamp() o un Timestamp). El documento común no lleva dueño.
import { formatImportPlan, planCatalogImport } from "./import-core.mjs";
import { CATALOG_ADMIN_UID } from "./import-plan.mjs";
import { ingredientKey } from "./ingredients.mjs";

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
  return {
    id,
    ownerUid: data.ownerUid,
    hasSource: s != null && typeof s === "object",
    updatedAtMillis: updatedAtMillis ?? 0,
    importedAtMillis,
    fields: {
      name: data.name,
      nameLower: data.nameLower,
      category: data.category,
      defaultUnit: data.defaultUnit,
      source: s ? { provider: s.provider } : undefined,
    },
  };
}

export function ingredientToFirestoreDoc(entry, { now, create = false }) {
  const m = ingredientManagedFields(entry);
  const doc = { ...m, source: { ...m.source, importedAt: now }, updatedBy: CATALOG_ADMIN_UID, updatedAt: now };
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
