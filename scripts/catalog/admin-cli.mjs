// Protecciones de proyecto y argumentos compartidas por los scripts de catálogo que escriben en Firestore.
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { applicationDefault, initializeApp } from "firebase-admin/app";

/**
 * Decide el proyecto de destino sin conectar. `readKeyProject(ruta)` devuelve el `project_id` de la
 * clave. Devuelve `{ projectId, useCredential }` o lanza el error de la protección que salte.
 */
export function resolveTarget(projectArg, env, readKeyProject) {
  const projectId = projectArg === "dev" ? "bobitos-dev" : projectArg;
  const emulator = env.FIRESTORE_EMULATOR_HOST;
  if (projectId?.startsWith("demo-")) {
    if (!emulator) throw new Error("Un proyecto demo-* exige FIRESTORE_EMULATOR_HOST (emulador).");
    return { projectId, useCredential: false };
  }
  if (projectId === "bobitos-dev") {
    if (emulator) throw new Error("FIRESTORE_EMULATOR_HOST está definida: no se importa a bobitos-dev.");
    const keyPath = env.GOOGLE_APPLICATION_CREDENTIALS;
    if (!keyPath) throw new Error("Falta GOOGLE_APPLICATION_CREDENTIALS (clave de cuenta de servicio fuera del repo).");
    const keyProject = readKeyProject(keyPath);
    if (keyProject !== "bobitos-dev") throw new Error(`La clave es del proyecto «${keyProject}», no de bobitos-dev.`);
    return { projectId, useCredential: true };
  }
  throw new Error("Usa --project demo-bobitos, bobitos-dev o dev.");
}

export function connectAdmin(projectArg) {
  const { projectId, useCredential } = resolveTarget(
    projectArg,
    process.env,
    (path) => JSON.parse(readFileSync(path, "utf8")).project_id,
  );
  return useCredential ? initializeApp({ credential: applicationDefault(), projectId }) : initializeApp({ projectId });
}

/**
 * Lee `argv` según `spec` ({ "--flag": { key, value: true si lleva valor } }) a partir de `initial`.
 * Lanza «Argumento desconocido» ante cualquier otro argumento.
 */
export function parseCliArgs(argv, spec, initial) {
  const args = { ...initial };
  for (let i = 0; i < argv.length; i++) {
    // Solo claves propias: constructor, toString o __proto__ no son argumentos válidos.
    const flag = Object.hasOwn(spec, argv[i]) ? spec[argv[i]] : null;
    if (!flag) throw new Error(`Argumento desconocido: ${argv[i]}`);
    args[flag.key] = flag.value ? argv[++i] : true;
  }
  return args;
}

export function parseImportArgs(argv, { defaultCatalog }) {
  return parseCliArgs(
    argv,
    { "--apply": { key: "apply" }, "--project": { key: "project", value: true }, "--catalog": { key: "catalog", value: true } },
    { apply: false, catalog: defaultCatalog, project: null },
  );
}

/** Ejecuta `fn` solo si el módulo es el programa principal; los errores salen por stderr con código 1. */
export async function runMain(importMetaUrl, fn) {
  if (!(process.argv[1] && importMetaUrl === pathToFileURL(process.argv[1]).href)) return;
  try {
    await fn();
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
