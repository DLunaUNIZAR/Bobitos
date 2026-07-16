import { readFile } from "node:fs/promises";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, doc, setDoc } from "firebase/firestore";

const projectId = "demo-bobitos";
const rules = await readFile("firestore.rules", "utf8");
const testEnvironment = await initializeTestEnvironment({
  projectId,
  firestore: { rules },
});

try {
  await testEnvironment.clearFirestore();
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    const timestamp = Timestamp.now();
    await Promise.all([
      setDoc(doc(firestore, "users", "demo-owner"), {
        displayName: "David Demo",
        email: "david@bobitos.invalid",
      }),
      setDoc(doc(firestore, "spaces", "demo-home"), {
        name: "Casa de prueba",
        ownerId: "demo-owner",
        createdBy: "demo-owner",
        memberCount: 1,
        lastMembershipChangeUserId: "demo-owner",
        createdAt: timestamp,
        updatedAt: timestamp,
      }),
      setDoc(doc(firestore, "memberships", "demo-home_demo-owner"), {
        spaceId: "demo-home",
        userId: "demo-owner",
        displayName: "David Demo",
        role: "OWNER",
        status: "ACTIVE",
        joinedAt: timestamp,
      }),
      setDoc(
        doc(firestore, "spaces", "demo-home", "shoppingItems", "demo-milk"),
        {
          name: "Leche",
          quantity: "2 litros",
          notes: "Sin lactosa",
          purchased: false,
          createdBy: "demo-owner",
          createdByName: "David Demo",
          createdAt: timestamp,
          updatedBy: "demo-owner",
          updatedAt: timestamp,
          purchasedBy: null,
          purchasedByName: null,
          purchasedAt: null,
        },
      ),
    ]);
  });

  console.log("Datos de prueba cargados en demo-bobitos.");
} finally {
  await testEnvironment.cleanup();
}
