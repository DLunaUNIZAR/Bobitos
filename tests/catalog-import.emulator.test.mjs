import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
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
  for (const col of ["exercises", "exerciseImages"]) {
    const snap = await db.collection(col).get();
    await Promise.all(snap.docs.map((d) => d.ref.delete()));
  }
  await db.doc("catalogMeta/exercises").delete();
});

const quiet = () => {};
const imagesDir = fileURLToPath(new URL("../data/catalog/images/", import.meta.url));
const withImages = small.exercises.filter((e) => e.image);
const sha = (buf) => createHash("sha256").update(buf).digest("hex");

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
    image: { ...small.exercises[0].image, author: "Ana" },
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

test("la primera importación deja la versión en 1 y una segunda sin cambios no la toca", async () => {
  const first = await runImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(first.versionBumped, true);
  const meta = (await db.doc("catalogMeta/exercises").get()).data();
  assert.equal(meta.version, 1);
  assert.equal(meta.updatedBy, CATALOG_ADMIN_UID);
  const stamp = meta.updatedAt.toMillis();
  const second = await runImport({ db, catalog: small, apply: true, log: quiet });
  assert.equal(second.versionBumped, false);
  const again = (await db.doc("catalogMeta/exercises").get()).data();
  assert.equal(again.version, 1);
  assert.equal(again.updatedAt.toMillis(), stamp);
});

test("actualizar una ficha sube la versión en 1", async () => {
  await runImport({ db, catalog: small, apply: true, log: quiet });
  const e = small.exercises[0];
  const changed = { ...small, exercises: [{ ...e, description: "Cambio del JSON" }, ...small.exercises.slice(1)] };
  const p = await runImport({ db, catalog: changed, apply: true, log: quiet });
  assert.equal(p.versionBumped, true);
  assert.equal((await db.doc("catalogMeta/exercises").get()).data().version, 2);
});

test("la simulación no crea catalogMeta", async () => {
  const p = await runImport({ db, catalog: small, apply: false, log: quiet });
  assert.equal(p.versionBumped, false);
  assert.equal((await db.doc("catalogMeta/exercises").get()).exists, false);
});

test("importa imágenes antes que fichas, con los bytes y el hash correctos", async () => {
  assert.ok(withImages.length >= 2);
  const order = [];
  const spy = {
    collection: (n) => db.collection(n),
    doc: (p) => db.doc(p),
    batch: () => {
      const b = db.batch();
      const keys = [];
      for (const m of ["create", "set", "update"]) {
        const orig = b[m].bind(b);
        b[m] = (ref, ...rest) => {
          keys.push(ref.path.split("/")[0]);
          return orig(ref, ...rest);
        };
      }
      const commit = b.commit.bind(b);
      b.commit = async () => {
        order.push(keys.includes("exerciseImages") ? "imagenes" : "fichas");
        return commit();
      };
      return b;
    },
  };
  const first = await runImport({ db: spy, catalog: small, apply: true, log: quiet, imagesDir });
  assert.deepEqual(first.images.upload.sort(), withImages.map((e) => e.id).sort());
  assert.ok(order.indexOf("imagenes") >= 0 && order.lastIndexOf("imagenes") < order.indexOf("fichas"), order.join());
  for (const e of withImages) {
    const d = (await db.doc(`exerciseImages/${e.id}`).get()).data();
    const file = await readFile(join(imagesDir, `${e.id}.webp`));
    assert.ok(Buffer.isBuffer(d.data));
    assert.ok(file.equals(d.data));
    assert.equal(d.hash, e.image.hash);
    assert.equal(sha(d.data), e.image.hash);
    assert.equal(d.contentType, "image/webp");
    assert.ok(d.width > 0 && d.width <= 400 && d.height > 0 && d.height <= 400);
    assert.equal(d.author, e.image.author);
    assert.equal(d.license, e.image.license);
    assert.equal(d.sourceUrl, e.image.sourceUrl);
    assert.ok(d.updatedAt.toMillis() > 0);
    assert.equal((await db.doc(`exercises/${e.id}`).get()).data().image.hash, e.image.hash);
  }
  assert.equal((await db.collection("exerciseImages").get()).size, withImages.length);
});

test("una segunda importación no sube ninguna imagen", async () => {
  await runImport({ db, catalog: small, apply: true, log: quiet, imagesDir });
  const stamps = Object.fromEntries(
    (await db.collection("exerciseImages").select("hash").get()).docs.map((d) => [d.id, d.updateTime.toMillis()]),
  );
  const second = await runImport({ db, catalog: small, apply: true, log: quiet, imagesDir });
  assert.equal(second.images.upload.length, 0);
  assert.equal(second.images.unchanged.length, withImages.length);
  for (const d of (await db.collection("exerciseImages").select("hash").get()).docs) {
    assert.equal(d.updateTime.toMillis(), stamps[d.id]);
  }
});

test("cambiar una imagen sube solo esa, actualiza el hash de la ficha y sube la versión", async () => {
  await runImport({ db, catalog: small, apply: true, log: quiet, imagesDir });
  const [a, b] = withImages;
  const dir = await mkdtemp(join(tmpdir(), "bobitos-img-"));
  for (const e of withImages) await writeFile(join(dir, `${e.id}.webp`), await readFile(join(imagesDir, `${e.id}.webp`)));
  const sharp = (await import("sharp")).default;
  const nuevo = await sharp({ create: { width: 8, height: 6, channels: 3, background: "#336699" } }).webp().toBuffer();
  await writeFile(join(dir, `${a.id}.webp`), nuevo);
  const changed = {
    ...small,
    exercises: small.exercises.map((e) => (e.id === a.id ? { ...e, image: { ...e.image, hash: sha(nuevo) } } : e)),
  };
  const p = await runImport({ db, catalog: changed, apply: true, log: quiet, imagesDir: dir });
  assert.deepEqual(p.images.upload, [a.id]);
  assert.deepEqual(p.images.unchanged, withImages.filter((e) => e.id !== a.id).map((e) => e.id));
  assert.equal(p.update.length, 1);
  const img = (await db.doc(`exerciseImages/${a.id}`).get()).data();
  assert.equal(img.hash, sha(nuevo));
  assert.ok(nuevo.equals(img.data));
  assert.deepEqual([img.width, img.height], [8, 6]);
  assert.equal((await db.doc(`exercises/${a.id}`).get()).data().image.hash, sha(nuevo));
  assert.equal((await db.doc(`exerciseImages/${b.id}`).get()).data().hash, b.image.hash);
  assert.equal((await db.doc("catalogMeta/exercises").get()).data().version, 2);
});

test("un fichero que no coincide con el hash del catálogo aborta sin subir nada", async () => {
  const [a] = withImages;
  const dir = await mkdtemp(join(tmpdir(), "bobitos-img-"));
  for (const e of withImages) await writeFile(join(dir, `${e.id}.webp`), await readFile(join(imagesDir, `${e.id}.webp`)));
  await writeFile(join(dir, `${a.id}.webp`), Buffer.from("no es la imagen"));
  await assert.rejects(runImport({ db, catalog: small, apply: true, log: quiet, imagesDir: dir }), new RegExp(a.id));
  assert.equal((await db.collection("exerciseImages").get()).size, 0);
  assert.equal((await db.collection("exercises").get()).size, 0);
});

test("la simulación no escribe exerciseImages", async () => {
  const p = await runImport({ db, catalog: small, apply: false, log: quiet, imagesDir });
  assert.equal(p.images.upload.length, withImages.length);
  assert.equal((await db.collection("exerciseImages").get()).size, 0);
});

test("las imágenes huérfanas se informan y no se borran", async () => {
  await db.doc("exerciseImages/vieja").set({ hash: "f".repeat(64), data: Buffer.from("x") });
  const lines = [];
  const p = await runImport({ db, catalog: small, apply: true, log: (l) => lines.push(l), imagesDir });
  assert.deepEqual(p.images.orphaned, ["vieja"]);
  assert.ok((await db.doc("exerciseImages/vieja").get()).exists);
  assert.match(lines.join("\n"), /Imágenes huérfanas/);
});

test("una ficha editada en la app no recibe una imagen nueva del catálogo", async () => {
  await runImport({ db, catalog: small, apply: true, log: quiet, imagesDir });
  const [a] = withImages;
  const before = (await db.doc(`exerciseImages/${a.id}`).get()).data();
  const ref = db.doc(`exercises/${a.id}`);
  const imp = (await ref.get()).data().source.importedAt.toMillis();
  await ref.update({ description: "Editada a mano", updatedAt: Timestamp.fromMillis(imp + 60000) });
  const dir = await mkdtemp(join(tmpdir(), "bobitos-img-"));
  for (const e of withImages) await writeFile(join(dir, `${e.id}.webp`), await readFile(join(imagesDir, `${e.id}.webp`)));
  const sharp = (await import("sharp")).default;
  const nuevo = await sharp({ create: { width: 8, height: 6, channels: 3, background: "#336699" } }).webp().toBuffer();
  await writeFile(join(dir, `${a.id}.webp`), nuevo);
  const changed = {
    ...small,
    exercises: small.exercises.map((e) => (e.id === a.id ? { ...e, image: { ...e.image, hash: sha(nuevo), author: "Otro" } } : e)),
  };
  const p = await runImport({ db, catalog: changed, apply: true, log: quiet, imagesDir: dir });
  assert.deepEqual(p.images.skipped, [a.id]);
  assert.deepEqual(p.images.upload, []);
  const after = (await db.doc(`exerciseImages/${a.id}`).get()).data();
  assert.equal(after.hash, before.hash);
  assert.equal(after.author, before.author);
  assert.ok(before.data.equals(after.data));
});
