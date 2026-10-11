// Plan de importación del catálogo de rutinas. Puro: no importa firebase-admin (lo usan los tests de
// reglas). `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp). Las rutinas comunes
// son GLOBAL y del admin del catálogo; las de los usuarios comparten colección y nunca se tocan.
import { CATALOG_ADMIN_UID, baseExisting, formatImportPlan, planCatalogImport, section, stampCatalogDoc } from "./import-core.mjs";
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

/**
 * Plan genérico + `manualGlobal`: comunes del admin sin `source` y con un id que no está en el JSON
 * (creadas a mano en la app). Se informa de ellas para que el usuario decida; nunca se tocan.
 */
export function planRoutineImport({ routines, existing, adminUid }) {
  const isForeign = (d) => d.ownerUid !== adminUid;
  const plan = planCatalogImport({ entries: routines, existing, managedFields: routineManagedFields, isForeign });
  const ids = new Set(routines.map((r) => r.id));
  plan.manualGlobal = existing
    .filter((d) => !isForeign(d) && !d.hasSource && d.visibility === "GLOBAL" && !ids.has(d.id))
    .map((d) => d.id);
  return plan;
}

export function formatRoutinePlan(plan) {
  return [
    ...formatImportPlan(plan, { title: "Plan de importación de rutinas", metaPath: null }),
    ...section("Comunes creadas a mano en la app (no se tocan)", plan.manualGlobal),
  ].join("\n");
}
