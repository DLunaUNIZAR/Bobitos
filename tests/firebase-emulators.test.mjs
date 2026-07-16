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
  increment,
  onSnapshot,
  runTransaction,
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

test("dos clientes conectados reciben cambios del espacio en tiempo real", async () => {
  await seedSpace("realtime-space", "realtime-owner", ["realtime-member"]);
  const owner = verifiedFirestore("realtime-owner");
  const member = verifiedFirestore("realtime-member");
  const memberSpace = doc(member, "spaces", "realtime-space");

  await new Promise((resolve, reject) => {
    let initialSnapshotReceived = false;
    const timeout = setTimeout(() => {
      unsubscribe();
      reject(new Error("El segundo cliente no recibió el cambio en tiempo real"));
    }, 5_000);
    const unsubscribe = onSnapshot(
      memberSpace,
      (snapshot) => {
        if (!initialSnapshotReceived) {
          initialSnapshotReceived = true;
          updateDoc(doc(owner, "spaces", "realtime-space"), {
            name: "Nombre en tiempo real",
            updatedAt: serverTimestamp(),
          }).catch(reject);
          return;
        }
        if (snapshot.data()?.name === "Nombre en tiempo real") {
          clearTimeout(timeout);
          unsubscribe();
          resolve();
        }
      },
      (error) => {
        clearTimeout(timeout);
        reject(error);
      },
    );
  });
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

test("solo el propietario crea invitaciones de 72 horas", async () => {
  await seedSpace("invite-create", "invite-owner", ["invite-member"]);
  const owner = verifiedFirestore("invite-owner");
  const member = verifiedFirestore("invite-member");

  await assertSucceeds(
    createInvitation(owner, "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", "invite-create", "invite-owner"),
  );
  await assertFails(
    createInvitation(member, "BCDEFGHIJKLMNOPQRSTUVWXYZ234567A", "invite-create", "invite-member"),
  );
});

test("el token permite get pero nunca un listado general de invitaciones", async () => {
  await seedSpace("invite-list-a", "list-a-owner");
  await seedSpace("invite-list-b", "list-b-owner");
  const ownerA = verifiedFirestore("list-a-owner");
  const ownerB = verifiedFirestore("list-b-owner");
  const outsider = verifiedFirestore("invite-list-outsider");
  const tokenA = "CDEFGHIJKLMNOPQRSTUVWXYZ234567AB";
  const tokenB = "DEFGHIJKLMNOPQRSTUVWXYZ234567ABC";
  await createInvitation(ownerA, tokenA, "invite-list-a", "list-a-owner");
  await createInvitation(ownerB, tokenB, "invite-list-b", "list-b-owner");

  await assertSucceeds(getDoc(doc(outsider, "invitations", tokenA)));
  await assertSucceeds(
    getDocs(
      query(
        collection(ownerA, "invitations"),
        where("spaceId", "==", "invite-list-a"),
        where("status", "==", "ACTIVE"),
      ),
    ),
  );
  await assertFails(getDocs(collection(ownerA, "invitations")));
  await assertFails(
    getDoc(doc(unverifiedFirestore("invite-unverified"), "invitations", tokenA)),
  );
});

test("aceptar una invitación consume el token y crea la membresía atómicamente", async () => {
  await seedSpace("invite-accept", "accept-owner");
  const owner = verifiedFirestore("accept-owner");
  const guest = verifiedFirestore("accept-guest");
  const token = "EFGHIJKLMNOPQRSTUVWXYZ234567ABCD";
  await createInvitation(owner, token, "invite-accept", "accept-owner");

  await assertSucceeds(acceptInvitation(guest, token, "accept-guest"));
  const [invitation, membership, space] = await Promise.all([
    getDoc(doc(guest, "invitations", token)),
    getDoc(doc(guest, "memberships", "invite-accept_accept-guest")),
    getDoc(doc(guest, "spaces", "invite-accept")),
  ]);

  assert.equal(invitation.data().status, "USED");
  assert.equal(invitation.data().usedBy, "accept-guest");
  assert.equal(membership.data().joinedViaInvitationId, token);
  assert.equal(space.data().memberCount, 2);
});

test("una invitación usada no puede aceptarse de nuevo", async () => {
  await seedSpace("invite-used", "used-owner");
  const owner = verifiedFirestore("used-owner");
  const firstGuest = verifiedFirestore("used-first");
  const secondGuest = verifiedFirestore("used-second");
  const token = "FGHIJKLMNOPQRSTUVWXYZ234567ABCDE";
  await createInvitation(owner, token, "invite-used", "used-owner");
  await acceptInvitation(firstGuest, token, "used-first");

  await assertFails(acceptInvitation(secondGuest, token, "used-second"));
});

test("una invitación revocada o caducada es rechazada", async () => {
  await seedSpace("invite-invalid", "invalid-owner");
  const owner = verifiedFirestore("invalid-owner");
  const guest = verifiedFirestore("invalid-guest");
  const revokedToken = "GHIJKLMNOPQRSTUVWXYZ234567ABCDEF";
  const expiredToken = "HIJKLMNOPQRSTUVWXYZ234567ABCDEFG";
  await createInvitation(owner, revokedToken, "invite-invalid", "invalid-owner");
  await assertSucceeds(
    updateDoc(doc(owner, "invitations", revokedToken), {
      status: "REVOKED",
      revokedAt: serverTimestamp(),
    }),
  );
  await seedInvitation(expiredToken, "invite-invalid", "invalid-owner", {
    expiresAt: Timestamp.fromMillis(Date.now() - 60_000),
  });

  await assertFails(acceptInvitation(guest, revokedToken, "invalid-guest"));
  await assertFails(acceptInvitation(guest, expiredToken, "invalid-guest"));
});

test("dos cuentas simultáneas solo pueden consumir una invitación una vez", async () => {
  await seedSpace("invite-race", "race-owner");
  const owner = verifiedFirestore("race-owner");
  const guestA = verifiedFirestore("race-guest-a");
  const guestB = verifiedFirestore("race-guest-b");
  const token = "IJKLMNOPQRSTUVWXYZ234567ABCDEFGH";
  await createInvitation(owner, token, "invite-race", "race-owner");

  const results = await Promise.allSettled([
    acceptInvitation(guestA, token, "race-guest-a"),
    acceptInvitation(guestB, token, "race-guest-b"),
  ]);

  assert.equal(results.filter((result) => result.status === "fulfilled").length, 1);
  assert.equal(results.filter((result) => result.status === "rejected").length, 1);
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    const [space, invitation, memberships] = await Promise.all([
      getDoc(doc(firestore, "spaces", "invite-race")),
      getDoc(doc(firestore, "invitations", token)),
      getDocs(
        query(
          collection(firestore, "memberships"),
          where("spaceId", "==", "invite-race"),
        ),
      ),
    ]);
    assert.equal(space.data().memberCount, 2);
    assert.equal(invitation.data().status, "USED");
    assert.equal(memberships.size, 2);
  });
});

test("un miembro existente abre el espacio sin duplicar su membresía", async () => {
  await seedSpace("invite-existing", "existing-owner", ["existing-member"]);
  const owner = verifiedFirestore("existing-owner");
  const member = verifiedFirestore("existing-member");
  const token = "JKLMNOPQRSTUVWXYZ234567ABCDEFGHI";
  await createInvitation(owner, token, "invite-existing", "existing-owner");

  const spaceId = await acceptInvitation(member, token, "existing-member");
  const [space, invitation] = await Promise.all([
    getDoc(doc(member, "spaces", "invite-existing")),
    getDoc(doc(member, "invitations", token)),
  ]);

  assert.equal(spaceId, "invite-existing");
  assert.equal(space.data().memberCount, 2);
  assert.equal(invitation.data().status, "ACTIVE");
});

test("un espacio con 10 miembros no admite invitaciones ni nuevas membresías", async () => {
  const members = Array.from({ length: 9 }, (_, index) => `full-member-${index}`);
  await seedSpace("invite-full", "full-owner", members);
  const owner = verifiedFirestore("full-owner");
  const guest = verifiedFirestore("full-guest");
  const token = "KLMNOPQRSTUVWXYZ234567ABCDEFGHIJ";

  await assertFails(createInvitation(owner, token, "invite-full", "full-owner"));
  await seedInvitation(token, "invite-full", "full-owner");
  await assertFails(acceptInvitation(guest, token, "full-guest"));
});

test("todos los miembros pueden crear, editar y eliminar productos", async () => {
  await seedSpace("shopping-crud", "shopping-owner", ["shopping-member"]);
  const member = verifiedFirestore("shopping-member");
  const outsider = verifiedFirestore("shopping-outsider");
  const itemReference = doc(member, "spaces", "shopping-crud", "shoppingItems", "milk");

  await assertSucceeds(
    setDoc(itemReference, shoppingItem("shopping-member", { name: "Leche" })),
  );
  await assertSucceeds(
    updateDoc(itemReference, {
      name: "Leche entera",
      quantity: "2 litros",
      notes: "Sin lactosa",
      updatedBy: "shopping-member",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    getDoc(doc(outsider, "spaces", "shopping-crud", "shoppingItems", "milk")),
  );
  await assertSucceeds(writeBatch(member).delete(itemReference).commit());
});

test("los productos no admiten assigneeId ni campos ajenos al contrato", async () => {
  await seedSpace("shopping-schema", "schema-owner");
  const owner = verifiedFirestore("schema-owner");

  await assertFails(
    setDoc(
      doc(owner, "spaces", "shopping-schema", "shoppingItems", "assigned"),
      shoppingItem("schema-owner", { assigneeId: "schema-owner" }),
    ),
  );
});

test("marcar y desmarcar conserva una atribución coherente", async () => {
  await seedSpace("shopping-mark", "mark-owner", ["mark-member"]);
  await seedShoppingItem("shopping-mark", "bread", "mark-owner");
  const member = verifiedFirestore("mark-member");
  const itemReference = doc(member, "spaces", "shopping-mark", "shoppingItems", "bread");

  await assertFails(
    updateDoc(itemReference, {
      purchased: true,
      purchasedBy: "mark-owner",
      purchasedByName: "mark-owner",
      purchasedAt: serverTimestamp(),
      updatedBy: "mark-member",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    updateDoc(itemReference, {
      purchased: true,
      purchasedBy: "mark-member",
      purchasedByName: "mark-member",
      purchasedAt: serverTimestamp(),
      updatedBy: "mark-member",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    updateDoc(itemReference, {
      purchased: false,
      purchasedBy: null,
      purchasedByName: null,
      purchasedAt: null,
      updatedBy: "mark-member",
      updatedAt: serverTimestamp(),
    }),
  );

  const item = await getDoc(itemReference);
  assert.equal(item.data().purchased, false);
  assert.equal(item.data().purchasedBy, null);
});

test("dos clientes reciben la lista de la compra en tiempo real", async () => {
  await seedSpace("shopping-realtime", "shopping-live-owner", ["shopping-live-member"]);
  const owner = verifiedFirestore("shopping-live-owner");
  const member = verifiedFirestore("shopping-live-member");
  const memberItems = collection(member, "spaces", "shopping-realtime", "shoppingItems");

  await new Promise((resolve, reject) => {
    let initialSnapshotReceived = false;
    const timeout = setTimeout(() => {
      unsubscribe();
      reject(new Error("El segundo cliente no recibió el producto en tiempo real"));
    }, 5_000);
    const unsubscribe = onSnapshot(
      memberItems,
      (snapshot) => {
        if (!initialSnapshotReceived) {
          initialSnapshotReceived = true;
          setDoc(
            doc(owner, "spaces", "shopping-realtime", "shoppingItems", "apples"),
            shoppingItem("shopping-live-owner", { name: "Manzanas" }),
          ).catch(reject);
          return;
        }
        if (snapshot.docs.some((item) => item.data().name === "Manzanas")) {
          clearTimeout(timeout);
          unsubscribe();
          resolve();
        }
      },
      (error) => {
        clearTimeout(timeout);
        reject(error);
      },
    );
  });
});

test("ediciones simultáneas de campos distintos no corrompen el producto", async () => {
  await seedSpace("shopping-edits", "edit-owner", ["edit-member"]);
  await seedShoppingItem("shopping-edits", "rice", "edit-owner");
  const owner = verifiedFirestore("edit-owner");
  const member = verifiedFirestore("edit-member");

  await Promise.all([
    updateDoc(doc(owner, "spaces", "shopping-edits", "shoppingItems", "rice"), {
      quantity: "2 paquetes",
      updatedBy: "edit-owner",
      updatedAt: serverTimestamp(),
    }),
    updateDoc(doc(member, "spaces", "shopping-edits", "shoppingItems", "rice"), {
      notes: "Integral",
      updatedBy: "edit-member",
      updatedAt: serverTimestamp(),
    }),
  ]);

  const item = await getDoc(
    doc(owner, "spaces", "shopping-edits", "shoppingItems", "rice"),
  );
  assert.equal(item.data().quantity, "2 paquetes");
  assert.equal(item.data().notes, "Integral");
});

test("limpiar comprados no borra un producto desmarcado simultáneamente", async () => {
  await seedSpace("shopping-clear", "clear-owner", ["clear-member"]);
  await seedShoppingItem("shopping-clear", "keep", "clear-owner", { purchased: true });
  await seedShoppingItem("shopping-clear", "delete", "clear-owner", { purchased: true });
  const owner = verifiedFirestore("clear-owner");
  const member = verifiedFirestore("clear-member");
  const candidates = await getDocs(
    query(
      collection(owner, "spaces", "shopping-clear", "shoppingItems"),
      where("purchased", "==", true),
    ),
  );

  await updateDoc(doc(member, "spaces", "shopping-clear", "shoppingItems", "keep"), {
    purchased: false,
    purchasedBy: null,
    purchasedByName: null,
    purchasedAt: null,
    updatedBy: "clear-member",
    updatedAt: serverTimestamp(),
  });
  await runTransaction(owner, async (transaction) => {
    const currentItems = [];
    for (const candidate of candidates.docs) {
      currentItems.push(await transaction.get(candidate.ref));
    }
    for (const current of currentItems) {
      if (current.exists() && current.data().purchased === true) {
        transaction.delete(current.ref);
      }
    }
  });

  const [kept, removed] = await Promise.all([
    getDoc(doc(owner, "spaces", "shopping-clear", "shoppingItems", "keep")),
    getDoc(doc(owner, "spaces", "shopping-clear", "shoppingItems", "delete")),
  ]);
  assert.equal(kept.exists(), true);
  assert.equal(kept.data().purchased, false);
  assert.equal(removed.exists(), false);
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

function shoppingItem(userId, overrides = {}) {
  return {
    name: "Producto",
    quantity: null,
    notes: null,
    purchased: false,
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    purchasedBy: null,
    purchasedByName: null,
    purchasedAt: null,
    ...overrides,
  };
}

async function seedShoppingItem(spaceId, itemId, userId, overrides = {}) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    const purchased = overrides.purchased === true;
    await setDoc(doc(context.firestore(), "spaces", spaceId, "shoppingItems", itemId), {
      name: "Producto",
      quantity: null,
      notes: null,
      purchased,
      createdBy: userId,
      createdByName: userId,
      createdAt: timestamp,
      updatedBy: userId,
      updatedAt: timestamp,
      purchasedBy: purchased ? userId : null,
      purchasedByName: purchased ? userId : null,
      purchasedAt: purchased ? timestamp : null,
      ...overrides,
    });
  });
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

function createInvitation(firestore, invitationId, spaceId, ownerId) {
  return setDoc(doc(firestore, "invitations", invitationId), {
    spaceId,
    createdBy: ownerId,
    createdAt: serverTimestamp(),
    expiresAt: Timestamp.fromMillis(Date.now() + 72 * 60 * 60 * 1000 - 5_000),
    status: "ACTIVE",
    usedBy: null,
    usedAt: null,
    revokedAt: null,
  });
}

function acceptInvitation(firestore, invitationId, userId) {
  return getDoc(doc(firestore, "invitations", invitationId)).then(async (initialInvitation) => {
    if (!initialInvitation.exists()) throw new Error("Invitation not found");
    const initialSpaceId = initialInvitation.data().spaceId;
    const memberships = await getDocs(
      query(
        collection(firestore, "memberships"),
        where("userId", "==", userId),
        where("status", "==", "ACTIVE"),
      ),
    );
    if (memberships.docs.some((membership) => membership.data().spaceId === initialSpaceId)) {
      return initialSpaceId;
    }

    return consumeInvitation(firestore, invitationId, userId);
  });
}

function consumeInvitation(firestore, invitationId, userId) {
  return runTransaction(firestore, async (transaction) => {
    const invitationReference = doc(firestore, "invitations", invitationId);
    const invitation = await transaction.get(invitationReference);
    if (!invitation.exists()) throw new Error("Invitation not found");
    const spaceId = invitation.data().spaceId;
    const membershipReference = doc(firestore, "memberships", `${spaceId}_${userId}`);

    transaction.update(invitationReference, {
      status: "USED",
      usedBy: userId,
      usedAt: serverTimestamp(),
    });
    transaction.set(membershipReference, {
      spaceId,
      userId,
      displayName: userId,
      role: "MEMBER",
      status: "ACTIVE",
      joinedAt: serverTimestamp(),
      joinedViaInvitationId: invitationId,
    });
    transaction.update(doc(firestore, "spaces", spaceId), {
      memberCount: increment(1),
      lastMembershipChangeUserId: userId,
      updatedAt: serverTimestamp(),
    });
    return spaceId;
  });
}

async function seedInvitation(invitationId, spaceId, ownerId, overrides = {}) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const now = Timestamp.now();
    await setDoc(doc(context.firestore(), "invitations", invitationId), {
      spaceId,
      createdBy: ownerId,
      createdAt: now,
      expiresAt: Timestamp.fromMillis(Date.now() + 72 * 60 * 60 * 1000),
      status: "ACTIVE",
      usedBy: null,
      usedAt: null,
      revokedAt: null,
      ...overrides,
    });
  });
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
