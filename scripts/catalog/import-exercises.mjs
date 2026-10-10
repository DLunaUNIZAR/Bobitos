// Importa data/catalog/exercises.json a Firestore con firebase-admin. Idempotente; nunca borra.
// Uso: node scripts/catalog/import-exercises.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, CATALOG_META_PATH, docToExisting, formatPlan, planImageImport, planImport, toFirestoreDoc } from "./import-plan.mjs";
import { sha256Hex } from "./images.mjs";
import { validateEntry } from "./selection.mjs";

const BATCH_SIZE = 400;
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
  for (let i = 0; i < ops.length; i += BATCH_SIZE) {
    const batch = db.batch();
    for (const { kind, e } of ops.slice(i, i + BATCH_SIZE)) {
      const ref = db.collection("exercises").doc(e.id);
      if (kind === "create") batch.create(ref, toFirestoreDoc(e, { now, create: true }));
      else batch.update(ref, toFirestoreDoc(e, { now, create: false }), { lastUpdateTime: updateTimes.get(e.id) });
    }
    // Cada lote con operaciones sube la versión del catálogo (se crea en 1 si no existe).
    batch.set(
      db.doc(CATALOG_META_PATH),
      { version: FieldValue.increment(1), updatedAt: now, updatedBy: CATALOG_ADMIN_UID },
      { merge: true },
    );
    await batch.commit();
  }
  log(
    `\nAplicado: ${plan.create.length} creadas, ${plan.update.length} actualizadas, ${toUpload.length} imágenes subidas.`,
  );
  return { ...plan, versionBumped: ops.length > 0 };
}

function parseArgs(argv) {
  const args = { apply: false, catalog: "data/catalog/exercises.json", project: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--apply") args.apply = true;
    else if (argv[i] === "--project") args.project = argv[++i];
    else if (argv[i] === "--catalog") args.catalog = argv[++i];
    else throw new Error(`Argumento desconocido: ${argv[i]}`);
  }
  return args;
}

function connect(projectArg) {
  const projectId = projectArg === "dev" ? "bobitos-dev" : projectArg;
  const emulator = process.env.FIRESTORE_EMULATOR_HOST;
  if (projectId?.startsWith("demo-")) {
    if (!emulator) throw new Error("Un proyecto demo-* exige FIRESTORE_EMULATOR_HOST (emulador).");
    return initializeApp({ projectId });
  }
  if (projectId === "bobitos-dev") {
    if (emulator) throw new Error("FIRESTORE_EMULATOR_HOST está definida: no se importa a bobitos-dev.");
    const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
    if (!keyPath) throw new Error("Falta GOOGLE_APPLICATION_CREDENTIALS (clave de cuenta de servicio fuera del repo).");
    const keyProject = JSON.parse(readFileSync(keyPath, "utf8")).project_id;
    if (keyProject !== "bobitos-dev") throw new Error(`La clave es del proyecto «${keyProject}», no de bobitos-dev.`);
    return initializeApp({ credential: applicationDefault(), projectId });
  }
  throw new Error("Usa --project demo-bobitos, bobitos-dev o dev.");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const args = parseArgs(process.argv.slice(2));
    const app = connect(args.project);
    const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
    await runImport({ db: getFirestore(app), catalog, apply: args.apply });
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
