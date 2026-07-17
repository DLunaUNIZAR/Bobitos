import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const ports = [9099, 8080];

function findAdb() {
  const candidates = [
    process.env.ANDROID_SDK_ROOT && join(process.env.ANDROID_SDK_ROOT, "platform-tools", "adb"),
    process.env.ANDROID_HOME && join(process.env.ANDROID_HOME, "platform-tools", "adb"),
    join(homedir(), "Library", "Android", "sdk", "platform-tools", "adb"),
  ].filter(Boolean);

  return candidates.find((candidate) => existsSync(candidate)) ?? "adb";
}

function run(adb, args, options = {}) {
  return spawnSync(adb, args, {
    encoding: "utf8",
    ...options,
  });
}

function fail(message, result) {
  const detail = result?.stderr?.trim() || result?.stdout?.trim();
  console.error(`Error: ${message}`);
  if (detail) console.error(detail);
  process.exit(1);
}

const adb = findAdb();
const devicesResult = run(adb, ["devices"]);

if (devicesResult.error?.code === "ENOENT") {
  fail("no se encuentra adb. Instala Android SDK Platform-Tools o define ANDROID_SDK_ROOT.");
}
if (devicesResult.status !== 0) {
  fail("adb no ha podido consultar los dispositivos conectados.", devicesResult);
}

const devices = devicesResult.stdout
  .split("\n")
  .slice(1)
  .map((line) => line.trim().split(/\s+/))
  .filter(([, state]) => state === "device")
  .map(([serial]) => serial);

const requestedSerial = process.env.ANDROID_SERIAL;
if (requestedSerial && !devices.includes(requestedSerial)) {
  fail(`ANDROID_SERIAL=${requestedSerial} no corresponde a un dispositivo disponible.`);
}
if (!requestedSerial && devices.length === 0) {
  fail("no hay ningún emulador o dispositivo Android activo.");
}
if (!requestedSerial && devices.length > 1) {
  fail(`hay varios dispositivos activos (${devices.join(", ")}). Define ANDROID_SERIAL antes de repetir el comando.`);
}

const serial = requestedSerial ?? devices[0];
for (const port of ports) {
  const result = run(adb, ["-s", serial, "reverse", `tcp:${port}`, `tcp:${port}`]);
  if (result.status !== 0) {
    fail(`no se pudo redirigir el puerto ${port} al dispositivo ${serial}.`, result);
  }
}

const listResult = run(adb, ["-s", serial, "reverse", "--list"]);
if (listResult.status !== 0) {
  fail("no se pudo comprobar la redirección de puertos.", listResult);
}

console.log(`Firebase conectado con ${serial}:`);
console.log(listResult.stdout.trim());
console.log("Ya puedes ejecutar Bobitos desde Android Studio.");
