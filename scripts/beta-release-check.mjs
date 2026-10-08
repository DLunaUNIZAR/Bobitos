// Comprobaciones previas a distribuir una beta. Evitan volver a subir un APK viejo (p. ej. si
// `./gradlew assembleRelease` falló y quedó el de la beta anterior) o uno sin número de versión.

const RECORD = "distribution/.last-distributed.json";

/**
 * @param {object} input
 * @param {object | null} input.metadata contenido de output-metadata.json junto al APK.
 * @param {Date} input.apkModifiedAt fecha de modificación del APK.
 * @param {Date} input.notesModifiedAt fecha de modificación de las notas de la versión.
 * @param {string} [input.apkSha256] huella del APK que se va a subir.
 * @param {unknown} [input.lastDistributed] registro de la última beta distribuida desde este equipo
 *   (`undefined` si no existe; cualquier otro valor se valida).
 * @returns {string[]} motivos para no distribuir; vacío si todo está bien.
 */
export function releaseProblems({ metadata, apkModifiedAt, notesModifiedAt, apkSha256, lastDistributed }) {
  const element = metadata?.elements?.[0];
  if (!Number.isInteger(element?.versionCode)) {
    return ["No se encuentra la versión del APK. Ejecuta ./gradlew assembleRelease antes de distribuir."];
  }
  if (lastDistributed !== undefined && !isValidRecord(lastDistributed)) {
    return [`El registro de la última beta (${RECORD}) está dañado. Revísalo o bórralo si sabes qué se distribuyó.`];
  }
  const { versionCode, versionName } = element;
  const problems = [];
  if (versionCode <= 1) {
    problems.push(
      `El APK tiene versionCode ${versionCode}: constrúyelo con -PVERSION_CODE=<n> -PVERSION_NAME=0.1.0-beta.<n>.`,
    );
  }
  if (lastDistributed) {
    if (apkSha256 && lastDistributed.sha256 === apkSha256) {
      problems.push(
        `Es el mismo APK que ya se distribuyó (${lastDistributed.versionName}): ¿falló ./gradlew assembleRelease? ` +
          "Vuelve a compilar con la versión nueva.",
      );
    } else if (versionCode <= lastDistributed.versionCode) {
      problems.push(
        `El APK es ${versionName} (${versionCode}) y desde este equipo ya se distribuyó ` +
          `${lastDistributed.versionName} (${lastDistributed.versionCode}). Incrementa la versión y vuelve a ` +
          `compilar; para reenviar esa misma versión a propósito, borra ${RECORD}.`,
      );
    }
  } else if (apkModifiedAt < notesModifiedAt) {
    // Sin registro (primera beta en este equipo) solo queda comparar fechas.
    problems.push(
      `El APK (${versionName}) es anterior a las notas de la versión: ¿falló ./gradlew assembleRelease? ` +
        "Si solo editaste las notas después de compilar, recompila con --rerun-tasks.",
    );
  }
  return problems;
}

function isValidRecord(record) {
  return (
    record !== null &&
    typeof record === "object" &&
    Number.isInteger(record.versionCode) &&
    typeof record.versionName === "string" &&
    (record.sha256 === undefined || typeof record.sha256 === "string")
  );
}
