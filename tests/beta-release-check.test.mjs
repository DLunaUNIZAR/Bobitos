import assert from "node:assert/strict";
import { test } from "node:test";
import { releaseProblems } from "../scripts/beta-release-check.mjs";

const metadata = (versionCode, versionName) => ({ elements: [{ versionCode, versionName }] });
const notesModifiedAt = new Date("2026-10-08T10:00:00Z");
const freshApk = new Date("2026-10-08T11:00:00Z");

test("un APK nuevo, construido después de las notas y con versión mayor, se puede distribuir", () => {
  assert.deepEqual(
    releaseProblems({
      metadata: metadata(16, "0.1.0-beta.16"),
      apkModifiedAt: freshApk,
      notesModifiedAt,
      lastDistributed: { versionCode: 15, versionName: "0.1.0-beta.15" },
    }),
    [],
  );
});

test("sin versión distribuida antes en este equipo basta con que el APK sea válido", () => {
  assert.deepEqual(
    releaseProblems({ metadata: metadata(16, "0.1.0-beta.16"), apkModifiedAt: freshApk, notesModifiedAt }),
    [],
  );
});

test("un APK más antiguo que las notas se rechaza (la compilación falló y quedó el anterior)", () => {
  const problems = releaseProblems({
    metadata: metadata(15, "0.1.0-beta.15"),
    apkModifiedAt: new Date("2026-10-07T09:00:00Z"),
    notesModifiedAt,
  });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /anterior a las notas/);
});

test("una versión ya distribuida o anterior se rechaza", () => {
  for (const versionCode of [15, 16]) {
    const problems = releaseProblems({
      metadata: metadata(versionCode, `0.1.0-beta.${versionCode}`),
      apkModifiedAt: freshApk,
      notesModifiedAt,
      lastDistributed: { versionCode: 16, versionName: "0.1.0-beta.16" },
    });
    assert.equal(problems.length, 1);
    assert.match(problems[0], /0\.1\.0-beta\.16/);
  }
});

test("un APK construido sin -PVERSION_CODE (versionCode 1) se rechaza", () => {
  const problems = releaseProblems({ metadata: metadata(1, "0.1.0"), apkModifiedAt: freshApk, notesModifiedAt });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /VERSION_CODE/);
});

test("sin metadatos de versión del APK no se distribuye", () => {
  const problems = releaseProblems({ metadata: null, apkModifiedAt: freshApk, notesModifiedAt });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /assembleRelease/);
});

test("con registro previo, editar las notas después de compilar no impide distribuir", () => {
  assert.deepEqual(
    releaseProblems({
      metadata: metadata(17, "0.1.0-beta.17"),
      apkModifiedAt: new Date("2026-10-08T09:00:00Z"),
      notesModifiedAt,
      apkSha256: "nuevo",
      lastDistributed: { versionCode: 16, versionName: "0.1.0-beta.16", sha256: "viejo" },
    }),
    [],
  );
});

test("sin registro, un APK más antiguo que las notas explica cómo forzar la compilación", () => {
  const [problem] = releaseProblems({
    metadata: metadata(17, "0.1.0-beta.17"),
    apkModifiedAt: new Date("2026-10-08T09:00:00Z"),
    notesModifiedAt,
  });
  assert.match(problem, /--rerun-tasks/);
});

test("el mismo APK que el último distribuido se rechaza aunque cambie la versión registrada", () => {
  const problems = releaseProblems({
    metadata: metadata(17, "0.1.0-beta.17"),
    apkModifiedAt: freshApk,
    notesModifiedAt,
    apkSha256: "igual",
    lastDistributed: { versionCode: 16, versionName: "0.1.0-beta.16", sha256: "igual" },
  });
  assert.equal(problems.length, 1);
  assert.match(problems[0], /mismo APK/);
});

test("un registro de la última beta dañado se rechaza en lugar de ignorarse", () => {
  for (const lastDistributed of [{}, { versionCode: "16", versionName: "0.1.0-beta.16" }, null]) {
    const problems = releaseProblems({
      metadata: metadata(17, "0.1.0-beta.17"),
      apkModifiedAt: freshApk,
      notesModifiedAt,
      lastDistributed,
    });
    assert.equal(problems.length, 1);
    assert.match(problems[0], /\.last-distributed\.json/);
  }
});

test("una versión repetida indica cómo reenviarla a propósito", () => {
  const [problem] = releaseProblems({
    metadata: metadata(16, "0.1.0-beta.16"),
    apkModifiedAt: freshApk,
    notesModifiedAt,
    lastDistributed: { versionCode: 16, versionName: "0.1.0-beta.16" },
  });
  assert.match(problem, /borra distribution\/\.last-distributed\.json/);
});
