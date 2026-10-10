// Descarga las miniaturas elegidas por el catálogo (solo wger.de/media), las convierte a WebP en
// data/catalog/images/<id>.webp y regenera el catálogo con los hash. Requiere build/catalog/wger-exerciseinfo.json.
import { execFileSync } from "node:child_process";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { buildImages } from "./images.mjs";
import { toCandidate } from "./normalize.mjs";
import { buildCatalog } from "./selection.mjs";
import { fetchBinary } from "./wger-http.mjs";

const ROOT = new URL("../../", import.meta.url);
const OUT = new URL("data/catalog/images/", ROOT);

let raw;
let selection;
try {
  raw = JSON.parse(await readFile(new URL("build/catalog/wger-exerciseinfo.json", ROOT), "utf8"));
  selection = JSON.parse(await readFile(new URL("data/catalog/exercises-selection.json", ROOT), "utf8"));
} catch {
  console.error("Faltan build/catalog/wger-exerciseinfo.json o data/catalog/exercises-selection.json.");
  process.exit(1);
}
const candidates = new Map();
for (const info of raw.results) {
  const { candidate } = toCandidate(info);
  if (candidate) candidates.set(candidate.wgerId, candidate);
}
// Sin imageBytes: las fichas llevan solo la procedencia (sourceUrl), que es lo que hay que descargar.
const { catalog, problems: selectionProblems } = buildCatalog({ candidates, selection, fetchedAt: raw.fetchedAt });
if (selectionProblems.length) {
  console.error("Problemas:\n" + selectionProblems.map((p) => `- ${p}`).join("\n"));
  process.exit(1);
}

await mkdir(OUT, { recursive: true });
const MANIFEST = new URL("sources.json", OUT);
let manifest = {};
try {
  manifest = JSON.parse(await readFile(MANIFEST, "utf8"));
} catch {
  // Sin manifiesto: se vuelve a descargar todo.
}
const r = await buildImages({
  entries: catalog.exercises,
  manifest,
  fetchBinary,
  readExisting: async () => new Set(await readdir(OUT)),
  writeImage: (name, data) => writeFile(new URL(name, OUT), data),
});
await writeFile(MANIFEST, JSON.stringify(r.manifest, null, 2) + "\n");
console.log(`Imágenes: ${r.written.length} nuevas, ${r.skipped.length} ya existentes.`);
if (r.unused.length) console.warn(`Ficheros sin usar (no se borran): ${r.unused.join(", ")}`);
if (r.problems.length) {
  console.error("Problemas con imágenes:\n" + r.problems.map((p) => `- ${p}`).join("\n"));
  process.exit(1);
}
execFileSync(process.execPath, [fileURLToPath(new URL("scripts/catalog/build-catalog.mjs", ROOT))], { stdio: "inherit" });
