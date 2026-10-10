import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import {
  INGREDIENT_CATEGORIES, INGREDIENT_UNITS, ingredientKey, renderIngredientReview, validateIngredient, validateIngredientCatalog,
} from "../scripts/catalog/ingredients.mjs";
import { slug } from "../scripts/catalog/normalize.mjs";

const ing = (over = {}) => ({ id: "chocolate-negro", name: "Chocolate negro", category: "Dulces y chocolate", defaultUnit: "g", ...over });
const cat = (...ingredients) => ({ schemaVersion: 1, ingredients });

test("hay 14 categorías y tres unidades", () => {
  assert.equal(INGREDIENT_CATEGORIES.length, 14);
  assert.equal(INGREDIENT_CATEGORIES[0], "Frutas");
  assert.equal(INGREDIENT_CATEGORIES[13], "Congelados y otros");
  assert.deepEqual(INGREDIENT_UNITS, ["g", "ml", "ud"]);
});

test("ingredientKey junta singular y plural, tildes y mayúsculas, y separa variantes", () => {
  assert.equal(ingredientKey("Limón"), ingredientKey("Limones"));
  assert.equal(ingredientKey("Tomate"), ingredientKey("tomates"));
  assert.equal(ingredientKey("Pan"), ingredientKey("Panes"));
  assert.equal(ingredientKey("Pimiento rojo"), ingredientKey("Pimientos rojos"));
  assert.notEqual(ingredientKey("Leche entera"), ingredientKey("Leche semidesnatada"));
  assert.notEqual(ingredientKey("Pan de molde"), ingredientKey("Pan"));
});

test("una entrada correcta no tiene problemas", () => {
  assert.deepEqual(validateIngredient(ing()), []);
});

test("el id tiene que ser el slug del nombre", () => {
  assert.equal(validateIngredient(ing({ id: "chocolate" })).length, 1);
});

test("rechaza nombre vacío, largo, con espacios de más o en minúscula inicial", () => {
  assert.ok(validateIngredient(ing({ name: "", id: "" })).length > 0);
  const largo = "A".repeat(121);
  assert.ok(validateIngredient(ing({ name: largo, id: slug(largo) })).length > 0);
  assert.ok(validateIngredient(ing({ name: " Chocolate negro", id: "chocolate-negro" })).length > 0);
  assert.ok(validateIngredient(ing({ name: "Chocolate  negro", id: "chocolate-negro" })).length > 0);
  assert.ok(validateIngredient(ing({ name: "chocolate negro" })).length > 0);
});

test("rechaza categoría o unidad fuera de la lista y claves de más", () => {
  assert.ok(validateIngredient(ing({ category: "Dulces" })).length > 0);
  assert.ok(validateIngredient(ing({ defaultUnit: "kg" })).length > 0);
  assert.ok(validateIngredient(ing({ brand: "Valor" })).length > 0);
});

test("el catálogo rechaza ids repetidos y casi duplicados", () => {
  assert.ok(validateIngredientCatalog(cat(ing(), ing())).some((p) => p.includes("chocolate-negro")));
  const plural = ing({ id: "chocolates-negros", name: "Chocolates negros" });
  assert.ok(validateIngredientCatalog(cat(ing(), plural)).some((p) => p.includes("chocolates-negros")));
  const tilde = ing({ id: "limon", name: "Limón", category: "Frutas", defaultUnit: "ud" });
  const otra = ing({ id: "limones", name: "Limones", category: "Frutas", defaultUnit: "ud" });
  assert.ok(validateIngredientCatalog(cat(tilde, otra)).length > 0);
});

test("el catálogo exige schemaVersion 1 y una lista", () => {
  assert.ok(validateIngredientCatalog({ ingredients: [ing()] }).length > 0);
  assert.ok(validateIngredientCatalog({ schemaVersion: 1 }).length > 0);
});

test("la revisión agrupa por categoría en orden y cuenta cada una", () => {
  const md = renderIngredientReview(cat(
    ing(),
    ing({ id: "manzana", name: "Manzana", category: "Frutas", defaultUnit: "ud" }),
  ));
  assert.ok(md.indexOf("## Frutas") < md.indexOf("## Dulces y chocolate"));
  assert.match(md, /Manzana/);
  assert.match(md, /\| Frutas \| 1 \|/);
  assert.match(md, /Total: 2/);
});

test("data/catalog/ingredients.json es válido, usa las 14 categorías y tiene entre 300 y 400", async () => {
  const data = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
  assert.deepEqual(validateIngredientCatalog(data), []);
  assert.ok(data.ingredients.length >= 300 && data.ingredients.length <= 400, `hay ${data.ingredients.length}`);
  assert.deepEqual(new Set(data.ingredients.map((i) => i.category)), new Set(INGREDIENT_CATEGORIES));
});

test("data/catalog/ingredients-review.md está al día", async () => {
  const data = JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8"));
  const md = await readFile(new URL("../data/catalog/ingredients-review.md", import.meta.url), "utf8");
  assert.equal(md, renderIngredientReview(data));
});
