import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import {
  CATALOG_ADMIN_UID,
  CATALOG_AUTHOR_NAME,
  CATALOG_META_PATH,
  formatPlan,
  docToExisting,
  managedFields,
  planImageImport,
  pickSource,
  planImport,
  toFirestoreDoc,
} from "../scripts/catalog/import-plan.mjs";

const entry = (over = {}) => ({
  id: "press-de-banca",
  name: "Press de banca",
  type: "PESO_LIBRE",
  muscleGroup: "Pecho",
  description: "Túmbate en el banco.",
  equipment: ["BARRA", "BANCO"],
  source: { provider: "wger", id: 1, author: "Ana", license: "CC-BY-SA-4.0", url: "https://wger.de/es/exercise/1/view/" },
  ...over,
});

const catalogOf = (...exercises) => ({ schemaVersion: 1, exercises });

// Documento existente tal y como lo dejaría el importador.
const imported = (e, over = {}) => ({
  id: e.id,
  ownerUid: CATALOG_ADMIN_UID,
  fields: managedFields(e),
  hasSource: true,
  updatedAtMillis: 1000,
  importedAtMillis: 1000,
  ...over,
});

const plan = (catalog, existing = []) => planImport({ catalog, existing, adminUid: CATALOG_ADMIN_UID });

test("un catálogo sobre colección vacía lo crea todo", () => {
  const p = plan(catalogOf(entry(), entry({ id: "sentadilla", name: "Sentadilla" })));
  assert.equal(p.create.length, 2);
  assert.equal(p.update.length, 0);
  assert.equal(p.unchanged.length, 0);
});

test("reimportar sin cambios no escribe nada", () => {
  const e = entry();
  const p = plan(catalogOf(e), [imported(e)]);
  assert.equal(p.create.length + p.update.length, 0);
  assert.deepEqual(p.unchanged.map((x) => x.id ?? x), [e.id]);
});

test("una ficha importada con cambios se actualiza sin tocar createdAt/ownerUid", () => {
  const e = entry();
  const changed = entry({ description: "Otra descripción." });
  const p = plan(catalogOf(changed), [imported(e)]);
  assert.equal(p.update.length, 1);
  const now = { fake: "now" };
  const data = toFirestoreDoc(changed, { now, create: false });
  assert.equal(data.description, "Otra descripción.");
  assert.equal(data.updatedBy, CATALOG_ADMIN_UID);
  assert.equal(data.updatedAt, now);
  assert.equal(data.source.importedAt, now);
  for (const k of ["createdAt", "createdBy", "createdByName", "ownerUid"]) assert.ok(!(k in data), k);
});

test("una ficha de usuario con el mismo slug no se pisa y se informa", () => {
  const e = entry();
  const p = plan(catalogOf(e), [imported(e, { ownerUid: "otro", hasSource: false, fields: { name: "Press de banca" } })]);
  assert.deepEqual(p.skippedUserOwned, [{ id: e.id, ownerUid: "otro" }]);
  assert.equal(p.create.length + p.update.length, 0);
});

test("una ficha del admin sin source no se pisa", () => {
  const e = entry();
  const p = plan(catalogOf(e), [imported(e, { hasSource: false, importedAtMillis: null })]);
  assert.equal(p.skippedAdminManual.length, 1);
  assert.equal(p.update.length + p.create.length, 0);
});

test("una ficha importada editada después en la app no se pisa", () => {
  const e = entry();
  const p = plan(catalogOf(entry({ description: "Nueva" })), [imported(e, { updatedAtMillis: 5000 })]);
  assert.equal(p.skippedEditedInApp.length, 1);
  assert.equal(p.update.length, 0);
});

test("una ficha que ya no está en el JSON se informa como huérfana", () => {
  const e = entry();
  const gone = entry({ id: "vieja", name: "Vieja" });
  const p = plan(catalogOf(e), [imported(e), imported(gone)]);
  assert.deepEqual(p.orphaned.map((x) => x.id ?? x), ["vieja"]);
});

test("un nombre casi igual a una ficha existente se avisa", () => {
  const e = entry({ id: "press-banca", name: "Press banca" });
  const other = { id: "press-de-banca", ownerUid: "otro", fields: { name: "Press de banca" }, hasSource: false, updatedAtMillis: 1, importedAtMillis: null };
  const p = plan(catalogOf(e), [other]);
  assert.deepEqual(p.nearDuplicates, [{ id: "press-banca", existingId: "press-de-banca" }]);
  assert.equal(p.create.length, 1);
});

test("toFirestoreDoc incluye todo lo que exige el parser de la app", () => {
  const now = { fake: "now" };
  const d = toFirestoreDoc(entry(), { now, create: true });
  assert.equal(d.name, "Press de banca");
  assert.equal(d.nameLower, "press de banca");
  assert.equal(d.type, "PESO_LIBRE");
  assert.equal(d.ownerUid, CATALOG_ADMIN_UID);
  assert.equal(d.createdBy, CATALOG_ADMIN_UID);
  assert.equal(d.updatedBy, CATALOG_ADMIN_UID);
  assert.equal(d.createdByName, CATALOG_AUTHOR_NAME);
  assert.equal(d.createdAt, now);
  assert.equal(d.updatedAt, now);
  assert.equal(d.source.importedAt, now);
  assert.equal(d.source.id, 1);
  assert.deepEqual(d.equipment, ["BARRA", "BANCO"]);
});

test("formatPlan resume el plan en español", () => {
  const out = formatPlan(plan(catalogOf(entry())));
  assert.match(out, /Crear: 1/);
});

test("formatPlan avisa de si la versión del catálogo sube", () => {
  assert.equal(CATALOG_META_PATH, "catalogMeta/exercises");
  const sube = formatPlan(plan(catalogOf(entry())));
  assert.match(sube, /La versión del catálogo subirá/);
  const e = entry();
  const igual = formatPlan(plan(catalogOf(e), [imported(e)]));
  assert.match(igual, /La versión del catálogo no cambia/);
  assert.doesNotMatch(igual, /subirá/);
});

test("CATALOG_ADMIN_UID coincide con firestore.rules y RecipeAdmins.kt", async () => {
  const rules = await readFile(new URL("../firestore.rules", import.meta.url), "utf8");
  const kt = await readFile(
    new URL("../app/src/main/java/com/dlunaunizar/bobitos/data/repository/RecipeAdmins.kt", import.meta.url),
    "utf8",
  );
  const block = rules.slice(rules.indexOf("function recipeAdmins()"));
  assert.ok(block.slice(0, 400).includes(`"${CATALOG_ADMIN_UID}"`), "firestore.rules");
  assert.ok(kt.includes(`"${CATALOG_ADMIN_UID}"`), "RecipeAdmins.kt");
});

test("planImport avisa de un alta casi igual a una existente con material extra", () => {
  const old = entry({ id: "press-banca", name: "Press banca" });
  const nuevo = entry({ id: "press-de-banca-con-barra", name: "Press de banca con barra" });
  const p = plan(catalogOf(nuevo), [imported(old)]);
  assert.deepEqual(p.nearDuplicates, [{ id: "press-de-banca-con-barra", existingId: "press-banca" }]);
});

test("pickSource no deja claves undefined", () => {
  const own = pickSource({ provider: "bobitos", id: 1, author: "Catálogo Bobitos", license: "CC-BY-SA-4.0" });
  assert.deepEqual(own, { provider: "bobitos", id: 1, author: "Catálogo Bobitos", license: "CC-BY-SA-4.0" });
  assert.ok(!("url" in own));
  assert.ok(Object.values(pickSource({ provider: "wger", id: 2, license: "CC0-1.0" })).every((v) => v !== undefined));
});

test("managedFields pone REPS e image null por defecto", () => {
  const m = managedFields(entry());
  assert.equal(m.measure, "REPS");
  assert.ok("image" in m);
  assert.equal(m.image, null);
  const img = { hash: "a".repeat(64), license: "CC0-1.0", sourceUrl: "https://wger.de/media/a.png" };
  const withData = managedFields(entry({ measure: "SECONDS", image: { ...img, extra: "x" } }));
  assert.equal(withData.measure, "SECONDS");
  assert.deepEqual(withData.image, img);
  assert.ok(Object.values(withData.image).every((v) => v !== undefined));
});

test("una ficha importada sin measure ni image no se reescribe si el JSON trae REPS y sin imagen", () => {
  const e = entry({ measure: "REPS" });
  const old = docToExisting(
    e.id,
    {
      ownerUid: CATALOG_ADMIN_UID,
      name: e.name,
      nameLower: e.name.toLowerCase(),
      type: e.type,
      muscleGroup: e.muscleGroup,
      description: e.description,
      equipment: e.equipment,
      source: e.source,
    },
    { updatedAtMillis: 1000, importedAtMillis: 1000 },
  );
  assert.equal(old.fields.measure, "REPS");
  assert.equal(old.fields.image, null);
  const p = plan(catalogOf(e), [old]);
  assert.equal(p.update.length, 0);
  assert.equal(p.unchanged.length, 1);
  const withImage = entry({ image: { hash: "a".repeat(64), license: "CC0-1.0", sourceUrl: "https://wger.de/media/a.png" } });
  assert.equal(plan(catalogOf(withImage), [old]).update.length, 1);
  assert.equal(plan(catalogOf(entry({ measure: "SECONDS" })), [old]).update.length, 1);
});

const hashOf = (c) => c.repeat(64);
const withImage = (id, c) =>
  entry({ id, name: id, image: { hash: hashOf(c), author: "Ana", license: "CC0-1.0", sourceUrl: "https://wger.de/media/a.png" } });

test("planImageImport sube las nuevas o con hash distinto, deja las iguales e informa de huérfanas", () => {
  const catalog = catalogOf(withImage("nueva", "a"), withImage("cambiada", "b"), withImage("igual", "c"), entry({ id: "sin-foto" }));
  const existingImages = [
    { id: "cambiada", hash: hashOf("x") },
    { id: "igual", ...withImage("igual", "c").image },
    { id: "vieja", hash: hashOf("d") },
    { id: "sin-foto", hash: hashOf("e") },
  ];
  const p = planImageImport({ catalog, existingImages, plan: plan(catalog) });
  assert.deepEqual(p.upload, ["nueva", "cambiada"]);
  assert.deepEqual(p.unchanged, ["igual"]);
  assert.deepEqual(p.orphaned, ["vieja", "sin-foto"]);
  assert.deepEqual(planImageImport({ catalog: catalogOf(entry()), existingImages: [], plan: plan(catalogOf(entry())) }), { upload: [], unchanged: [], skipped: [], orphaned: [] });
});

test("planImageImport no sube imágenes de fichas omitidas y las informa", () => {
  const frozen = withImage("congelada", "b");
  const user = withImage("de-usuario", "c");
  const manual = withImage("manual", "d");
  const ok = withImage("nueva", "a");
  const catalog = catalogOf(frozen, user, manual, ok);
  const existingImages = [{ id: "congelada", hash: hashOf("x") }];
  const p = plan(catalog, [
    imported(frozen, { updatedAtMillis: 9000, importedAtMillis: 1000 }),
    imported(user, { ownerUid: "otro" }),
    imported(manual, { hasSource: false }),
  ]);
  const out = planImageImport({ catalog, existingImages, plan: p });
  assert.deepEqual(out.upload, ["nueva"]);
  assert.deepEqual(out.skipped, ["congelada", "de-usuario", "manual"]);
  assert.deepEqual(out.orphaned, []);
  assert.match(formatPlan({ ...p, images: out }), /Imágenes omitidas \(ficha omitida\): 3/);
});

test("planImageImport sube cuando solo cambian autor, licencia o sourceUrl con el mismo hash", () => {
  const e = withImage("igual", "c");
  const same = { id: "igual", ...e.image };
  const run = (over) => planImageImport({ catalog: catalogOf(e), existingImages: [{ ...same, ...over }], plan: plan(catalogOf(e), [imported(e)]) });
  assert.deepEqual(run({}).unchanged, ["igual"]);
  assert.deepEqual(run({ author: "Otro" }).upload, ["igual"]);
  assert.deepEqual(run({ license: "CC0-1.0x" }).upload, ["igual"]);
  assert.deepEqual(run({ sourceUrl: "https://wger.de/media/z.png" }).upload, ["igual"]);
});

test("formatPlan muestra las imágenes a subir", () => {
  const catalog = catalogOf(withImage("nueva", "a"), withImage("igual", "c"));
  const imagePlan = planImageImport({ catalog, existingImages: [{ id: "igual", ...withImage("igual", "c").image }, { id: "vieja", hash: hashOf("d") }], plan: plan(catalog) });
  const out = formatPlan({ ...plan(catalog), images: imagePlan });
  assert.match(out, /Imágenes a subir: 1/);
  assert.match(out, /Imágenes sin cambios: 1/);
  assert.match(out, /Imágenes huérfanas \(no se borran\): 1/);
  assert.match(out, / - vieja/);
  assert.doesNotMatch(formatPlan(plan(catalog)), /Imágenes/);
});

test("import-plan.mjs no importa firebase-admin", async () => {
  const src = await readFile(new URL("../scripts/catalog/import-plan.mjs", import.meta.url), "utf8");
  assert.doesNotMatch(src, /(from|import)\s*\(?\s*["']firebase-admin/);
});
