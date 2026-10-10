// Importa data/catalog/ingredients.json a Firestore (colección ingredients) con firebase-admin.
// Idempotente; nunca borra.
// Uso: node scripts/catalog/import-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID } from "./import-plan.mjs";
import { commitCatalogOps } from "./import-core.mjs";
import { connectAdmin, parseImportArgs } from "./admin-cli.mjs";
import {
  INGREDIENTS_META_PATH, formatIngredientPlan, ingredientDocToExisting, ingredientToFirestoreDoc, planIngredientImport,
} from "./ingredient-import-plan.mjs";
import { validateIngredientCatalog } from "./ingredients.mjs";

const toMillis = (t) => (t && typeof t.toMillis === "function" ? t.toMillis() : null);

export async function runIngredientImport({ db, catalog, apply, log = console.log }) {
  // Antes de leer nada de Firestore.
  const problems = validateIngredientCatalog(catalog);
  if (problems.length) throw new Error(`Catálogo inválido:\n${problems.join("\n")}`);

  const snap = await db.collection("ingredients").get();
  const updateTimes = new Map();
  const existing = snap.docs.map((d) => {
    const data = d.data();
    updateTimes.set(d.id, d.updateTime);
    return ingredientDocToExisting(d.id, data, {
      updatedAtMillis: toMillis(data.updatedAt),
      importedAtMillis: toMillis(data.source?.importedAt),
    });
  });

  const plan = planIngredientImport({ catalog, existing, adminUid: CATALOG_ADMIN_UID });
  log(formatIngredientPlan(plan));
  if (!apply) {
    log("\nSimulación: no se ha escrito nada. Usa --apply para aplicar.");
    return { ...plan, versionBumped: false };
  }

  const now = FieldValue.serverTimestamp();
  // Nunca se borra ninguna ficha.
  const ops = [
    ...plan.create.map((e) => ({ kind: "create", e })),
    ...plan.update.map((e) => ({ kind: "update", e })),
  ];
  await commitCatalogOps({
    db, collection: "ingredients", ops, toDoc: ingredientToFirestoreDoc, updateTimes,
    metaPath: INGREDIENTS_META_PATH, adminUid: CATALOG_ADMIN_UID, now,
  });
  log(`\nAplicado: ${plan.create.length} creados, ${plan.update.length} actualizados.`);
  return { ...plan, versionBumped: ops.length > 0 };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const args = parseImportArgs(process.argv.slice(2), { defaultCatalog: "data/catalog/ingredients.json" });
    const app = connectAdmin(args.project);
    const catalog = JSON.parse(readFileSync(args.catalog, "utf8"));
    await runIngredientImport({ db: getFirestore(app), catalog, apply: args.apply });
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
