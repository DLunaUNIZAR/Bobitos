import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, test } from "node:test";
import {
  assertFails,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteApp, initializeApp } from "firebase/app";
import {
  applyActionCode,
  confirmPasswordReset,
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  reload,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";

const projectId = "demo-bobitos";
let testEnvironment;
let authApp;
let createdUser;
let auth;

const testEmail = "firebase-test@bobitos.invalid";
const initialPassword = "bobitos-test-password";
const updatedPassword = "bobitos-updated-password";

before(async () => {
  const rules = await readFile("firestore.rules", "utf8");
  testEnvironment = await initializeTestEnvironment({
    projectId,
    firestore: { rules },
  });

  authApp = initializeApp(
    {
      apiKey: "demo-api-key",
      appId: "1:000000000000:web:demo-bobitos",
      authDomain: `${projectId}.firebaseapp.com`,
      projectId,
    },
    `emulator-test-${Date.now()}`,
  );

  auth = getAuth(authApp);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", {
    disableWarnings: true,
  });
  createdUser = await createUserWithEmailAndPassword(
    auth,
    testEmail,
    initialPassword,
  );
});

after(async () => {
  await Promise.all([
    testEnvironment?.cleanup(),
    authApp ? deleteApp(authApp) : Promise.resolve(),
  ]);
});

test("Authentication permite crear una cuenta local", () => {
  assert.equal(createdUser.user.email, testEmail);
  assert.ok(createdUser.user.uid);
});

test("Authentication permite verificar el correo local", async () => {
  await sendEmailVerification(createdUser.user);
  const code = await findOutOfBandCode("VERIFY_EMAIL");

  await applyActionCode(auth, code);
  await reload(createdUser.user);

  assert.equal(createdUser.user.emailVerified, true);
});

test("Authentication permite recuperar la contraseña local", async () => {
  await sendPasswordResetEmail(auth, testEmail);
  const code = await findOutOfBandCode("PASSWORD_RESET");

  await confirmPasswordReset(auth, code, updatedPassword);
  await signOut(auth);
  const result = await signInWithEmailAndPassword(
    auth,
    testEmail,
    updatedPassword,
  );

  assert.equal(result.user.email, testEmail);
});

test("Firestore rechaza lecturas sin autenticar", async () => {
  const firestore = testEnvironment.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(firestore, "spaces", "private-space")));
});

test("Firestore permanece cerrado incluso con autenticación", async () => {
  const firestore = testEnvironment
    .authenticatedContext("test-user")
    .firestore();
  await assertFails(getDoc(doc(firestore, "spaces", "private-space")));
});

async function findOutOfBandCode(requestType) {
  const response = await fetch(
    `http://127.0.0.1:9099/emulator/v1/projects/${projectId}/oobCodes`,
  );
  assert.equal(response.ok, true);
  const payload = await response.json();
  const match = payload.oobCodes
    .slice()
    .reverse()
    .find(
      (entry) =>
        entry.email === testEmail &&
        (entry.requestType === requestType || entry.mode === requestType),
    );

  assert.ok(match, `No existe un código ${requestType} para ${testEmail}`);
  return match.oobCode;
}
