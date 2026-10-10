// Núcleo genérico del importador de catálogos (ejercicios, ingredientes). Puro salvo `loadExisting` y
// `commitCatalogOps`, que reciben `db` (y `FieldValue`) ya construidos: no importa firebase-admin.

// Debe coincidir con firestore.rules (recipeAdmins()) y RecipeAdmins.kt; un test lo vigila.
export const CATALOG_ADMIN_UID = "dWWH7eRhHEPopJf5BHPB3Dp6fry1";

const toMillis = (t) => (t && typeof t.toMillis === "function" ? t.toMillis() : null);

/**
 * Lee la colección y la convierte con `docToExisting(id, data, {updatedAtMillis, importedAtMillis})`.
 * Devuelve `{ existing, updateTimes }` (updateTimes: id → updateTime, para la precondición del lote).
 */
export async function loadExisting(db, collection, docToExisting) {
  const snap = await db.collection(collection).get();
  const updateTimes = new Map();
  const existing = snap.docs.map((d) => {
    const data = d.data();
    updateTimes.set(d.id, d.updateTime);
    return docToExisting(d.id, data, {
      updatedAtMillis: toMillis(data.updatedAt),
      importedAtMillis: toMillis(data.source?.importedAt),
    });
  });
  return { existing, updateTimes };
}

/** Esqueleto común de `ExistingDoc`; cada catálogo aporta solo `fields`. */
export function baseExisting(id, data, { updatedAtMillis, importedAtMillis }, fields) {
  const s = data.source;
  return {
    id,
    ownerUid: data.ownerUid,
    hasSource: s != null && typeof s === "object",
    updatedAtMillis: updatedAtMillis ?? 0,
    importedAtMillis,
    fields,
  };
}

/** Parte común del documento a escribir: campos gestionados + marca de importación y de autor. */
export function stampCatalogDoc(managed, { now, adminUid }) {
  return { ...managed, source: { ...managed.source, importedAt: now }, updatedBy: adminUid, updatedAt: now };
}

export const canonical = (v) =>
  JSON.stringify(v, (_k, x) =>
    x && typeof x === "object" && !Array.isArray(x)
      ? Object.fromEntries(Object.keys(x).sort().map((k) => [k, x[k]]))
      : x,
  );

export const section = (title, list, fmt = (x) => x) =>
  list.length === 0 ? [] : [`${title}: ${list.length}`, ...list.map((x) => `  - ${fmt(x)}`)];

/**
 * Plan de importación genérico. `entries`: entradas del catálogo con `id` y `name`. `existing`:
 * [{id, ownerUid, hasSource, updatedAtMillis, importedAtMillis, fields}]. `managedFields(entry)`: los
 * campos que gestiona el importador. `isForeign(doc)`: cierto si el documento es de un usuario (se
 * omite y nunca es huérfano). `isNearDuplicate(a, b)`: solo se mira al crear; con `null` no se calcula.
 */
export function planCatalogImport({ entries, existing, managedFields, isForeign = () => false, isNearDuplicate = null }) {
  const plan = {
    create: [], update: [], unchanged: [],
    skippedUserOwned: [], skippedAdminManual: [], skippedEditedInApp: [],
    orphaned: [], nearDuplicates: [],
  };
  const byId = new Map(existing.map((d) => [d.id, d]));
  const entryIds = new Set(entries.map((e) => e.id));
  const named = existing.filter((d) => typeof d.fields?.name === "string");

  for (const entry of entries) {
    const cur = byId.get(entry.id);
    if (!cur) {
      plan.create.push(entry);
      if (isNearDuplicate) {
        for (const { id: existingId, fields } of named) {
          if (existingId !== entry.id && isNearDuplicate(entry.name, fields.name)) {
            plan.nearDuplicates.push({ id: entry.id, existingId });
          }
        }
      }
    } else if (isForeign(cur)) {
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
    if (!isForeign(d) && d.hasSource && !entryIds.has(d.id)) plan.orphaned.push(d.id);
  }
  return plan;
}

/** Líneas comunes del texto del plan (sin las de imágenes). */
export function formatImportPlan(plan, { title, metaPath }) {
  return [
    title,
    `Crear: ${plan.create.length}`,
    `Actualizar: ${plan.update.length}`,
    `Sin cambios: ${plan.unchanged.length}`,
    plan.create.length + plan.update.length > 0
      ? `La versión del catálogo subirá (${metaPath}).`
      : "La versión del catálogo no cambia.",
    ...section("Omitidas (ficha de usuario con el mismo id)", plan.skippedUserOwned, (x) => `${x.id} (dueño ${x.ownerUid})`),
    ...section("Omitidas (ficha manual del admin, sin fuente)", plan.skippedAdminManual),
    ...section("Omitidas (editadas en la app tras importarse)", plan.skippedEditedInApp),
    ...section("Huérfanas (ya no están en el JSON; no se borran)", plan.orphaned),
    ...section("Posibles duplicados (nombre casi igual)", plan.nearDuplicates, (x) => `${x.id} ~ ${x.existingId}`),
  ];
}

/**
 * Escribe en lotes las altas (`plan.create`) y las actualizaciones (`plan.update`). `FieldValue` es el de
 * firebase-admin/firestore. Cada lote con operaciones sube la `version` de `metaPath` (se crea en 1 si no
 * existe). Devuelve cuántas operaciones escribió.
 */
export async function commitCatalogOps({ db, FieldValue, collection, plan, toDoc, updateTimes, metaPath, adminUid, now, batchSize = 400 }) {
  const ops = [
    ...plan.create.map((e) => ({ kind: "create", e })),
    ...plan.update.map((e) => ({ kind: "update", e })),
  ];
  for (let i = 0; i < ops.length; i += batchSize) {
    const batch = db.batch();
    for (const { kind, e } of ops.slice(i, i + batchSize)) {
      const ref = db.collection(collection).doc(e.id);
      if (kind === "create") batch.create(ref, toDoc(e, { now, create: true }));
      else batch.update(ref, toDoc(e, { now, create: false }), { lastUpdateTime: updateTimes.get(e.id) });
    }
    // Cada lote con operaciones sube la versión del catálogo (se crea en 1 si no existe).
    batch.set(
      db.doc(metaPath),
      { version: FieldValue.increment(1), updatedAt: now, updatedBy: adminUid },
      { merge: true },
    );
    await batch.commit();
  }
  return ops.length;
}
