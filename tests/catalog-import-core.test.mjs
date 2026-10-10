import assert from "node:assert/strict";
import { test } from "node:test";
import { canonical, formatImportPlan, planCatalogImport } from "../scripts/catalog/import-core.mjs";
import { parseImportArgs, resolveTarget } from "../scripts/catalog/admin-cli.mjs";
import { parseResetArgs } from "../scripts/catalog/reset-ingredients.mjs";

const fields = (e) => ({ name: e.name, n: e.n ?? 1 });
const doc = (e, over = {}) => ({
  id: e.id, ownerUid: undefined, hasSource: true, updatedAtMillis: 1000, importedAtMillis: 1000, fields: fields(e), ...over,
});
const plan = (entries, existing, opts = {}) => planCatalogImport({ entries, existing, managedFields: fields, ...opts });

test("canonical no depende del orden de las claves", () => {
  assert.equal(canonical({ b: 1, a: { d: 2, c: 3 } }), canonical({ a: { c: 3, d: 2 }, b: 1 }));
});

test("planCatalogImport crea, actualiza, deja igual e informa de huérfanas", () => {
  const a = { id: "a", name: "A" };
  const b = { id: "b", name: "B", n: 2 };
  const c = { id: "c", name: "C" };
  const p = plan([a, b, c], [doc(a), doc({ id: "b", name: "B" }), doc({ id: "z", name: "Z" })]);
  assert.deepEqual(p.create.map((e) => e.id), ["c"]);
  assert.deepEqual(p.update.map((e) => e.id), ["b"]);
  assert.deepEqual(p.unchanged, ["a"]);
  assert.deepEqual(p.orphaned, ["z"]);
});

test("planCatalogImport sin isForeign trata como del catálogo un documento sin dueño", () => {
  const a = { id: "a", name: "A" };
  assert.deepEqual(plan([a], [doc(a)]).skippedUserOwned, []);
});

test("planCatalogImport omite los documentos ajenos y no los cuenta como huérfanos", () => {
  const a = { id: "a", name: "A" };
  const isForeign = (d) => d.ownerUid != null && d.ownerUid !== "admin";
  const p = plan([a], [doc(a, { ownerUid: "u1" }), doc({ id: "y", name: "Y" }, { ownerUid: "u2" })], { isForeign });
  assert.deepEqual(p.skippedUserOwned, [{ id: "a", ownerUid: "u1" }]);
  assert.deepEqual(p.orphaned, []);
});

test("planCatalogImport no pisa lo editado en la app ni las fichas manuales", () => {
  const a = { id: "a", name: "A", n: 2 };
  const b = { id: "b", name: "B", n: 2 };
  const p = plan([a, b], [doc({ id: "a", name: "A" }, { updatedAtMillis: 2000 }), doc({ id: "b", name: "B" }, { hasSource: false })]);
  assert.deepEqual(p.skippedEditedInApp, ["a"]);
  assert.deepEqual(p.skippedAdminManual, ["b"]);
});

test("planCatalogImport calcula casi duplicados solo con la función inyectada", () => {
  const nuevo = { id: "chocolates-negros", name: "Chocolates negros" };
  const existing = [doc({ id: "chocolate-negro", name: "Chocolate negro" })];
  assert.deepEqual(plan([nuevo], existing).nearDuplicates, []);
  const same = (x, y) => x.replace(/s\b/g, "") === y.replace(/s\b/g, "");
  assert.deepEqual(plan([nuevo], existing, { isNearDuplicate: same }).nearDuplicates, [
    { id: "chocolates-negros", existingId: "chocolate-negro" },
  ]);
});

test("formatImportPlan usa el título y la ruta de la versión", () => {
  const p = plan([{ id: "a", name: "A" }], []);
  const lines = formatImportPlan(p, { title: "Plan de importación de ingredientes", metaPath: "catalogMeta/ingredients" });
  assert.equal(lines[0], "Plan de importación de ingredientes");
  assert.ok(lines.includes("La versión del catálogo subirá (catalogMeta/ingredients)."));
});

test("resolveTarget exige el emulador para demo-* y lo prohíbe para bobitos-dev", () => {
  const keyOf = () => "bobitos-dev";
  assert.throws(() => resolveTarget("demo-bobitos", {}, keyOf), /FIRESTORE_EMULATOR_HOST/);
  assert.deepEqual(resolveTarget("demo-bobitos", { FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" }, keyOf), {
    projectId: "demo-bobitos", useCredential: false,
  });
  const env = { GOOGLE_APPLICATION_CREDENTIALS: "/k.json" };
  assert.throws(() => resolveTarget("dev", { ...env, FIRESTORE_EMULATOR_HOST: "x" }, keyOf), /no se importa a bobitos-dev/);
  assert.throws(() => resolveTarget("dev", {}, keyOf), /GOOGLE_APPLICATION_CREDENTIALS/);
  assert.throws(() => resolveTarget("dev", env, () => "otro"), /«otro»/);
  assert.deepEqual(resolveTarget("dev", env, keyOf), { projectId: "bobitos-dev", useCredential: true });
  assert.throws(() => resolveTarget("produccion", {}, keyOf), /Usa --project/);
});

test("parseImportArgs lee --apply, --project y --catalog y rechaza lo desconocido", () => {
  assert.deepEqual(parseImportArgs(["--project", "dev", "--apply"], { defaultCatalog: "x.json" }), {
    apply: true, catalog: "x.json", project: "dev",
  });
  assert.equal(parseImportArgs(["--catalog", "y.json"], { defaultCatalog: "x.json" }).catalog, "y.json");
  assert.throws(() => parseImportArgs(["--borrar"], { defaultCatalog: "x.json" }), /Argumento desconocido: --borrar/);
});

test("parseImportArgs y parseResetArgs rechazan claves heredadas como argumentos", () => {
  for (const clave of ["constructor", "toString", "__proto__"]) {
    const esperado = new RegExp(`Argumento desconocido: ${clave}`);
    assert.throws(() => parseImportArgs([clave], { defaultCatalog: "x.json" }), esperado);
    assert.throws(() => parseResetArgs([clave]), esperado);
  }
});
