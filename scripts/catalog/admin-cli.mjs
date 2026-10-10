// Protecciones de proyecto y argumentos compartidas por los scripts de catálogo que escriben en Firestore.
import { readFileSync } from "node:fs";
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

export function parseImportArgs(argv, { defaultCatalog }) {
  const args = { apply: false, catalog: defaultCatalog, project: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--apply") args.apply = true;
    else if (argv[i] === "--project") args.project = argv[++i];
    else if (argv[i] === "--catalog") args.catalog = argv[++i];
    else throw new Error(`Argumento desconocido: ${argv[i]}`);
  }
  return args;
}
