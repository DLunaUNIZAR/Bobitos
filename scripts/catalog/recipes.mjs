// Catálogo común de recetas de Bobitos (lista propia): validación contra el catálogo de ingredientes,
// construcción de los documentos y revisión. Funciones puras y sin dependencias de red.
import { ingredientKey } from "./ingredients.mjs";
import { foldText, groupBy, slug } from "./normalize.mjs";

// Categorías por tipo de plato, en el orden de la revisión.
export const RECIPE_CATEGORIES = [
  "Legumbres", "Arroces", "Pasta", "Carnes", "Pescados", "Verduras", "Ensaladas", "Huevos", "Sopas y cremas",
  "Desayunos y meriendas", "Postres", "Saludables",
];

// Unidades de la lista cerrada, para que «Añadir a la compra» pueda sumar cantidades (ShoppingQuantity en la
// app). Las abreviadas no cambian; las demás van en singular hasta 1 y en plural por encima, como al sumar.
// Es la misma tabla que `tests/fixtures/recipe-units.json`, que también lee el test de la app.
export const RECIPE_UNITS = {
  invariable: ["g", "kg", "ml", "l", "ud"],
  counted: [
    { singular: "cucharada", plural: "cucharadas" },
    { singular: "cucharadita", plural: "cucharaditas" },
    { singular: "pizca", plural: "pizcas" },
    { singular: "diente", plural: "dientes" },
    { singular: "rebanada", plural: "rebanadas" },
    { singular: "hoja", plural: "hojas" },
    { singular: "ramita", plural: "ramitas" },
  ],
};

// Límites: los de las reglas y la app (RecipeLimits, FirestoreRecipeRepository).
export const RECIPE_LIMITS = { title: 120, description: 1000, ingredients: 50, steps: 30, step: 500, servings: 50, minutes: 1440 };
const L = RECIPE_LIMITS;
const RECIPE_KEYS = ["title", "category", "description", "servings", "totalMinutes", "ingredients", "steps"];
const INGREDIENT_KEYS = ["name", "quantity", "unit"];
const INGREDIENT_NAME_MAX = 120;
const WATER = ingredientKey("Agua");
const UNIT_NAMES = [...RECIPE_UNITS.invariable, ...RECIPE_UNITS.counted.flatMap((u) => [u.singular, u.plural])];

const isObject = (x) => x != null && typeof x === "object" && !Array.isArray(x);
const isIntIn = (x, min, max) => Number.isInteger(x) && x >= min && x <= max;
// Texto de 1 a `max` caracteres, sin espacios en los extremos, sin espacios dobles y sin saltos de línea.
const isTextIn = (x, max) =>
  typeof x === "string" && x.length >= 1 && x.length <= max && x === x.trim() && !/\s{2,}|[\r\n\t]/.test(x);

/** Índice clave tolerante (`ingredientKey`) → nombre canónico de `ingredients.json`. */
export function indexIngredients(ingredientCatalog) {
  return new Map(ingredientCatalog.ingredients.map((i) => [ingredientKey(i.name), i.name]));
}

// Distancia de edición (Levenshtein) entre dos textos.
function editDistance(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[b.length];
}

/**
 * El nombre del catálogo más parecido a `name`: el que comparte más palabras de la clave tolerante (en
 * proporción); si ninguno comparte palabras, el más cercano letra a letra. En empate, el primero del catálogo.
 */
export function suggestIngredient(name, index) {
  const words = new Set(ingredientKey(name).split(" ").filter(Boolean));
  let best = null;
  let bestScore = 0;
  for (const [key, canonical] of index) {
    const keyWords = key.split(" ");
    const shared = keyWords.filter((w) => words.has(w)).length;
    const score = shared / (words.size + keyWords.length - shared);
    if (score > bestScore) [best, bestScore] = [canonical, score];
  }
  if (best) return best;
  const folded = foldText(name);
  let bestDistance = Infinity;
  for (const canonical of index.values()) {
    const distance = editDistance(folded, foldText(canonical));
    if (distance < bestDistance) [best, bestDistance] = [canonical, distance];
  }
  return best;
}

// Valor de una cantidad del catálogo: entero, decimal con coma o fracción «a/b» (b ≥ 2), mayor que cero y
// sin ceros a la izquierda. null si no se lee.
function quantityValue(text) {
  const fraction = /^([1-9]\d*)\/([1-9]\d*)$/.exec(text);
  if (fraction) return Number(fraction[2]) >= 2 ? Number(fraction[1]) / Number(fraction[2]) : null;
  if (!/^(0|[1-9]\d*)(,\d+)?$/.test(text)) return null;
  const value = Number(text.replace(",", "."));
  return value > 0 ? value : null;
}

// Problema de cantidad y unidad de un ingrediente, o null. Sin cantidad («al gusto») no lleva unidad.
function amountProblem(entry) {
  const { quantity, unit } = entry;
  if (quantity === null) return "quantity no puede ser null: si va al gusto, quita la clave quantity (y unit).";
  if (unit === null) return "unit no puede ser null: quita la clave unit o pon una unidad de la lista.";
  if (!("quantity" in entry)) return "unit" in entry ? "Una unidad sin cantidad: quita la unidad o pon la cantidad." : null;
  if (typeof quantity !== "string") return `quantity tiene que ser texto («${quantity}» entre comillas).`;
  const value = quantityValue(quantity);
  if (value == null) return `Cantidad «${quantity}» no válida: entero, decimal con coma o fracción, mayor que cero.`;
  if (!("unit" in entry)) return `La cantidad «${quantity}» necesita unidad (${UNIT_NAMES.join(", ")}).`;
  if (RECIPE_UNITS.invariable.includes(unit)) return null;
  const counted = RECIPE_UNITS.counted.find((u) => u.singular === unit || u.plural === unit);
  if (!counted) return `Unidad «${unit}» fuera de la lista (${UNIT_NAMES.join(", ")}).`;
  const expected = value <= 1 ? counted.singular : counted.plural;
  return unit === expected ? null : `Con «${quantity}» la unidad es «${expected}».`;
}

function validateRecipeIngredient(entry, index) {
  if (!isObject(entry)) return ["El ingrediente no es un objeto."];
  const problems = [];
  const extra = Object.keys(entry).filter((k) => !INGREDIENT_KEYS.includes(k));
  if (extra.length) problems.push(`Claves no permitidas: ${extra.join(", ")}.`);
  if (!isTextIn(entry.name, INGREDIENT_NAME_MAX)) return [...problems, "name tiene que ser texto, sin espacios de más."];
  const key = ingredientKey(entry.name);
  if (key === WATER) problems.push("El agua no se lista.");
  else if (!index.has(key)) {
    problems.push(`«${entry.name}» no está en ingredients.json (¿«${suggestIngredient(entry.name, index)}»?).`);
  }
  const amount = amountProblem(entry);
  if (amount) problems.push(amount);
  return problems;
}

function validateIngredientList(ingredients, index) {
  if (!Array.isArray(ingredients) || ingredients.length < 1 || ingredients.length > L.ingredients) {
    return [`ingredients tiene que ser una lista de 1 a ${L.ingredients} ingredientes.`];
  }
  const problems = [];
  const seen = new Set();
  ingredients.forEach((e, i) => {
    const label = `ingrediente ${i + 1} (${e?.name ?? "?"})`;
    for (const p of validateRecipeIngredient(e, index)) problems.push(`${label}: ${p}`);
    if (typeof e?.name !== "string") return;
    const key = ingredientKey(e.name);
    if (seen.has(key)) problems.push(`${label}: repetido.`);
    seen.add(key);
  });
  return problems;
}

function validateSteps(steps) {
  if (!Array.isArray(steps) || steps.length < 1 || steps.length > L.steps) {
    return [`steps tiene que ser una lista de 1 a ${L.steps} pasos.`];
  }
  return steps.flatMap((s, i) =>
    isTextIn(s, L.step) ? [] : [`paso ${i + 1}: entre 1 y ${L.step} caracteres, sin espacios de más ni saltos de línea.`]);
}

function validateRecipe(recipe, index) {
  if (!isObject(recipe)) return ["La receta no es un objeto."];
  const problems = [];
  const extra = Object.keys(recipe).filter((k) => !RECIPE_KEYS.includes(k));
  const missing = RECIPE_KEYS.filter((k) => !(k in recipe));
  if (extra.length) problems.push(`Claves no permitidas: ${extra.join(", ")}.`);
  if (missing.length) problems.push(`Faltan claves: ${missing.join(", ")}.`);
  const { title, category, description, servings, totalMinutes } = recipe;
  if (!isTextIn(title, L.title)) problems.push(`title tiene que tener entre 1 y ${L.title} caracteres, sin espacios de más.`);
  else if (!/\p{Lu}/u.test(title[0])) problems.push("title tiene que empezar por una letra mayúscula.");
  if (!RECIPE_CATEGORIES.includes(category)) problems.push(`category tiene que ser una de: ${RECIPE_CATEGORIES.join(", ")}.`);
  if (!isTextIn(description, L.description)) {
    problems.push(`description tiene que tener entre 1 y ${L.description} caracteres, sin espacios de más.`);
  }
  if (!isIntIn(servings, 1, L.servings)) problems.push(`servings tiene que ser un entero entre 1 y ${L.servings}.`);
  if (!isIntIn(totalMinutes, 1, L.minutes)) problems.push(`totalMinutes tiene que ser un entero entre 1 y ${L.minutes}.`);
  return [...problems, ...validateIngredientList(recipe.ingredients, index), ...validateSteps(recipe.steps)];
}

export function validateRecipeCatalog(catalog, index) {
  const problems = [];
  if (!catalog || catalog.schemaVersion !== 1) problems.push("schemaVersion tiene que ser 1.");
  const list = catalog?.recipes;
  if (!Array.isArray(list) || list.length === 0) {
    problems.push("recipes tiene que ser una lista no vacía.");
    return problems;
  }
  const ids = new Set();
  list.forEach((r, i) => {
    for (const p of validateRecipe(r, index)) problems.push(`${r?.title ?? `#${i + 1}`}: ${p}`);
    if (typeof r?.title !== "string") return;
    const id = slug(r.title);
    if (ids.has(id)) problems.push(`id repetido: ${id}`);
    else ids.add(id);
  });
  return problems;
}

/**
 * Recetas listas para importar, en el orden del fichero, con la forma que escribe la app
 * (FirestoreRecipeRepository): cada ingrediente `{name, quantity, unit}` con el nombre canónico del catálogo y
 * `null` si falta la cantidad; los pasos, como lista. Exige un catálogo ya validado.
 */
export function buildRecipes(catalog, index) {
  return catalog.recipes.map((r) => ({
    id: slug(r.title),
    title: r.title,
    description: r.description,
    category: r.category,
    servings: r.servings,
    totalMinutes: r.totalMinutes,
    ingredients: r.ingredients.map((i) => ({
      name: index.get(ingredientKey(i.name)),
      quantity: i.quantity ?? null,
      unit: i.unit ?? null,
    })),
    steps: [...r.steps],
  }));
}

const servingsText = (n) => (n === 1 ? "1 ración" : `${n} raciones`);
const amountText = (i) => (i.quantity == null ? "al gusto" : `${i.quantity} ${i.unit}`);

export function renderRecipeReview(recipes) {
  const byCategory = groupBy(recipes, (r) => r.category);
  const lines = [
    "# Revisión del catálogo de recetas",
    "",
    "Generado con `npm run catalog:recipes` a partir de `data/catalog/recipes.json`; no se edita a mano.",
    "",
    `Total: ${recipes.length} recetas.`,
    "",
    "| Categoría | Recetas |",
    "|---|---|",
    ...RECIPE_CATEGORIES.map((c) => `| ${c} | ${(byCategory.get(c) ?? []).length} |`),
    "",
  ];
  for (const c of RECIPE_CATEGORIES) {
    const list = byCategory.get(c);
    if (!list) continue;
    lines.push(`## ${c}`, "");
    for (const r of list) {
      lines.push(
        `### ${r.title}`, "",
        `\`${r.id}\` · ${servingsText(r.servings)} · ${r.totalMinutes} min`, "",
        r.description, "",
        "**Ingredientes**", "",
        ...r.ingredients.map((i) => `- ${i.name} — ${amountText(i)}`), "",
        "**Preparación**", "",
        ...r.steps.map((s, n) => `${n + 1}. ${s}`), "",
      );
    }
  }
  return lines.join("\n");
}
