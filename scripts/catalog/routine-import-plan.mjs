// Plan de importación del catálogo de rutinas. Puro: no importa firebase-admin (lo usan los tests de
// reglas). `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp). Las rutinas comunes
// son GLOBAL y del admin del catálogo; las de los usuarios comparten colección y nunca se tocan.
import { CATALOG_ADMIN_UID, baseExisting, formatGlobalCatalogPlan, planGlobalCatalogImport, stampCatalogDoc } from "./import-core.mjs";
import { CATALOG_AUTHOR_NAME } from "./import-plan.mjs";

export const ROUTINES_COLLECTION = "routines";

export function routineManagedFields(entry) {
  return {
    title: entry.title,
    description: entry.description,
    exercises: entry.exercises,
    source: { provider: "bobitos" },
  };
}

/** Documento de Firestore → `ExistingDoc` de `planRoutineImport` (con `visibility`, para las manuales). */
export function routineDocToExisting(id, data, { updatedAtMillis, importedAtMillis }) {
  const s = data.source;
  return {
    ...baseExisting(id, data, { updatedAtMillis, importedAtMillis }, {
      title: data.title,
      description: data.description ?? null,
      exercises: data.exercises ?? [],
      source: s ? { provider: s.provider } : undefined,
    }),
    visibility: data.visibility,
  };
}

export function routineToFirestoreDoc(entry, { now, create = false }) {
  const doc = stampCatalogDoc(routineManagedFields(entry), { now, adminUid: CATALOG_ADMIN_UID });
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
export function planRoutineImport({ routines, existing, adminUid }) {
  return planGlobalCatalogImport({ entries: routines, existing, managedFields: routineManagedFields, adminUid });
}

export function formatRoutinePlan(plan) {
  return formatGlobalCatalogPlan(plan, "Plan de importación de rutinas");
}
