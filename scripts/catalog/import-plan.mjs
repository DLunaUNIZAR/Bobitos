// Plan de importación del catálogo de ejercicios. Puro: no importa firebase-admin (lo usan los
// tests de reglas). `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp).
import { groupBy, nearDuplicateKey } from "./normalize.mjs";

// Debe coincidir con firestore.rules (recipeAdmins()) y RecipeAdmins.kt; un test lo vigila.
export const CATALOG_ADMIN_UID = "dWWH7eRhHEPopJf5BHPB3Dp6fry1";
export const CATALOG_AUTHOR_NAME = "Catálogo Bobitos";

export const pickSource = (s) => ({ provider: s.provider, id: s.id, author: s.author, license: s.license, url: s.url });

/**
 * Documento de Firestore (datos ya planos) → `ExistingDoc` de `planImport`.
 * Las marcas de tiempo llegan ya en milisegundos (null si faltan).
 */
export function docToExisting(id, data, { updatedAtMillis, importedAtMillis }) {
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
      type: data.type,
      muscleGroup: data.muscleGroup,
      description: data.description,
      equipment: data.equipment,
      source: s ? pickSource(s) : undefined,
    },
  };
}

export function managedFields(entry) {
  return {
    name: entry.name,
    nameLower: entry.name.toLowerCase(),
    type: entry.type,
    muscleGroup: entry.muscleGroup,
    description: entry.description,
    equipment: [...entry.equipment],
    source: pickSource(entry.source),
  };
}

export function toFirestoreDoc(entry, { now, create = false }) {
  const m = managedFields(entry);
  const doc = { ...m, source: { ...m.source, importedAt: now }, updatedBy: CATALOG_ADMIN_UID, updatedAt: now };
  if (!create) return doc;
  return {
    ...doc,
    ownerUid: CATALOG_ADMIN_UID,
    createdBy: CATALOG_ADMIN_UID,
    createdByName: CATALOG_AUTHOR_NAME,
    createdAt: now,
  };
}

const canonical = (v) =>
  JSON.stringify(v, (_k, x) =>
    x && typeof x === "object" && !Array.isArray(x)
      ? Object.fromEntries(Object.keys(x).sort().map((k) => [k, x[k]]))
      : x,
  );

export function planImport({ catalog, existing, adminUid }) {
  const plan = {
    create: [], update: [], unchanged: [],
    skippedUserOwned: [], skippedAdminManual: [], skippedEditedInApp: [],
    orphaned: [], nearDuplicates: [],
  };
  const byId = new Map(existing.map((d) => [d.id, d]));
  const catalogIds = new Set(catalog.exercises.map((e) => e.id));
  const existingByKey = groupBy(
    existing.filter((d) => typeof d.fields?.name === "string"),
    (d) => nearDuplicateKey(d.fields.name),
  );

  for (const entry of catalog.exercises) {
    const cur = byId.get(entry.id);
    if (!cur) {
      plan.create.push(entry);
      const k = nearDuplicateKey(entry.name);
      for (const { id: existingId } of existingByKey.get(k) ?? []) {
        if (existingId !== entry.id) plan.nearDuplicates.push({ id: entry.id, existingId });
      }
    } else if (cur.ownerUid !== adminUid) {
      plan.skippedUserOwned.push({ id: cur.id, ownerUid: cur.ownerUid });
    } else if (!cur.hasSource) {
      plan.skippedAdminManual.push(cur.id);
    } else if (cur.importedAtMillis != null && cur.updatedAtMillis > cur.importedAtMillis) {
      plan.skippedEditedInApp.push(cur.id);
    } else if (canonical(cur.fields) === canonical(managedFields(entry))) {
      plan.unchanged.push(entry.id);
    } else {
      plan.update.push(entry);
    }
  }

  for (const d of existing) {
    if (d.ownerUid === adminUid && d.hasSource && !catalogIds.has(d.id)) plan.orphaned.push(d.id);
  }
  return plan;
}

const section = (title, list, fmt = (x) => x) =>
  list.length === 0 ? [] : [`${title}: ${list.length}`, ...list.map((x) => `  - ${fmt(x)}`)];

export function formatPlan(plan) {
  const lines = [
    "Plan de importación de ejercicios",
    `Crear: ${plan.create.length}`,
    `Actualizar: ${plan.update.length}`,
    `Sin cambios: ${plan.unchanged.length}`,
    ...section("Omitidas (ficha de usuario con el mismo id)", plan.skippedUserOwned, (x) => `${x.id} (dueño ${x.ownerUid})`),
    ...section("Omitidas (ficha manual del admin, sin fuente)", plan.skippedAdminManual),
    ...section("Omitidas (editadas en la app tras importarse)", plan.skippedEditedInApp),
    ...section("Huérfanas (ya no están en el JSON; no se borran)", plan.orphaned),
    ...section("Posibles duplicados (nombre casi igual)", plan.nearDuplicates, (x) => `${x.id} ~ ${x.existingId}`),
  ];
  return lines.join("\n");
}
