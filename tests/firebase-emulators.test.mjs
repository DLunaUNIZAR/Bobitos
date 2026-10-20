import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteApp, initializeApp } from "firebase/app";
import { toFirestoreDoc } from "../scripts/catalog/import-plan.mjs";
import { recipeToFirestoreDoc } from "../scripts/catalog/recipe-import-plan.mjs";
import { routineToFirestoreDoc } from "../scripts/catalog/routine-import-plan.mjs";
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
  Bytes,
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
  deleteDoc,
  deleteField,
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

test("al quitar a un miembro, su membresía y el espacio ya no se leen por documento, pero sus consultas de membresías sí, sin ella", async () => {
  await seedSpace("lost-space", "lost-owner", ["lost-member"]);
  const member = verifiedFirestore("lost-member");
  const memberships = collection(member, "memberships");
  const mine = query(memberships, where("userId", "==", "lost-member"), where("status", "==", "ACTIVE"));
  const mineHere = query(
    memberships,
    where("userId", "==", "lost-member"),
    where("spaceId", "==", "lost-space"),
    where("status", "==", "ACTIVE"),
  );
  assert.equal((await getDocs(mine)).size, 1);

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "memberships", "lost-space_lost-member"));
  });

  await assertFails(getDoc(doc(member, "memberships", "lost-space_lost-member")));
  await assertFails(getDoc(doc(member, "spaces", "lost-space")));
  assert.equal((await assertSucceeds(getDocs(mine))).size, 0);
  assert.equal((await assertSucceeds(getDocs(mineHere))).size, 0);
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

test("una tarea puede crearse y quedarse sin responsable, pero exige acoplar id y nombre", async () => {
  await seedSpace("tasks-unassigned", "unassigned-owner", ["unassigned-member"]);
  const owner = verifiedFirestore("unassigned-owner");
  const reference = doc(owner, "spaces", "tasks-unassigned", "tasks", "orphan");

  // Crear sin responsable (id y nombre a null) es válido.
  await assertSucceeds(
    setDoc(reference, taskData("unassigned-owner", null)),
  );
  // Reasignar a un miembro activo y volver a dejarla sin responsable también es válido.
  await assertSucceeds(
    updateDoc(reference, {
      assigneeId: "unassigned-member",
      assigneeName: "unassigned-member",
      updatedBy: "unassigned-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    updateDoc(reference, {
      assigneeId: null,
      assigneeName: null,
      updatedBy: "unassigned-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  // Romper el acoplamiento id/nombre se rechaza en ambos sentidos:
  // (a) id nulo con nombre presente.
  await assertFails(
    setDoc(
      doc(owner, "spaces", "tasks-unassigned", "tasks", "half-name"),
      taskData("unassigned-owner", null, { assigneeName: "Fantasma" }),
    ),
  );
  // (b) id de miembro activo con nombre nulo.
  await assertFails(
    setDoc(
      doc(owner, "spaces", "tasks-unassigned", "tasks", "half-id"),
      taskData("unassigned-owner", "unassigned-member", { assigneeName: null }),
    ),
  );
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

test("deshacer completar devuelve estado y fechas en una escritura", async () => {
  await seedSpace("tasks-undo-complete", "undo-owner", ["undo-member"]);
  const owner = verifiedFirestore("undo-owner");
  const member = verifiedFirestore("undo-member");
  const start = Timestamp.fromDate(new Date("2026-10-01T08:00:00Z"));
  const due = Timestamp.fromDate(new Date("2026-10-02T08:00:00Z"));

  // Tarea normal completada: un miembro la reabre y devuelve las fechas en el mismo updateDoc.
  const done = doc(owner, "spaces", "tasks-undo-complete", "tasks", "done");
  await assertSucceeds(setDoc(done, taskData("undo-owner", null, { startAt: Timestamp.now(), dueAt: Timestamp.now() })));
  await assertSucceeds(completeTask(owner, "tasks-undo-complete", "done", "undo-owner"));
  await assertSucceeds(updateDoc(doc(member, "spaces", "tasks-undo-complete", "tasks", "done"), {
    status: "TODO",
    completedBy: null,
    completedByName: null,
    completedAt: null,
    startAt: start,
    dueAt: due,
    updatedBy: "undo-member",
    updatedAt: serverTimestamp(),
  }));
  const reopened = (await getDoc(done)).data();
  assert.equal(reopened.status, "TODO");
  assert.equal(reopened.completedBy, null);
  assert.deepEqual(reopened.startAt, start);
  assert.deepEqual(reopened.dueAt, due);

  // Tarea recurrente que sigue en TODO con las fechas adelantadas: solo cambian las fechas.
  const repeating = doc(owner, "spaces", "tasks-undo-complete", "tasks", "repeating");
  await assertSucceeds(setDoc(repeating, taskData("undo-owner", null, { startAt: Timestamp.now(), dueAt: Timestamp.now() })));
  await assertSucceeds(updateDoc(doc(member, "spaces", "tasks-undo-complete", "tasks", "repeating"), {
    startAt: start,
    dueAt: due,
    updatedBy: "undo-member",
    updatedAt: serverTimestamp(),
  }));
});

test("deshacer el borrado de una tarea completada la devuelve con su id y completada en una sola escritura", async () => {
  await seedSpace("tasks-restore", "task-restore-owner", ["task-restore-member"]);
  const owner = verifiedFirestore("task-restore-owner");
  const member = verifiedFirestore("task-restore-member");
  const reference = doc(owner, "spaces", "tasks-restore", "tasks", "done-task");
  await assertSucceeds(setDoc(reference, taskData("task-restore-owner", null, { title: "Fregar" })));
  await assertSucceeds(completeTask(owner, "tasks-restore", "done-task", "task-restore-owner"));
  await assertSucceeds(writeBatch(owner).delete(reference).commit());

  const restoredReference = doc(member, "spaces", "tasks-restore", "tasks", "done-task");
  await assertSucceeds(runTransaction(member, async (transaction) => {
    await transaction.get(doc(member, "spaces", "tasks-restore"));
    transaction.set(restoredReference, doneTask("task-restore-member", { title: "Fregar" }));
  }));
  const restored = await getDoc(restoredReference);
  assert.equal(restored.exists(), true);
  assert.equal(restored.data().title, "Fregar");
  assert.equal(restored.data().status, "DONE");
  assert.equal(restored.data().createdBy, "task-restore-member");
});

test("deshacer el borrado de un producto comprado lo devuelve con su id y comprado en una sola escritura", async () => {
  await seedSpace("shopping-restore", "shop-restore-owner", ["shop-restore-member"]);
  const owner = verifiedFirestore("shop-restore-owner");
  const member = verifiedFirestore("shop-restore-member");
  const reference = doc(owner, "spaces", "shopping-restore", "shoppingItems", "bought");
  await assertSucceeds(setDoc(reference, shoppingItem("shop-restore-owner", { name: "Pan" })));
  await assertSucceeds(writeBatch(owner).delete(reference).commit());

  const restoredReference = doc(member, "spaces", "shopping-restore", "shoppingItems", "bought");
  await assertSucceeds(setDoc(restoredReference, boughtItem("shop-restore-member", { name: "Pan" })));
  const restored = await getDoc(restoredReference);
  assert.equal(restored.exists(), true);
  assert.equal(restored.data().name, "Pan");
  assert.equal(restored.data().purchased, true);
});

test("deshacer el borrado de una comida cocinada la devuelve con su id y cocinada", async () => {
  await seedSpace("meals-restore", "meal-restore-owner", ["meal-restore-member"]);
  const owner = verifiedFirestore("meal-restore-owner");
  const member = verifiedFirestore("meal-restore-member");
  const reference = doc(owner, "spaces", "meals-restore", "meals", "cooked-meal");
  await assertSucceeds(setDoc(reference, mealData("meal-restore-owner", { name: "Lentejas", cooked: true })));
  await assertSucceeds(writeBatch(owner).delete(reference).commit());

  const restoredReference = doc(member, "spaces", "meals-restore", "meals", "cooked-meal");
  await assertSucceeds(setDoc(restoredReference, mealData("meal-restore-member", {
    name: "Lentejas", cooked: true, recipeId: "receta-1",
  })));
  const restored = await getDoc(restoredReference);
  assert.equal(restored.exists(), true);
  assert.equal(restored.data().name, "Lentejas");
  assert.equal(restored.data().cooked, true);
  assert.equal(restored.data().recipeId, "receta-1");
  assert.equal(restored.data().createdBy, "meal-restore-member");
});

test("deshacer el borrado de una actividad hecha devuelve actividad y evento con sus ids, y ya hecha, en un único lote", async () => {
  await seedSpace("act-restore", "act-restore-owner", ["act-restore-member"]);
  const owner = verifiedFirestore("act-restore-owner");
  const member = verifiedFirestore("act-restore-member");
  const activityRef = doc(owner, "spaces", "act-restore", "activities", "leg-day");
  const eventRef = doc(owner, "spaces", "act-restore", "events", "leg-event");
  await assertSucceeds(setDoc(activityRef, activityData("act-restore-owner", { name: "Pierna", eventId: "leg-event" })));
  await assertSucceeds(setDoc(eventRef, eventData("act-restore-owner", { title: "Pierna (editado)" })));
  await assertSucceeds(updateDoc(activityRef, { done: true, updatedBy: "act-restore-owner", updatedAt: serverTimestamp() }));
  await assertSucceeds(writeBatch(owner).delete(activityRef).delete(eventRef).commit());

  const restoredActivity = doc(member, "spaces", "act-restore", "activities", "leg-day");
  const restoredEvent = doc(member, "spaces", "act-restore", "events", "leg-event");
  await assertSucceeds(
    writeBatch(member)
      .set(restoredActivity, activityData("act-restore-member", { name: "Pierna", eventId: "leg-event", done: true }))
      .set(restoredEvent, eventData("act-restore-member", { title: "Pierna (editado)" }))
      .commit(),
  );
  const activity = await getDoc(restoredActivity);
  const event = await getDoc(restoredEvent);
  assert.equal(activity.data().done, true);
  assert.equal(activity.data().eventId, "leg-event");
  assert.equal(event.exists(), true);
  assert.equal(event.data().title, "Pierna (editado)");
});

test("el alta de una tarea o un producto completado con atribución falsa es rechazada", async () => {
  await seedSpace("create-done-negatives", "neg-owner", ["neg-member"]);
  const member = verifiedFirestore("neg-member");
  const tasks = (id) => doc(member, "spaces", "create-done-negatives", "tasks", id);
  const items = (id) => doc(member, "spaces", "create-done-negatives", "shoppingItems", id);
  await assertFails(setDoc(tasks("t1"), doneTask("neg-member", { completedBy: "neg-owner" })));
  await assertFails(setDoc(tasks("t2"), doneTask("neg-member", { completedAt: Timestamp.fromMillis(1000) })));
  await assertFails(setDoc(tasks("t3"), doneTask("neg-member", { completedByName: null })));
  await assertFails(setDoc(tasks("t4"), taskData("neg-member", null, { completedBy: "neg-member" })));
  await assertFails(setDoc(items("i1"), boughtItem("neg-member", { purchasedBy: "neg-owner" })));
  await assertFails(setDoc(items("i2"), boughtItem("neg-member", { purchasedAt: Timestamp.fromMillis(1000) })));
  await assertFails(setDoc(items("i3"), shoppingItem("neg-member", { purchasedBy: "neg-member" })));
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

test("un artículo de la compra admite las tiendas de barrio", async () => {
  await seedSpace("shopping-barrio", "barrio-owner");
  const owner = verifiedFirestore("barrio-owner");

  for (const store of ["CARNICERIA", "PESCADERIA", "FRUTERIA", "PANADERIA"]) {
    await assertSucceeds(
      setDoc(
        doc(owner, "spaces", "shopping-barrio", "shoppingItems", store),
        shoppingItem("barrio-owner", { supermarket: store }),
      ),
    );
  }
  await assertFails(
    setDoc(
      doc(owner, "spaces", "shopping-barrio", "shoppingItems", "lidl"),
      shoppingItem("barrio-owner", { supermarket: "LIDL" }),
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

test("volver a apuntar un comprado lo deja pendiente con la cantidad nueva en una sola escritura", async () => {
  await seedSpace("shopping-relist", "relist-owner", ["relist-member"]);
  await seedShoppingItem("shopping-relist", "rice", "relist-owner", { purchased: true, quantity: "1 kg" });
  const member = verifiedFirestore("relist-member");
  const itemReference = doc(member, "spaces", "shopping-relist", "shoppingItems", "rice");

  await assertSucceeds(
    updateDoc(itemReference, {
      name: "Producto",
      quantity: "500 g",
      notes: null,
      supermarket: "DIA",
      brand: "Brillante",
      purchased: false,
      purchasedBy: null,
      purchasedByName: null,
      purchasedAt: null,
      updatedBy: "relist-member",
      updatedAt: serverTimestamp(),
    }),
  );

  const item = await getDoc(itemReference);
  assert.equal(item.data().purchased, false);
  assert.equal(item.data().quantity, "500 g");
  assert.equal(item.data().purchasedBy, null);
  assert.equal(item.data().purchasedAt, null);
  assert.equal(item.data().brand, "Brillante");
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

test("deshacer el borrado de un evento lo restaura con su id original", async () => {
  await seedSpace("calendar-restore", "restore-owner", ["restore-member"]);
  const owner = verifiedFirestore("restore-owner");
  const member = verifiedFirestore("restore-member");
  const data = eventData("restore-owner", { participantIds: ["restore-owner"], participantNames: ["restore-owner"] });
  await assertSucceeds(setDoc(doc(owner, "spaces", "calendar-restore", "events", "linked"), data));
  const reference = doc(member, "spaces", "calendar-restore", "events", "linked");
  await assertSucceeds(deleteDoc(reference));
  await assertSucceeds(setDoc(reference, eventData("restore-member", { participantIds: ["restore-owner"], participantNames: ["restore-owner"] })));
  assert.equal((await getDoc(reference)).exists(), true);
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

test("un setDoc de cliente no pisa una receta existente, propia o ajena", async () => {
  const owner = "recipe-overwrite-owner";
  const ownerDb = verifiedFirestore(owner);
  const otherId = "recipe-overwrite-other";
  const otherDb = verifiedFirestore(otherId);
  const ref = doc(ownerDb, "recipes", "pisar-propia");
  await assertSucceeds(setDoc(ref, recipeData(owner)));

  // Los datos actuales los lee el dueño: una receta privada no la lee otro.
  const current = (await getDoc(ref)).data();

  // Recrearla cambiaría su fecha de alta (createdAt). El otro usuario conserva los campos inmutables
  // (dueño, visibilidad, autor y fecha de alta), así que solo lo para no ser el dueño.
  await assertFails(setDoc(ref, recipeData(owner, { title: "Otra" })));
  await assertFails(setDoc(doc(otherDb, "recipes", "pisar-propia"), {
    ...current, title: "Otra", updatedBy: otherId, updatedAt: serverTimestamp(),
  }));

  // El camino legítimo: reescribirla conservando dueño, autor y fecha de alta.
  await assertSucceeds(setDoc(ref, {
    ...current, title: "Otra", updatedBy: owner, updatedAt: serverTimestamp(),
  }));

  await seedImportedRecipe("pisar-comun");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertFails(setDoc(doc(admin, "recipes", "pisar-comun"),
    recipeData(RECIPE_ADMIN_UID, { visibility: "GLOBAL" })));
  const user = verifiedFirestore("recipe-overwrite-user");
  await assertFails(setDoc(doc(user, "recipes", "pisar-comun"),
    recipeData("recipe-overwrite-user", { visibility: "GLOBAL" })));
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

test("una receta guarda pasos, raciones y tiempo, y su dueño los edita o los vacía", async () => {
  const chef = verifiedFirestore("recipe-steps");
  const ref = doc(chef, "recipes", "con-pasos");

  await assertSucceeds(setDoc(ref, recipeData("recipe-steps", {
    steps: ["Pon las lentejas en remojo.", "Sofríe la verdura."], servings: 4, totalMinutes: 60,
  })));
  await assertSucceeds(updateDoc(ref, {
    steps: ["Cuece todo junto."], servings: 2, totalMinutes: 45,
    updatedBy: "recipe-steps", updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(ref, {
    steps: [], servings: null, totalMinutes: null, updatedBy: "recipe-steps", updatedAt: serverTimestamp(),
  }));
});

test("una receta antigua, sin pasos ni raciones, se edita añadiéndolos vacíos", async () => {
  const chef = verifiedFirestore("recipe-legacy");
  const ref = doc(chef, "recipes", "antigua");
  await assertSucceeds(setDoc(ref, recipeData("recipe-legacy")));

  await assertSucceeds(updateDoc(ref, {
    title: "Lentejas de la abuela", steps: [], servings: null, totalMinutes: null,
    updatedBy: "recipe-legacy", updatedAt: serverTimestamp(),
  }));
});

test("la receta rechaza más de 30 pasos y raciones o tiempo fuera de rango", async () => {
  const chef = verifiedFirestore("recipe-limits");
  const create = (id, overrides) => setDoc(doc(chef, "recipes", id), recipeData("recipe-limits", overrides));
  const steps = (count) => Array.from({ length: count }, (_, index) => `Paso ${index + 1}`);

  await assertSucceeds(create("maximos", { steps: steps(30), servings: 50, totalMinutes: 1440 }));
  await assertSucceeds(create("minimos", { steps: [], servings: 1, totalMinutes: 1 }));
  await assertFails(create("31-pasos", { steps: steps(31) }));
  await assertFails(create("pasos-texto", { steps: "Remojar y cocer" }));
  await assertFails(create("cero-raciones", { servings: 0 }));
  await assertFails(create("51-raciones", { servings: 51 }));
  await assertFails(create("raciones-decimales", { servings: 2.5 }));
  await assertFails(create("raciones-texto", { servings: "4" }));
  await assertFails(create("cero-minutos", { totalMinutes: 0 }));
  await assertFails(create("1441-minutos", { totalMinutes: 1441 }));
  await assertFails(updateDoc(doc(chef, "recipes", "minimos"), {
    servings: 51, updatedBy: "recipe-limits", updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(chef, "recipes", "minimos"), {
    steps: steps(31), updatedBy: "recipe-limits", updatedAt: serverTimestamp(),
  }));
});

test("solo un admin crea una receta con la fuente del catálogo («Deshacer»); un usuario normal, no", async () => {
  const source = { provider: "bobitos", importedAt: Timestamp.fromMillis(Date.now() - 60_000) };
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertSucceeds(setDoc(doc(admin, "recipes", "falsa-global"),
    recipeData(RECIPE_ADMIN_UID, { visibility: "GLOBAL", source })));
  await assertFails(setDoc(doc(admin, "recipes", "falsa-privada"),
    recipeData(RECIPE_ADMIN_UID, { visibility: "PRIVATE", source })));
  const user = verifiedFirestore("recipe-fake-source");
  await assertFails(setDoc(doc(user, "recipes", "falsa"), recipeData("recipe-fake-source", { source })));
});

test("el admin edita una receta común del catálogo y su fuente se conserva", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "recipes", "lentejas-catalogo"), {
      ...recipeData(RECIPE_ADMIN_UID, {
        visibility: "GLOBAL", title: "Lentejas estofadas", ingredients: [],
        steps: ["Remoja las lentejas."], servings: 4, totalMinutes: 60,
        source: { provider: "bobitos", importedAt: timestamp },
      }),
      createdByName: "Catálogo Bobitos",
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-catalogo");

  await assertSucceeds(updateDoc(ref, {
    title: "Lentejas estofadas (editada)", steps: ["Remoja las lentejas.", "Cuécelas."], servings: 6, totalMinutes: 75,
    updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref, {
    source: { provider: "bobitos", importedAt: Timestamp.now() }, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const stored = (await getDoc(doc(context.firestore(), "recipes", "lentejas-catalogo"))).data();
    assert.equal(stored.source.provider, "bobitos");
    assert.equal(stored.title, "Lentejas estofadas (editada)");
    assert.deepEqual(stored.steps, ["Remoja las lentejas.", "Cuécelas."]);
  });
  const user = verifiedFirestore("recipe-catalog-user");
  await assertFails(updateDoc(doc(user, "recipes", "lentejas-catalogo"), {
    title: "Mía", updatedBy: "recipe-catalog-user", updatedAt: serverTimestamp(),
  }));
});

const importedRecipe = {
  id: "lentejas-importadas", title: "Lentejas estofadas", description: "Las de toda la vida.", category: "Legumbres",
  servings: 4, totalMinutes: 60,
  ingredients: [{ name: "Lentejas pardinas", quantity: "300", unit: "g" }, { name: "Sal", quantity: null, unit: null }],
  steps: ["Cuece las lentejas.", "Sirve."],
};

async function seedImportedRecipe(id) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "recipes", id),
      recipeToFirestoreDoc({ ...importedRecipe, id }, { now: Timestamp.now(), create: true }));
  });
}

test("el admin edita en la app una receta del importador, pero no su fuente, y cualquiera la lee", async () => {
  await seedImportedRecipe("lentejas-importadas");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-importadas");
  await assertSucceeds(updateDoc(ref, {
    title: "Lentejas estofadas (editada)", description: "Otra", category: "Legumbres",
    ingredients: [{ name: "Lentejas pardinas", quantity: "400", unit: "g" }], steps: ["Cuece.", "Sirve."],
    servings: 6, totalMinutes: 75, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref, {
    source: { provider: "bobitos", importedAt: Timestamp.now() }, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(verifiedFirestore("recipe-imported-reader"), "recipes", "lentejas-importadas")));
});

test("«Deshacer»: el admin recrea una común importada tal como era, con su dueño, sus fechas y su fuente", async () => {
  await seedImportedRecipe("lentejas-deshacer");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-deshacer");
  const before = (await getDoc(ref)).data();
  await assertSucceeds(writeBatch(admin).delete(ref).commit());

  await assertSucceeds(setDoc(ref, before));

  const after = (await getDoc(ref)).data();
  assert.equal(after.ownerUid, RECIPE_ADMIN_UID);
  assert.equal(after.createdByName, before.createdByName);
  assert.equal(after.source.provider, "bobitos");
  assert.ok(after.source.importedAt.isEqual(before.source.importedAt));
  assert.ok(after.updatedAt.isEqual(before.updatedAt));
});

test("«Deshacer» de una común: solo un admin, con dueño, autor y editor admins y fechas pasadas", async () => {
  await seedImportedRecipe("lentejas-deshacer-mal");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-deshacer-mal");
  const before = (await getDoc(ref)).data();
  await assertSucceeds(writeBatch(admin).delete(ref).commit());
  const future = Timestamp.fromMillis(Date.now() + 3_600_000);

  await assertFails(setDoc(ref, { ...before, ownerUid: "otro-autor", createdBy: "otro-autor" }));
  await assertFails(setDoc(ref, { ...before, createdBy: "otro-autor" }));
  await assertFails(setDoc(ref, { ...before, updatedBy: "otro-autor" }));
  await assertFails(setDoc(ref, { ...before, updatedAt: future }));
  await assertFails(setDoc(ref, { ...before, createdAt: future }));
  await assertFails(setDoc(ref, { ...before, visibility: "PRIVATE" }));

  // La fecha de importación no puede ser posterior a la última edición (que ya no puede ser futura).
  const at = (minutes) => Timestamp.fromMillis(Date.now() + minutes * 60_000);
  const dated = { ...before, createdAt: at(-120), updatedAt: at(-60) };
  await assertFails(setDoc(ref, { ...dated, source: { ...before.source, importedAt: at(-30) } }));
  await assertSucceeds(setDoc(ref, { ...dated, source: { ...before.source, importedAt: at(-90) } }));

  await assertSucceeds(writeBatch(admin).delete(ref).commit());
  const normal = verifiedFirestore("recipe-undo-normal");
  const normalRef = doc(normal, "recipes", "lentejas-deshacer-mal");
  await assertFails(setDoc(normalRef, before));
  await assertFails(setDoc(normalRef, {
    ...before, ownerUid: "recipe-undo-normal", createdBy: "recipe-undo-normal", updatedBy: "recipe-undo-normal",
  }));
});

test("«Deshacer» de una común de un admin retirado: se recrea a nombre de quien deshace, no con el retirado", async () => {
  await seedImportedRecipe("lentejas-deshacer-retirado");
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "recipes", "lentejas-deshacer-retirado"),
      { ownerUid: "ex-admin", createdBy: "ex-admin", updatedBy: "ex-admin" });
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "recipes", "lentejas-deshacer-retirado");
  const before = (await getDoc(ref)).data();
  await assertSucceeds(writeBatch(admin).delete(ref).commit());

  await assertFails(setDoc(ref, before));
  await assertSucceeds(setDoc(ref, {
    ...before, ownerUid: RECIPE_ADMIN_UID, createdBy: RECIPE_ADMIN_UID, updatedBy: RECIPE_ADMIN_UID,
  }));

  const after = (await getDoc(ref)).data();
  assert.equal(after.ownerUid, RECIPE_ADMIN_UID);
  assert.ok(after.createdAt.isEqual(before.createdAt));
  assert.ok(after.source.importedAt.isEqual(before.source.importedAt));
});

test("un admin retirado ya no edita ni borra sus recetas y rutinas comunes; sí las personales y anonimizar su autor", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    const timestamp = Timestamp.now();
    const dates = { createdAt: timestamp, updatedAt: timestamp };
    await setDoc(doc(firestore, "recipes", "ex-admin-common"),
      { ...recipeData("ex-admin", { visibility: "GLOBAL" }), ...dates });
    await setDoc(doc(firestore, "recipes", "ex-admin-private"), { ...recipeData("ex-admin"), ...dates });
    await setDoc(doc(firestore, "routines", "ex-admin-common"),
      { ...routineData("ex-admin", { visibility: "GLOBAL" }), ...dates });
    await setDoc(doc(firestore, "routines", "ex-admin-private"), { ...routineData("ex-admin"), ...dates });
  });
  const exAdmin = verifiedFirestore("ex-admin");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const edit = { title: "Editada por el retirado", updatedBy: "ex-admin", updatedAt: serverTimestamp() };

  for (const collectionName of ["recipes", "routines"]) {
    const common = doc(exAdmin, collectionName, "ex-admin-common");
    const personal = doc(exAdmin, collectionName, "ex-admin-private");
    await assertFails(updateDoc(common, edit));
    await assertFails(writeBatch(exAdmin).delete(common).commit());
    await assertSucceeds(updateDoc(common, { createdByName: "Usuario eliminado" }));
    await assertSucceeds(updateDoc(personal, edit));
    await assertSucceeds(writeBatch(exAdmin).delete(personal).commit());
    await assertSucceeds(writeBatch(admin).delete(doc(admin, collectionName, "ex-admin-common")).commit());
  }
});


// --- Catálogo común de ingredientes (solo el admin escribe) y datos personales ---

const commonIngredient = (uid, overrides = {}) => ({
  name: "Tomate",
  nameLower: "tomate",
  category: "Verduras",
  defaultUnit: "g",
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  updatedBy: uid,
  ...overrides,
});

// Forma que escribe el importador (Admin SDK): con source y sin campos de dueño.
const importedIngredient = () => ({
  name: "Arroz",
  nameLower: "arroz",
  category: "Cereales",
  defaultUnit: "g",
  source: { provider: "bobitos", importedAt: Timestamp.now() },
  createdAt: Timestamp.now(),
  updatedAt: Timestamp.now(),
  updatedBy: RECIPE_ADMIN_UID,
});

test("ingredientes comunes: todos los verificados los leen; solo el admin los crea, edita y borra", async () => {
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const user = verifiedFirestore("ing-user");

  await assertSucceeds(setDoc(doc(admin, "ingredients", "tomate"), commonIngredient(RECIPE_ADMIN_UID)));
  await assertFails(setDoc(doc(user, "ingredients", "cebolla"), commonIngredient("ing-user")));
  await assertFails(setDoc(doc(unverifiedFirestore(RECIPE_ADMIN_UID), "ingredients", "ajo"), commonIngredient(RECIPE_ADMIN_UID)));

  await assertSucceeds(getDoc(doc(user, "ingredients", "tomate")));
  await assertSucceeds(getDocs(collection(user, "ingredients")));
  await assertFails(getDoc(doc(unverifiedFirestore("ing-unverified"), "ingredients", "tomate")));

  await assertFails(
    updateDoc(doc(user, "ingredients", "tomate"), {
      name: "Manipulado", nameLower: "manipulado", updatedBy: "ing-user", updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(writeBatch(user).delete(doc(user, "ingredients", "tomate")).commit());
  await assertSucceeds(
    updateDoc(doc(admin, "ingredients", "tomate"), {
      name: "Tomate pera", nameLower: "tomate pera", updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(writeBatch(admin).delete(doc(admin, "ingredients", "tomate")).commit());
});

test("un ingrediente común rechaza source desde el cliente y campos ajenos; el admin edita uno importado sin tocar su source", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "ingredients", "arroz"), importedIngredient());
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);

  await assertFails(
    setDoc(doc(admin, "ingredients", "con-source"),
      commonIngredient(RECIPE_ADMIN_UID, { source: { provider: "bobitos", importedAt: Timestamp.now() } })),
  );
  await assertFails(setDoc(doc(admin, "ingredients", "ajeno"), commonIngredient(RECIPE_ADMIN_UID, { ownerUid: "x" })));
  await assertFails(setDoc(doc(admin, "ingredients", "vacio"), commonIngredient(RECIPE_ADMIN_UID, { name: "" })));
  await assertFails(setDoc(doc(admin, "ingredients", "otro-uid"), commonIngredient("otro")));

  await assertSucceeds(
    updateDoc(doc(admin, "ingredients", "arroz"), {
      name: "Arroz redondo", nameLower: "arroz redondo", updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
    }),
  );
  const source = (await getDoc(doc(admin, "ingredients", "arroz"))).data().source;
  assert.equal(source.provider, "bobitos");
  await assertFails(
    updateDoc(doc(admin, "ingredients", "arroz"), {
      source: { provider: "bobitos", importedAt: Timestamp.now() },
      updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(doc(admin, "ingredients", "arroz"), { createdAt: Timestamp.now(), updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp() }),
  );
});

test("las marcas bajo ingredients están cerradas para todos", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "ingredients", "arroz", "brands", "b1"), { name: "SOS" });
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const user = verifiedFirestore("brand-closed");
  for (const db of [admin, user]) {
    await assertFails(getDoc(doc(db, "ingredients", "arroz", "brands", "b1")));
    await assertFails(getDocs(collection(db, "ingredients", "arroz", "brands")));
    await assertFails(setDoc(doc(db, "ingredients", "arroz", "brands", "b2"), { name: "Nueva" }));
    await assertFails(writeBatch(db).delete(doc(db, "ingredients", "arroz", "brands", "b1")).commit());
  }
});

test("catalogMeta/ingredients: un verificado la lee; solo el admin la crea y la sube de uno en uno", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "catalogMeta", "ingredients"));
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const user = verifiedFirestore("meta-ing-user");
  const ref = (db) => doc(db, "catalogMeta", "ingredients");

  await assertFails(setDoc(ref(user), metaData("meta-ing-user", 1)));
  await assertFails(setDoc(ref(admin), metaData(RECIPE_ADMIN_UID, 2)));
  await assertSucceeds(setDoc(ref(admin), metaData(RECIPE_ADMIN_UID, 1)));

  await assertSucceeds(getDoc(ref(user)));
  await assertFails(getDoc(ref(unverifiedFirestore("meta-ing-unverified"))));

  await assertFails(updateDoc(ref(user), { version: 2, updatedAt: serverTimestamp(), updatedBy: "meta-ing-user" }));
  await assertFails(updateDoc(ref(admin), { version: 3, updatedAt: serverTimestamp(), updatedBy: RECIPE_ADMIN_UID }));
  await assertFails(updateDoc(ref(admin), { version: 1, updatedAt: serverTimestamp(), updatedBy: RECIPE_ADMIN_UID }));
  await assertSucceeds(updateDoc(ref(admin), { version: 2, updatedAt: serverTimestamp(), updatedBy: RECIPE_ADMIN_UID }));
  await assertFails(deleteDoc(ref(admin)));
});

test("una transacción del admin crea un ingrediente común y sube catalogMeta/ingredients", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "catalogMeta", "ingredients"));
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const metaRef = doc(admin, "catalogMeta", "ingredients");
  const alta = (id) =>
    runTransaction(admin, async (tx) => {
      const snap = await tx.get(metaRef);
      tx.set(doc(admin, "ingredients", id), commonIngredient(RECIPE_ADMIN_UID));
      if (snap.exists()) tx.update(metaRef, metaData(RECIPE_ADMIN_UID, snap.data().version + 1));
      else tx.set(metaRef, metaData(RECIPE_ADMIN_UID, 1));
    });
  await assertSucceeds(alta("tx-uno"));
  assert.equal((await getDoc(metaRef)).data().version, 1);
  await assertSucceeds(alta("tx-dos"));
  assert.equal((await getDoc(metaRef)).data().version, 2);
});

const personalIngredient = (overrides = {}) => ({
  name: "Tomate frito casero",
  nameLower: "tomate frito casero",
  category: "Salsas",
  defaultUnit: "g",
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...overrides,
});

const personalBrand = (overrides = {}) => ({
  ingredientId: "tomate",
  name: "Hacendado",
  favorite: false,
  stores: ["MERCADONA"],
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...overrides,
});

test("ingredientes personales: solo su dueño los lee, lista y escribe", async () => {
  const alice = verifiedFirestore("pi-alice");
  const bob = verifiedFirestore("pi-bob");
  const path = (db, uid, id) => doc(db, "users", uid, "ingredients", id);

  await assertSucceeds(setDoc(path(alice, "pi-alice", "salsa-casera"), personalIngredient()));
  await assertSucceeds(getDoc(path(alice, "pi-alice", "salsa-casera")));
  await assertSucceeds(getDocs(collection(alice, "users", "pi-alice", "ingredients")));
  await assertFails(getDoc(path(bob, "pi-alice", "salsa-casera")));
  await assertFails(getDocs(collection(bob, "users", "pi-alice", "ingredients")));
  await assertFails(setDoc(path(bob, "pi-alice", "otra"), personalIngredient()));
  await assertFails(setDoc(path(unverifiedFirestore("pi-un"), "pi-un", "otra"), personalIngredient()));
  await assertFails(
    updateDoc(path(bob, "pi-alice", "salsa-casera"), { name: "X", nameLower: "x", updatedAt: serverTimestamp() }),
  );
  await assertSucceeds(
    updateDoc(path(alice, "pi-alice", "salsa-casera"), {
      name: "Salsa", nameLower: "salsa", updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(writeBatch(bob).delete(path(bob, "pi-alice", "salsa-casera")).commit());
  await assertSucceeds(writeBatch(alice).delete(path(alice, "pi-alice", "salsa-casera")).commit());
});

test("un ingrediente personal no puede tener el id de uno común ni un id que no sea slug", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "ingredients", "arroz"), importedIngredient());
  });
  const alice = verifiedFirestore("pi-slug");
  const path = (id) => doc(alice, "users", "pi-slug", "ingredients", id);

  await assertFails(setDoc(path("arroz"), personalIngredient()));
  await assertSucceeds(setDoc(path("arroz-integral"), personalIngredient()));
  await assertFails(setDoc(path("Arroz"), personalIngredient()));
  await assertFails(setDoc(path("con_guion_bajo"), personalIngredient()));
  await assertFails(setDoc(path("doble--guion"), personalIngredient()));
  await assertFails(setDoc(path("-empieza"), personalIngredient()));
  await assertFails(setDoc(path("a".repeat(121)), personalIngredient()));
  await assertSucceeds(setDoc(path("a".repeat(120)), personalIngredient()));
});

test("un ingrediente personal valida límites y no cambia createdAt", async () => {
  const alice = verifiedFirestore("pi-limits");
  const path = (id) => doc(alice, "users", "pi-limits", "ingredients", id);

  await assertFails(setDoc(path("vacio"), personalIngredient({ name: "" })));
  await assertFails(setDoc(path("largo"), personalIngredient({ name: "a".repeat(121) })));
  await assertFails(setDoc(path("nl-largo"), personalIngredient({ nameLower: "a".repeat(121) })));
  await assertFails(setDoc(path("cat-larga"), personalIngredient({ category: "a".repeat(61) })));
  await assertFails(setDoc(path("unit-larga"), personalIngredient({ defaultUnit: "a".repeat(25) })));
  await assertFails(setDoc(path("ajeno"), personalIngredient({ ownerUid: "pi-limits" })));
  await assertFails(setDoc(path("sin-nl"), { name: "X", createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  await assertFails(setDoc(path("fecha-falsa"), personalIngredient({ createdAt: Timestamp.fromMillis(1000) })));
  await assertSucceeds(setDoc(path("nulos"), personalIngredient({ category: null, defaultUnit: null })));
  await assertSucceeds(setDoc(path("minimo"), {
    name: "Mínimo", nameLower: "minimo", createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  }));

  await assertFails(updateDoc(path("nulos"), { createdAt: Timestamp.fromMillis(1000), updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(path("nulos"), { name: "Y", nameLower: "y", updatedAt: Timestamp.fromMillis(1000) }));
  await assertFails(updateDoc(path("nulos"), { name: "Y", nameLower: "y", extra: 1, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(path("nulos"), { name: "Y", nameLower: "y", updatedAt: serverTimestamp() }));
});

test("marcas personales: solo su dueño las lee, lista y escribe", async () => {
  const alice = verifiedFirestore("pb-alice");
  const bob = verifiedFirestore("pb-bob");
  const path = (db, uid, id) => doc(db, "users", uid, "brands", id);

  await assertSucceeds(setDoc(path(alice, "pb-alice", "b1"), personalBrand({ energyKcal: 30, salt: 0.1, barcode: "8480000" })));
  await assertSucceeds(getDoc(path(alice, "pb-alice", "b1")));
  await assertSucceeds(getDocs(collection(alice, "users", "pb-alice", "brands")));
  await assertFails(getDoc(path(bob, "pb-alice", "b1")));
  await assertFails(getDocs(collection(bob, "users", "pb-alice", "brands")));
  await assertFails(setDoc(path(bob, "pb-alice", "b2"), personalBrand()));
  await assertFails(setDoc(path(unverifiedFirestore("pb-un"), "pb-un", "b2"), personalBrand()));
  await assertFails(updateDoc(path(bob, "pb-alice", "b1"), { name: "X", updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(path(alice, "pb-alice", "b1"), { name: "SOS", favorite: true, updatedAt: serverTimestamp() }));
  await assertFails(writeBatch(bob).delete(path(bob, "pb-alice", "b1")).commit());
  await assertSucceeds(writeBatch(alice).delete(path(alice, "pb-alice", "b1")).commit());
});

test("una marca personal admite tiendas de barrio y sigue limitada a 6", async () => {
  const alice = verifiedFirestore("pb-barrio");
  const path = (id) => doc(alice, "users", "pb-barrio", "brands", id);

  await assertSucceeds(setDoc(path("barrio"), personalBrand({ stores: ["CARNICERIA", "MERCADONA"] })));
  await assertSucceeds(setDoc(path("seis-barrio"), personalBrand({ stores: ["CARNICERIA", "PESCADERIA", "FRUTERIA", "PANADERIA", "DIA", "OTROS"] })));
  await assertFails(setDoc(path("siete"), personalBrand({
    stores: ["DIA", "EROSKI", "MERCADONA", "CARNICERIA", "PESCADERIA", "FRUTERIA", "PANADERIA"],
  })));
  await assertFails(setDoc(path("indif"), personalBrand({ stores: ["FRUTERIA", "INDIFERENTE"] })));
});

test("una marca personal valida tiendas (enum, sin INDIFERENTE, sin repetidos, como mucho 6), favorita y nutrición", async () => {
  const alice = verifiedFirestore("pb-shape");
  const path = (id) => doc(alice, "users", "pb-shape", "brands", id);

  await assertSucceeds(setDoc(path("ok"), personalBrand({ stores: [], fat: 1.2, sugars: null })));
  await assertSucceeds(setDoc(path("seis"), personalBrand({
    stores: ["DIA", "EROSKI", "MERCADONA", "CARREFOUR", "ALCAMPO", "OTROS"],
  })));
  await assertFails(setDoc(path("indif"), personalBrand({ stores: ["INDIFERENTE"] })));
  await assertFails(setDoc(path("desconocida"), personalBrand({ stores: ["LIDL"] })));
  await assertFails(setDoc(path("repetida"), personalBrand({ stores: ["DIA", "DIA"] })));
  await assertFails(setDoc(path("siete"), personalBrand({
    stores: ["DIA", "EROSKI", "MERCADONA", "CARREFOUR", "ALCAMPO", "OTROS", "DIA"],
  })));
  await assertFails(setDoc(path("no-lista"), personalBrand({ stores: "DIA" })));
  await assertFails(setDoc(path("sin-stores"), (({ stores, ...rest }) => rest)(personalBrand())));
  await assertFails(setDoc(path("fav-texto"), personalBrand({ favorite: "si" })));
  await assertFails(setDoc(path("sin-fav"), (({ favorite, ...rest }) => rest)(personalBrand())));
  await assertFails(setDoc(path("fat-texto"), personalBrand({ fat: "mucha" })));
  await assertFails(setDoc(path("nombre-vacio"), personalBrand({ name: "" })));
  await assertFails(setDoc(path("nombre-largo"), personalBrand({ name: "a".repeat(61) })));
  await assertFails(setDoc(path("barcode-largo"), personalBrand({ barcode: "1".repeat(33) })));
  await assertFails(setDoc(path("ing-no-slug"), personalBrand({ ingredientId: "No Slug" })));
  await assertFails(setDoc(path("ing-largo"), personalBrand({ ingredientId: "a".repeat(121) })));
  await assertFails(setDoc(path("ajeno"), personalBrand({ ownerUid: "pb-shape" })));
  await assertFails(setDoc(path("fecha-falsa"), personalBrand({ createdAt: Timestamp.fromMillis(1000) })));

  await assertFails(updateDoc(path("ok"), { stores: ["INDIFERENTE"], updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(path("ok"), { stores: ["DIA", "OTROS"], updatedAt: serverTimestamp() }));
});

test("una marca personal no cambia de ingrediente al editarla", async () => {
  const alice = verifiedFirestore("pb-fixed");
  const ref = doc(alice, "users", "pb-fixed", "brands", "b1");
  await assertSucceeds(setDoc(ref, personalBrand()));

  await assertFails(updateDoc(ref, { ingredientId: "cebolla", updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(ref, { createdAt: Timestamp.fromMillis(1000), updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(ref, { name: "Otra", updatedAt: Timestamp.fromMillis(1000) }));
  await assertSucceeds(updateDoc(ref, { name: "Otra", updatedAt: serverTimestamp() }));
  assert.equal((await getDoc(ref)).data().ingredientId, "tomate");
});

test("cualquier usuario crea un ejercicio del catálogo y todos lo leen, pero no a nombre de otro", async () => {
  const alice = verifiedFirestore("ex-alice");
  const bob = verifiedFirestore("ex-bob");

  await assertSucceeds(setDoc(doc(alice, "exercises", "press-banca"), exerciseData("ex-alice")));
  await assertSucceeds(getDoc(doc(bob, "exercises", "press-banca")));
  await assertFails(setDoc(doc(bob, "exercises", "sentadilla"), exerciseData("ex-alice")));
});

test("un ejercicio valida el tipo y rechaza campos ajenos o nombre vacío", async () => {
  const chef = verifiedFirestore("ex-shape");

  await assertSucceeds(setDoc(doc(chef, "exercises", "remo"), exerciseData("ex-shape", { type: "MAQUINA" })));
  await assertFails(
    setDoc(doc(chef, "exercises", "bad-type"), exerciseData("ex-shape", { type: "YOGA" })),
  );
  await assertFails(setDoc(doc(chef, "exercises", "extra"), exerciseData("ex-shape", { spaceId: "x" })));
  await assertFails(setDoc(doc(chef, "exercises", "vacio"), exerciseData("ex-shape", { name: "", nameLower: "" })));
});

const metaData = (uid, version, overrides = {}) => ({
  version,
  updatedAt: serverTimestamp(),
  updatedBy: uid,
  ...overrides,
});

test("catalogMeta: un verificado la lee y uno sin verificar no", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "catalogMeta", "exercises"), {
      version: 3, updatedAt: Timestamp.now(), updatedBy: RECIPE_ADMIN_UID,
    });
  });
  await assertSucceeds(getDoc(doc(verifiedFirestore("meta-reader"), "catalogMeta", "exercises")));
  await assertFails(getDoc(doc(unverifiedFirestore("meta-unverified"), "catalogMeta", "exercises")));
  await assertFails(getDocs(collection(verifiedFirestore("meta-reader"), "catalogMeta")));
});

test("catalogMeta: crear solo con version 1, updatedAt == request.time y updatedBy propio", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "catalogMeta", "exercises"));
  });
  const user = verifiedFirestore("meta-creator");
  const ref = (db) => doc(db, "catalogMeta", "exercises");
  await assertFails(setDoc(ref(user), metaData("meta-creator", 2)));
  await assertFails(setDoc(ref(user), metaData("meta-creator", 1.5)));
  await assertFails(setDoc(ref(user), metaData("meta-creator", "1")));
  await assertFails(setDoc(ref(user), metaData("meta-creator", 1, { updatedBy: "otro" })));
  await assertFails(setDoc(ref(user), metaData("meta-creator", 1, { updatedAt: Timestamp.fromMillis(1000) })));
  await assertFails(setDoc(ref(user), metaData("meta-creator", 1, { extra: true })));
  await assertFails(setDoc(ref(user), { version: 1, updatedAt: serverTimestamp() }));
  await assertFails(setDoc(ref(unverifiedFirestore("meta-creator-u")), metaData("meta-creator-u", 1)));
  await assertSucceeds(setDoc(ref(user), metaData("meta-creator", 1)));
});

test("catalogMeta: actualizar solo sube de uno en uno (ni igual, ni +2, ni bajar, ni campos extra)", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "catalogMeta", "exercises"), {
      version: 5, updatedAt: Timestamp.now(), updatedBy: RECIPE_ADMIN_UID,
    });
  });
  const user = verifiedFirestore("meta-updater");
  const ref = doc(user, "catalogMeta", "exercises");
  const upd = (v, extra = {}) => updateDoc(ref, { version: v, updatedAt: serverTimestamp(), updatedBy: "meta-updater", ...extra });
  await assertFails(upd(5));
  await assertFails(upd(7));
  await assertFails(upd(4));
  await assertFails(upd(6.5));
  await assertFails(upd(6, { extra: 1 }));
  await assertFails(upd(6, { updatedBy: "otro" }));
  await assertFails(updateDoc(ref, { version: 6, updatedAt: Timestamp.fromMillis(1000), updatedBy: "meta-updater" }));
  await assertFails(updateDoc(doc(unverifiedFirestore("meta-updater-u"), "catalogMeta", "exercises"),
    { version: 6, updatedAt: serverTimestamp(), updatedBy: "meta-updater-u" }));
  await assertSucceeds(upd(6));
  await assertFails(upd(6));
  await assertSucceeds(upd(7));
});

test("catalogMeta: nadie la borra y otro catalogId se rechaza", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "catalogMeta", "exercises"), {
      version: 1, updatedAt: Timestamp.now(), updatedBy: RECIPE_ADMIN_UID,
    });
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertFails(deleteDoc(doc(admin, "catalogMeta", "exercises")));
  await assertFails(setDoc(doc(verifiedFirestore("meta-other"), "catalogMeta", "recipes"), metaData("meta-other", 1)));
  await assertFails(getDoc(doc(admin, "catalogMeta", "recipes")));
});

test("catalogMeta: una transacción de alta de ejercicio + subida de versión pasa", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "catalogMeta", "exercises"));
  });
  const user = verifiedFirestore("meta-tx");
  const metaRef = doc(user, "catalogMeta", "exercises");
  const altaConVersion = (id) =>
    runTransaction(user, async (tx) => {
      const snap = await tx.get(metaRef);
      tx.set(doc(user, "exercises", id), exerciseData("meta-tx"));
      if (snap.exists()) tx.update(metaRef, metaData("meta-tx", snap.data().version + 1));
      else tx.set(metaRef, metaData("meta-tx", 1));
    });
  await assertSucceeds(altaConVersion("tx-uno"));
  assert.equal((await getDoc(metaRef)).data().version, 1);
  await assertSucceeds(altaConVersion("tx-dos"));
  assert.equal((await getDoc(metaRef)).data().version, 2);
  // La subida de versión sigue siendo opcional para las reglas de exercises (beta 17).
  await assertSucceeds(setDoc(doc(user, "exercises", "sin-version"), exerciseData("meta-tx")));
});

test("solo el autor o un admin editan/borran un ejercicio del catálogo", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "exercises", "curl"), {
      ...exerciseData("ex-owner"),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const owner = verifiedFirestore("ex-owner");
  const other = verifiedFirestore("ex-other");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);

  await assertSucceeds(
    updateDoc(doc(owner, "exercises", "curl"), {
      name: "Curl bíceps",
      nameLower: "curl bíceps",
      updatedBy: "ex-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(doc(other, "exercises", "curl"), {
      name: "Manipulado",
      nameLower: "manipulado",
      updatedBy: "ex-other",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(writeBatch(other).delete(doc(other, "exercises", "curl")).commit());
  await assertSucceeds(writeBatch(admin).delete(doc(admin, "exercises", "curl")).commit());
});

const importedEntry = {
  id: "sentadilla-trasera",
  name: "Sentadilla trasera",
  type: "PESO_LIBRE",
  muscleGroup: "Cuádriceps",
  description: "Texto del catálogo",
  equipment: ["BARRA"],
  source: {
    provider: "wger",
    id: 111,
    author: "Autor",
    license: "CC-BY-SA-4.0",
    url: "https://wger.de/es/exercise/111/view/",
  },
};

async function seedImportedExercise(id, entry = importedEntry, raw = {}) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "exercises", id), {
      ...toFirestoreDoc(entry, { now: Timestamp.now(), create: true }),
      ...raw,
    });
  });
}

function editFields(uid, fields) {
  return { ...fields, updatedBy: uid, updatedAt: serverTimestamp() };
}

test("un ejercicio acepta PESO_CORPORAL, descripción y material válidos", async () => {
  const user = verifiedFirestore("ex-new-fields");
  await assertSucceeds(
    setDoc(
      doc(user, "exercises", "dominadas"),
      exerciseData("ex-new-fields", {
        type: "PESO_CORPORAL",
        description: "Colgado de la barra.",
        equipment: ["BARRA_DOMINADAS", "BANDA_ELASTICA"],
      }),
    ),
  );
  await assertSucceeds(
    setDoc(doc(user, "exercises", "flexiones"), exerciseData("ex-new-fields", { description: null, equipment: [] })),
  );
});

test("un ejercicio rechaza descripción >2000, material desconocido, repetido o que no es lista", async () => {
  const user = verifiedFirestore("ex-bad-fields");
  const bad = (id, overrides) =>
    assertFails(setDoc(doc(user, "exercises", id), exerciseData("ex-bad-fields", overrides)));
  await bad("larga", { description: "x".repeat(2001) });
  await bad("no-texto", { description: 5 });
  await bad("desconocido", { equipment: ["TRX"] });
  await bad("repetido", { equipment: ["BARRA", "BARRA"] });
  await bad("no-lista", { equipment: "BARRA" });
  await bad("mapa", { equipment: { BARRA: true } });
  await assertSucceeds(
    setDoc(doc(user, "exercises", "limite"), exerciseData("ex-bad-fields", { description: "x".repeat(2000) })),
  );
});

test("nadie crea un ejercicio con source desde el cliente, ni siquiera el admin", async () => {
  const user = verifiedFirestore("ex-src-user");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertFails(
    setDoc(doc(user, "exercises", "con-fuente"), exerciseData("ex-src-user", { source: importedEntry.source })),
  );
  await assertFails(
    setDoc(doc(admin, "exercises", "con-fuente-admin"), exerciseData(RECIPE_ADMIN_UID, { source: importedEntry.source })),
  );
});

test("un documento del importador cumple la forma: el admin edita su descripción, nadie toca su source y otro usuario no lo edita", async () => {
  await seedImportedExercise("sentadilla-trasera");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const other = verifiedFirestore("ex-imp-other");
  const ref = (db) => doc(db, "exercises", "sentadilla-trasera");

  await assertSucceeds(
    updateDoc(ref(admin), editFields(RECIPE_ADMIN_UID, { description: "Editada", equipment: ["BARRA", "BANCO"] })),
  );
  await assertFails(updateDoc(ref(other), editFields("ex-imp-other", { description: "Ajena" })));
  await assertFails(
    updateDoc(ref(admin), editFields(RECIPE_ADMIN_UID, { source: { ...importedEntry.source, id: 999 } })),
  );
  await assertFails(updateDoc(ref(admin), editFields(RECIPE_ADMIN_UID, { "source.author": "Otro" })));
  await assertFails(updateDoc(ref(admin), editFields(RECIPE_ADMIN_UID, { source: null })));
});

test("un source con proveedor o licencia no admitidos invalida cualquier edición", async () => {
  await seedImportedExercise("fuente-mala-proveedor", {
    ...importedEntry,
    source: { ...importedEntry.source, provider: "otro" },
  });
  await seedImportedExercise("fuente-mala-licencia", {
    ...importedEntry,
    source: { ...importedEntry.source, license: "ODbL" },
  });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  for (const id of ["fuente-mala-proveedor", "fuente-mala-licencia"]) {
    await assertFails(updateDoc(doc(admin, "exercises", id), editFields(RECIPE_ADMIN_UID, { description: "x" })));
  }
});

const wgerImage = {
  hash: "a".repeat(64),
  author: "Ana",
  license: "CC-BY-SA-4.0",
  sourceUrl: "https://wger.de/media/exercise-images/111/a.png",
};
const ownSource = { provider: "bobitos", id: 1, license: "CC-BY-SA-4.0", author: "Catálogo Bobitos" };

test("un ejercicio acepta measure REPS o SECONDS y rechaza MINUTES", async () => {
  const user = verifiedFirestore("ex-measure");
  await assertSucceeds(setDoc(doc(user, "exercises", "m-reps"), exerciseData("ex-measure", { measure: "REPS" })));
  await assertSucceeds(setDoc(doc(user, "exercises", "m-secs"), exerciseData("ex-measure", { measure: "SECONDS" })));
  await assertSucceeds(setDoc(doc(user, "exercises", "m-null"), exerciseData("ex-measure", { measure: null })));
  await assertFails(setDoc(doc(user, "exercises", "m-min"), exerciseData("ex-measure", { measure: "MINUTES" })));
  await assertFails(setDoc(doc(user, "exercises", "m-num"), exerciseData("ex-measure", { measure: 30 })));
  await assertSucceeds(
    updateDoc(doc(user, "exercises", "m-reps"), editFields("ex-measure", { measure: "SECONDS" })),
  );
  await assertFails(updateDoc(doc(user, "exercises", "m-reps"), editFields("ex-measure", { measure: "MINUTES" })));
});

test("nadie crea un ejercicio con image desde el cliente, ni el admin", async () => {
  const user = verifiedFirestore("ex-img-user");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertFails(setDoc(doc(user, "exercises", "con-imagen"), exerciseData("ex-img-user", { image: wgerImage })));
  await assertFails(setDoc(doc(user, "exercises", "imagen-nula"), exerciseData("ex-img-user", { image: null })));
  await assertFails(
    setDoc(doc(admin, "exercises", "con-imagen-admin"), exerciseData(RECIPE_ADMIN_UID, { image: wgerImage })),
  );
});

test("en una ficha importada con imagen el admin edita la descripción y nadie cambia la imagen", async () => {
  await seedImportedExercise("con-imagen", { ...importedEntry, image: wgerImage });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "exercises", "con-imagen");
  await assertSucceeds(updateDoc(ref, editFields(RECIPE_ADMIN_UID, { description: "Editada", measure: "SECONDS" })));
  await assertFails(updateDoc(ref, editFields(RECIPE_ADMIN_UID, { image: { ...wgerImage, author: "Otro" } })));
  await assertFails(updateDoc(ref, editFields(RECIPE_ADMIN_UID, { image: null })));
  await assertFails(updateDoc(ref, editFields(RECIPE_ADMIN_UID, { "image.sourceUrl": "https://wger.de/media/otra.png" })));
  await assertFails(updateDoc(ref, editFields(RECIPE_ADMIN_UID, { image: deleteField() })));
});

test("una ficha con image {hash, license, sourceUrl} es válida; image con url, hash no hexadecimal o sourceUrl ajena no", async () => {
  const good = {
    "img-minima": { hash: "b".repeat(64), license: "CC0-1.0", sourceUrl: "https://wger.de/media/a.png" },
    "img-completa": wgerImage,
  };
  const bad = {
    "img-url-vieja": { url: "https://wger.de/media/a.png", author: "Ana", license: "CC-BY-SA-4.0" },
    "img-url-extra": { ...wgerImage, url: "https://wger.de/media/a.png" },
    "img-sin-hash": { license: "CC0-1.0", sourceUrl: "https://wger.de/media/a.png" },
    "img-hash-corto": { ...wgerImage, hash: "a".repeat(63) },
    "img-hash-mayus": { ...wgerImage, hash: "A".repeat(64) },
    "img-hash-no-hex": { ...wgerImage, hash: "g".repeat(64) },
    "img-hash-numero": { ...wgerImage, hash: 12 },
    "img-ajena": { ...wgerImage, sourceUrl: "https://evil.example/media/a.png" },
    "img-ruta": { ...wgerImage, sourceUrl: "https://wger.de/es/exercise/1/view/" },
    "img-larga-url": { ...wgerImage, sourceUrl: `https://wger.de/media/${"x".repeat(300)}` },
    "img-licencia": { ...wgerImage, license: "ODbL" },
    "img-larga": { ...wgerImage, author: "x".repeat(201) },
    "img-extra": { ...wgerImage, extra: 1 },
    "img-texto": "https://wger.de/media/a.png",
  };
  const entries = { ...good, ...bad };
  for (const [id, image] of Object.entries(entries)) await seedImportedExercise(id, importedEntry, { image });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  for (const id of Object.keys(good)) {
    await assertSucceeds(updateDoc(doc(admin, "exercises", id), editFields(RECIPE_ADMIN_UID, { description: "x" })));
  }
  for (const id of Object.keys(bad)) {
    await assertFails(updateDoc(doc(admin, "exercises", id), editFields(RECIPE_ADMIN_UID, { description: "x" })));
  }
});

test("exerciseImages: un verificado la lee (get) y uno sin verificar no; nadie la lista ni la escribe", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "exerciseImages", "con-foto"), {
      data: Bytes.fromUint8Array(new Uint8Array([1, 2, 3])),
      contentType: "image/webp",
      hash: "a".repeat(64),
      width: 10,
      height: 10,
      license: "CC0-1.0",
      sourceUrl: "https://wger.de/media/a.png",
    });
  });
  const user = verifiedFirestore("img-reader");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertSucceeds(getDoc(doc(user, "exerciseImages", "con-foto")));
  await assertSucceeds(getDoc(doc(user, "exerciseImages", "no-existe")));
  await assertFails(getDoc(doc(unverifiedFirestore("img-unverified"), "exerciseImages", "con-foto")));
  await assertFails(getDocs(collection(user, "exerciseImages")));
  await assertFails(getDocs(collection(admin, "exerciseImages")));
  const payload = { data: Bytes.fromUint8Array(new Uint8Array([9])), contentType: "image/webp", hash: "b".repeat(64) };
  await assertFails(setDoc(doc(user, "exerciseImages", "nueva"), payload));
  await assertFails(setDoc(doc(admin, "exerciseImages", "nueva-admin"), payload));
  await assertFails(updateDoc(doc(admin, "exerciseImages", "con-foto"), { hash: "c".repeat(64) }));
  await assertFails(deleteDoc(doc(admin, "exerciseImages", "con-foto")));
});

test("una ficha bobitos (sin url, CC BY-SA 4.0) es editable por el admin; bobitos con url u otra licencia no", async () => {
  await seedImportedExercise("propia", { ...importedEntry, source: ownSource });
  await seedImportedExercise("propia-url", {
    ...importedEntry,
    source: { ...ownSource, url: "https://wger.de/es/exercise/1/view/" },
  });
  await seedImportedExercise("propia-licencia", { ...importedEntry, source: { ...ownSource, license: "CC0-1.0" } });
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const other = verifiedFirestore("ex-own-other");
  await assertSucceeds(
    updateDoc(doc(admin, "exercises", "propia"), editFields(RECIPE_ADMIN_UID, { description: "Editada" })),
  );
  await assertFails(updateDoc(doc(other, "exercises", "propia"), editFields("ex-own-other", { description: "x" })));
  await assertFails(
    updateDoc(doc(admin, "exercises", "propia"), editFields(RECIPE_ADMIN_UID, { source: ownSource })),
  );
  for (const id of ["propia-url", "propia-licencia"]) {
    await assertFails(updateDoc(doc(admin, "exercises", id), editFields(RECIPE_ADMIN_UID, { description: "x" })));
  }
});

test("el dueño añade descripción y material a un ejercicio antiguo", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "exercises", "antiguo"), {
      ...exerciseData("ex-old-owner"),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const owner = verifiedFirestore("ex-old-owner");
  const other = verifiedFirestore("ex-old-other");
  await assertSucceeds(
    updateDoc(
      doc(owner, "exercises", "antiguo"),
      editFields("ex-old-owner", { description: "Nueva", equipment: ["BARRA"], type: "PESO_CORPORAL" }),
    ),
  );
  await assertFails(updateDoc(doc(other, "exercises", "antiguo"), editFields("ex-old-other", { description: "x" })));
  await assertFails(
    updateDoc(doc(owner, "exercises", "antiguo"), editFields("ex-old-owner", { source: importedEntry.source })),
  );
});

test("cualquier usuario verificado crea y lee sus rutinas personales, no las ajenas", async () => {
  const trainer = verifiedFirestore("routine-trainer");
  const other = verifiedFirestore("routine-other");
  const reference = doc(trainer, "routines", "trainer-private");

  await assertSucceeds(setDoc(reference, routineData("routine-trainer")));
  await assertSucceeds(getDoc(reference));
  await assertFails(getDoc(doc(other, "routines", "trainer-private")));
});

test("las rutinas del catálogo común (GLOBAL) las lee cualquier usuario verificado", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "routines", "global-routine-1"), {
      ...routineData("routine-catalog-owner", { visibility: "GLOBAL" }),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const anyone = verifiedFirestore("routine-reader");

  await assertSucceeds(getDoc(doc(anyone, "routines", "global-routine-1")));
  await assertSucceeds(
    getDocs(query(collection(anyone, "routines"), where("visibility", "==", "GLOBAL"))),
  );
});

test("un usuario no puede crear una rutina a nombre de otro ni una GLOBAL sin ser admin", async () => {
  const trainer = verifiedFirestore("routine-spoof");

  await assertFails(
    setDoc(doc(trainer, "routines", "spoofed"), routineData("routine-spoof", { ownerUid: "someone-else" })),
  );
  await assertFails(
    setDoc(doc(trainer, "routines", "sneaky-global"), routineData("routine-spoof", { visibility: "GLOBAL" })),
  );
});

test("un admin del catálogo puede publicar una rutina GLOBAL y curar (borrar) la ajena", async () => {
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertSucceeds(
    setDoc(
      doc(admin, "routines", "curated-routine"),
      routineData(RECIPE_ADMIN_UID, { visibility: "GLOBAL", title: "Full body común" }),
    ),
  );

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "routines", "curated-foreign"), {
      ...routineData("otro-autor", { visibility: "GLOBAL" }),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const normal = verifiedFirestore("routine-normal");
  await assertFails(writeBatch(normal).delete(doc(normal, "routines", "curated-foreign")).commit());
  await assertSucceeds(writeBatch(admin).delete(doc(admin, "routines", "curated-foreign")).commit());
});

test("solo el propietario edita o borra su rutina personal", async () => {
  const trainer = verifiedFirestore("routine-owner");
  const other = verifiedFirestore("routine-intruder");
  const reference = doc(trainer, "routines", "owned-routine");
  await assertSucceeds(setDoc(reference, routineData("routine-owner")));

  await assertFails(
    updateDoc(doc(other, "routines", "owned-routine"), {
      title: "Editada por otro",
      updatedBy: "routine-intruder",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(writeBatch(other).delete(doc(other, "routines", "owned-routine")).commit());
  await assertSucceeds(
    updateDoc(reference, {
      title: "Editada por su dueño",
      updatedBy: "routine-owner",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(writeBatch(trainer).delete(reference).commit());
});

test("una rutina no puede cambiar de propietario ni de visibilidad al editarla", async () => {
  const trainer = verifiedFirestore("routine-immutable");
  const reference = doc(trainer, "routines", "fixed-routine");
  await assertSucceeds(setDoc(reference, routineData("routine-immutable")));

  await assertFails(
    updateDoc(reference, {
      visibility: "GLOBAL",
      updatedBy: "routine-immutable",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("la rutina valida la forma y rechaza campos ajenos o una lista de ejercicios enorme", async () => {
  const trainer = verifiedFirestore("routine-schema");

  await assertFails(
    setDoc(doc(trainer, "routines", "bad-visibility"), routineData("routine-schema", { visibility: "SECRETO" })),
  );
  await assertFails(
    setDoc(doc(trainer, "routines", "extra-field"), routineData("routine-schema", { spaceId: "x" })),
  );
  await assertFails(
    setDoc(
      doc(trainer, "routines", "too-many-exercises"),
      routineData("routine-schema", {
        exercises: Array.from({ length: 31 }, (_, index) => ({ name: `ej-${index}`, type: "OTROS" })),
      }),
    ),
  );
  await assertFails(
    setDoc(doc(trainer, "routines", "bad-exercises"), routineData("routine-schema", { exercises: "press, curl" })),
  );
});

const importedRoutine = {
  id: "torso",
  title: "Torso",
  description: "Intermedio · 60 min · barra · descansa 90 s entre series · 2 veces por semana.",
  exercises: [{ name: "Press de banca", exerciseId: "press-de-banca", type: "PESO_LIBRE",
    sets: [{ reps: 8, weight: null }], durationMinutes: null, level: null, notes: null }],
};

async function seedImportedRoutine(id) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "routines", id),
      routineToFirestoreDoc({ ...importedRoutine, id }, { now: Timestamp.now(), create: true }));
  });
}

test("el admin edita una rutina importada pero no su fuente, y cualquiera la lee", async () => {
  await seedImportedRoutine("torso-importada");
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  const ref = doc(admin, "routines", "torso-importada");
  await assertSucceeds(updateDoc(ref, {
    title: "Torso (editada)", description: "Otra", exercises: [], updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(ref, {
    source: { provider: "bobitos", importedAt: Timestamp.now() }, updatedBy: RECIPE_ADMIN_UID, updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(verifiedFirestore("routine-imported-reader"), "routines", "torso-importada")));
});

test("ningún cliente crea una rutina con la fuente del catálogo", async () => {
  const source = { provider: "bobitos", importedAt: Timestamp.now() };
  const admin = verifiedFirestore(RECIPE_ADMIN_UID);
  await assertFails(setDoc(doc(admin, "routines", "falsa-global"),
    routineData(RECIPE_ADMIN_UID, { visibility: "GLOBAL", source })));
  const user = verifiedFirestore("routine-fake-source");
  await assertFails(setDoc(doc(user, "routines", "falsa"), routineData("routine-fake-source", { source })));
});

test("deshacer el borrado de una rutina personal la devuelve con su id", async () => {
  const trainer = verifiedFirestore("routine-restorer");
  const reference = doc(trainer, "routines", "restorable");
  await assertSucceeds(setDoc(reference, routineData("routine-restorer", { sourceRoutineId: "torso" })));
  await assertSucceeds(writeBatch(trainer).delete(reference).commit());
  await assertSucceeds(setDoc(reference, routineData("routine-restorer", { sourceRoutineId: "torso" })));
  const restored = await getDoc(reference);
  assert.equal(restored.exists(), true);
  assert.equal(restored.data().sourceRoutineId, "torso");
});

test("una copia guarda el id de la común, válido e inmutable", async () => {
  const user = verifiedFirestore("routine-forker");
  const ref = doc(user, "routines", "copia");
  await assertSucceeds(setDoc(ref, routineData("routine-forker", { sourceRoutineId: "torso" })));
  await assertSucceeds(updateDoc(ref, { title: "Mi torso", updatedBy: "routine-forker", updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(ref, { sourceRoutineId: "otra", updatedBy: "routine-forker", updatedAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(user, "routines", "origen-nulo"), routineData("routine-forker", { sourceRoutineId: null })));
  await assertFails(setDoc(doc(user, "routines", "origen-numero"), routineData("routine-forker", { sourceRoutineId: 123 })));
  await assertFails(setDoc(doc(user, "routines", "origen-vacio"), routineData("routine-forker", { sourceRoutineId: "" })));
  await assertFails(setDoc(doc(user, "routines", "origen-largo"),
    routineData("routine-forker", { sourceRoutineId: "x".repeat(129) })));
});

test("un usuario puede anonimizar el autor de su rutina pero no el de otra", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const timestamp = Timestamp.now();
    await setDoc(doc(context.firestore(), "routines", "anon-routine"), {
      ...routineData("routine-anon", { visibility: "GLOBAL" }),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  });
  const owner = verifiedFirestore("routine-anon");
  const other = verifiedFirestore("routine-anon-other");

  await assertSucceeds(
    updateDoc(doc(owner, "routines", "anon-routine"), { createdByName: "Usuario eliminado" }),
  );
  await assertFails(
    updateDoc(doc(other, "routines", "anon-routine"), { createdByName: "Nombre manipulado" }),
  );
});

test("las preferencias de ingredientes solo las lee y escribe su dueño", async () => {
  const alice = verifiedFirestore("pref-alice");
  const bob = verifiedFirestore("pref-bob");

  await assertSucceeds(
    setDoc(
      doc(alice, "ingredientPrefs", "pref-alice"),
      { entries: { tomate: { stores: ["MERCADONA", "DIA"] } } },
      { merge: true },
    ),
  );
  await assertSucceeds(getDoc(doc(alice, "ingredientPrefs", "pref-alice")));
  await assertFails(getDoc(doc(bob, "ingredientPrefs", "pref-alice")));
  await assertFails(
    setDoc(doc(bob, "ingredientPrefs", "pref-alice"), { entries: { tomate: { stores: ["DIA"] } } }, { merge: true }),
  );
  await assertFails(
    setDoc(doc(alice, "ingredientPrefs", "pref-alice"), { entries: {}, hacked: true }, { merge: true }),
  );
});

test("borrar la cuenta: el dueño borra sus ingredientes, marcas y preferencias; otro no borra las ajenas", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const admin = context.firestore();
    await setDoc(doc(admin, "users", "pref-del", "ingredients", "salsa-casera"), personalIngredient());
    await setDoc(doc(admin, "users", "pref-del", "brands", "b1"), personalBrand({ ingredientId: "salsa-casera" }));
    await setDoc(doc(admin, "ingredientPrefs", "pref-del"), { entries: { tomate: { stores: ["DIA"] } } });
  });
  const owner = verifiedFirestore("pref-del");
  const other = verifiedFirestore("pref-del-otro");

  await assertFails(deleteDoc(doc(other, "ingredientPrefs", "pref-del")));
  await assertSucceeds(
    writeBatch(owner)
      .delete(doc(owner, "users", "pref-del", "ingredients", "salsa-casera"))
      .commit(),
  );
  await assertSucceeds(writeBatch(owner).delete(doc(owner, "users", "pref-del", "brands", "b1")).commit());
  await assertSucceeds(deleteDoc(doc(owner, "ingredientPrefs", "pref-del")));
});

test("borrar un ingrediente personal se acepta con y sin documento de preferencias previo", async () => {
  const deleteBatch = (db, uid, id) =>
    writeBatch(db)
      .delete(doc(db, "users", uid, "brands", "b1"))
      .delete(doc(db, "users", uid, "ingredients", id))
      .set(doc(db, "ingredientPrefs", uid), { entries: { [id]: deleteField() } }, { merge: true });

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const admin = context.firestore();
    for (const uid of ["pref-nodoc", "pref-condoc"]) {
      await setDoc(doc(admin, "users", uid, "ingredients", "salsa"), personalIngredient());
      await setDoc(doc(admin, "users", uid, "brands", "b1"), personalBrand({ ingredientId: "salsa" }));
    }
  });
  const sinPrefs = verifiedFirestore("pref-nodoc");
  await assertSucceeds(deleteBatch(sinPrefs, "pref-nodoc", "salsa").commit());

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "ingredientPrefs", "pref-condoc"), {
      entries: { salsa: { stores: ["DIA"] }, otro: { stores: ["MERCADONA"] } },
    });
  });
  const conPrefs = verifiedFirestore("pref-condoc");
  await assertSucceeds(deleteBatch(conPrefs, "pref-condoc", "salsa").commit());
  const entries = (await getDoc(doc(conPrefs, "ingredientPrefs", "pref-condoc"))).data().entries;
  assert.equal("salsa" in entries, false);
  assert.equal("otro" in entries, true);
});

test("deshacer el borrado de un ejercicio propio lo devuelve con su id y sube la versión del catálogo", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), "catalogMeta", "exercises"));
  });
  const user = verifiedFirestore("ex-undo");
  const metaRef = doc(user, "catalogMeta", "exercises");
  const exerciseRef = doc(user, "exercises", "press-banca-2");
  const withVersion = (write) =>
    runTransaction(user, async (tx) => {
      const snap = await tx.get(metaRef);
      write(tx);
      if (snap.exists()) tx.update(metaRef, metaData("ex-undo", snap.data().version + 1));
      else tx.set(metaRef, metaData("ex-undo", 1));
    });
  await assertSucceeds(withVersion((tx) => tx.set(exerciseRef, exerciseData("ex-undo"))));
  await assertSucceeds(withVersion((tx) => tx.delete(exerciseRef)));
  assert.equal((await getDoc(exerciseRef)).exists(), false);
  // Deshacer: mismo id, el dueño actual y sin source ni image.
  await assertSucceeds(withVersion((tx) => tx.set(exerciseRef, exerciseData("ex-undo"))));
  assert.equal((await getDoc(exerciseRef)).exists(), true);
  assert.equal((await getDoc(metaRef)).data().version, 3);
});

test("deshacer el borrado de un ingrediente personal devuelve sus marcas y sus tiendas", async () => {
  const user = verifiedFirestore("pi-undo");
  const ingredientRef = doc(user, "users", "pi-undo", "ingredients", "salsa-casera");
  const brandRef = doc(user, "users", "pi-undo", "brands", "marca-1");
  const prefsRef = doc(user, "ingredientPrefs", "pi-undo");
  await assertSucceeds(
    writeBatch(user)
      .set(ingredientRef, personalIngredient())
      .set(brandRef, personalBrand({ ingredientId: "salsa-casera", stores: ["DIA"] }))
      .set(prefsRef, { entries: { "salsa-casera": { stores: ["DIA", "MERCADONA"] } } }, { merge: true })
      .commit(),
  );
  await assertSucceeds(
    writeBatch(user)
      .delete(brandRef)
      .delete(ingredientRef)
      .set(prefsRef, { entries: { "salsa-casera": deleteField() } }, { merge: true })
      .commit(),
  );
  // Deshacer: un único lote con el ingrediente, cada marca con su id y la entrada de preferencias.
  await assertSucceeds(
    writeBatch(user)
      .set(ingredientRef, personalIngredient())
      .set(brandRef, personalBrand({ ingredientId: "salsa-casera", stores: ["DIA"] }))
      .set(prefsRef, { entries: { "salsa-casera": { stores: ["DIA", "MERCADONA"] } } }, { merge: true })
      .commit(),
  );
  assert.equal((await getDoc(ingredientRef)).exists(), true);
  assert.equal((await getDoc(brandRef)).exists(), true);
  assert.deepEqual((await getDoc(prefsRef)).data().entries["salsa-casera"].stores, ["DIA", "MERCADONA"]);
});

test("deshacer un ingrediente personal cuyo slug ya es común no escribe nada", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "ingredients", "salsa-ocupada"), importedIngredient());
  });
  const user = verifiedFirestore("pi-undo-taken");
  const ingredientRef = doc(user, "users", "pi-undo-taken", "ingredients", "salsa-ocupada");
  const brandRef = doc(user, "users", "pi-undo-taken", "brands", "marca-1");
  const prefsRef = doc(user, "ingredientPrefs", "pi-undo-taken");
  await assertFails(
    writeBatch(user)
      .set(ingredientRef, personalIngredient())
      .set(brandRef, personalBrand({ ingredientId: "salsa-ocupada" }))
      .set(prefsRef, { entries: { "salsa-ocupada": { stores: ["DIA"] } } }, { merge: true })
      .commit(),
  );
  assert.equal((await getDoc(ingredientRef)).exists(), false);
  assert.equal((await getDoc(brandRef)).exists(), false);
  assert.equal((await getDoc(prefsRef)).exists(), false);
});

function exerciseData(userId, overrides = {}) {
  return {
    name: "Press banca",
    nameLower: "press banca",
    type: "PESO_LIBRE",
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

function routineData(userId, overrides = {}) {
  return {
    ownerUid: userId,
    visibility: "PRIVATE",
    title: "Empuje",
    description: null,
    exercises: [{ name: "Press banca", type: "PESO_LIBRE", sets: [{ reps: 10, weight: 60 }] }],
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

test("una comida acepta cocinero si es participante y lo rechaza si no lo es", async () => {
  await seedSpace("meals-cook", "meals-cook-owner");
  const owner = verifiedFirestore("meals-cook-owner");

  // Cocinero que es participante: válido.
  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "meals-cook", "meals", "ok"),
      mealData("meals-cook-owner", {
        participantIds: ["meals-cook-owner"],
        participantNames: ["meals-cook-owner"],
        cookId: "meals-cook-owner",
        cookName: "meals-cook-owner",
      }),
    ),
  );
  // Cocinero que NO está entre los participantes: rechazado.
  await assertFails(
    setDoc(
      doc(owner, "spaces", "meals-cook", "meals", "bad"),
      mealData("meals-cook-owner", {
        participantIds: ["meals-cook-owner"],
        participantNames: ["meals-cook-owner"],
        cookId: "outsider",
        cookName: "Fantasma",
      }),
    ),
  );
});

test("todos los miembros crean, editan y eliminan notas del espacio", async () => {
  await seedSpace("notes-crud", "notes-owner", ["notes-member"]);
  const member = verifiedFirestore("notes-member");
  const outsider = verifiedFirestore("notes-outsider");
  const reference = doc(member, "spaces", "notes-crud", "notes", "welcome");

  await assertSucceeds(setDoc(reference, noteData("notes-member")));
  await assertSucceeds(
    updateDoc(reference, {
      title: "Normas de la casa",
      body: "Sacar la basura los martes",
      pinned: true,
      updatedBy: "notes-member",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(getDoc(doc(outsider, "spaces", "notes-crud", "notes", "welcome")));
  await assertFails(
    setDoc(doc(outsider, "spaces", "notes-crud", "notes", "sneaky"), noteData("notes-outsider")),
  );
  await assertSucceeds(writeBatch(member).delete(reference).commit());
});

test("deshacer el borrado de una nota la devuelve con su id y fijada", async () => {
  await seedSpace("notes-restore", "notes-restore-owner", ["notes-restore-member"]);
  const owner = verifiedFirestore("notes-restore-owner");
  const member = verifiedFirestore("notes-restore-member");
  await assertSucceeds(
    setDoc(doc(owner, "spaces", "notes-restore", "notes", "pinned"), noteData("notes-restore-owner", { pinned: true })),
  );
  const reference = doc(member, "spaces", "notes-restore", "notes", "pinned");
  await assertSucceeds(writeBatch(member).delete(reference).commit());
  await assertSucceeds(setDoc(reference, noteData("notes-restore-member", { pinned: true })));
  const restored = await getDoc(reference);
  assert.equal(restored.exists(), true);
  assert.equal(restored.data().pinned, true);
});

test("las notas validan la forma y rechazan título vacío, cuerpo enorme o campos ajenos", async () => {
  await seedSpace("notes-schema", "notes-schema-owner");
  const owner = verifiedFirestore("notes-schema-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "notes-schema", "notes", "ok"),
      noteData("notes-schema-owner", { body: "texto válido", pinned: true }),
    ),
  );
  await assertFails(
    setDoc(doc(owner, "spaces", "notes-schema", "notes", "empty-title"), noteData("notes-schema-owner", { title: "" })),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "notes-schema", "notes", "long-body"),
      noteData("notes-schema-owner", { body: "a".repeat(5001) }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "notes-schema", "notes", "bad-pinned"),
      noteData("notes-schema-owner", { pinned: "sí" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "notes-schema", "notes", "extra-field"),
      noteData("notes-schema-owner", { assigneeId: "notes-schema-owner" }),
    ),
  );
});

test("al editar una nota no se pueden alterar los campos de creación", async () => {
  await seedSpace("notes-immutable", "notes-imm-owner", ["notes-imm-member"]);
  const owner = verifiedFirestore("notes-imm-owner");
  const member = verifiedFirestore("notes-imm-member");
  const reference = doc(owner, "spaces", "notes-immutable", "notes", "n1");
  await assertSucceeds(setDoc(reference, noteData("notes-imm-owner")));

  await assertFails(
    updateDoc(doc(member, "spaces", "notes-immutable", "notes", "n1"), {
      createdBy: "notes-imm-member",
      updatedBy: "notes-imm-member",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("un usuario puede anonimizar su nombre en sus notas pero no en las ajenas", async () => {
  await seedSpace("notes-anon", "notes-anon-owner", ["notes-anon-member"]);
  const owner = verifiedFirestore("notes-anon-owner");
  const member = verifiedFirestore("notes-anon-member");
  const memberNote = doc(member, "spaces", "notes-anon", "notes", "member-note");
  await assertSucceeds(setDoc(memberNote, noteData("notes-anon-member")));

  await assertSucceeds(updateDoc(memberNote, { createdByName: "Usuario eliminado" }));
  await assertFails(
    updateDoc(doc(owner, "spaces", "notes-anon", "notes", "member-note"), {
      createdByName: "Nombre manipulado",
    }),
  );
});

test("todos los miembros apuntan, leen y quitan cosas de la despensa", async () => {
  await seedSpace("pantry-crud", "pantry-owner", ["pantry-member"]);
  const member = verifiedFirestore("pantry-member");
  const owner = verifiedFirestore("pantry-owner");
  const outsider = verifiedFirestore("pantry-outsider");
  const reference = doc(member, "spaces", "pantry-crud", "pantryItems", "tomat");

  await assertSucceeds(setDoc(reference, pantryData("pantry-member")));
  await assertSucceeds(getDoc(doc(owner, "spaces", "pantry-crud", "pantryItems", "tomat")));
  await assertSucceeds(getDocs(collection(member, "spaces", "pantry-crud", "pantryItems")));
  await assertFails(getDoc(doc(outsider, "spaces", "pantry-crud", "pantryItems", "tomat")));
  await assertFails(
    setDoc(doc(outsider, "spaces", "pantry-crud", "pantryItems", "sal"), pantryData("pantry-outsider", { name: "Sal" })),
  );
  await assertFails(deleteDoc(doc(outsider, "spaces", "pantry-crud", "pantryItems", "tomat")));
  await assertSucceeds(deleteDoc(reference));
  // Quitar lo que ya no está no falla (otro miembro lo quitó antes).
  await assertSucceeds(deleteDoc(reference));
});

test("una entrada de la despensa no se edita: se crea o se quita", async () => {
  await seedSpace("pantry-update", "pantry-upd-owner");
  const owner = verifiedFirestore("pantry-upd-owner");
  const reference = doc(owner, "spaces", "pantry-update", "pantryItems", "tomat");
  await assertSucceeds(setDoc(reference, pantryData("pantry-upd-owner")));

  await assertFails(updateDoc(reference, { category: "Frutas" }));
  await assertFails(setDoc(reference, pantryData("pantry-upd-owner", { name: "Tomates" })));
});

test("la despensa valida la forma, el autor, la hora y el id", async () => {
  await seedSpace("pantry-schema", "pantry-schema-owner", ["pantry-schema-member"]);
  const owner = verifiedFirestore("pantry-schema-owner");
  const at = (id) => doc(owner, "spaces", "pantry-schema", "pantryItems", id);

  await assertSucceeds(setDoc(at("ok"), pantryData("pantry-schema-owner", { category: null, shoppingItemId: "milk" })));
  await assertSucceeds(
    setDoc(at("sin-opcionales"), { name: "Sal", createdBy: "pantry-schema-owner", createdAt: serverTimestamp() }),
  );
  await assertFails(setDoc(at("vacio"), pantryData("pantry-schema-owner", { name: "" })));
  await assertFails(setDoc(at("largo"), pantryData("pantry-schema-owner", { name: "a".repeat(121) })));
  await assertFails(setDoc(at("categoria"), pantryData("pantry-schema-owner", { category: "a".repeat(61) })));
  await assertFails(setDoc(at("compra"), pantryData("pantry-schema-owner", { shoppingItemId: 7 })));
  await assertFails(setDoc(at("extra"), pantryData("pantry-schema-owner", { quantity: "1 kg" })));
  await assertFails(setDoc(at("sin-nombre"), { createdBy: "pantry-schema-owner", createdAt: serverTimestamp() }));
  await assertFails(setDoc(at("autor"), pantryData("pantry-schema-member")));
  await assertFails(setDoc(at("hora"), pantryData("pantry-schema-owner", { createdAt: Timestamp.now() })));
  await assertFails(setDoc(at("Tomate"), pantryData("pantry-schema-owner")));
  await assertFails(setDoc(at("con espacio"), pantryData("pantry-schema-owner")));
  await assertFails(setDoc(at("-tomat"), pantryData("pantry-schema-owner")));
  await assertFails(setDoc(at("a".repeat(121)), pantryData("pantry-schema-owner")));
});

test("marcar comprado y apuntarlo en la despensa van en la misma transacción, y desmarcar lo quita", async () => {
  await seedSpace("pantry-purchase", "pantry-buy-owner", ["pantry-buy-member"]);
  await seedShoppingItem("pantry-purchase", "tomatoes", "pantry-buy-owner", { name: "Tomates" });
  const member = verifiedFirestore("pantry-buy-member");
  const item = doc(member, "spaces", "pantry-purchase", "shoppingItems", "tomatoes");
  const pantry = doc(member, "spaces", "pantry-purchase", "pantryItems", "tomat");

  await assertSucceeds(
    runTransaction(member, async (transaction) => {
      await transaction.get(item);
      await transaction.get(pantry);
      transaction.update(item, {
        purchased: true,
        purchasedBy: "pantry-buy-member",
        purchasedByName: "pantry-buy-member",
        purchasedAt: serverTimestamp(),
        updatedBy: "pantry-buy-member",
        updatedAt: serverTimestamp(),
      });
      transaction.set(
        pantry,
        pantryData("pantry-buy-member", { category: "Verduras y hortalizas", shoppingItemId: "tomatoes" }),
      );
    }),
  );
  assert.equal((await getDoc(pantry)).data().shoppingItemId, "tomatoes");

  await assertSucceeds(
    runTransaction(member, async (transaction) => {
      await transaction.get(item);
      await transaction.get(pantry);
      transaction.update(item, {
        purchased: false,
        purchasedBy: null,
        purchasedByName: null,
        purchasedAt: null,
        updatedBy: "pantry-buy-member",
        updatedAt: serverTimestamp(),
      });
      transaction.delete(pantry);
    }),
  );
  assert.equal((await getDoc(pantry)).exists(), false);
});

test("en una misma transacción, purchasedAt del artículo y createdAt de la entrada quedan iguales", async () => {
  await seedSpace("pantry-same-time", "pantry-time-owner");
  await seedShoppingItem("pantry-same-time", "tomatoes", "pantry-time-owner", { name: "Tomates" });
  const owner = verifiedFirestore("pantry-time-owner");
  const item = doc(owner, "spaces", "pantry-same-time", "shoppingItems", "tomatoes");
  const pantry = doc(owner, "spaces", "pantry-same-time", "pantryItems", "tomat");

  await assertSucceeds(
    runTransaction(owner, async (transaction) => {
      await transaction.get(item);
      await transaction.get(pantry);
      transaction.update(item, {
        purchased: true,
        purchasedBy: "pantry-time-owner",
        purchasedByName: "pantry-time-owner",
        purchasedAt: serverTimestamp(),
        updatedBy: "pantry-time-owner",
        updatedAt: serverTimestamp(),
      });
      transaction.set(pantry, pantryData("pantry-time-owner", { shoppingItemId: "tomatoes" }));
    }),
  );
  // El cliente decide si desmarcar borra la entrada comparando ambas horas: tienen que ser idénticas.
  const purchasedAt = (await getDoc(item)).data().purchasedAt;
  const createdAt = (await getDoc(pantry)).data().createdAt;
  assert.ok(purchasedAt.isEqual(createdAt));
});

function noteData(userId, overrides = {}) {
  return {
    title: "Nota",
    body: null,
    pinned: false,
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function pantryData(userId, overrides = {}) {
  return {
    name: "Tomate",
    category: "Verduras y hortalizas",
    createdBy: userId,
    createdAt: serverTimestamp(),
    shoppingItemId: null,
    ...overrides,
  };
}

test("todos los miembros crean, editan y eliminan actividades de deporte", async () => {
  await seedSpace("act-crud", "act-owner", ["act-member"]);
  const member = verifiedFirestore("act-member");
  const outsider = verifiedFirestore("act-outsider");
  const reference = doc(member, "spaces", "act-crud", "activities", "padel");

  await assertSucceeds(setDoc(reference, activityData("act-member")));
  await assertSucceeds(
    updateDoc(reference, {
      type: "GIMNASIO",
      name: "Pierna",
      done: true,
      participantIds: ["act-member"],
      participantNames: ["act-member"],
      updatedBy: "act-member",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(getDoc(doc(outsider, "spaces", "act-crud", "activities", "padel")));
  await assertFails(
    setDoc(doc(outsider, "spaces", "act-crud", "activities", "sneaky"), activityData("act-outsider")),
  );
  await assertSucceeds(writeBatch(member).delete(reference).commit());
});

test("las actividades validan el tipo y rechazan campos ajenos al contrato", async () => {
  await seedSpace("act-schema", "act-schema-owner");
  const owner = verifiedFirestore("act-schema-owner");

  await assertSucceeds(
    setDoc(doc(owner, "spaces", "act-schema", "activities", "ok"), activityData("act-schema-owner", { type: "CORRER" })),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "act-schema", "activities", "bad-type"),
      activityData("act-schema-owner", { type: "NATACION" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "act-schema", "activities", "bad-done"),
      activityData("act-schema-owner", { done: "sí" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "act-schema", "activities", "extra-field"),
      activityData("act-schema-owner", { recipeId: "x" }),
    ),
  );
});

test("una actividad de gimnasio guarda rutina y sesión, y las admite al editar", async () => {
  await seedSpace("act-gym", "act-gym-owner");
  const owner = verifiedFirestore("act-gym-owner");

  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "act-gym", "activities", "gym-ok"),
      activityData("act-gym-owner", {
        type: "GIMNASIO",
        routineId: "rutina-1",
        session: [{ name: "Sentadilla", type: "PESO_LIBRE", sets: [{ reps: 8, weight: 80 }] }],
      }),
    ),
  );

  const editable = doc(owner, "spaces", "act-gym", "activities", "gym-edit");
  await assertSucceeds(setDoc(editable, activityData("act-gym-owner", { type: "GIMNASIO" })));
  await assertSucceeds(
    updateDoc(editable, {
      routineId: "rutina-2",
      session: [{ name: "Press", type: "MAQUINA", sets: [] }],
      updatedBy: "act-gym-owner",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("una actividad rechaza una sesión enorme o un routineId no textual", async () => {
  await seedSpace("act-session", "act-session-owner");
  const owner = verifiedFirestore("act-session-owner");

  await assertFails(
    setDoc(
      doc(owner, "spaces", "act-session", "activities", "bad-routine"),
      activityData("act-session-owner", { type: "GIMNASIO", routineId: 123 }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "act-session", "activities", "huge-session"),
      activityData("act-session-owner", {
        type: "GIMNASIO",
        session: Array.from({ length: 31 }, (_, index) => ({ name: `ej-${index}`, type: "OTROS" })),
      }),
    ),
  );
});

test("al añadir una actividad se puede crear su evento de calendario enlazado (eventId + evento todo el día)", async () => {
  await seedSpace("act-ev", "act-ev-owner");
  const owner = verifiedFirestore("act-ev-owner");

  // Actividad con el id del evento enlazado.
  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "act-ev", "activities", "run"),
      activityData("act-ev-owner", { type: "CORRER", eventId: "ev-run" }),
    ),
  );
  // Evento de calendario (todo el día) que la refleja, con la forma que escribe el repo.
  await assertSucceeds(
    setDoc(
      doc(owner, "spaces", "act-ev", "events", "ev-run"),
      eventData("act-ev-owner", {
        title: "Correr",
        allDay: true,
        startDate: "2026-07-20",
        endDateExclusive: "2026-07-21",
      }),
    ),
  );
  await assertFails(
    setDoc(
      doc(owner, "spaces", "act-ev", "activities", "bad-event"),
      activityData("act-ev-owner", { eventId: 123 }),
    ),
  );

  // El evento se puede enlazar al editar una actividad previa (affectedKeys incluye eventId).
  const ref = doc(owner, "spaces", "act-ev", "activities", "link-later");
  await assertSucceeds(setDoc(ref, activityData("act-ev-owner")));
  await assertSucceeds(
    updateDoc(ref, { eventId: "ev-later", updatedBy: "act-ev-owner", updatedAt: serverTimestamp() }),
  );
});

test("al editar una actividad el evento enlazado se actualiza parcialmente y conserva lo que el usuario cambió en el calendario", async () => {
  await seedSpace("act-ev-partial", "act-evp-owner", ["act-evp-member"]);
  const owner = verifiedFirestore("act-evp-owner");
  const member = verifiedFirestore("act-evp-member");
  const ref = doc(owner, "spaces", "act-ev-partial", "events", "ev-run");

  await assertSucceeds(
    setDoc(
      ref,
      eventData("act-evp-owner", {
        title: "Correr", description: "con el perro", color: "GREEN", allDay: true,
        startDate: "2026-07-20", endDateExclusive: "2026-07-21",
        participantIds: ["act-evp-owner"], participantNames: ["act-evp-owner"],
      }),
    ),
  );
  // Otro miembro edita la actividad: solo cambian título y participantes, sin tocar descripción ni color.
  await assertSucceeds(
    updateDoc(doc(member, "spaces", "act-ev-partial", "events", "ev-run"), {
      title: "Correr 5k",
      participantIds: ["act-evp-owner", "act-evp-member"],
      participantNames: ["act-evp-owner", "act-evp-member"],
      updatedBy: "act-evp-member",
      updatedAt: serverTimestamp(),
    }),
  );
  const data = (await getDoc(ref)).data();
  assert.equal(data.description, "con el perro");
  assert.equal(data.color, "GREEN");
  assert.equal(data.createdBy, "act-evp-owner");
});

test("las actividades exigen que participantNames tenga el mismo tamaño que participantIds", async () => {
  await seedSpace("act-parts", "act-parts-owner");
  const owner = verifiedFirestore("act-parts-owner");
  await assertFails(
    setDoc(
      doc(owner, "spaces", "act-parts", "activities", "mismatch"),
      activityData("act-parts-owner", { participantIds: ["act-parts-owner"], participantNames: [] }),
    ),
  );
});

test("al editar una actividad no se pueden alterar los campos de creación", async () => {
  await seedSpace("act-immutable", "act-imm-owner", ["act-imm-member"]);
  const owner = verifiedFirestore("act-imm-owner");
  const member = verifiedFirestore("act-imm-member");
  const reference = doc(owner, "spaces", "act-immutable", "activities", "a1");
  await assertSucceeds(setDoc(reference, activityData("act-imm-owner")));

  await assertFails(
    updateDoc(doc(member, "spaces", "act-immutable", "activities", "a1"), {
      createdBy: "act-imm-member",
      updatedBy: "act-imm-member",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("un usuario puede anonimizar su nombre en sus actividades pero no en las ajenas", async () => {
  await seedSpace("act-anon", "act-anon-owner", ["act-anon-member"]);
  const owner = verifiedFirestore("act-anon-owner");
  const member = verifiedFirestore("act-anon-member");
  const memberActivity = doc(member, "spaces", "act-anon", "activities", "member-act");
  await assertSucceeds(setDoc(memberActivity, activityData("act-anon-member")));

  await assertSucceeds(updateDoc(memberActivity, { createdByName: "Usuario eliminado" }));
  await assertFails(
    updateDoc(doc(owner, "spaces", "act-anon", "activities", "member-act"), {
      createdByName: "Nombre manipulado",
    }),
  );
});

test("al borrar su cuenta, un participante anonimiza su nombre en la actividad de otro", async () => {
  await seedSpace("act-anon-part", "act-part-owner", ["act-part-member"]);
  const owner = verifiedFirestore("act-part-owner");
  const member = verifiedFirestore("act-part-member");
  await assertSucceeds(setDoc(
    doc(owner, "spaces", "act-anon-part", "activities", "owner-act"),
    activityData("act-part-owner", {
      participantIds: ["act-part-owner", "act-part-member"],
      participantNames: ["Ana", "Luis"],
    }),
  ));
  const reference = doc(member, "spaces", "act-anon-part", "activities", "owner-act");

  await assertSucceeds(updateDoc(reference, { participantNames: ["Ana", "Usuario eliminado"] }));
  await assertFails(updateDoc(reference, { createdByName: "Usuario eliminado" }));
});

function activityData(userId, overrides = {}) {
  return {
    date: "2026-07-20",
    type: "PADEL",
    name: "Pádel",
    participantIds: [],
    participantNames: [],
    done: false,
    createdBy: userId,
    createdByName: userId,
    createdAt: serverTimestamp(),
    updatedBy: userId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

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

function doneTask(userId, overrides = {}) {
  return taskData(userId, null, {
    status: "DONE",
    completedBy: userId,
    completedByName: userId,
    completedAt: serverTimestamp(),
    ...overrides,
  });
}

function boughtItem(userId, overrides = {}) {
  return shoppingItem(userId, {
    purchased: true,
    purchasedBy: userId,
    purchasedByName: userId,
    purchasedAt: serverTimestamp(),
    ...overrides,
  });
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
