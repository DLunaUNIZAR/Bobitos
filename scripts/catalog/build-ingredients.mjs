// Valida data/catalog/ingredients.json y genera data/catalog/ingredients-review.md.
import { readFile, writeFile } from "node:fs/promises";
import { renderIngredientReview, validateIngredientCatalog } from "./ingredients.mjs";

const ROOT = new URL("../../", import.meta.url);
const SOURCE = new URL("data/catalog/ingredients.json", ROOT);
const REVIEW = new URL("data/catalog/ingredients-review.md", ROOT);

const catalog = JSON.parse(await readFile(SOURCE, "utf8"));
const problems = validateIngredientCatalog(catalog);
if (problems.length) {
  for (const p of problems) console.error(`- ${p}`);
  console.error(`${problems.length} problemas en data/catalog/ingredients.json.`);
  process.exit(1);
}
await writeFile(REVIEW, renderIngredientReview(catalog));
console.log(`Catálogo de ingredientes: ${catalog.ingredients.length} ingredientes. Revisión en data/catalog/ingredients-review.md`);
