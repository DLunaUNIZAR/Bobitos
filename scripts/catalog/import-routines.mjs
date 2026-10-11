// Importa data/catalog/routines.json a Firestore (colección routines, comunes GLOBAL) con firebase-admin.
// Idempotente; nunca borra; sin catalogMeta (la app lee las rutinas en vivo).
// Uso: node scripts/catalog/import-routines.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, commitCatalogOps, loadExisting } from "./import-core.mjs";
import { connectAdmin, parseImportArgs, runMain } from "./admin-cli.mjs";
import {
  ROUTINES_COLLECTION, formatRoutinePlan, planRoutineImport, routineDocToExisting, routineToFirestoreDoc,
} from "./routine-import-plan.mjs";
import { buildRoutines, indexExercises, validateRoutineCatalog } from "./routines.mjs";

const EXERCISES = new URL("../../data/catalog/exercises.json", import.meta.url);

/** `exercises`: el contenido de exercises.json (para validar y copiar nombre, tipo y medida). */
export async function runRoutineImport({ db, catalog, exercises, apply, log = console.log }) {
  // Antes de leer nada de Firestore.
  const exercisesById = indexExercises(exercises);
  const problems = validateRoutineCatalog(catalog, exercisesById);
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);
  const routines = buildRoutines(catalog, exercisesById);

  const { existing, updateTimes } = await loadExisting(db, ROUTINES_COLLECTION, routineDocToExisting);
  const plan = planRoutineImport({ routines, existing, adminUid: CATALOG_ADMIN_UID });
  log(formatRoutinePlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return plan;
  }

  // Nunca se borra ninguna rutina.
  await commitCatalogOps({
    db, FieldValue, collection: ROUTINES_COLLECTION, plan, toDoc: routineToFirestoreDoc, updateTimes,
    metaPath: null, adminUid: CATALOG_ADMIN_UID, now: FieldValue.serverTimestamp(),
  });
  log(`\nAplicado: ${plan.create.length} creadas, ${plan.update.length} actualizadas.`);
  return plan;
}

await runMain(import.meta.url, async () => {
  const args = parseImportArgs(process.argv.slice(2), { defaultCatalog: "data/catalog/routines.json" });
  const app = connectAdmin(args.project);
  const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
  const exercises = JSON.parse(readFileSync(EXERCISES, "utf8"));
  await runRoutineImport({ db: getFirestore(app), catalog, exercises, apply: args.apply });
});
