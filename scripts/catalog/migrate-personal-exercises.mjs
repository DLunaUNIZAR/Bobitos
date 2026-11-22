// Script de un solo uso: los ejercicios que los usuarios crearon en el catálogo común (`exercises`, sin `source`,
// que solo pone el importador) pasan a ser personales. Cada uno se copia tal cual a
// users/{ownerUid}/exercises/{id} y se borra del común en el mismo lote, de modo que nunca queda a medias.
// Las fichas del admin del catálogo (ownerUid admin) y las importadas (con `source`) no se tocan.
// Si se mueve alguna, sube catalogMeta/exercises una vez (+1) para que las cachés por versión de los clientes
// dejen de mostrarlas como comunes. Repetible: una segunda pasada no encuentra nada.
// Orden de despliegue (docs/EXERCISE_CATALOG.md): publicar la app nueva, desplegar las reglas y luego migrar.
// Sin --apply solo cuenta y lista lo que movería.
// Uso: node scripts/catalog/migrate-personal-exercises.mjs --project demo-bobitos|bobitos-dev|dev [--apply]
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { connectAdmin, parseCliArgs, runMain } from "./admin-cli.mjs";
import { CATALOG_ADMIN_UID, CATALOG_META_PATH } from "./import-plan.mjs";

export const EXERCISES_COLLECTION = "exercises";
const BATCH_PAIRS = 200; // dos escrituras por ejercicio (copia + borrado), lejos del límite de 500 del lote

export function parseMigrateArgs(argv) {
  return parseCliArgs(
    argv,
    { "--apply": { key: "apply" }, "--project": { key: "project", value: true } },
    { apply: false, project: null },
  );
}

/** Ejercicios del común que son de un usuario: sin `source`, con dueño y que no son del admin. */
export function isPersonalLegacy(data, adminUids = [CATALOG_ADMIN_UID]) {
  return !("source" in data)
    && typeof data.ownerUid === "string"
    && data.ownerUid.length > 0
    && !adminUids.includes(data.ownerUid);
}

export async function planMigration(db) {
  const snapshot = await db.collection(EXERCISES_COLLECTION).get();
  return snapshot.docs
    .filter((d) => isPersonalLegacy(d.data()))
    .map((d) => ({ id: d.id, ownerUid: d.get("ownerUid"), data: d.data() }));
}

export async function runMigration({ db, apply, log = console.log }) {
  const moves = await planMigration(db);
  const owners = new Set(moves.map((m) => m.ownerUid));
  log(`Ejercicios personales en el catálogo común: ${moves.length} de ${owners.size} usuarios`);
  moves.forEach((m) => log(`  exercises/${m.id} → users/${m.ownerUid}/exercises/${m.id}`));
  if (!apply) {
    log("Simulación: no se ha movido nada. Usa --apply para migrar.");
    return { moved: 0, pending: moves.length, owners: owners.size, versionBumped: false };
  }
  for (let i = 0; i < moves.length; i += BATCH_PAIRS) {
    const batch = db.batch();
    for (const m of moves.slice(i, i + BATCH_PAIRS)) {
      batch.set(db.doc(`users/${m.ownerUid}/${EXERCISES_COLLECTION}/${m.id}`), m.data);
      batch.delete(db.doc(`${EXERCISES_COLLECTION}/${m.id}`));
    }
    await batch.commit();
  }
  if (moves.length > 0) {
    await db.doc(CATALOG_META_PATH).set(
      { version: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp(), updatedBy: CATALOG_ADMIN_UID },
      { merge: true },
    );
    log("Se ha subido la versión del catálogo de ejercicios para invalidar las cachés de los clientes.");
  }
  log(`Migrados: ${moves.length}`);
  return { moved: moves.length, pending: 0, owners: owners.size, versionBumped: moves.length > 0 };
}

await runMain(import.meta.url, async () => {
  const args = parseMigrateArgs(process.argv.slice(2));
  const app = connectAdmin(args.project);
  await runMigration({ db: getFirestore(app), apply: args.apply });
});
