// Comprobaciones previas a distribuir una beta. Evitan volver a subir un APK viejo (p. ej. si
// `./gradlew assembleRelease` falló y quedó el de la beta anterior) o uno sin número de versión.

/**
 * @param {object} input
 * @param {object | null} input.metadata contenido de output-metadata.json junto al APK.
 * @param {Date} input.apkModifiedAt fecha de modificación del APK.
 * @param {Date} input.notesModifiedAt fecha de modificación de las notas de la versión.
 * @param {{versionCode: number, versionName: string} | undefined} input.lastDistributed última beta
 *   distribuida desde este equipo.
 * @returns {string[]} motivos para no distribuir; vacío si todo está bien.
 */
export function releaseProblems({ metadata, apkModifiedAt, notesModifiedAt, lastDistributed }) {
  const element = metadata?.elements?.[0];
  if (!Number.isInteger(element?.versionCode)) {
    return ["No se encuentra la versión del APK. Ejecuta ./gradlew assembleRelease antes de distribuir."];
  }
  const { versionCode, versionName } = element;
  const problems = [];
  if (versionCode <= 1) {
    problems.push(
      `El APK tiene versionCode ${versionCode}: constrúyelo con -PVERSION_CODE=<n> -PVERSION_NAME=0.1.0-beta.<n>.`,
    );
  }
  if (lastDistributed && versionCode <= lastDistributed.versionCode) {
    problems.push(
      `El APK es ${versionName} (${versionCode}) y desde este equipo ya se distribuyó ` +
        `${lastDistributed.versionName} (${lastDistributed.versionCode}). Incrementa la versión y vuelve a compilar.`,
    );
  }
  if (apkModifiedAt < notesModifiedAt) {
    problems.push(
      `El APK (${versionName}) es anterior a las notas de la versión: ¿falló ./gradlew assembleRelease? ` +
        "Vuelve a compilar antes de distribuir.",
    );
  }
  return problems;
}
