import { access, readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { constants } from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const firebaseConfigPath = path.join(root, "app", "google-services.json");
const apkPath = path.join(root, "app", "build", "outputs", "apk", "release", "app-release.apk");
const releaseNotesPath = path.join(root, "distribution", "release-notes.txt");
const expectedPackage = "com.dlunaunizar.bobitos";

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
process.exitCode = result.status ?? 1;
