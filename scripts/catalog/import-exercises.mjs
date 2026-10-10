// Importa data/catalog/exercises.json a Firestore con firebase-admin. Idempotente; nunca borra.
// Uso: node scripts/catalog/import-exercises.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, formatPlan, planImport, toFirestoreDoc } from "./import-plan.mjs";
import { validateEntry } from "./selection.mjs";

const BATCH_SIZE = 400;

const toMillis = (t) => (t && typeof t.toMillis === "function" ? t.toMillis() : null);

export async function runImport({ db, catalog, apply, log = console.log }) {
  const problems = catalog.exercises.flatMap((e) => validateEntry(e).map((m) => `${e.id ?? e.name}: ${m}`));
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);

  const snap = await db.collection("exercises").get();
  const updateTimes = new Map();
  const existing = snap.docs.map((d) => {
    const data = d.data();
    updateTimes.set(d.id, d.updateTime);
    const s = data.source;
    return {
      id: d.id,
      ownerUid: data.ownerUid,
      hasSource: s != null && typeof s === "object",
      updatedAtMillis: toMillis(data.updatedAt) ?? 0,
      importedAtMillis: toMillis(s?.importedAt),
      fields: {
        name: data.name,
        nameLower: data.nameLower,
        type: data.type,
        muscleGroup: data.muscleGroup,
        description: data.description,
        equipment: data.equipment,
        source: s ? { provider: s.provider, id: s.id, author: s.author, license: s.license, url: s.url } : undefined,
      },
    };
  });

  const plan = planImport({ catalog, existing, adminUid: CATALOG_ADMIN_UID });
  log(formatPlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return plan;
  }

  const now = FieldValue.serverTimestamp();
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
    await batch.commit();
  }
  log(`\nAplicado: ${plan.create.length} creadas, ${plan.update.length} actualizadas.`);
  return plan;
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
