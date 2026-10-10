// Script de un solo uso para empezar de cero con los ingredientes (spec, decisión 7): borra la colección
// común ingredients (con sus marcas, también las colgadas de documentos padre sin datos) e ingredientPrefs.
// No toca ninguna otra ruta (users, exercises, exerciseImages, catalogMeta, recipes...).
// Sin --apply solo cuenta y no escribe nada.
// Uso: node scripts/catalog/reset-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply]
import { pathToFileURL } from "node:url";
import { getFirestore } from "firebase-admin/firestore";
import { connectAdmin } from "./admin-cli.mjs";

export function parseResetArgs(argv) {
  const args = { apply: false, project: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--apply") args.apply = true;
    else if (argv[i] === "--project") args.project = argv[++i];
    else throw new Error(`Argumento desconocido: ${argv[i]}`);
  }
  return args;
}

export async function countIngredientData(db) {
  // listDocuments incluye los padres sin datos que solo tienen marcas.
  const refs = await db.collection("ingredients").listDocuments();
  let brands = 0;
  for (const ref of refs) brands += (await ref.collection("brands").count().get()).data().count;
  const prefs = (await db.collection("ingredientPrefs").count().get()).data().count;
  return { ingredients: refs.length, brands, prefs };
}

export async function runReset({ db, apply, log = console.log }) {
  const counts = await countIngredientData(db);
  log(`Borrado de ingredientes: ${counts.ingredients} ingredientes, ${counts.brands} marcas, ${counts.prefs} preferencias`);
  if (!apply) {
    log("Simulación: no se ha borrado nada. Usa --apply para borrar.");
    return { ...counts, deleted: false };
  }
  // Solo estas dos colecciones.
  await db.recursiveDelete(db.collection("ingredients"));
  await db.recursiveDelete(db.collection("ingredientPrefs"));
  log(`Borrado: ${counts.ingredients} ingredientes, ${counts.brands} marcas, ${counts.prefs} preferencias`);
  return { ...counts, deleted: true };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const args = parseResetArgs(process.argv.slice(2));
    const app = connectAdmin(args.project);
    await runReset({ db: getFirestore(app), apply: args.apply });
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
