import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import {
  isPersonalLegacy,
  parseMigrateArgs,
  runMigration,
} from "../scripts/catalog/migrate-personal-exercises.mjs";
import { CATALOG_ADMIN_UID } from "../scripts/catalog/import-core.mjs";
import { quiet, setupEmulatorDb } from "./helpers/emulator-db.mjs";

const db = setupEmulatorDb("personal-exercises-migration-test");
const created = Timestamp.fromMillis(1_700_000_000_000);

const userExercise = (uid, name) => ({
  name,
  nameLower: name.toLowerCase(),
  type: "PESO_LIBRE",
  ownerUid: uid,
  createdBy: uid,
  createdByName: uid,
  createdAt: created,
  updatedBy: uid,
  updatedAt: created,
});

beforeEach(async () => {
  for (const path of ["exercises", "users", "catalogMeta"]) await db.recursiveDelete(db.collection(path));
  await db.doc("exercises/remo-ana").set(userExercise("ana", "Remo Ana"));
  await db.doc("exercises/curl-ana").set(userExercise("ana", "Curl Ana"));
  await db.doc("exercises/plancha-bea").set(userExercise("bea", "Plancha Bea"));
  // Lo que se queda en el común: del admin y del importador (con source, aunque su dueño sea otro).
  await db.doc("exercises/press-admin").set(userExercise(CATALOG_ADMIN_UID, "Press admin"));
  await db.doc("exercises/sentadilla").set({
    ...userExercise(CATALOG_ADMIN_UID, "Sentadilla"),
    source: { provider: "wger", id: 1, license: "CC-BY-SA-4.0" },
  });
  await db.doc("exercises/importada-ajena").set({
    ...userExercise("ana", "Importada"),
    source: { provider: "bobitos", id: 2, license: "CC-BY-SA-4.0" },
  });
  await db.doc("catalogMeta/exercises").set({ version: 7 });
});

test("la simulación lista lo que movería sin escribir nada", async () => {
  const lines = [];
  const r = await runMigration({ db, apply: false, log: (m) => lines.push(m) });
  assert.deepEqual(r, { moved: 0, pending: 3, owners: 2, versionBumped: false });
  assert.equal((await db.collection("exercises").get()).size, 6);
  assert.equal((await db.collection("users/ana/exercises").get()).size, 0);
  assert.equal((await db.doc("catalogMeta/exercises").get()).get("version"), 7);
  assert.ok(lines.some((l) => l.includes("exercises/remo-ana → users/ana/exercises/remo-ana")));
});

test("con apply mueve cada ejercicio de usuario a su subcolección tal cual y lo borra del común", async () => {
  const r = await runMigration({ db, apply: true, log: quiet });
  assert.deepEqual(r, { moved: 3, pending: 0, owners: 2, versionBumped: true });

  const remo = await db.doc("users/ana/exercises/remo-ana").get();
  assert.deepEqual(remo.data(), userExercise("ana", "Remo Ana"));
  assert.equal((await db.doc("users/ana/exercises/curl-ana").get()).exists, true);
  assert.equal((await db.doc("users/bea/exercises/plancha-bea").get()).exists, true);
  for (const id of ["remo-ana", "curl-ana", "plancha-bea"]) {
    assert.equal((await db.doc(`exercises/${id}`).get()).exists, false);
  }
  for (const id of ["press-admin", "sentadilla", "importada-ajena"]) {
    assert.equal((await db.doc(`exercises/${id}`).get()).exists, true);
  }
});

test("sube catalogMeta/exercises una sola vez", async () => {
  await runMigration({ db, apply: true, log: quiet });
  const meta = await db.doc("catalogMeta/exercises").get();
  assert.equal(meta.get("version"), 8);
  assert.equal(meta.get("updatedBy"), CATALOG_ADMIN_UID);
  assert.ok(meta.get("updatedAt"));
});

test("una segunda pasada no encuentra nada ni sube la versión", async () => {
  await runMigration({ db, apply: true, log: quiet });
  const r = await runMigration({ db, apply: true, log: quiet });
  assert.deepEqual(r, { moved: 0, pending: 0, owners: 0, versionBumped: false });
  assert.equal((await db.doc("catalogMeta/exercises").get()).get("version"), 8);
});

test("isPersonalLegacy descarta lo importado, lo del admin y lo que no tiene dueño", () => {
  assert.equal(isPersonalLegacy({ ownerUid: "ana" }), true);
  assert.equal(isPersonalLegacy({ ownerUid: "ana", source: null }), false);
  assert.equal(isPersonalLegacy({ ownerUid: CATALOG_ADMIN_UID }), false);
  assert.equal(isPersonalLegacy({}), false);
  assert.equal(isPersonalLegacy({ ownerUid: "" }), false);
});

test("parseMigrateArgs solo admite --project y --apply", () => {
  assert.deepEqual(parseMigrateArgs(["--project", "demo-bobitos"]), { apply: false, project: "demo-bobitos" });
  assert.deepEqual(parseMigrateArgs(["--apply", "--project", "dev"]), { apply: true, project: "dev" });
  assert.throws(() => parseMigrateArgs(["--repetir"]), /Argumento desconocido: --repetir/);
});
