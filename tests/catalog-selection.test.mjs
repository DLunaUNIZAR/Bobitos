import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { toCandidate } from "../scripts/catalog/normalize.mjs";
import { buildCatalog, validateEntry } from "../scripts/catalog/selection.mjs";

const sample = JSON.parse(
  readFileSync(new URL("./fixtures/wger-exerciseinfo.sample.json", import.meta.url), "utf8"),
);
const candidates = new Map();
for (const info of sample) {
  const { candidate } = toCandidate(info);
  if (candidate) candidates.set(candidate.wgerId, candidate);
}
const run = (selection) => buildCatalog({ candidates, selection, fetchedAt: "2026-10-10T00:00:00Z" });

test("buildCatalog aplica correcciones y recalcula el id", () => {
  const { catalog, problems } = run({
    include: [{ wgerId: 257, name: "Sentadilla frontal con barra", equipment: ["BANCO", "BARRA"] }],
    exclude: [],
  });
  assert.deepEqual(problems, []);
  const [e] = catalog.exercises;
  assert.equal(e.id, "sentadilla-frontal-con-barra");
  assert.equal(e.name, "Sentadilla frontal con barra");
  assert.deepEqual(e.equipment, ["BARRA", "BANCO"]);
  assert.equal(catalog.schemaVersion, 1);
  assert.equal(catalog.license, "CC-BY-SA-4.0");
});

test("buildCatalog falla con id inexistente, slug repetido, enum inválido o seleccionado+excluido", () => {
  assert.ok(run({ include: [{ wgerId: 1 }], exclude: [] }).problems.length > 0);
  assert.ok(run({ include: [{ wgerId: 900002 }], exclude: [] }).problems.length > 0);
  assert.ok(run({ include: [{ wgerId: 257 }, { wgerId: 238, name: "Sentadilla frontal" }], exclude: [] }).problems.length > 0);
  assert.ok(run({ include: [{ wgerId: 257, type: "NOPE" }], exclude: [] }).problems.length > 0);
  assert.ok(run({ include: [{ wgerId: 257, equipment: ["XXX"] }], exclude: [] }).problems.length > 0);
  assert.ok(run({ include: [{ wgerId: 257 }], exclude: [{ wgerId: 257, reason: "x" }] }).problems.length > 0);
});

test("buildCatalog avisa de casi duplicados sin fallar", () => {
  const r = run({ include: [{ wgerId: 245 }, { wgerId: 211 }], exclude: [] });
  assert.deepEqual(r.problems, []);
  assert.ok(r.warnings.some((w) => /duplicad/i.test(w)));
});

test("buildCatalog es determinista", () => {
  const sel = { include: [{ wgerId: 254 }, { wgerId: 257 }, { wgerId: 238 }], exclude: [] };
  const a = run(sel);
  const b = run({ include: [...sel.include].reverse(), exclude: [] });
  assert.equal(JSON.stringify(a.catalog), JSON.stringify(b.catalog));
  const ids = a.catalog.exercises.map((e) => e.id);
  assert.deepEqual(ids, [...ids].sort());
});

test("validateEntry rechaza nombre >120, grupo >60, descripción >2000 y url que no es de wger", () => {
  const ok = {
    id: "press",
    name: "Press",
    type: "PESO_LIBRE",
    muscleGroup: "Pecho",
    description: "Descripción válida de más de veinte caracteres.",
    equipment: ["BARRA"],
    source: { provider: "wger", id: 1, author: "a", license: "CC-BY-SA-4.0", url: "https://wger.de/es/exercise/1/view/" },
  };
  assert.deepEqual(validateEntry(ok), []);
  assert.ok(validateEntry({ ...ok, name: "x".repeat(121), id: "x" }).length > 0);
  assert.ok(validateEntry({ ...ok, muscleGroup: "x".repeat(61) }).length > 0);
  assert.ok(validateEntry({ ...ok, description: "x".repeat(2001) }).length > 0);
  assert.ok(validateEntry({ ...ok, source: { ...ok.source, url: "https://evil.example/1" } }).length > 0);
  assert.ok(validateEntry({ ...ok, id: "otro" }).length > 0);
});

test("buildCatalog avisa de «Press banca» frente a «Press de banca con barra»", () => {
  const r = run({ include: [{ wgerId: 257, name: "Press banca" }, { wgerId: 238, name: "Press de banca con barra" }], exclude: [] });
  assert.deepEqual(r.problems, []);
  assert.ok(r.warnings.some((w) => /duplicad/i.test(w) && w.includes("Press banca") && w.includes("Press de banca con barra")));
});

test("validateEntry exige source.id entero > 0", () => {
  const ok = {
    id: "press",
    name: "Press",
    type: "PESO_LIBRE",
    muscleGroup: "Pecho",
    description: "Descripción válida de más de veinte caracteres.",
    equipment: ["BARRA"],
    source: { provider: "wger", id: 1, author: "a", license: "CC-BY-SA-4.0", url: "https://wger.de/es/exercise/1/view/" },
  };
  assert.deepEqual(validateEntry(ok), []);
  for (const bad of [0, -1, 1.5, "7", undefined]) {
    assert.ok(validateEntry({ ...ok, source: { ...ok.source, id: bad } }).length > 0, `id ${bad}`);
  }
});
