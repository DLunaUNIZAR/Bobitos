import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
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
import {
  Timestamp,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  collection,
} from "firebase/firestore";

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

test("Firestore exige correo verificado para crear un espacio", async () => {
  const firestore = unverifiedFirestore("unverified-user");
  await assertFails(createSpace(firestore, "unverified-space", "unverified-user"));
});

test("Firestore crea el espacio y su propietario en una escritura atómica", async () => {
  const firestore = verifiedFirestore("creator");

  await assertSucceeds(createSpace(firestore, "created-space", "creator"));
  const [space, membership] = await Promise.all([
    getDoc(doc(firestore, "spaces", "created-space")),
    getDoc(doc(firestore, "memberships", "created-space_creator")),
  ]);

  assert.equal(space.data().ownerId, "creator");
  assert.equal(space.data().memberCount, 1);
  assert.equal(membership.data().role, "OWNER");
});

test("solo los miembros pueden leer un espacio privado", async () => {
  await seedSpace("private-space", "private-owner", ["private-member"]);
  const member = verifiedFirestore("private-member");
  const outsider = verifiedFirestore("private-outsider");

  await assertSucceeds(getDoc(doc(member, "spaces", "private-space")));
  await assertFails(getDoc(doc(outsider, "spaces", "private-space")));
});

test("un usuario puede listar sus membresías pero no las de espacios ajenos", async () => {
  await seedSpace("listed-space", "list-owner", ["list-member"]);
  const member = verifiedFirestore("list-member");
  const outsider = verifiedFirestore("list-outsider");

  await assertSucceeds(
    getDocs(
      query(
        collection(member, "memberships"),
        where("userId", "==", "list-member"),
        where("status", "==", "ACTIVE"),
      ),
    ),
  );
  await assertFails(
    getDocs(
      query(
        collection(outsider, "memberships"),
        where("spaceId", "==", "listed-space"),
        where("status", "==", "ACTIVE"),
      ),
    ),
  );
});

test("solo el propietario puede cambiar el nombre", async () => {
  await seedSpace("rename-space", "rename-owner", ["rename-member"]);
  const owner = verifiedFirestore("rename-owner");
  const member = verifiedFirestore("rename-member");

  await assertFails(
    updateDoc(doc(member, "spaces", "rename-space"), {
      name: "Nombre prohibido",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    updateDoc(doc(owner, "spaces", "rename-space"), {
      name: "Nombre permitido",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("la transferencia de propiedad cambia ambos roles de forma atómica", async () => {
  await seedSpace("transfer-space", "transfer-owner", ["transfer-member"]);
  const owner = verifiedFirestore("transfer-owner");
  const batch = writeBatch(owner);
  batch.update(doc(owner, "spaces", "transfer-space"), {
    ownerId: "transfer-member",
    updatedAt: serverTimestamp(),
  });
  batch.update(
    doc(owner, "memberships", "transfer-space_transfer-owner"),
    { role: "MEMBER" },
  );
  batch.update(
    doc(owner, "memberships", "transfer-space_transfer-member"),
    { role: "OWNER" },
  );

  await assertSucceeds(batch.commit());
  const space = await getDoc(doc(owner, "spaces", "transfer-space"));
  assert.equal(space.data().ownerId, "transfer-member");
});

test("una transferencia incompleta no puede dejar roles inconsistentes", async () => {
  await seedSpace("broken-transfer", "broken-owner", ["broken-member"]);
  const owner = verifiedFirestore("broken-owner");
  const batch = writeBatch(owner);
  batch.update(doc(owner, "spaces", "broken-transfer"), {
    ownerId: "broken-member",
    updatedAt: serverTimestamp(),
  });
  batch.update(
    doc(owner, "memberships", "broken-transfer_broken-member"),
    { role: "OWNER" },
  );

  await assertFails(batch.commit());
});

test("el propietario no puede abandonar el espacio sin transferirlo", async () => {
  await seedSpace("owner-leave", "leaving-owner", ["remaining-member"]);
  const owner = verifiedFirestore("leaving-owner");
  const batch = writeBatch(owner);
  batch.update(doc(owner, "spaces", "owner-leave"), {
    memberCount: 1,
    lastMembershipChangeUserId: "leaving-owner",
    updatedAt: serverTimestamp(),
  });
  batch.delete(doc(owner, "memberships", "owner-leave_leaving-owner"));

  await assertFails(batch.commit());
});

test("al abandonar, las tareas pendientes del miembro quedan sin responsable", async () => {
  await seedSpace("leave-space", "stay-owner", ["leaving-member"], {
    taskId: "pending-task",
    assigneeId: "leaving-member",
  });
  const member = verifiedFirestore("leaving-member");
  const batch = writeBatch(member);
  batch.update(doc(member, "spaces", "leave-space"), {
    memberCount: 1,
    lastMembershipChangeUserId: "leaving-member",
    updatedAt: serverTimestamp(),
  });
  batch.update(doc(member, "spaces", "leave-space", "tasks", "pending-task"), {
    assigneeId: null,
    updatedAt: serverTimestamp(),
  });
  batch.delete(doc(member, "memberships", "leave-space_leaving-member"));

  await assertSucceeds(batch.commit());
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const task = await getDoc(
      doc(context.firestore(), "spaces", "leave-space", "tasks", "pending-task"),
    );
    assert.equal(task.data().assigneeId, null);
  });
});

test("el propietario puede expulsar a un miembro con la misma operación segura", async () => {
  await seedSpace("remove-space", "remove-owner", ["removed-member"]);
  const owner = verifiedFirestore("remove-owner");
  const batch = writeBatch(owner);
  batch.update(doc(owner, "spaces", "remove-space"), {
    memberCount: 1,
    lastMembershipChangeUserId: "removed-member",
    updatedAt: serverTimestamp(),
  });
  batch.delete(doc(owner, "memberships", "remove-space_removed-member"));

  await assertSucceeds(batch.commit());
});

function verifiedFirestore(uid) {
  return testEnvironment.authenticatedContext(uid, {
    email: `${uid}@bobitos.invalid`,
    email_verified: true,
  }).firestore();
}

function unverifiedFirestore(uid) {
  return testEnvironment.authenticatedContext(uid, {
    email: `${uid}@bobitos.invalid`,
    email_verified: false,
  }).firestore();
}

function createSpace(firestore, spaceId, userId) {
  const batch = writeBatch(firestore);
  batch.set(doc(firestore, "spaces", spaceId), {
    name: "Casa",
    ownerId: userId,
    createdBy: userId,
    memberCount: 1,
    lastMembershipChangeUserId: userId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  batch.set(doc(firestore, "memberships", `${spaceId}_${userId}`), {
    spaceId,
    userId,
    displayName: userId,
    role: "OWNER",
    status: "ACTIVE",
    joinedAt: serverTimestamp(),
  });
  return batch.commit();
}

async function seedSpace(spaceId, ownerId, memberIds = [], task = null) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    const timestamp = Timestamp.now();
    const writes = [
      setDoc(doc(firestore, "spaces", spaceId), {
        name: "Espacio de prueba",
        ownerId,
        createdBy: ownerId,
        memberCount: memberIds.length + 1,
        lastMembershipChangeUserId: ownerId,
        createdAt: timestamp,
        updatedAt: timestamp,
      }),
      setDoc(doc(firestore, "memberships", `${spaceId}_${ownerId}`), {
        spaceId,
        userId: ownerId,
        displayName: ownerId,
        role: "OWNER",
        status: "ACTIVE",
        joinedAt: timestamp,
      }),
      ...memberIds.map((memberId) =>
        setDoc(doc(firestore, "memberships", `${spaceId}_${memberId}`), {
          spaceId,
          userId: memberId,
          displayName: memberId,
          role: "MEMBER",
          status: "ACTIVE",
          joinedAt: timestamp,
        }),
      ),
    ];
    if (task) {
      writes.push(
        setDoc(doc(firestore, "spaces", spaceId, "tasks", task.taskId), {
          title: "Tarea pendiente",
          status: "TODO",
          assigneeId: task.assigneeId,
          updatedAt: timestamp,
        }),
      );
    }
    await Promise.all(writes);
  });
}

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
