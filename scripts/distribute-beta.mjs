import { access, readFile, stat, writeFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { constants } from "node:fs";
import path from "node:path";
import process from "node:process";
import { releaseProblems } from "./beta-release-check.mjs";

const root = process.cwd();
const firebaseConfigPath = path.join(root, "app", "google-services.json");
const apkPath = path.join(root, "app", "build", "outputs", "apk", "release", "app-release.apk");
const releaseNotesPath = path.join(root, "distribution", "release-notes.txt");
const metadataPath = path.join(path.dirname(apkPath), "output-metadata.json");
// Última beta distribuida desde este equipo (ignorado por git).
const lastDistributedPath = path.join(root, "distribution", ".last-distributed.json");
const expectedPackage = "com.dlunaunizar.bobitos";

async function readJson(filePath) {
    try {
        return JSON.parse(await readFile(filePath, "utf8"));
    } catch {
        return null;
    }
}

// undefined si no hay registro; null si existe pero no se puede leer (se rechaza, no se ignora).
async function readRecord(filePath) {
    try {
        await access(filePath, constants.F_OK);
    } catch {
        return undefined;
    }
    return readJson(filePath);
}

async function assertReadable(filePath, message) {
    try {
        await access(filePath, constants.R_OK);
    } catch {
        throw new Error(message);
    }
}

await assertReadable(
    firebaseConfigPath,
    "Falta app/google-services.json. Descárgalo desde el proyecto bobitos-dev de Firebase.",
);
await assertReadable(
    apkPath,
    "Falta el APK release firmado. Ejecuta ./gradlew assembleRelease antes de distribuir.",
);
await assertReadable(releaseNotesPath, "Falta distribution/release-notes.txt.");

const metadata = await readJson(metadataPath);
const apkSha256 = createHash("sha256").update(await readFile(apkPath)).digest("hex");
const problems = releaseProblems({
    metadata,
    apkModifiedAt: (await stat(apkPath)).mtime,
    notesModifiedAt: (await stat(releaseNotesPath)).mtime,
    apkSha256,
    lastDistributed: await readRecord(lastDistributedPath),
});
if (problems.length > 0) {
    console.error(`No se distribuye la beta:\n- ${problems.join("\n- ")}`);
    process.exitCode = 1;
} else {
    await distribute();
}

async function distribute() {
    const { versionCode, versionName } = metadata.elements[0];
    console.log(`Distribuyendo ${versionName} (${versionCode})…`);

    const firebaseConfig = JSON.parse(await readFile(firebaseConfigPath, "utf8"));
    const androidClient = firebaseConfig.client?.find(
        (client) => client.client_info?.android_client_info?.package_name === expectedPackage,
    );
    const appId = androidClient?.client_info?.mobilesdk_app_id;

    if (!appId) {
        throw new Error(`google-services.json no contiene la aplicación Android ${expectedPackage}.`);
    }

    const firebaseExecutable = process.platform === "win32" ? "firebase.cmd" : "firebase";
    const result = spawnSync(
        path.join(root, "node_modules", ".bin", firebaseExecutable),
        [
            "appdistribution:distribute",
            apkPath,
            "--project",
            "bobitos-dev",
            "--app",
            appId,
            "--groups",
            "bobitos-beta",
            "--release-notes-file",
            releaseNotesPath,
        ],
        { stdio: "inherit" },
    );

    if (result.error) {
        throw result.error;
    }
    if (result.status === 0) {
        await writeFile(lastDistributedPath, `${JSON.stringify({ versionCode, versionName, sha256: apkSha256 }, null, 2)}\n`);
    }
    process.exitCode = result.status ?? 1;
}
