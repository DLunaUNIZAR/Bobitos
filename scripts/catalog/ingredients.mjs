// Lista de ingredientes genéricos del catálogo común de Bobitos: constantes, validación y revisión.
// Funciones puras y sin dependencias de red.
import { groupBy, nearDuplicateKey, slug } from "./normalize.mjs";

// Mismas categorías y mismo orden que la interfaz de la app.
export const INGREDIENT_CATEGORIES = [
  "Frutas",
  "Verduras y hortalizas",
  "Carnes",
  "Pescados y mariscos",
  "Lácteos y huevos",
  "Legumbres",
  "Cereales, pasta y arroz",
  "Panadería",
  "Aceites, salsas y condimentos",
  "Especias y hierbas",
  "Frutos secos",
  "Dulces y chocolate",
  "Bebidas",
  "Congelados y otros",
];
export const INGREDIENT_UNITS = ["g", "ml", "ud"];
export const INGREDIENT_NAME_MAX = 120;
const KEYS = ["id", "name", "category", "defaultUnit"];

// Clave de casi duplicado: `nearDuplicateKey` deja «Limones» como `limone`, así que se recorta
// además la «e» final de las palabras de más de 3 letras (limone → limon, pane → pan).
export function ingredientKey(name) {
  return nearDuplicateKey(name)
    .split(" ")
    .filter(Boolean)
    .map((w) => (w.length > 3 && w.endsWith("e") ? w.slice(0, -1) : w))
    .sort()
    .join(" ");
}

export function validateIngredient(entry) {
  const problems = [];
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) return ["La entrada no es un objeto."];
  const extra = Object.keys(entry).filter((k) => !KEYS.includes(k));
  const missing = KEYS.filter((k) => !(k in entry));
  if (extra.length) problems.push(`Claves no permitidas: ${extra.join(", ")}.`);
  if (missing.length) problems.push(`Faltan claves: ${missing.join(", ")}.`);
  const { name } = entry;
  if (typeof name !== "string") {
    problems.push("El campo name tiene que ser texto.");
  } else {
    if (name.length < 1 || name.length > INGREDIENT_NAME_MAX) {
      problems.push(`El campo name tiene que tener entre 1 y ${INGREDIENT_NAME_MAX} caracteres.`);
    }
    if (name !== name.trim() || /\s{2,}/.test(name)) problems.push("El campo name tiene espacios de más.");
    const first = name.trim()[0];
    if (first && !(/\p{L}/u.test(first) && first === first.toLocaleUpperCase("es"))) {
      problems.push("El campo name tiene que empezar por una letra mayúscula.");
    }
    if (entry.id !== slug(name)) problems.push(`El campo id tiene que ser «${slug(name)}», el slug de name.`);
  }
  if (!INGREDIENT_CATEGORIES.includes(entry.category)) problems.push("El campo category no es una categoría válida.");
  if (!INGREDIENT_UNITS.includes(entry.defaultUnit)) problems.push("El campo defaultUnit tiene que ser g, ml o ud.");
  return problems;
}

export function validateIngredientCatalog(catalog) {
  const problems = [];
  if (!catalog || catalog.schemaVersion !== 1) problems.push("schemaVersion tiene que ser 1.");
  const list = catalog?.ingredients;
  if (!Array.isArray(list) || list.length === 0) {
    problems.push("ingredients tiene que ser una lista no vacía.");
    return problems;
  }
  const ids = new Set();
  const keys = new Map();
  for (const entry of list) {
    for (const p of validateIngredient(entry)) problems.push(`${entry?.id ?? entry?.name}: ${p}`);
    if (typeof entry?.id !== "string" || typeof entry?.name !== "string") continue;
    if (ids.has(entry.id)) problems.push(`id repetido: ${entry.id}`);
    else ids.add(entry.id);
    const key = ingredientKey(entry.name);
    if (keys.has(key) && keys.get(key) !== entry.id) problems.push(`casi duplicado: ${keys.get(key)} ~ ${entry.id}`);
    else keys.set(key, entry.id);
  }
  return problems;
}

export function renderIngredientReview(catalog) {
  const list = catalog.ingredients;
  const collator = new Intl.Collator("es");
  const lines = [
    "# Revisión del catálogo de ingredientes",
    "",
    "Generado con `npm run catalog:ingredients` a partir de `data/catalog/ingredients.json`; no se edita a mano.",
    "",
    `Total: ${list.length}`,
    "",
    "| Categoría | Ingredientes |",
    "|---|---|",
  ];
  const byCategory = groupBy(list, (i) => i.category);
  for (const c of INGREDIENT_CATEGORIES) lines.push(`| ${c} | ${(byCategory.get(c) ?? []).length} |`);
  lines.push("");
  for (const c of INGREDIENT_CATEGORIES) {
    const items = [...(byCategory.get(c) ?? [])].sort((a, b) => collator.compare(a.name, b.name));
    if (!items.length) continue;
    lines.push(`## ${c}`, "", "| Nombre | Unidad | id |", "|---|---|---|");
    for (const i of items) lines.push(`| ${i.name} | ${i.defaultUnit} | ${i.id} |`);
    lines.push("");
  }
  return lines.join("\n");
}
