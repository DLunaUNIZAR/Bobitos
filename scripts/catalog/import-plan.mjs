// Plan de importación del catálogo de ejercicios. Puro: no importa firebase-admin (lo usan los
// tests de reglas). `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp).
import { isNearDuplicate } from "./normalize.mjs";

// Debe coincidir con firestore.rules (recipeAdmins()) y RecipeAdmins.kt; un test lo vigila.
export const CATALOG_ADMIN_UID = "dWWH7eRhHEPopJf5BHPB3Dp6fry1";
// Documento con la versión del catálogo (la caché del cliente caduca al cambiar).
export const CATALOG_META_PATH = "catalogMeta/exercises";
export const CATALOG_AUTHOR_NAME = "Catálogo Bobitos";

// Solo las claves presentes: Firestore rechaza `undefined` y las fichas propias no llevan `url`.
export const pickSource = (s) =>
  Object.fromEntries(["provider", "id", "author", "license", "url"].filter((k) => s[k] !== undefined).map((k) => [k, s[k]]));

const pickImage = (i) =>
  i == null ? null : Object.fromEntries(["hash", "author", "license", "sourceUrl"].filter((k) => i[k] !== undefined).map((k) => [k, i[k]]));

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
      measure: data.measure ?? "REPS",
      image: data.image ?? null,
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
    measure: entry.measure ?? "REPS",
    image: pickImage(entry.image),
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
  const named = existing.filter((d) => typeof d.fields?.name === "string");

  for (const entry of catalog.exercises) {
    const cur = byId.get(entry.id);
    if (!cur) {
      plan.create.push(entry);
      for (const { id: existingId, fields } of named) {
        if (existingId !== entry.id && isNearDuplicate(entry.name, fields.name)) {
          plan.nearDuplicates.push({ id: entry.id, existingId });
        }
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

const IMAGE_META = ["hash", "author", "license", "sourceUrl"];
const sameImageMeta = (a, b) => IMAGE_META.every((k) => (a[k] ?? null) === (b[k] ?? null));

/**
 * Imágenes (exerciseImages/<id>) frente al catálogo. `existingImages`: [{id, hash, author?, license?,
 * sourceUrl?}]. Solo se tocan las de fichas que el importador escribe o deja igual (create ∪ update ∪
 * unchanged de `plan`); las de fichas omitidas (usuario, manual del admin, editadas en la app) van en
 * `skipped` para que ficha e imagen no se desfasen. `upload`: sin imagen o con otro hash/autor/licencia/
 * sourceUrl; `unchanged`: todo igual; `orphaned`: imágenes que ya no usa ninguna ficha (se informan, no
 * se borran). Si el lote de fichas fallase por precondición
 * tras subir la imagen, esta se queda (la siguiente importación la deja como `unchanged`).
 */
export function planImageImport({ catalog, existingImages, plan }) {
  const byId = new Map(existingImages.map((i) => [i.id, i]));
  const allowed = new Set([...plan.create.map((e) => e.id), ...plan.update.map((e) => e.id), ...plan.unchanged]);
  const out = { upload: [], unchanged: [], skipped: [], orphaned: [] };
  const wanted = new Set();
  for (const e of catalog.exercises) {
    if (!e.image) continue;
    wanted.add(e.id);
    if (!allowed.has(e.id)) {
      out.skipped.push(e.id);
      continue;
    }
    const cur = byId.get(e.id);
    (cur && sameImageMeta(cur, e.image) ? out.unchanged : out.upload).push(e.id);
  }
  out.orphaned = existingImages.filter((i) => !wanted.has(i.id)).map((i) => i.id);
  return out;
}

const section = (title, list, fmt = (x) => x) =>
  list.length === 0 ? [] : [`${title}: ${list.length}`, ...list.map((x) => `  - ${fmt(x)}`)];

export function formatPlan(plan) {
  const lines = [
    "Plan de importación de ejercicios",
    `Crear: ${plan.create.length}`,
    `Actualizar: ${plan.update.length}`,
    `Sin cambios: ${plan.unchanged.length}`,
    plan.create.length + plan.update.length > 0
      ? "La versión del catálogo subirá (catalogMeta/exercises)."
      : "La versión del catálogo no cambia.",
    ...section("Omitidas (ficha de usuario con el mismo id)", plan.skippedUserOwned, (x) => `${x.id} (dueño ${x.ownerUid})`),
    ...section("Omitidas (ficha manual del admin, sin fuente)", plan.skippedAdminManual),
    ...section("Omitidas (editadas en la app tras importarse)", plan.skippedEditedInApp),
    ...section("Huérfanas (ya no están en el JSON; no se borran)", plan.orphaned),
    ...section("Posibles duplicados (nombre casi igual)", plan.nearDuplicates, (x) => `${x.id} ~ ${x.existingId}`),
    ...(plan.images
      ? [
          `Imágenes a subir: ${plan.images.upload.length}`,
          `Imágenes sin cambios: ${plan.images.unchanged.length}`,
          ...section("Imágenes omitidas (ficha omitida)", plan.images.skipped),
          ...section("Imágenes huérfanas (no se borran)", plan.images.orphaned),
        ]
      : []),
  ];
  return lines.join("\n");
}
