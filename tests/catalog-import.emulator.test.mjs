import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-plan.mjs";
import { runImport } from "../scripts/catalog/import-exercises.mjs";

const catalog = JSON.parse(await readFile(new URL("../data/catalog/exercises.json", import.meta.url), "utf8"));
const small = { ...catalog, exercises: catalog.exercises.slice(0, 5) };
let app;
let db;

before(() => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "falta FIRESTORE_EMULATOR_HOST");
  app = initializeApp({ projectId: "demo-bobitos" }, "catalog-import-test");
  db = getFirestore(app);
});
after(() => deleteApp(app));
beforeEach(async () => {
  const snap = await db.collection("exercises").get();
  await Promise.all(snap.docs.map((d) => d.ref.delete()));
});

const quiet = () => {};

test("importa y una segunda pasada no escribe", async () => {
  const first = await runImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(first.create.length, 5);
  const snap = await db.collection("exercises").get();
  assert.equal(snap.size, 5);
  const d = snap.docs[0].data();
  assert.equal(d.ownerUid, CATALOG_ADMIN_UID);
  assert.equal(d.updatedAt.toMillis(), d.source.importedAt.toMillis());
  const second = await runImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(second.create.length + second.update.length, 0);
  assert.equal(second.unchanged.length, 5);
});

test("la simulación no escribe", async () => {
  await runImport({ db, catalog: small, apply: false, log: quiet });
  assert.equal((await db.collection("exercises").get()).size, 0);
});

test("no pisa una ficha de usuario", async () => {
  const e = small.exercises[0];
  await db.doc(`exercises/${e.id}`).set({ name: "Mía", nameLower: "mía", type: "OTROS", ownerUid: "usuario-x" });
  const p = await runImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(p.skippedUserOwned.length, 1);
  assert.equal(p.create.length, 4);
  assert.equal((await db.doc(`exercises/${e.id}`).get()).data().name, "Mía");
});

test("no sobrescribe una ficha editada en la app", async () => {
  await runImport({ db, catalog: small, apply: true, log: quiet });
  const e = small.exercises[0];
  const ref = db.doc(`exercises/${e.id}`);
  const imp = (await ref.get()).data().source.importedAt.toMillis();
  await ref.update({ description: "Editada a mano", updatedAt: Timestamp.fromMillis(imp + 60000) });
  const changed = { ...small, exercises: [{ ...e, description: "Cambio del JSON" }, ...small.exercises.slice(1)] };
  const p = await runImport({ db, catalog: changed, apply: true, log: quiet });
  assert.equal(p.skippedEditedInApp.length, 1);
  assert.equal((await ref.get()).data().description, "Editada a mano");
});

test("actualiza una ficha importada cuyo JSON cambió", async () => {
  await runImport({ db, catalog: small, apply: true, log: quiet });
  const e = small.exercises[0];
  const changed = { ...small, exercises: [{ ...e, description: "Cambio del JSON" }, ...small.exercises.slice(1)] };
  const p = await runImport({ db, catalog: changed, apply: true, log: quiet });
  assert.equal(p.update.length, 1);
  const d = (await db.doc(`exercises/${e.id}`).get()).data();
  assert.equal(d.description, "Cambio del JSON");
  assert.equal(d.updatedAt.toMillis(), d.source.importedAt.toMillis());
});

test("importa una ficha propia sin url ni claves undefined", async () => {
  const own = {
    id: "plancha-lateral-propia",
    name: "Plancha lateral propia",
    type: "PESO_CORPORAL",
    measure: "SECONDS",
    muscleGroup: "Abdomen",
    description: "Apoya el antebrazo y mantén el cuerpo recto.",
    equipment: ["ESTERILLA"],
    source: { provider: "bobitos", id: 1, author: "Catálogo Bobitos", license: "CC-BY-SA-4.0" },
  };
  const withImage = {
    ...small.exercises[0],
    image: { url: "https://wger.de/media/exercise-images/1/a.png", author: "Ana", license: "CC-BY-SA-4.0" },
  };
  const cat = { ...small, exercises: [own, withImage] };
  const p = await runImport({ db, catalog: cat, apply: true, log: quiet });
  assert.equal(p.create.length, 2);
  const d = (await db.doc(`exercises/${own.id}`).get()).data();
  assert.equal(d.source.provider, "bobitos");
  assert.ok(!("url" in d.source));
  assert.equal(d.measure, "SECONDS");
  assert.equal(d.image, null);
  assert.deepEqual((await db.doc(`exercises/${withImage.id}`).get()).data().image, withImage.image);
  const again = await runImport({ db, catalog: cat, apply: true, log: quiet });
  assert.equal(again.create.length + again.update.length, 0);
});
