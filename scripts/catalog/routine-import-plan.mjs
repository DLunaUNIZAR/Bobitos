// Plan de importación del catálogo de rutinas. Puro: no importa firebase-admin (lo usan los tests de
// reglas). `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp). Las rutinas comunes
// son GLOBAL y del admin del catálogo; las de los usuarios comparten colección y nunca se tocan.
import { formatGlobalCatalogPlan, globalCatalogDoc, globalDocToExisting, planGlobalCatalogImport } from "./import-core.mjs";
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
  return globalDocToExisting(id, data, { updatedAtMillis, importedAtMillis }, {
    title: data.title,
    description: data.description ?? null,
    exercises: data.exercises ?? [],
  });
}

export function routineToFirestoreDoc(entry, { now, create = false }) {
  return globalCatalogDoc(routineManagedFields(entry), { now, create, authorName: CATALOG_AUTHOR_NAME });
}

/** Plan genérico + `manualGlobal` (ver `planGlobalCatalogImport`). */
export function planRoutineImport({ routines, existing, adminUid }) {
  return planGlobalCatalogImport({ entries: routines, existing, managedFields: routineManagedFields, adminUid });
}

export function formatRoutinePlan(plan) {
  return formatGlobalCatalogPlan(plan, "Plan de importación de rutinas");
}
