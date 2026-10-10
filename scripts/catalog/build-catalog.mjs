// Sin argumentos: genera data/catalog/exercises.json y exercises-review.md a partir de la selección.
// Con --candidates: genera build/catalog/candidates.{md,json} para elegir.
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { toCandidate } from "./normalize.mjs";
import { buildCatalog, renderCandidates, renderReview, scoreCandidate } from "./selection.mjs";

const ROOT = new URL("../../", import.meta.url);
const RAW = new URL("build/catalog/wger-exerciseinfo.json", ROOT);
const SELECTION = new URL("data/catalog/exercises-selection.json", ROOT);

let raw;
try {
  raw = JSON.parse(await readFile(RAW, "utf8"));
} catch {
  console.error("Falta build/catalog/wger-exerciseinfo.json: ejecuta antes `npm run catalog:fetch`.");
  process.exit(1);
}

const candidates = new Map();
const rejected = [];
for (const info of raw.results) {
  const { candidate, rejected: r } = toCandidate(info);
  if (candidate) candidates.set(candidate.wgerId, candidate);
  else rejected.push(r);
}

if (process.argv.includes("--candidates")) {
  const outDir = new URL("build/catalog/", ROOT);
  await mkdir(outDir, { recursive: true });
  const list = [...candidates.values()];
  await writeFile(new URL("candidates.md", outDir), renderCandidates(list, rejected));
  await writeFile(
    new URL("candidates.json", outDir),
    JSON.stringify(list.map((c) => ({ ...c, score: scoreCandidate(c) })), null, 2) + "\n",
  );
  const byReason = {};
  for (const r of rejected) byReason[r.reason] = (byReason[r.reason] ?? 0) + 1;
  console.log(`${list.length} candidatos; descartados: ${JSON.stringify(byReason)}`);
  process.exit(0);
}

let selection;
try {
  selection = JSON.parse(await readFile(SELECTION, "utf8"));
} catch {
  console.error("Falta data/catalog/exercises-selection.json.");
  process.exit(1);
}
const { catalog, problems, warnings } = buildCatalog({ candidates, selection, fetchedAt: raw.fetchedAt });
if (problems.length) {
  console.error("Problemas:\n" + problems.map((p) => `- ${p}`).join("\n"));
  process.exit(1);
}
const n = catalog.exercises.length;
if (n < 200 || n > 300) console.warn(`Aviso: ${n} ejercicios, fuera del rango 200-300.`);
const notes = selection.notes ?? [];
const excluded = (selection.exclude ?? []).map((x) => ({ ...x, name: candidates.get(x.wgerId)?.name }));
await writeFile(new URL("data/catalog/exercises.json", ROOT), JSON.stringify(catalog, null, 2) + "\n");
await writeFile(new URL("data/catalog/exercises-review.md", ROOT), renderReview(catalog, warnings, notes, excluded));
for (const w of warnings) console.warn(`Aviso: ${w}`);
console.log(`Catálogo con ${n} ejercicios.`);
