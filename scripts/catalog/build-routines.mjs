// Valida data/catalog/routines.json contra exercises.json y genera data/catalog/routines-review.md.
import { readFile, writeFile } from "node:fs/promises";
import { buildRoutines, indexExercises, renderRoutineReview, validateRoutineCatalog } from "./routines.mjs";

const ROOT = new URL("../../", import.meta.url);
const SOURCE = new URL("data/catalog/routines.json", ROOT);
const EXERCISES = new URL("data/catalog/exercises.json", ROOT);
const REVIEW = new URL("data/catalog/routines-review.md", ROOT);

const catalog = JSON.parse(await readFile(SOURCE, "utf8"));
const exercisesById = indexExercises(JSON.parse(await readFile(EXERCISES, "utf8")));
const problems = validateRoutineCatalog(catalog, exercisesById);
if (problems.length) {
  for (const p of problems) console.error(`- ${p}`);
  console.error(`${problems.length} problemas en data/catalog/routines.json.`);
  process.exit(1);
}
const routines = buildRoutines(catalog, exercisesById);
await writeFile(REVIEW, renderRoutineReview(routines));
console.log(`Catálogo de rutinas: ${routines.length} rutinas. Revisión en data/catalog/routines-review.md`);
