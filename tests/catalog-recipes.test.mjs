import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import {
  RECIPE_CATEGORIES, RECIPE_LIMITS, RECIPE_UNITS, buildRecipes, indexIngredients, renderRecipeReview,
  suggestIngredient, validateRecipeCatalog,
} from "../scripts/catalog/recipes.mjs";

const units = JSON.parse(await readFile(new URL("./fixtures/recipe-units.json", import.meta.url), "utf8"));
const index = indexIngredients({
  ingredients: ["Lentejas", "Lentejas pardinas", "Lentejas cocidas", "Tomate", "Ajo", "Sal", "Agua", "Huevos", "Pimentón dulce"]
    .map((name) => ({ name })),
});
const recipe = (over = {}) => ({
  title: "Lentejas estofadas",
  category: "Legumbres",
  description: "Las de toda la vida.",
  servings: 4,
  totalMinutes: 60,
  ingredients: [
    { name: "Lentejas pardinas", quantity: "300", unit: "g" },
    { name: "Ajos", quantity: "2", unit: "dientes" },
    { name: "Sal" },
  ],
  steps: ["Pon las lentejas a cocer.", "Sirve."],
  ...over,
});
const cat = (...recipes) => ({ schemaVersion: 1, recipes });
const problems = (...recipes) => validateRecipeCatalog(cat(...recipes), index);
const withIngredient = (i) => problems(recipe({ ingredients: [i] }));
const ajo = (quantity, unit) => withIngredient({ name: "Ajo", quantity, unit });

test("la tabla de unidades es la compartida con la app", () => {
  assert.deepEqual(RECIPE_UNITS, units);
});

test("una receta correcta no da problemas y se construye con el id y los nombres del catálogo", () => {
  assert.deepEqual(problems(recipe()), []);
  const [built] = buildRecipes(cat(recipe()), index);
  assert.deepEqual(built, {
    id: "lentejas-estofadas",
    title: "Lentejas estofadas",
    description: "Las de toda la vida.",
    category: "Legumbres",
    servings: 4,
    totalMinutes: 60,
    ingredients: [
      { name: "Lentejas pardinas", quantity: "300", unit: "g" },
      { name: "Ajo", quantity: "2", unit: "dientes" },
      { name: "Sal", quantity: null, unit: null },
    ],
    steps: ["Pon las lentejas a cocer.", "Sirve."],
  });
});

test("la cantidad se escribe como texto, nunca como número", () => {
  assert.match(ajo(2, "dientes").join("\n"), /texto/);
  const [built] = buildRecipes(cat(recipe()), index);
  assert.ok(built.ingredients.every((i) => i.quantity === null || typeof i.quantity === "string"));
});

test("rechaza un ingrediente que no está en el catálogo y sugiere el más parecido", () => {
  assert.match(
    withIngredient({ name: "Lentejas rojas", quantity: "300", unit: "g" }).join("\n"),
    /«Lentejas rojas» no está en ingredients\.json \(¿«Lentejas»\?\)/,
  );
  assert.equal(suggestIngredient("Pimentón picante", index), "Pimentón dulce");
  assert.equal(suggestIngredient("Ajoo", index), "Ajo");
});

test("el agua no se lista", () => {
  assert.match(withIngredient({ name: "Agua", quantity: "1", unit: "l" }).join("\n"), /El agua no se lista/);
});

test("rechaza un ingrediente repetido por clave tolerante", () => {
  const twice = recipe({ ingredients: [{ name: "Tomate", quantity: "2", unit: "ud" }, { name: "Tomates", quantity: "1", unit: "ud" }] });
  assert.match(problems(twice).join("\n"), /repetido/);
});

test("la unidad es de la lista cerrada, obligatoria con cantidad y concordada en número", () => {
  for (const ok of [["1", "diente"], ["2", "dientes"], ["1/2", "cucharadita"], ["1,5", "cucharadas"], ["1,5", "kg"], ["2", "ud"], ["1", "pizca"]]) {
    assert.deepEqual(ajo(...ok), [], ok.join(" "));
  }
  for (const bad of [["1", "vaso"], ["1", "dientes"], ["2", "diente"], ["1/2", "cucharaditas"], ["2", "uds"]]) {
    assert.notDeepEqual(ajo(...bad), [], bad.join(" "));
  }
  assert.match(withIngredient({ name: "Ajo", quantity: "300" }).join("\n"), /necesita unidad/);
  assert.match(withIngredient({ name: "Ajo", unit: "g" }).join("\n"), /unidad sin cantidad/);
});

test("la cantidad es un entero, un decimal con coma o una fracción, mayor que cero", () => {
  for (const ok of ["3", "0,5", "3/4", "3/2"]) assert.deepEqual(ajo(ok, "g"), [], ok);
  for (const bad of ["0", "1.5", "1/0", "1/1", "0/2", "dos", "1 1/2", "-1", "05", ""]) assert.notDeepEqual(ajo(bad, "g"), [], bad);
});

test("un null en quantity o en unit pide quitar la clave", () => {
  const noQuantity = withIngredient({ name: "Ajo", quantity: null }).join("\n");
  assert.match(noQuantity, /quantity no puede ser null: si va al gusto, quita la clave quantity/);
  assert.doesNotMatch(noQuantity, /entre comillas/);
  assert.match(withIngredient({ name: "Ajo", quantity: "2", unit: null }).join("\n"), /unit no puede ser null/);
  assert.match(withIngredient({ name: "Ajo", unit: null }).join("\n"), /unit no puede ser null/);
});

test("respeta categorías y límites", () => {
  const L = RECIPE_LIMITS;
  const steps = (n) => Array.from({ length: n }, (_, i) => `Paso ${i + 1}.`);
  const many = Array.from({ length: L.ingredients + 1 }, () => ({ name: "Sal" }));
  for (const over of [
    { category: "Tapas" },
    { title: "x".repeat(L.title + 1) },
    { description: "x".repeat(L.description + 1) },
    { description: "" },
    { servings: 0 }, { servings: L.servings + 1 }, { servings: 2.5 }, { servings: "4" },
    { totalMinutes: 0 }, { totalMinutes: L.minutes + 1 },
    { ingredients: [] }, { ingredients: many },
    { steps: [] }, { steps: steps(L.steps + 1) }, { steps: ["x".repeat(L.step + 1)] },
    { steps: ["Pon  las lentejas."] }, { steps: [" Pon las lentejas."] }, { steps: ["Pon las\nlentejas."] },
  ]) {
    assert.notDeepEqual(problems(recipe(over)), [], JSON.stringify(over).slice(0, 60));
  }
  const { description: _d, ...withoutDescription } = recipe();
  assert.match(problems(withoutDescription).join("\n"), /Faltan claves: description/);
  assert.deepEqual(problems(recipe({ steps: steps(L.steps), servings: L.servings, totalMinutes: L.minutes })), []);
});

test("rechaza claves desconocidas, títulos mal escritos y títulos repetidos", () => {
  assert.notDeepEqual(problems(recipe({ foto: "x" })), []);
  assert.notDeepEqual(withIngredient({ name: "Sal", nota: "x" }), []);
  assert.notDeepEqual(problems(recipe({ title: "lentejas" })), []);
  assert.notDeepEqual(problems(recipe({ title: "Lentejas  estofadas" })), []);
  assert.match(problems(recipe(), recipe({ title: "Léntejas estofadas" })).join("\n"), /id repetido: lentejas-estofadas/);
});

test("rechaza un catálogo sin schemaVersion 1 o sin recetas", () => {
  assert.notDeepEqual(validateRecipeCatalog({ schemaVersion: 2, recipes: [recipe()] }, index), []);
  assert.notDeepEqual(validateRecipeCatalog({ schemaVersion: 1, recipes: [] }, index), []);
});

test("la revisión enseña las recetas por categorías, con raciones, tiempo, ingredientes y pasos", () => {
  const tortilla = recipe({
    title: "Tortilla", category: "Huevos", servings: 1, totalMinutes: 20,
    ingredients: [{ name: "Huevos", quantity: "2", unit: "ud" }], steps: ["Bate.", "Cuaja."],
  });
  const md = renderRecipeReview(buildRecipes(cat(tortilla, recipe()), index));
  assert.match(md, /^# Revisión del catálogo de recetas/);
  assert.match(md, /Total: 2 recetas\./);
  assert.match(md, /\| Legumbres \| 1 \|/);
  assert.match(md, /\| Pasta \| 0 \|/);
  assert.doesNotMatch(md, /## Pasta/);
  assert.ok(md.indexOf("## Legumbres") < md.indexOf("## Huevos"), "orden de RECIPE_CATEGORIES");
  assert.match(md, /### Lentejas estofadas\n\n`lentejas-estofadas` · 4 raciones · 60 min\n\nLas de toda la vida\./);
  assert.match(md, /- Ajo — 2 dientes/);
  assert.match(md, /- Sal — al gusto/);
  assert.match(md, /\*\*Preparación\*\*\n\n1\. Pon las lentejas a cocer\.\n2\. Sirve\./);
  assert.match(md, /`tortilla` · 1 ración · 20 min/);
  assert.equal(RECIPE_CATEGORIES.length, 12);
});

test("el recipes.json real es válido: 160 recetas en las 12 categorías y cantidades como texto", async () => {
  const real = JSON.parse(await readFile(new URL("../data/catalog/recipes.json", import.meta.url), "utf8"));
  const realIndex = indexIngredients(JSON.parse(await readFile(new URL("../data/catalog/ingredients.json", import.meta.url), "utf8")));
  assert.deepEqual(validateRecipeCatalog(real, realIndex), []);
  const built = buildRecipes(real, realIndex);
  assert.equal(built.length, 160);
  assert.deepEqual(
    Object.fromEntries(RECIPE_CATEGORIES.map((c) => [c, built.filter((r) => r.category === c).length])),
    {
      Legumbres: 12, Arroces: 9, Pasta: 11, Carnes: 16, Pescados: 15, Verduras: 13, Ensaladas: 10, Huevos: 9,
      "Sopas y cremas": 11, "Desayunos y meriendas": 16, Postres: 14, Saludables: 24,
    },
  );
  assert.equal(built.reduce((n, r) => n + r.ingredients.length, 0), 1468);
  assert.ok(built.every((r) => r.ingredients.every((i) => i.quantity === null || typeof i.quantity === "string")));
});

test("la lasaña del catálogo real salpimienta la carne", async () => {
  const real = JSON.parse(await readFile(new URL("../data/catalog/recipes.json", import.meta.url), "utf8"));
  const lasagna = real.recipes.find((r) => r.title === "Lasaña de carne");
  assert.ok(lasagna.steps[1].endsWith("unos 5 minutos. Salpimienta."));
  assert.ok(!JSON.stringify(real).includes("Sala y pimienta"));
});
