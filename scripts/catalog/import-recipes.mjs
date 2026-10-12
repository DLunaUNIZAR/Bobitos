// Importa data/catalog/recipes.json a Firestore (colección recipes, comunes GLOBAL) con firebase-admin.
// Idempotente; nunca borra; sin catalogMeta (el Recetario lee las recetas en vivo).
// Uso: node scripts/catalog/import-recipes.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, commitCatalogOps, loadExisting } from "./import-core.mjs";
import { connectAdmin, parseImportArgs, runMain } from "./admin-cli.mjs";
import {
  RECIPES_COLLECTION, formatRecipePlan, planRecipeImport, recipeDocToExisting, recipeToFirestoreDoc,
} from "./recipe-import-plan.mjs";
import { buildRecipes, indexIngredients, validateRecipeCatalog } from "./recipes.mjs";

const INGREDIENTS = new URL("../../data/catalog/ingredients.json", import.meta.url);

/** `ingredients`: el contenido de ingredients.json (para validar y escribir el nombre canónico). */
export async function runRecipeImport({ db, catalog, ingredients, apply, log = console.log }) {
  // Antes de leer nada de Firestore.
  const index = indexIngredients(ingredients);
  const problems = validateRecipeCatalog(catalog, index);
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);
  const recipes = buildRecipes(catalog, index);

  const { existing, updateTimes } = await loadExisting(db, RECIPES_COLLECTION, recipeDocToExisting);
  const plan = planRecipeImport({ recipes, existing, adminUid: CATALOG_ADMIN_UID });
  log(formatRecipePlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return plan;
  }

  // Nunca se borra ninguna receta.
  await commitCatalogOps({
    db, FieldValue, collection: RECIPES_COLLECTION, plan, toDoc: recipeToFirestoreDoc, updateTimes,
    metaPath: null, adminUid: CATALOG_ADMIN_UID, now: FieldValue.serverTimestamp(),
  });
  log(`\nAplicado: ${plan.create.length} creadas, ${plan.update.length} actualizadas.`);
  return plan;
}

await runMain(import.meta.url, async () => {
  const args = parseImportArgs(process.argv.slice(2), { defaultCatalog: "data/catalog/recipes.json" });
  const app = connectAdmin(args.project);
  const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
  const ingredients = JSON.parse(readFileSync(INGREDIENTS, "utf8"));
  await runRecipeImport({ db: getFirestore(app), catalog, ingredients, apply: args.apply });
});
