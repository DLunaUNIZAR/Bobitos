import { readFile } from "node:fs/promises";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";

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
    await Promise.all([
      setDoc(doc(firestore, "users", "demo-owner"), {
        displayName: "David Demo",
        email: "david@bobitos.invalid",
      }),
      setDoc(doc(firestore, "spaces", "demo-home"), {
        name: "Casa de prueba",
        ownerId: "demo-owner",
      }),
      setDoc(doc(firestore, "memberships", "demo-home_demo-owner"), {
        role: "owner",
        spaceId: "demo-home",
        userId: "demo-owner",
      }),
      setDoc(
        doc(firestore, "spaces", "demo-home", "shoppingItems", "demo-milk"),
        {
          name: "Leche",
          purchased: false,
        },
      ),
    ]);
  });

  console.log("Datos de prueba cargados en demo-bobitos.");
} finally {
  await testEnvironment.cleanup();
}
