// Valida data/catalog/recipes.json contra ingredients.json y genera data/catalog/recipes-review.md.
import { readFile, writeFile } from "node:fs/promises";
import { buildRecipes, indexIngredients, renderRecipeReview, validateRecipeCatalog } from "./recipes.mjs";

const ROOT = new URL("../../", import.meta.url);
const SOURCE = new URL("data/catalog/recipes.json", ROOT);
const INGREDIENTS = new URL("data/catalog/ingredients.json", ROOT);
const REVIEW = new URL("data/catalog/recipes-review.md", ROOT);

const catalog = JSON.parse(await readFile(SOURCE, "utf8"));
const index = indexIngredients(JSON.parse(await readFile(INGREDIENTS, "utf8")));
const problems = validateRecipeCatalog(catalog, index);
if (problems.length) {
  for (const p of problems) console.error(`- ${p}`);
  console.error(`${problems.length} problemas en data/catalog/recipes.json.`);
  process.exit(1);
}
const recipes = buildRecipes(catalog, index);
await writeFile(REVIEW, renderRecipeReview(recipes));
console.log(`Catálogo de recetas: ${recipes.length} recetas. Revisión en data/catalog/recipes-review.md`);
