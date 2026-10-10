// Plan de importación del catálogo de ejercicios. Puro: no importa firebase-admin (lo usan los
// tests de reglas). `now` llega ya construido (FieldValue.serverTimestamp() o un Timestamp).
import { CATALOG_ADMIN_UID, baseExisting, formatImportPlan, planCatalogImport, section, stampCatalogDoc } from "./import-core.mjs";
import { isNearDuplicate } from "./normalize.mjs";

// Se reexporta desde el núcleo (lo importan los tests y los scripts de ejercicios).
export { CATALOG_ADMIN_UID };
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
  return baseExisting(id, data, { updatedAtMillis, importedAtMillis }, {
    name: data.name,
    nameLower: data.nameLower,
    type: data.type,
    muscleGroup: data.muscleGroup,
    description: data.description,
    equipment: data.equipment,
    measure: data.measure ?? "REPS",
    image: data.image ?? null,
    source: s ? pickSource(s) : undefined,
  });
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
  const doc = stampCatalogDoc(managedFields(entry), { now, adminUid: CATALOG_ADMIN_UID });
  if (!create) return doc;
  return {
    ...doc,
    ownerUid: CATALOG_ADMIN_UID,
    createdBy: CATALOG_ADMIN_UID,
    createdByName: CATALOG_AUTHOR_NAME,
    createdAt: now,
  };
}

export function planImport({ catalog, existing, adminUid }) {
  return planCatalogImport({
    entries: catalog.exercises,
    existing,
    managedFields,
    isForeign: (d) => d.ownerUid !== adminUid,
    isNearDuplicate,
  });
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

export function formatPlan(plan) {
  const lines = [
    ...formatImportPlan(plan, { title: "Plan de importación de ejercicios", metaPath: CATALOG_META_PATH }),
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
