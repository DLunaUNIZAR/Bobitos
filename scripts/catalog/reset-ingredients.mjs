// Script de un solo uso para empezar de cero con los ingredientes (spec, decisión 7): borra la colección
// común ingredients (con sus marcas, también las colgadas de documentos padre sin datos) e ingredientPrefs.
// No toca ninguna otra ruta (users, exercises, exerciseImages, catalogMeta, recipes...).
// Sin --apply solo cuenta y no escribe nada.
// Protección: si catalogMeta/ingredients existe, el catálogo ya se importó e ingredientPrefs guarda las tiendas
// de los usuarios; borrar las perdería. En ese caso la simulación avisa y --apply se niega salvo con --repetir.
// Uso: node scripts/catalog/reset-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--repetir]
import { pathToFileURL } from "node:url";
import { getFirestore } from "firebase-admin/firestore";
import { connectAdmin } from "./admin-cli.mjs";

export function parseResetArgs(argv) {
  const args = { apply: false, project: null, repeat: false };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--apply") args.apply = true;
    else if (argv[i] === "--repetir") args.repeat = true;
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

const AVISO_YA_IMPORTADO =
  "El catálogo de ingredientes ya se importó (existe catalogMeta/ingredients). ingredientPrefs guarda las tiendas " +
  "de los usuarios y borrar las perdería. El borrado es de un solo uso.";

export async function runReset({ db, apply, repeat = false, log = console.log }) {
  const yaImportado = (await db.doc("catalogMeta/ingredients").get()).exists;
  if (yaImportado && apply && !repeat) {
    throw new Error(`${AVISO_YA_IMPORTADO} No se ha borrado nada. Para forzarlo, añade --repetir.`);
  }
  const counts = await countIngredientData(db);
  log(`Borrado de ingredientes: ${counts.ingredients} ingredientes, ${counts.brands} marcas, ${counts.prefs} preferencias`);
  if (yaImportado) log(`AVISO: ${AVISO_YA_IMPORTADO} Para forzarlo con --apply hará falta --repetir.`);
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
    await runReset({ db: getFirestore(app), apply: args.apply, repeat: args.repeat });
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
