// Script de un solo uso para empezar de cero con los ingredientes (spec, decisión 7): borra la colección
// común ingredients (con sus marcas, también las colgadas de documentos padre sin datos) e ingredientPrefs.
// No toca ninguna otra ruta (users, exercises, exerciseImages, catalogMeta, recipes...).
// Sin --apply solo cuenta y no escribe nada.
// Protección: si catalogMeta/ingredients existe, el catálogo ya se importó e ingredientPrefs guarda las tiendas
// de los usuarios; borrar las perdería. En ese caso la simulación avisa y --apply se niega salvo con --repetir.
// Uso: node scripts/catalog/reset-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--repetir]
import { getFirestore } from "firebase-admin/firestore";
import { connectAdmin, parseCliArgs, runMain } from "./admin-cli.mjs";
import { INGREDIENTS_COLLECTION, INGREDIENTS_META_PATH } from "./ingredient-import-plan.mjs";

export function parseResetArgs(argv) {
  return parseCliArgs(
    argv,
    { "--apply": { key: "apply" }, "--repetir": { key: "repeat" }, "--project": { key: "project", value: true } },
    { apply: false, project: null, repeat: false },
  );
}

export async function countIngredientData(db) {
  // listDocuments incluye los padres sin datos que solo tienen marcas.
  const refs = await db.collection(INGREDIENTS_COLLECTION).listDocuments();
  const perParent = await Promise.all(refs.map(async (ref) => (await ref.collection("brands").count().get()).data().count));
  const brands = perParent.reduce((a, n) => a + n, 0);
  const prefs = (await db.collection("ingredientPrefs").count().get()).data().count;
  return { ingredients: refs.length, brands, prefs };
}

const AVISO_YA_IMPORTADO =
  "El catálogo de ingredientes ya se importó (existe catalogMeta/ingredients). ingredientPrefs guarda las tiendas " +
  "de los usuarios y borrar las perdería. El borrado es de un solo uso.";

export async function runReset({ db, apply, repeat = false, log = console.log }) {
  const yaImportado = (await db.doc(INGREDIENTS_META_PATH).get()).exists;
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
  await db.recursiveDelete(db.collection(INGREDIENTS_COLLECTION));
  await db.recursiveDelete(db.collection("ingredientPrefs"));
  log(`Borrado: ${counts.ingredients} ingredientes, ${counts.brands} marcas, ${counts.prefs} preferencias`);
  return { ...counts, deleted: true };
}

await runMain(import.meta.url, async () => {
  const args = parseResetArgs(process.argv.slice(2));
  const app = connectAdmin(args.project);
  await runReset({ db: getFirestore(app), apply: args.apply, repeat: args.repeat });
});
