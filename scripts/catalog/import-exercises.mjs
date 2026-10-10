// Importa data/catalog/exercises.json a Firestore con firebase-admin. Idempotente; nunca borra.
// Uso: node scripts/catalog/import-exercises.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, CATALOG_META_PATH, docToExisting, formatPlan, planImageImport, planImport, toFirestoreDoc } from "./import-plan.mjs";
import { commitCatalogOps } from "./import-core.mjs";
import { connectAdmin, parseImportArgs } from "./admin-cli.mjs";
import { sha256Hex } from "./images.mjs";
import { validateEntry } from "./selection.mjs";

// Lotes de imágenes: pocos documentos y con tope de bytes (cada WebP pesa ≤200 KB; el límite de
// Firestore por commit es 10 MiB).
const IMAGE_BATCH_DOCS = 50;
const IMAGE_BATCH_BYTES = 5 * 1024 * 1024;
const DEFAULT_IMAGES_DIR = fileURLToPath(new URL("../../data/catalog/images/", import.meta.url));

const toMillis = (t) => (t && typeof t.toMillis === "function" ? t.toMillis() : null);

/** Lee data/catalog/images/<id>.webp de cada imagen a subir y comprueba su sha256 con el catálogo. */
function loadImagesToUpload(ids, catalog, imagesDir) {
  const byId = new Map(catalog.exercises.map((e) => [e.id, e]));
  return ids.map((id) => {
    const { image } = byId.get(id);
    let data;
    try {
      data = readFileSync(join(imagesDir, `${id}.webp`));
    } catch (e) {
      throw new Error(`${id}: no se puede leer ${id}.webp en ${imagesDir} (${e.message})`);
    }
    const hash = sha256Hex(data);
    if (hash !== image.hash) {
      throw new Error(`${id}: el sha256 de ${id}.webp (${hash}) no coincide con image.hash del catálogo (${image.hash})`);
    }
    return { id, data, image };
  });
}

/** Agrupa en lotes de como mucho IMAGE_BATCH_DOCS documentos e IMAGE_BATCH_BYTES bytes de imagen. */
export function chunkImages(items) {
  const chunks = [];
  let cur = [];
  let bytes = 0;
  for (const it of items) {
    if (cur.length > 0 && (cur.length >= IMAGE_BATCH_DOCS || bytes + it.data.length > IMAGE_BATCH_BYTES)) {
      chunks.push(cur);
      cur = [];
      bytes = 0;
    }
    cur.push(it);
    bytes += it.data.length;
  }
  if (cur.length > 0) chunks.push(cur);
  return chunks;
}

export async function runImport({ db, catalog, apply, log = console.log, imagesDir = DEFAULT_IMAGES_DIR }) {
  const problems = catalog.exercises.flatMap((e) => validateEntry(e).map((m) => `${e.id ?? e.name}: ${m}`));
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);

  const snap = await db.collection("exercises").get();
  const updateTimes = new Map();
  const existing = snap.docs.map((d) => {
    const data = d.data();
    updateTimes.set(d.id, d.updateTime);
    return docToExisting(d.id, data, {
      updatedAtMillis: toMillis(data.updatedAt),
      importedAtMillis: toMillis(data.source?.importedAt),
    });
  });

  // Solo los metadatos: select() evita traer los bytes de cada imagen.
  const imageSnap = await db.collection("exerciseImages").select("hash", "author", "license", "sourceUrl").get();
  const existingImages = imageSnap.docs.map((d) => ({
    id: d.id,
    hash: d.get("hash"),
    author: d.get("author"),
    license: d.get("license"),
    sourceUrl: d.get("sourceUrl"),
  }));

  const plan = planImport({ catalog, existing, adminUid: CATALOG_ADMIN_UID });
  plan.images = planImageImport({ catalog, existingImages, plan });
  // Antes de escribir nada: todos los ficheros existen y casan con el hash del catálogo.
  const toUpload = loadImagesToUpload(plan.images.upload, catalog, imagesDir);
  log(formatPlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return { ...plan, versionBumped: false };
  }

  const now = FieldValue.serverTimestamp();
  // Las imágenes van antes que las fichas que apuntan a su hash. Nunca se borra ninguna.
  let uploads = toUpload;
  if (toUpload.length > 0) {
    const sharp = (await import("sharp")).default;
    uploads = await Promise.all(
      toUpload.map(async (it) => {
        const { width, height } = await sharp(it.data).metadata();
        return { ...it, width, height };
      }),
    );
  }
  for (const chunk of chunkImages(uploads)) {
    const batch = db.batch();
    for (const { id, data, image, width, height } of chunk) {
      batch.set(db.collection("exerciseImages").doc(id), {
        data,
        contentType: "image/webp",
        hash: image.hash,
        width,
        height,
        ...(image.author !== undefined ? { author: image.author } : {}),
        license: image.license,
        sourceUrl: image.sourceUrl,
        updatedAt: now,
      });
    }
    await batch.commit();
  }
  const ops = [
    ...plan.create.map((e) => ({ kind: "create", e })),
    ...plan.update.map((e) => ({ kind: "update", e })),
  ];
  await commitCatalogOps({
    db, collection: "exercises", ops, toDoc: toFirestoreDoc, updateTimes,
    metaPath: CATALOG_META_PATH, adminUid: CATALOG_ADMIN_UID, now,
  });
  log(
    `\nAplicado: ${plan.create.length} creadas, ${plan.update.length} actualizadas, ${toUpload.length} imágenes subidas.`,
  );
  return { ...plan, versionBumped: ops.length > 0 };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const args = parseImportArgs(process.argv.slice(2), { defaultCatalog: "data/catalog/exercises.json" });
    const app = connectAdmin(args.project);
    const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
    await runImport({ db: getFirestore(app), catalog, apply: args.apply });
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
