// Importa data/catalog/ingredients.json a Firestore (colección ingredients) con firebase-admin.
// Idempotente; nunca borra.
// Uso: node scripts/catalog/import-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID, commitCatalogOps, loadExisting } from "./import-core.mjs";
import { connectAdmin, parseImportArgs, runMain } from "./admin-cli.mjs";
import {
  INGREDIENTS_COLLECTION, INGREDIENTS_META_PATH, formatIngredientPlan, ingredientDocToExisting, ingredientToFirestoreDoc, planIngredientImport,
} from "./ingredient-import-plan.mjs";
import { validateIngredientCatalog } from "./ingredients.mjs";

export async function runIngredientImport({ db, catalog, apply, log = console.log }) {
  // Antes de leer nada de Firestore.
  const problems = validateIngredientCatalog(catalog);
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);

  const { existing, updateTimes } = await loadExisting(db, INGREDIENTS_COLLECTION, ingredientDocToExisting);

  const plan = planIngredientImport({ catalog, existing, adminUid: CATALOG_ADMIN_UID });
  log(formatIngredientPlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return { ...plan, versionBumped: false };
  }

  const now = FieldValue.serverTimestamp();
  // Nunca se borra ninguna ficha.
  await commitCatalogOps({
    db, FieldValue, collection: INGREDIENTS_COLLECTION, plan, toDoc: ingredientToFirestoreDoc, updateTimes,
    metaPath: INGREDIENTS_META_PATH, adminUid: CATALOG_ADMIN_UID, now,
  });
  log(`\nAplicado: ${plan.create.length} creados, ${plan.update.length} actualizados.`);
  return { ...plan, versionBumped: plan.create.length + plan.update.length > 0 };
}

await runMain(import.meta.url, async () => {
  const args = parseImportArgs(process.argv.slice(2), { defaultCatalog: "data/catalog/ingredients.json" });
  const app = connectAdmin(args.project);
  const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
  await runIngredientImport({ db: getFirestore(app), catalog, apply: args.apply });
});
