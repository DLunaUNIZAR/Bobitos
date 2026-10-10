import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import test from "node:test";
import { toCandidate } from "../scripts/catalog/normalize.mjs";
import { buildCatalog, renderReview, validateEntry } from "../scripts/catalog/selection.mjs";

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

const baseEntry = () => ({
  id: "press",
  name: "Press",
  type: "PESO_LIBRE",
  muscleGroup: "Pecho",
  description: "Descripción válida de más de veinte caracteres.",
  equipment: ["BARRA"],
  source: { provider: "wger", id: 1, author: "a", license: "CC-BY-SA-4.0", url: "https://wger.de/es/exercise/1/view/" },
});

test("validateEntry acepta measure REPS/SECONDS y rechaza otro valor o SECONDS en CARDIO", () => {
  const ok = baseEntry();
  assert.deepEqual(validateEntry({ ...ok, measure: "REPS" }), []);
  assert.deepEqual(validateEntry({ ...ok, measure: "SECONDS" }), []);
  for (const t of ["MAQUINA", "PESO_LIBRE", "PESO_CORPORAL"]) {
    assert.deepEqual(validateEntry({ ...ok, type: t, measure: "SECONDS" }), [], t);
  }
  assert.ok(validateEntry({ ...ok, measure: "MINUTES" }).length > 0);
  assert.ok(validateEntry({ ...ok, measure: null }).length > 0);
  assert.ok(validateEntry({ ...ok, type: "CARDIO", measure: "SECONDS" }).length > 0);
  assert.ok(validateEntry({ ...ok, type: "OTROS", measure: "SECONDS" }).length > 0);
  assert.deepEqual(validateEntry({ ...ok, type: "CARDIO", measure: "REPS" }), []);
});

test("validateEntry acepta image {hash, author, license, sourceUrl} y rechaza url, hash no hexadecimal o sourceUrl ajena", () => {
  const ok = baseEntry();
  const img = { hash: "a".repeat(64), author: "Ana", license: "CC-BY-SA-4.0", sourceUrl: "https://wger.de/media/exercise-images/1/a.png" };
  assert.deepEqual(validateEntry({ ...ok, image: img }), []);
  assert.deepEqual(validateEntry({ ...ok, image: null }), []);
  const { author: _a, ...noAuthor } = img;
  assert.deepEqual(validateEntry({ ...ok, image: { ...noAuthor, license: "CC0-1.0" } }), []);
  const bad = (over) => validateEntry({ ...ok, image: { ...img, ...over } }).length > 0;
  assert.ok(bad({ url: "https://wger.de/media/a.png" }));
  assert.ok(bad({ hash: "A".repeat(64) }));
  assert.ok(bad({ hash: "a".repeat(63) }));
  assert.ok(bad({ hash: "g".repeat(64) }));
  assert.ok(bad({ hash: undefined }));
  assert.ok(bad({ sourceUrl: "https://evil.example/media/a.png" }));
  assert.ok(bad({ sourceUrl: "https://wger.de/es/exercise/1/view/" }));
  assert.ok(bad({ sourceUrl: undefined }));
  assert.ok(bad({ sourceUrl: `https://wger.de/media/${"x".repeat(300)}` }));
  assert.ok(bad({ license: "ODbL" }));
  assert.ok(bad({ author: "x".repeat(201) }));
  assert.ok(bad({ extra: 1 }));
  assert.ok(validateEntry({ ...ok, image: "https://wger.de/media/a.png" }).length > 0);
  // Sin hash solo se admite al preparar las fuentes de las imágenes.
  assert.deepEqual(validateEntry({ ...ok, image: { ...img, hash: undefined } }, { hashOptional: true }), []);
});

test("validateEntry acepta bobitos sin url con CC-BY-SA-4.0 y rechaza bobitos con url u otra licencia", () => {
  const own = {
    ...baseEntry(),
    source: { provider: "bobitos", id: 1, author: "Catálogo Bobitos", license: "CC-BY-SA-4.0" },
  };
  assert.deepEqual(validateEntry(own), []);
  assert.ok(validateEntry({ ...own, source: { ...own.source, url: "https://wger.de/es/exercise/1/view/" } }).length > 0);
  assert.ok(validateEntry({ ...own, source: { ...own.source, license: "CC0-1.0" } }).length > 0);
  assert.ok(validateEntry({ ...own, source: { ...own.source, author: "Otro" } }).length > 0);
  assert.ok(validateEntry({ ...own, source: { ...own.source, provider: "otro" } }).length > 0);
  const img = { hash: "a".repeat(64), license: "CC-BY-SA-4.0", sourceUrl: "https://wger.de/media/a.png" };
  assert.ok(validateEntry({ ...own, image: img }).length > 0);
  assert.deepEqual(validateEntry({ ...own, image: null }), []);
});

const own = (over = {}) => ({
  bobitosId: 1,
  name: "Natación",
  type: "CARDIO",
  muscleGroup: "Cardio",
  equipment: [],
  description: "Nada a un ritmo cómodo y constante.\n\n• Respira con calma.",
  ...over,
});

test("buildCatalog copia measure SECONDS y rechaza SECONDS en CARDIO", () => {
  const r = run({ include: [{ wgerId: 257, measure: "SECONDS" }, { wgerId: 238 }], exclude: [] });
  assert.deepEqual(r.problems, []);
  const by = Object.fromEntries(r.catalog.exercises.map((e) => [e.source.id, e]));
  assert.equal(by[257].measure, "SECONDS");
  assert.equal(by[238].measure, "REPS");
  assert.ok(run({ include: [{ wgerId: 257, type: "CARDIO", measure: "SECONDS" }], exclude: [] }).problems.length > 0);
  assert.ok(run({ include: [{ wgerId: 257, measure: "MINUTES" }], exclude: [] }).problems.length > 0);
});

const bytesOf = (id) => Buffer.from(`webp-${id}`);

test("buildCatalog elige imagen: la primera, ninguna con false o la indicada; un id inexistente es problema", () => {
  const img = (sel) => buildCatalog({ candidates, selection: { include: [{ wgerId: 245, ...sel }], exclude: [] }, fetchedAt: "x", imageBytes: bytesOf });
  const first = img({}).catalog.exercises[0].image;
  assert.equal(first.sourceUrl, "https://wger.de/media/exercise-images/245/img30.png.400x400_q85.png");
  assert.deepEqual(Object.keys(first), ["hash", "author", "license", "sourceUrl"]);
  assert.equal(img({ image: false }).catalog.exercises[0].image, null);
  assert.ok(img({ image: 33 }).catalog.exercises[0].image.sourceUrl.includes("img33"));
  assert.ok(img({ image: 999 }).problems.length > 0);
  assert.equal(run({ include: [{ wgerId: 254 }], exclude: [] }).catalog.exercises[0].image, null);
});

test("buildCatalog completa el hash desde data/catalog/images y falla si falta el fichero", () => {
  const sel = { include: [{ wgerId: 245 }], exclude: [] };
  const asked = [];
  const ok = buildCatalog({
    candidates, selection: sel, fetchedAt: "x",
    imageBytes: (id) => (asked.push(id), bytesOf(id)),
  });
  assert.deepEqual(ok.problems, []);
  const e = ok.catalog.exercises[0];
  assert.deepEqual(asked, [e.id]);
  assert.equal(e.image.hash, createHash("sha256").update(bytesOf(e.id)).digest("hex"));
  const missing = buildCatalog({ candidates, selection: sel, fetchedAt: "x", imageBytes: () => undefined });
  assert.ok(missing.problems.some((p) => p.includes(`${e.id}.webp`)), missing.problems.join("|"));
  // Sin imageBytes (preparación de fuentes) la imagen queda sin hash y sin problemas.
  const pre = run(sel);
  assert.deepEqual(pre.problems, []);
  assert.deepEqual(Object.keys(pre.catalog.exercises[0].image), ["author", "license", "sourceUrl"]);
});

test("buildCatalog crea fichas propias con source bobitos sin url, measure explícita e image null", () => {
  const r = run({ include: [{ wgerId: 257 }], exclude: [], custom: [own(), own({ bobitosId: 2, name: "Caminata" })] });
  assert.deepEqual(r.problems, []);
  const e = r.catalog.exercises.find((x) => x.id === "natacion");
  assert.deepEqual(e.source, { provider: "bobitos", id: 1, author: "Catálogo Bobitos", license: "CC-BY-SA-4.0" });
  assert.equal(e.measure, "REPS");
  assert.equal(e.image, null);
  assert.equal(e.type, "CARDIO");
  assert.deepEqual(e.equipment, []);
  assert.equal(r.catalog.exercises.length, 3);
  const ids = r.catalog.exercises.map((x) => x.id);
  assert.deepEqual(ids, [...ids].sort());
  const t = run({ include: [], exclude: [], custom: [own({ type: "PESO_CORPORAL", measure: "SECONDS" })] });
  assert.equal(t.catalog.exercises[0].measure, "SECONDS");
});

test("buildCatalog falla con bobitosId repetido o no entero, o con slug repetido entre wger y propias", () => {
  const bad = (custom, include = []) => run({ include, exclude: [], custom }).problems;
  assert.ok(bad([own(), own({ name: "Caminata" })]).length > 0);
  for (const id of [0, -1, 1.5, "1", undefined]) assert.ok(bad([own({ bobitosId: id })]).length > 0, String(id));
  assert.ok(bad([own({ name: "Sentadilla frontal" })], [{ wgerId: 257, name: "Sentadilla frontal" }]).length > 0);
  assert.ok(bad([own({ name: "Natación" }), own({ bobitosId: 2, name: "Natacion" })]).length > 0);
  assert.ok(bad([own({ type: "NOPE" })]).length > 0);
});

test("renderReview muestra medida, imagen con autor y licencia, y el texto completo de las fichas propias", () => {
  const r = buildCatalog({
    candidates,
    selection: { include: [{ wgerId: 245, measure: "SECONDS" }, { wgerId: 254 }], exclude: [], custom: [own()] },
    fetchedAt: "x",
    imageBytes: bytesOf,
  });
  const hash = r.catalog.exercises.find((e) => e.image).image.hash;
  const md = renderReview(r.catalog, r.warnings, []);
  assert.match(md, /\| Tipo \| Medida \| .*\| Imagen \|/);
  assert.ok(
    md.includes(`[${hash.slice(0, 8)}](https://wger.de/media/exercise-images/245/img30.png.400x400_q85.png) · Eva · CC-BY-SA-4.0`),
  );
  assert.match(md, /SECONDS/);
  assert.match(md, /## Imágenes\n\n1 de 3 ejercicios con imagen/);
  assert.match(md, /## Fichas propias \(Catálogo Bobitos\)/);
  assert.ok(md.includes("Nada a un ritmo cómodo y constante.\n\n• Respira con calma."));
});

test("buildCatalog falla si el manifiesto de procedencia no casa con el sourceUrl de la imagen o falta", () => {
  const sel = { include: [{ wgerId: 245 }], exclude: [] };
  const url = "https://wger.de/media/exercise-images/245/img30.png.400x400_q85.png";
  const id = buildCatalog({ candidates, selection: sel, fetchedAt: "x" }).catalog.exercises[0].id;
  const go = (imageSources) => buildCatalog({ candidates, selection: sel, fetchedAt: "x", imageBytes: bytesOf, imageSources });
  assert.deepEqual(go({ [id]: url }).problems, []);
  const bad = go({ [id]: "https://wger.de/media/otra.png" });
  assert.ok(bad.problems.some((p) => p.includes("sources.json") && p.includes(id)), bad.problems.join("|"));
  const missing = go({});
  assert.ok(missing.problems.some((p) => p.includes("sources.json") && p.includes(id)), missing.problems.join("|"));
});
