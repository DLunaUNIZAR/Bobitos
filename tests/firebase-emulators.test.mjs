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

// Debe coincidir con recipeAdmins() en firestore.rules (ver docs/RECIPES_ADMIN.md).
const RECIPE_ADMIN_UID = "dWWH7eRhHEPopJf5BHPB3Dp6fry1";

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
    assigneeName: null,
    updatedBy: "leaving-member",
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

test("los miembros crean, editan y reasignan tareas a miembros activos", async () => {
  await seedSpace("tasks-crud", "tasks-owner", ["tasks-member"]);
  const owner = verifiedFirestore("tasks-owner");
  const member = verifiedFirestore("tasks-member");
  const outsider = verifiedFirestore("tasks-outsider");
  const reference = doc(owner, "spaces", "tasks-crud", "tasks", "clean");

  await assertSucceeds(setDoc(reference, taskData("tasks-owner", "tasks-member")));
  await assertSucceeds(updateDoc(doc(member, "spaces", "tasks-crud", "tasks", "clean"), {
    title: "Limpiar cocina",
    assigneeId: "tasks-owner",
    assigneeName: "tasks-owner",
    priority: "HIGH",
    updatedBy: "tasks-member",
    updatedAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(outsider, "spaces", "tasks-crud", "tasks", "clean")));
  await assertSucceeds(
    writeBatch(member)
      .delete(doc(member, "spaces", "tasks-crud", "tasks", "clean"))
      .commit(),
  );
});

test("una tarea no puede asignarse a un usuario ajeno", async () => {
  await seedSpace("tasks-assignee", "assignee-owner");
  const owner = verifiedFirestore("assignee-owner");
  await assertFails(setDoc(
    doc(owner, "spaces", "tasks-assignee", "tasks", "invalid"),
    taskData("assignee-owner", "outsider"),
  ));
});

test("cualquier miembro completa y reabre una tarea con atribución coherente", async () => {
  await seedSpace("tasks-complete", "complete-owner", ["complete-member"]);
  const owner = verifiedFirestore("complete-owner");
  const member = verifiedFirestore("complete-member");
  const reference = doc(owner, "spaces", "tasks-complete", "tasks", "laundry");
  await setDoc(reference, taskData("complete-owner", "complete-owner"));

  await assertSucceeds(updateDoc(doc(member, "spaces", "tasks-complete", "tasks", "laundry"), {
    status: "DONE",
    completedBy: "complete-member",
    completedByName: "complete-member",
    completedAt: serverTimestamp(),
    updatedBy: "complete-member",
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(reference, {
    status: "TODO",
    completedBy: null,
    completedByName: null,
    completedAt: null,
    updatedBy: "complete-owner",
    updatedAt: serverTimestamp(),
  }));
});

test("una atribución de completado falsa es rechazada", async () => {
  await seedSpace("tasks-attribution", "attribution-owner", ["attribution-member"]);
  const member = verifiedFirestore("attribution-member");
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(
      doc(context.firestore(), "spaces", "tasks-attribution", "tasks", "task"),
      seededTaskData("attribution-owner", "attribution-member"),
    );
  });
  await assertFails(updateDoc(doc(member, "spaces", "tasks-attribution", "tasks", "task"), {
    status: "DONE",
    completedBy: "attribution-owner",
    completedByName: "attribution-owner",
    completedAt: serverTimestamp(),
    updatedBy: "attribution-member",
    updatedAt: serverTimestamp(),
  }));
});

test("dos completados simultáneos dejan una única atribución válida", async () => {
  await seedSpace("tasks-race", "task-race-owner", ["task-race-member"]);
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(
      doc(context.firestore(), "spaces", "tasks-race", "tasks", "race"),
      seededTaskData("task-race-owner", "task-race-member"),
    );
  });
  const owner = verifiedFirestore("task-race-owner");
  const member = verifiedFirestore("task-race-member");
  const results = await Promise.allSettled([
    completeTask(owner, "tasks-race", "race", "task-race-owner"),
    completeTask(member, "tasks-race", "race", "task-race-member"),
  ]);

  const task = await getDoc(doc(owner, "spaces", "tasks-race", "tasks", "race"));
  assert.equal(task.data().status, "DONE");
  assert.ok(results.some((result) => result.status === "fulfilled"));
  assert.ok(["task-race-owner", "task-race-member"].includes(task.data().completedBy));
  assert.ok(task.data().completedAt instanceof Timestamp);
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

test("los productos aceptan supermercado y marca opcionales y validan el enum", async () => {
  await seedSpace("shopping-market", "market-owner");
  const owner = verifiedFirestore("market-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "shopping-market", "shoppingItems", "ok"),
      shoppingItem("market-owner", { supermarket: "MERCADONA", brand: "Hacendado" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "shopping-market", "shoppingItems", "bad-market"),
      shoppingItem("market-owner", { supermarket: "LIDL" }),
    ),
  );
});

test("las tareas aceptan tipo opcional y validan el enum", async () => {
  await seedSpace("task-type", "task-type-owner");
  const owner = verifiedFirestore("task-type-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "task-type", "tasks", "ok"),
      taskData("task-type-owner", "task-type-owner", { type: "LIMPIEZA" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "task-type", "tasks", "bad-type"),
      taskData("task-type-owner", "task-type-owner", { type: "COCINA" }),
    ),
  );
});

test("las tareas aceptan recurrencia opcional y validan unidad e intervalo", async () => {
  await seedSpace("task-rec", "task-rec-owner");
  const owner = verifiedFirestore("task-rec-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "task-rec", "tasks", "ok"),
      taskData("task-rec-owner", "task-rec-owner", {
        recurrenceUnit: "WEEK",
        recurrenceInterval: 2,
      }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "task-rec", "tasks", "bad-unit"),
      taskData("task-rec-owner", "task-rec-owner", {
        recurrenceUnit: "YEAR",
        recurrenceInterval: 1,
      }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "task-rec", "tasks", "bad-interval"),
      taskData("task-rec-owner", "task-rec-owner", {
        recurrenceUnit: "DAY",
        recurrenceInterval: 0,
      }),
    ),
  );
});

test("las tareas aceptan startAt opcional y validan que sea timestamp", async () => {
  await seedSpace("task-start", "task-start-owner");
  const owner = verifiedFirestore("task-start-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "task-start", "tasks", "ok"),
      taskData("task-start-owner", "task-start-owner", { startAt: Timestamp.now() }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "task-start", "tasks", "bad-start"),
      taskData("task-start-owner", "task-start-owner", { startAt: "no-timestamp" }),
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

test("todos los miembros ven y editan eventos aunque no sean participantes", async () => {
  await seedSpace("calendar-shared", "calendar-owner", ["calendar-member"]);
  const owner = verifiedFirestore("calendar-owner");
  const member = verifiedFirestore("calendar-member");
  const reference = doc(owner, "spaces", "calendar-shared", "events", "event-1");
  await assertSucceeds(setDoc(reference, eventData("calendar-owner", { participantIds: ["calendar-owner"], participantNames: ["calendar-owner"] })));
  const memberReference = doc(member, "spaces", "calendar-shared", "events", "event-1");
  await assertSucceeds(getDoc(memberReference));
  await assertSucceeds(updateDoc(memberReference, { title: "Editado", updatedBy: "calendar-member", updatedAt: serverTimestamp() }));
});

test("un usuario externo no puede leer eventos y los intervalos inválidos se rechazan", async () => {
  await seedSpace("calendar-private", "calendar-private-owner");
  const owner = verifiedFirestore("calendar-private-owner");
  const outsider = verifiedFirestore("calendar-outsider");
  const reference = doc(owner, "spaces", "calendar-private", "events", "event-1");
  await assertFails(setDoc(reference, eventData("calendar-private-owner", { endAt: Timestamp.fromMillis(1) })));
  await assertSucceeds(setDoc(reference, eventData("calendar-private-owner")));
  await assertFails(getDoc(doc(outsider, "spaces", "calendar-private", "events", "event-1")));
});

test("todos los miembros crean, editan y eliminan comidas del planificador", async () => {
  await seedSpace("meals-crud", "meals-owner", ["meals-member"]);
  const member = verifiedFirestore("meals-member");
  const outsider = verifiedFirestore("meals-outsider");
  const reference = doc(member, "spaces", "meals-crud", "meals", "lunch");

  await assertSucceeds(setDoc(reference, mealData("meals-member")));
  await assertSucceeds(
    updateDoc(reference, {
      name: "Lentejas con chorizo",
      slot: "CENA",
      participantIds: ["meals-member"],
      participantNames: ["meals-member"],
      updatedBy: "meals-member",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(getDoc(doc(outsider, "spaces", "meals-crud", "meals", "lunch")));
  await assertFails(
    setDoc(doc(outsider, "spaces", "meals-crud", "meals", "sneaky"), mealData("meals-outsider")),
  );
  await assertSucceeds(writeBatch(member).delete(reference).commit());
});

test("las comidas validan la franja y rechazan campos ajenos al contrato", async () => {
  await seedSpace("meals-schema", "meals-schema-owner");
  const owner = verifiedFirestore("meals-schema-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "meals-schema", "meals", "ok"),
      mealData("meals-schema-owner", { slot: "DESAYUNO" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "meals-schema", "meals", "bad-slot"),
      mealData("meals-schema-owner", { slot: "MERIENDA" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "meals-schema", "meals", "extra-field"),
      mealData("meals-schema-owner", { assigneeId: "meals-schema-owner" }),
    ),
  );
});

test("las comidas exigen que participantNames tenga el mismo tamaño que participantIds", async () => {
  await seedSpace("meals-participants", "meals-part-owner");
  const owner = verifiedFirestore("meals-part-owner");

  await assertFails(
    setDoc(
      doc(owner, "spaces", "meals-participants", "meals", "mismatch"),
      mealData("meals-part-owner", { participantIds: ["meals-part-owner"], participantNames: [] }),
    ),
  );
});

test("al editar una comida no se pueden alterar los campos de creación", async () => {
  await seedSpace("meals-immutable", "meals-imm-owner", ["meals-imm-member"]);
  const owner = verifiedFirestore("meals-imm-owner");
  const member = verifiedFirestore("meals-imm-member");
  const reference = doc(owner, "spaces", "meals-immutable", "meals", "dinner");
  await assertSucceeds(setDoc(reference, mealData("meals-imm-owner")));

  await assertFails(
    updateDoc(doc(member, "spaces", "meals-immutable", "meals", "dinner"), {
      createdBy: "meals-imm-member",
      updatedBy: "meals-imm-member",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("un usuario puede anonimizar su nombre en sus comidas pero no en las ajenas", async () => {
  await seedSpace("meals-anon", "meals-anon-owner", ["meals-anon-member"]);
  const owner = verifiedFirestore("meals-anon-owner");
  const member = verifiedFirestore("meals-anon-member");
  const memberMeal = doc(member, "spaces", "meals-anon", "meals", "member-meal");
  await assertSucceeds(setDoc(memberMeal, mealData("meals-anon-member")));

  await assertSucceeds(updateDoc(memberMeal, { createdByName: "Usuario eliminado" }));
  await assertFails(
    updateDoc(doc(owner, "spaces", "meals-anon", "meals", "member-meal"), {
      createdByName: "Nombre manipulado",
    }),
  );
});

test("el propietario elimina un espacio completo y un miembro no puede hacerlo", async () => {
  await seedSpace("delete-space", "delete-owner", ["delete-member"]);
  await seedShoppingItem("delete-space", "item", "delete-owner");
  const owner = verifiedFirestore("delete-owner");
  const member = verifiedFirestore("delete-member");
  await assertFails(writeBatch(member).delete(doc(member, "spaces", "delete-space")).commit());

  const batch = writeBatch(owner);
  batch.delete(doc(owner, "spaces", "delete-space", "shoppingItems", "item"));
  batch.delete(doc(owner, "memberships", "delete-space_delete-member"));
  batch.delete(doc(owner, "memberships", "delete-space_delete-owner"));
  batch.delete(doc(owner, "spaces", "delete-space"));
  await assertSucceeds(batch.commit());
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    assert.equal((await getDoc(doc(context.firestore(), "spaces", "delete-space"))).exists(), false);
    assert.equal((await getDoc(doc(context.firestore(), "memberships", "delete-space_delete-member"))).exists(), false);
  });
});

test("un usuario puede anonimizar su nombre histórico pero no el de otro miembro", async () => {
  await seedSpace("anonymize-space", "anon-owner", ["anon-member"]);
  await seedShoppingItem("anonymize-space", "member-item", "anon-member");
  const member = verifiedFirestore("anon-member");
  const owner = verifiedFirestore("anon-owner");
  const memberItem = doc(member, "spaces", "anonymize-space", "shoppingItems", "member-item");
  await assertSucceeds(updateDoc(memberItem, { createdByName: "Usuario eliminado" }));
  await assertFails(updateDoc(doc(owner, "spaces", "anonymize-space", "shoppingItems", "member-item"), { createdByName: "Nombre manipulado" }));
});

test("cualquier usuario verificado crea y lee sus recetas personales, no las ajenas", async () => {
  const chef = verifiedFirestore("recipe-chef");
  const other = verifiedFirestore("recipe-other");
  const reference = doc(chef, "recipes", "chef-private");

  await assertSucceeds(setDoc(reference, recipeData("recipe-chef")));
  await assertSucceeds(getDoc(reference));
  await assertFails(getDoc(doc(other, "recipes", "chef-private")));
});

test("las recetas del catálogo común (GLOBAL) las lee cualquier usuario verificado", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "recipes", "global-1"), {
      ...recipeData("catalog-owner", { visibility: "GLOBAL" }),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const anyone = verifiedFirestore("recipe-reader");

  await assertSucceeds(getDoc(doc(anyone, "recipes", "global-1")));
  await assertSucceeds(
    getDocs(query(collection(anyone, "recipes"), where("visibility", "==", "GLOBAL"))),
  );
});

test("un usuario no puede crear una receta a nombre de otro ni una GLOBAL sin ser admin", async () => {
  const chef = verifiedFirestore("recipe-spoof");

  await assertFails(
    setDoc(doc(chef, "recipes", "spoofed"), recipeData("recipe-spoof", { ownerUid: "someone-else" })),
  );
  await assertFails(
    setDoc(doc(chef, "recipes", "sneaky-global"), recipeData("recipe-spoof", { visibility: "GLOBAL" })),
  );
});

test("un admin del catálogo puede publicar una receta GLOBAL", async () => {
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);

  await assertSucceeds(
    setDoc(
      doc(admin, "recipes", "curated-1"),
      recipeData(RECIPE_ADMIN_UID, { visibility: "GLOBAL", title: "Cocido común" }),
    ),
  );
});

test("un admin puede curar (borrar) una receta GLOBAL ajena, pero un usuario normal no", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "recipes", "curated-foreign"), {
      ...recipeData("otro-autor", { visibility: "GLOBAL" }),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const normal = verifiedFirestore("recipe-normal");

  await assertFails(writeBatch(normal).delete(doc(normal, "recipes", "curated-foreign")).commit());
  await assertSucceeds(writeBatch(admin).delete(doc(admin, "recipes", "curated-foreign")).commit());
});

test("solo el propietario edita o borra su receta personal", async () => {
  const chef = verifiedFirestore("recipe-owner");
  const other = verifiedFirestore("recipe-intruder");
  const reference = doc(chef, "recipes", "owned");
  await assertSucceeds(setDoc(reference, recipeData("recipe-owner")));

  await assertFails(
    updateDoc(doc(other, "recipes", "owned"), {
      title: "Editada por otro",
      updatedBy: "recipe-intruder",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(writeBatch(other).delete(doc(other, "recipes", "owned")).commit());
  await assertSucceeds(
    updateDoc(reference, {
      title: "Editada por su dueño",
      updatedBy: "recipe-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(writeBatch(chef).delete(reference).commit());
});

test("una receta no puede cambiar de propietario ni de visibilidad al editarla", async () => {
  const chef = verifiedFirestore("recipe-immutable");
  const reference = doc(chef, "recipes", "fixed");
  await assertSucceeds(setDoc(reference, recipeData("recipe-immutable")));

  await assertFails(
    updateDoc(reference, {
      visibility: "GLOBAL",
      updatedBy: "recipe-immutable",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("la receta valida la forma y rechaza campos ajenos al contrato", async () => {
  const chef = verifiedFirestore("recipe-schema");

  await assertFails(
    setDoc(doc(chef, "recipes", "bad-visibility"), recipeData("recipe-schema", { visibility: "SECRETO" })),
  );
  await assertFails(
    setDoc(doc(chef, "recipes", "extra-field"), recipeData("recipe-schema", { spaceId: "x" })),
  );
});

test("un usuario puede anonimizar el autor de su receta pero no el de otra", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "recipes", "anon-global"), {
      ...recipeData("recipe-anon", { visibility: "GLOBAL" }),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const owner = verifiedFirestore("recipe-anon");
  const other = verifiedFirestore("recipe-anon-other");

  await assertSucceeds(
    updateDoc(doc(owner, "recipes", "anon-global"), { createdByName: "Usuario eliminado" }),
  );
  await assertFails(
    updateDoc(doc(other, "recipes", "anon-global"), { createdByName: "Nombre manipulado" }),
  );
});

test("una receta guarda su origen (fork) pero rechaza un sourceRecipeId no textual", async () => {
  const chef = verifiedFirestore("recipe-fork");

  await assertSucceeds(
    setDoc(doc(chef, "recipes", "forked"), recipeData("recipe-fork", { sourceRecipeId: "origen-1" })),
  );
  await assertFails(
    setDoc(doc(chef, "recipes", "bad-source"), recipeData("recipe-fork", { sourceRecipeId: 123 })),
  );
});

test("una receta importada guarda su enlace de origen pero rechaza un sourceUrl no textual o larguísimo", async () => {
  const chef = verifiedFirestore("recipe-import");

  await assertSucceeds(
    setDoc(
      doc(chef, "recipes", "imported"),
      recipeData("recipe-import", { sourceUrl: "https://example.com/receta" }),
    ),
  );
  await assertFails(
    setDoc(doc(chef, "recipes", "bad-url"), recipeData("recipe-import", { sourceUrl: 123 })),
  );
  await assertFails(
    setDoc(
      doc(chef, "recipes", "huge-url"),
      recipeData("recipe-import", { sourceUrl: "https://example.com/".padEnd(2001, "a") }),
    ),
  );
});

test("una receta guarda su lista de ingredientes pero rechaza un ingredients no-lista o demasiado largo", async () => {
  const chef = verifiedFirestore("recipe-ingredients");

  await assertSucceeds(
    setDoc(
      doc(chef, "recipes", "with-ingredients"),
      recipeData("recipe-ingredients", {
        ingredients: [
          { name: "Arroz", quantity: "300", unit: "g" },
          { name: "Azafrán", quantity: null, unit: null },
        ],
      }),
    ),
  );
  await assertFails(
    setDoc(
      doc(chef, "recipes", "bad-ingredients"),
      recipeData("recipe-ingredients", { ingredients: "arroz, azafrán" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(chef, "recipes", "too-many-ingredients"),
      recipeData("recipe-ingredients", {
        ingredients: Array.from({ length: 51 }, (_, index) => ({ name: `ing-${index}` })),
      }),
    ),
  );
});

test("cualquier usuario crea un ingrediente del catálogo y todos lo leen, pero no a nombre de otro", async () => {
  const alice = verifiedFirestore("ing-alice");
  const bob = verifiedFirestore("ing-bob");

  await assertSucceeds(setDoc(doc(alice, "ingredients", "tomate"), ingredientData("ing-alice")));
  await assertSucceeds(getDoc(doc(bob, "ingredients", "tomate")));
  await assertFails(setDoc(doc(bob, "ingredients", "cebolla"), ingredientData("ing-alice")));
});

test("solo el autor o un admin editan/borran una ficha del catálogo", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "ingredients", "arroz"), {
      ...ingredientData("ing-owner"),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const owner = verifiedFirestore("ing-owner");
  const other = verifiedFirestore("ing-other");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);

  await assertSucceeds(
    updateDoc(doc(owner, "ingredients", "arroz"), {
      name: "Arroz redondo",
      nameLower: "arroz redondo",
      updatedBy: "ing-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(doc(other, "ingredients", "arroz"), {
      name: "Manipulado",
      nameLower: "manipulado",
      updatedBy: "ing-other",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(writeBatch(other).delete(doc(other, "ingredients", "arroz")).commit());
  await assertSucceeds(
    updateDoc(doc(admin, "ingredients", "arroz"), {
      name: "Arroz basmati",
      nameLower: "arroz basmati",
      updatedBy: RECIPE_ADMIN_UID,
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(writeBatch(admin).delete(doc(admin, "ingredients", "arroz")).commit());
});

test("una ficha del catálogo congela su ownerUid y rechaza campos ajenos al contrato", async () => {
  const chef = verifiedFirestore("ing-shape");
  await assertSucceeds(setDoc(doc(chef, "ingredients", "harina"), ingredientData("ing-shape")));

  await assertFails(
    updateDoc(doc(chef, "ingredients", "harina"), {
      ownerUid: "otro",
      updatedBy: "ing-shape",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(setDoc(doc(chef, "ingredients", "sal"), ingredientData("ing-shape", { spaceId: "x" })));
  await assertFails(setDoc(doc(chef, "ingredients", "vacio"), ingredientData("ing-shape", { name: "" })));
});

test("las preferencias de ingredientes solo las lee y escribe su dueño", async () => {
  const alice = verifiedFirestore("pref-alice");
  const bob = verifiedFirestore("pref-bob");

  await assertSucceeds(
    setDoc(
      doc(alice, "ingredientPrefs", "pref-alice"),
      { entries: { tomate: { supermarket: "MERCADONA", brand: "Hacendado" } } },
      { merge: true },
    ),
  );
  await assertSucceeds(getDoc(doc(alice, "ingredientPrefs", "pref-alice")));
  await assertFails(getDoc(doc(bob, "ingredientPrefs", "pref-alice")));
  await assertFails(
    setDoc(doc(bob, "ingredientPrefs", "pref-alice"), { entries: { tomate: { supermarket: "DIA" } } }, { merge: true }),
  );
  await assertFails(
    setDoc(doc(alice, "ingredientPrefs", "pref-alice"), { entries: {}, hacked: true }, { merge: true }),
  );
});

test("cualquiera añade una marca a un ingrediente y todos la leen, pero no a nombre de otro", async () => {
  const alice = verifiedFirestore("brand-alice");
  const bob = verifiedFirestore("brand-bob");

  await assertSucceeds(
    setDoc(doc(alice, "ingredients", "tomate", "brands", "b1"), brandData("brand-alice", { energyKcal: 30, salt: 0.1 })),
  );
  await assertSucceeds(getDoc(doc(bob, "ingredients", "tomate", "brands", "b1")));
  await assertFails(
    setDoc(doc(bob, "ingredients", "tomate", "brands", "b2"), brandData("brand-alice")),
  );
});

test("solo el autor o un admin editan/borran una marca", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "ingredients", "arroz", "brands", "b1"), {
      ...brandData("brand-owner"),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const owner = verifiedFirestore("brand-owner");
  const other = verifiedFirestore("brand-other");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);

  await assertSucceeds(
    updateDoc(doc(owner, "ingredients", "arroz", "brands", "b1"), {
      name: "SOS",
      updatedBy: "brand-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(doc(other, "ingredients", "arroz", "brands", "b1"), {
      name: "Manipulada",
      updatedBy: "brand-other",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(writeBatch(other).delete(doc(other, "ingredients", "arroz", "brands", "b1")).commit());
  await assertSucceeds(writeBatch(admin).delete(doc(admin, "ingredients", "arroz", "brands", "b1")).commit());
});

test("una marca rechaza campos ajenos, nutrición no numérica y nombre vacío", async () => {
  const chef = verifiedFirestore("brand-shape");

  await assertSucceeds(
    setDoc(doc(chef, "ingredients", "harina", "brands", "b1"), brandData("brand-shape", { fat: 1.2 })),
  );
  await assertFails(
    setDoc(doc(chef, "ingredients", "harina", "brands", "bad1"), brandData("brand-shape", { spaceId: "x" })),
  );
  await assertFails(
    setDoc(doc(chef, "ingredients", "harina", "brands", "bad2"), brandData("brand-shape", { fat: "mucha" })),
  );
  await assertFails(
    setDoc(doc(chef, "ingredients", "harina", "brands", "bad3"), brandData("brand-shape", { name: "" })),
  );
});

function brandData(userId, overrides = {}) {
  return {
    name: "Hacendado",
    ownerUid: userId,
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function ingredientData(userId, overrides = {}) {
  return {
    name: "Tomate",
    nameLower: "tomate",
    ownerUid: userId,
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function recipeData(userId, overrides = {}) {
  return {
    ownerUid: userId,
    visibility: "PRIVATE",
    title: "Lentejas",
    description: null,
    category: null,
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

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

function eventData(userId, overrides = {}) {
  return {
    title: "Evento", description: null, allDay: false,
    startAt: Timestamp.fromMillis(Date.now() + 60_000), endAt: Timestamp.fromMillis(Date.now() + 120_000),
    startDate: null, endDateExclusive: null, timeZone: "Europe/Madrid", color: "BLUE",
    participantIds: [], participantNames: [], createdBy: userId, createdByName: userId,
    createdAt: serverTimestamp(), updatedBy: userId, updatedAt: serverTimestamp(), ...overrides,
  };
}

test("una comida guarda su receta (recipeId) pero rechaza un valor no textual", async () => {
  await seedSpace("meals-recipe", "meals-recipe-owner");
  const owner = verifiedFirestore("meals-recipe-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "meals-recipe", "meals", "linked"),
      mealData("meals-recipe-owner", { recipeId: "receta-1" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "meals-recipe", "meals", "bad-recipe"),
      mealData("meals-recipe-owner", { recipeId: 5 }),
    ),
  );
});

test("una comida admite el flag cooked booleano y permite alternarlo, pero rechaza un valor no booleano", async () => {
  await seedSpace("meals-cooked", "meals-cooked-owner");
  const owner = verifiedFirestore("meals-cooked-owner");
  const reference = doc(owner, "spaces", "meals-cooked", "meals", "dinner");

  await assertSucceeds(setDoc(reference, mealData("meals-cooked-owner", { cooked: true })));
  await assertSucceeds(
    updateDoc(reference, {
      cooked: false,
      updatedBy: "meals-cooked-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "meals-cooked", "meals", "bad-cooked"),
      mealData("meals-cooked-owner", { cooked: "sí" }),
    ),
  );
});

function mealData(userId, overrides = {}) {
  return {
    date: "2026-07-20",
    slot: "COMIDA",
    name: "Comida",
    participantIds: [],
    participantNames: [],
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
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

function taskData(userId, assigneeId, overrides = {}) {
  return {
    title: "Tarea",
    description: null,
    assigneeId,
    assigneeName: assigneeId,
    dueAt: null,
    priority: "MEDIUM",
    status: "TODO",
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    completedBy: null,
    completedByName: null,
    completedAt: null,
    ...overrides,
  };
}

function completeTask(firestore, spaceId, taskId, userId) {
  return runTransaction(firestore, async (transaction) => {
    const reference = doc(firestore, "spaces", spaceId, "tasks", taskId);
    const task = await transaction.get(reference);
    if (task.data()?.status !== "TODO") return;
    transaction.update(reference, {
      status: "DONE",
      completedBy: userId,
      completedByName: userId,
      completedAt: serverTimestamp(),
      updatedBy: userId,
      updatedAt: serverTimestamp(),
    });
  });
}

function seededTaskData(userId, assigneeId, overrides = {}) {
  const timestamp = Timestamp.now();
  return {
    ...taskData(userId, assigneeId),
    createdAt: timestamp,
    updatedAt: timestamp,
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
          assigneeName: task.assigneeId,
          updatedBy: task.assigneeId,
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
