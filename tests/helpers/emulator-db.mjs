// Base de datos del emulador de Firestore para los tests de scripts de catálogo.
import assert from "node:assert/strict";
import { after } from "node:test";
import { deleteApp, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

export const quiet = () => {};

/** Conecta con el emulador (proyecto demo-bobitos) y cierra la app al terminar los tests. */
export function setupEmulatorDb(name) {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "falta FIRESTORE_EMULATOR_HOST");
  const app = initializeApp({ projectId: "demo-bobitos" }, name);
  after(() => deleteApp(app));
  return getFirestore(app);
}
