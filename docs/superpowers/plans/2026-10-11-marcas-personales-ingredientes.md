# Marcas personales y pantalla Ingredientes (plan 2 de 3) — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (9 tareas), así que lleva los pasos 1 a 6, incluido `simplify`. Cada tarea la hace un subagente `sonnet`, en serie. La revisión final la hace `opus` (si falla, `fable`). **Hay re-revisión acotada si un arreglo toca reglas o el aislamiento de datos privados.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** pasar la app al modelo nuevo de la spec:
- el catálogo común de ingredientes, solo del admin y con caché por versión;
- ingredientes, marcas (con favorita y tiendas) y «Dónde lo compro» privados de cada usuario;
- la pantalla Ingredientes rehecha (lista, ficha y escaneo);
- el borrado de cuenta que elimina todo lo personal;
- la clave tolerante (`ingredientKey`) en Kotlin, con una tabla de casos compartida con Node.

**Arquitectura:**
- **Catálogo común:** se carga con `VersionedCatalogLoader` (clave `"ingredients"`), como Ejercicios, y el admin escribe en una transacción que sube `catalogMeta/ingredients`.
- **Lo personal** vive en `users/{uid}/ingredients/{slug}` y `users/{uid}/brands/{id}`, cada uno con su escucha limitada, y en `ingredientPrefs/{uid}` con la forma nueva `slug → {stores}`.
- **Los repositorios** exponen funciones puras de validación, parseo y orden, que son lo que se prueba en la JVM. Los ViewModels se prueban con dobles a mano.
- **Lo que solo usaba las preferencias antiguas** (súper y marca) se retira o se adapta en este plan (ver Rulings).

**Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Firestore (con `Source.CACHE`/`SERVER` y transacciones), reglas de Firestore, Node (`node:test`) y Firebase Emulator Suite.

**Spec:** `docs/superpowers/specs/2026-10-11-catalogo-ingredientes-design.md`. Este plan cubre:
- las secciones 1, 2, 4 y 6;
- de la sección 5, la retirada de las preferencias antiguas;
- la clave tolerante de la sección 1.

Lo que hace el plan 3: el selector de marca y tienda, «Añadir» en la Compra, el diálogo de revisión con selector y el botón «Añadir a la compra» de la ficha.

## Rulings de planificación (desviaciones conscientes de la spec)

1. **La retirada de las preferencias antiguas de la Compra se adelanta del plan 3 a este.** Es decir, la fila «Sugerido: … [Aplicar]», «Guardar como preferencia» y el ajuste automático de `ShoppingViewModel` (`:148-162`). El motivo es que este plan cambia la forma de `IngredientPref` y esos usos dejarían de compilar. Hasta el plan 3:
   - el diálogo de revisión de recetas y comidas usa como súper la **primera tienda** del ingrediente (`prefs[slug(nombre)]`) y deja la marca vacía;
   - «Añadir» en la Compra no aplica nada por defecto.

   El estado final de la spec no cambia, y nada se despliega antes de terminar el plan 3.
2. **«¿De qué ingrediente es?»** abre el buscador **vacío** y, debajo, una lista de **sugerencias** calculadas a partir del nombre del producto de Open Food Facts. La spec decía «buscador ya relleno con el nombre del producto». El motivo: con el nombre entero («Chocolate negro 85 % cacao Lindt»), la búsqueda por palabras no encuentra nada, mientras que las sugerencias por clave sí («Chocolate negro», «Cacao»).

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **EMU** (reglas): `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:emulators`
  - **NODE:** `npm run test:scripts`
  - **IMP:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:catalog-import`
- **Prohibido** ejecutar nada contra `bobitos-dev` (`--project dev`), `npm run catalog:fetch`, desplegar reglas o distribuir betas. Las acciones externas son del usuario.
- **Tests:** JVM puros (JUnit 4 y `kotlinx-coroutines-test`) con dobles a mano; no hay mockk ni Robolectric.
  - Firestore no se simula: la lógica se saca a funciones `internal` puras y eso es lo que se prueba.
  - **Orden TDD obligatorio en cada paso:** el test primero, verlo en rojo y después el código.
- **detekt:** extraer código antes que tocar el baseline.
- **ktlint:** sin imports sin usar.
- **Textos de la interfaz en español de España**, en `RES`. Cada texto nuevo se añade a `RES`, sin cadenas sueltas en el código.
- **Patrones que se copian:**
  - escritura versionada: `APP/data/repository/FirestoreExerciseRepository.kt` (`writeWithVersion` y `commitVersioned`);
  - fuente versionada: `APP/data/repository/FirestoreExerciseCatalogSource.kt`;
  - carga, error y reintento del catálogo en el ViewModel: `APP/feature/exercises/ExercisesViewModel.kt` (`startCatalog`, `retryCatalog` y `catalogError`);
  - editores: `runAction` con `withEditorSaveTimeout` y `EditorSaveStatus`, que ya usan los ViewModels de ingredientes;
  - repositorios: `runOperation`, `requireWritable`, `reportWriteFailure`, `realtimeMetrics.listenerStarted/snapshotReceived/listenerStopped` y el mapeo de `FirebaseFirestoreException.Code`, como en `FirestoreIngredientRepository.kt`.
- **Admin:** `RecipeAdmins.uids` en la app e `isRecipeAdmin()` en las reglas, sin cambios.
- **Enum de tiendas:** `Supermarket` (`APP/core/model/ShoppingItem.kt`). En las listas de tiendas de marcas y preferencias **nunca** va `INDIFERENTE`, sin repetidos y con 6 como máximo.
- **Límites:**
  - nombre de ingrediente: 120;
  - categoría: 60;
  - unidad: 24;
  - nombre de marca: **60** (el mismo límite que en la Compra);
  - código de barras: 32;
  - marcas: escucha con `limit(1000)`;
  - ingredientes personales: escucha con `limit(500)`;
  - catálogo común: `limit(1000)`.

## Review Focus

1. **Aislamiento:**
   - un usuario no puede leer, listar ni escribir `users/{otro}/…`;
   - un ingrediente personal no puede tener el id (slug) de uno común;
   - una marca no cambia de `ingredientId` al editarla;
   - el catálogo común solo lo escribe el admin;
   - `source` no lo escribe ningún cliente.
2. **Caché por versión:**
   - una edición del admin sube `catalogMeta/ingredients` en la misma transacción y se ve en la propia app sin reiniciar;
   - sin red, la lista sale de la caché;
   - un error del catálogo muestra «Reintentar», como Ejercicios.
3. **Consumidores de las preferencias:**
   - la Compra, Comidas y Recetas compilan y funcionan con la forma nueva;
   - una entrada antigua (`supermarket`/`brand`) se ignora sin fallar;
   - el diálogo de revisión pone la primera tienda del ingrediente.
4. **Nombres:**
   - crear un personal con un nombre que coincide por clave con uno común o personal no crea nada y lleva al que ya existe;
   - si aparece un común con el mismo id que un personal, gana el común y las marcas siguen viéndose;
   - renombrar un personal no cambia su id.
5. **Borrados:**
   - borrar un personal borra sus marcas y su entrada de «Dónde lo compro»;
   - borrar la cuenta borra `users/{uid}/ingredients`, `users/{uid}/brands` e `ingredientPrefs/{uid}`;
   - si falla a medias, se puede repetir.
6. **Escaneo:**
   - un código que ya está en tus marcas abre esa marca, incluso si es de otro ingrediente;
   - si Open Food Facts falla, se sigue con el editor vacío y el código ya puesto;
   - el flujo sobrevive a una rotación o la cancela sin crear nada a medias.

---

### Task 1: Clave tolerante (`ingredientKey`) en Kotlin y en Node, con plurales «-z/-ces»

**Ficheros:**
- Crear:
  - `tests/fixtures/ingredient-keys.json`: la tabla de casos compartida;
  - `UT/core/model/IngredientKeyTest.kt`.
- Modificar:
  - `scripts/catalog/ingredients.mjs`: `ingredientKey` añade «z final → c» y se corrige el comentario de encima, que hoy habla de una URL de wger y no tiene nada que ver;
  - `tests/catalog-ingredients.test.mjs`: un test que recorre la tabla;
  - `APP/core/model/CatalogIngredient.kt`: añadir `ingredientKey(name)` junto a `slug(name)`.

**Interfaces:**
- **Produce:**
  - Kotlin: `fun ingredientKey(name: String): String` (en `core/model`, público como `slug`);
  - Node: `ingredientKey(name)`, con el mismo resultado.
- **Algoritmo** (idéntico en los dos lenguajes):
  1. `slug(name)` y separar por `-`;
  2. quitar las palabras vacías `a, al, con, de, del, e, el, en, la, las, lo, los, o, para, por, u, un, una, y`;
  3. si la palabra tiene más de 3 letras, termina en `s` y no en `ss`, quitar la `s`;
  4. si tiene más de 3 letras y termina en `e`, quitar la `e`;
  5. si termina en `z`, cambiar la `z` por `c`;
  6. ordenar y unir con un espacio.

- [ ] **Paso 1: tabla y tests en rojo.** Crear `tests/fixtures/ingredient-keys.json`:

```json
[
  ["Limón", "limon"], ["Limones", "limon"], ["Tomate", "tomat"], ["tomates", "tomat"],
  ["Pan", "pan"], ["Panes", "pan"], ["Nuez", "nuec"], ["Nueces", "nuec"], ["Pez espada", "espada pec"],
  ["Maíz", "maic"], ["Aceite de oliva", "aceit oliva"], ["ACEITE DE OLIVA", "aceit oliva"],
  ["Pimiento rojo", "pimiento rojo"], ["Pimientos rojos", "pimiento rojo"], ["Leche de coco", "coco lech"],
  ["Huevos", "huevo"], ["Té verde", "te verd"], ["Mousse", "mouss"], ["Mousses", "mouss"],
  ["Leche 0 %", "0 lech"], ["Pan de molde", "mold pan"], ["  Chocolate   negro ", "chocolat negro"]
]
```

  En `tests/catalog-ingredients.test.mjs`, añadir:

```js
test("ingredientKey cumple la tabla compartida con Kotlin", async () => {
  const cases = JSON.parse(await readFile(new URL("./fixtures/ingredient-keys.json", import.meta.url), "utf8"));
  for (const [name, key] of cases) assert.equal(ingredientKey(name), key, name);
});
```

  `UT/core/model/IngredientKeyTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.core.model

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IngredientKeyTest {
    // La misma tabla que usa tests/catalog-ingredients.test.mjs: las dos versiones tienen que dar lo mismo.
    private val cases = File("../tests/fixtures/ingredient-keys.json").also {
        assertTrue("falta la tabla compartida en ${it.absolutePath}", it.exists())
    }.readText().let(::JSONArray)

    @Test
    fun `ingredientKey matches the table shared with Node`() {
        for (i in 0 until cases.length()) {
            val case = cases.getJSONArray(i)
            assertEquals(case.getString(0), case.getString(1), ingredientKey(case.getString(0)))
        }
    }

    @Test
    fun `different ingredients keep different keys`() {
        assertTrue(ingredientKey("Leche entera") != ingredientKey("Leche semidesnatada"))
        assertTrue(ingredientKey("Nuez moscada") != ingredientKey("Nueces"))
    }
}
```

- [ ] **Paso 2:** ejecutar NODE y GRADLE. Esperado: FALLAN. En NODE fallan «Nuez», «Maíz» y «Pez espada»; en GRADLE, `ingredientKey` no existe.
- [ ] **Paso 3: implementar.**
  - **Node:** en `ingredientKey`, añadir `.map((w) => (w.endsWith("z") ? `${w.slice(0, -1)}c` : w))` después del recorte de la «e», y sustituir el comentario equivocado por uno que explique la clave y que está duplicada en Kotlin (`core/model/CatalogIngredient.kt`), vigilada por `tests/fixtures/ingredient-keys.json`.
  - **Kotlin:** `ingredientKey` reutiliza `slug`, con una lista `private val INGREDIENT_STOP_WORDS = setOf(...)` y un comentario que remite a la versión de Node y a la tabla.
- [ ] **Paso 4:** ejecutar `npm run catalog:ingredients`.
  - Esperado: sin problemas. Si la regla nueva crea un casi duplicado en `data/catalog/ingredients.json`, **para y dilo en el informe**; no cambies los datos.
  - Después, NODE y GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): clave tolerante de ingredientes en Kotlin y Node con tabla compartida`.

### Task 2: Reglas de Firestore y sus tests

**Ficheros:**
- `firestore.rules`
- `tests/firebase-emulators.test.mjs`

**Interfaces:**
- **Produce** (los contratos que usan las tareas 3 a 5):
  - **`ingredients/{id}`:**
    - `get` y `list` para verificados;
    - `create`, `update` y `delete` solo con `isRecipeAdmin()`.
    - **Forma:**
      - `hasAll(['name','nameLower','createdAt','updatedAt','updatedBy'])`;
      - `hasOnly` añade `category`, `defaultUnit` y `source`;
      - `name` es string de 1 a 120; `nameLower` es string de 120 como máximo;
      - `category` es null o string de 60 como máximo; `defaultUnit` es null o string de 24 como máximo;
      - `source`, si está, cumple `validIngredientSource`: es un map con `hasAll` y `hasOnly` de `['provider','importedAt']`, `provider == 'bobitos'` e `importedAt is timestamp`.
    - **Al crear:**
      - sin `source`;
      - `createdAt == request.time`, `updatedAt == request.time` y `updatedBy == request.auth.uid`.
    - **Al editar:**
      - `affectedKeys().hasOnly(['name','nameLower','category','defaultUnit','updatedBy','updatedAt'])`;
      - `updatedAt == request.time` y `updatedBy == request.auth.uid`.
  - **`ingredients/{id}/brands/**`:** sin `match` propio, así que lo cierra el catch-all.
  - **`catalogMeta/{catalogId}`:**
    - `get` si `catalogId in ['exercises','ingredients']` y es verificado;
    - `create` y `update` como hoy para `exercises`, y para `ingredients`, lo mismo **más** `isRecipeAdmin()`.
  - **`users/{userId}/ingredients/{ingredientId}`:**
    - Solo el dueño verificado (`userId == request.auth.uid`): `get`, `list`, `create`, `update` y `delete`.
    - **Forma:**
      - `hasAll(['name','nameLower','createdAt','updatedAt'])`;
      - `hasOnly` añade `category` y `defaultUnit`;
      - los mismos límites que el común.
    - **Al crear:**
      - `ingredientId.matches('^[a-z0-9]+(-[a-z0-9]+)*$')` y `ingredientId.size() <= 120`;
      - `createdAt == request.time` y `updatedAt == request.time`;
      - `!exists(/databases/$(database)/documents/ingredients/$(ingredientId))`.
    - **Al editar:** `affectedKeys().hasOnly(['name','nameLower','category','defaultUnit','updatedAt'])` y `updatedAt == request.time`.
  - **`users/{userId}/brands/{brandId}`:**
    - Solo el dueño verificado, para todo.
    - **Forma:**
      - `hasAll(['ingredientId','name','favorite','stores','createdAt','updatedAt'])`;
      - `hasOnly` añade `barcode` y los 6 nutrientes;
      - `ingredientId` cumple la misma regex de slug y tiene 120 caracteres como máximo;
      - `name` es string de 1 a 60;
      - `barcode` es null o string de 32 como máximo;
      - `favorite is bool`;
      - `stores is list`, `stores.size() <= 6`, `stores.hasOnly(['DIA','EROSKI','MERCADONA','CARREFOUR','ALCAMPO','OTROS'])` y `stores.toSet().size() == stores.size()`;
      - los nutrientes con `optionalNumberField`.
    - **Al crear:** `createdAt == request.time` y `updatedAt == request.time`.
    - **Al editar:**
      - `affectedKeys().hasOnly(['name','barcode','energyKcal','fat','carbohydrates','sugars','protein','salt','favorite','stores','updatedAt'])`, de modo que **`ingredientId` no se puede cambiar**;
      - `updatedAt == request.time`.
  - **`ingredientPrefs/{userId}`:** las reglas no cambian; solo se actualiza el comentario a `slug → {stores: [...]}`.

- [ ] **Paso 1: tests en rojo.**
  - **Quitar** los tests que describen el modelo antiguo:
    - «cualquier usuario crea un ingrediente del catálogo…»;
    - «solo el autor o un admin editan/borran una ficha del catálogo»;
    - «una ficha del catálogo congela su ownerUid…»;
    - «cualquiera añade una marca a un ingrediente…»;
    - «solo el autor o un admin editan/borran una marca»;
    - «una marca rechaza campos ajenos…».
  - **Quitar** los helpers `ingredientData` y `brandData`, si se quedan sin uso.
  - **Añadir**, en el estilo del fichero (`verifiedFirestore`, `unverifiedFirestore`, `assertSucceeds` y `assertFails`), con datos que usen `serverTimestamp()`:
    - `ingredientes comunes: todos los verificados los leen; solo el admin los crea, edita y borra`
    - `un ingrediente común rechaza source desde el cliente y campos ajenos; el admin edita uno importado sin tocar su source`: sembrar con las reglas desactivadas un documento con la forma del importador, `toFirestoreDoc` no vale porque es de ejercicios; usar `{name, nameLower, category, defaultUnit, source: {provider: "bobitos", importedAt: Timestamp.now()}, createdAt, updatedAt, updatedBy}`
    - `las marcas bajo ingredients están cerradas para todos`
    - `catalogMeta/ingredients: un verificado la lee; solo el admin la crea y la sube de uno en uno`
    - `una transacción del admin crea un ingrediente común y sube catalogMeta/ingredients`
    - `ingredientes personales: solo su dueño los lee, lista y escribe`
    - `un ingrediente personal no puede tener el id de uno común ni un id que no sea slug`
    - `un ingrediente personal valida límites y no cambia createdAt`
    - `marcas personales: solo su dueño las lee, lista y escribe`
    - `una marca personal valida tiendas (enum, sin INDIFERENTE, sin repetidos, como mucho 6), favorita y nutrición`
    - `una marca personal no cambia de ingrediente al editarla`
  - **Cambiar** el test `las preferencias de ingredientes solo las lee y escribe su dueño` para que escriba `{entries: {tomate: {stores: ["MERCADONA", "DIA"]}}}`.
  - **Revisar** que los tests de `catalogMeta` de ejercicios siguen pasando.
- [ ] **Paso 2:** ejecutar EMU. Esperado: FALLAN los tests nuevos.
- [ ] **Paso 3:** implementar las reglas según la interfaz.
  - Funciones nuevas: `validIngredientShape`, `validIngredientSource`, `validNewIngredient`, `validIngredientUpdate`, `validPersonalIngredientShape`, `validPersonalBrandShape` y `validStoreList(stores)`.
  - Quitar las antiguas que ya no se usen.
  - Comentarios en español en cada bloque.
- [ ] **Paso 4:** ejecutar EMU, NODE e IMP. Esperado: verde. NODE incluye el test que vigila `CATALOG_ADMIN_UID` contra las reglas.
- [ ] **Paso 5:** commit `feat(reglas): catálogo común de ingredientes solo del admin y marcas e ingredientes personales`.

### Task 3: Catálogo común versionado en la app

**Ficheros:**
- Crear:
  - `APP/data/repository/CatalogIngredientFirestore.kt`, con funciones puras;
  - `APP/data/repository/FirestoreIngredientCatalogSource.kt`;
  - `UT/data/repository/CatalogIngredientFirestoreTest.kt`.
- Modificar:
  - `APP/core/model/CatalogIngredient.kt`;
  - `APP/data/repository/IngredientRepository.kt`;
  - `APP/data/repository/FirestoreIngredientRepository.kt`;
  - y todo lo que deje de compilar:
    - `APP/feature/ingredients/*`
    - `APP/feature/shopping/ShoppingViewModel.kt`
    - los tests y sus dobles en `UT/feature/ingredients/*` y `UT/feature/shopping/ShoppingViewModelTest.kt`.

**Interfaces:**
- **Produce:**
  - `data class CatalogIngredient(val id: String, val name: String, val category: String? = null, val defaultUnit: String? = null, val personal: Boolean = false)`. Desaparecen `ownerUid`, `createdBy`, `createdByName`, `createdAt`, `updatedBy` y `updatedAt`.
  - En `CatalogIngredientFirestore.kt`:
    - `internal fun parseCatalogIngredient(id: String, data: Map<String, Any?>, personal: Boolean): CatalogIngredient?`: `null` si `name` no es un string no vacío; `category` y `defaultUnit`, si son blancos o no son string, quedan en `null`;
    - `internal fun List<CatalogIngredient>.sortedForIngredientCatalog(): List<CatalogIngredient>`: `Collator` español en `PRIMARY` por nombre y, después, por `id`;
    - `internal data class IngredientInput(val name: String, val category: String?, val defaultUnit: String?)`;
    - `internal fun validateIngredientInput(name: String, category: String?, defaultUnit: String?): IngredientInput`: recorta los textos, convierte los blancos en `null` y lanza `IngredientRepositoryException` con `NameRequired`, `NameTooLong`, `CategoryTooLong` o `UnitTooLong`; el `slug` vacío también es `NameRequired`;
    - `internal fun IngredientInput.toFirestoreFields(): Map<String, Any?>` → `{name, nameLower: name.lowercase(), category, defaultUnit}`.
  - `class FirestoreIngredientCatalogSource(firestore: FirebaseFirestore) : CatalogSource<CatalogIngredient>`, igual que el de ejercicios:
    - colección `ingredients`, `limit(1000)`;
    - meta `catalogMeta/ingredients`, con un tiempo máximo de 10 s y el mismo mapeo de `Missing` y `Unreachable`;
    - `parseCatalogIngredient(…, personal = false)`.
  - `IngredientRepository`:
    - **se mantienen las firmas** `catalog(): Flow<List<CatalogIngredient>>`, `isCurrentUserCatalogAdmin()`, `currentUserId()`, `createIngredient`, `updateIngredient` y `deleteIngredient`;
    - **desaparece** `ingredientById`;
    - las escrituras son **solo para el admin**, en una transacción que sube `catalogMeta/ingredients`.
  - `IngredientFailure` añade `AlreadyExists`.
  - `FirestoreIngredientRepository` sigue el modelo de `FirestoreExerciseRepository`:
    - recibe por constructor `CatalogSyncStore`;
    - `loader = VersionedCatalogLoader(FirestoreIngredientCatalogSource(firestore), syncStore, key = "ingredients", now = System::currentTimeMillis)`;
    - `catalog() = loader.catalog(localChanges)`;
    - crear, editar y borrar pasan por `writeWithVersion` y `commitThenRefresh`;
    - **crear** comprueba dentro de la transacción que el documento no existe; si existe, `AlreadyExists`;
    - el documento creado lleva `name`, `nameLower`, `category`, `defaultUnit`, `createdAt`, `updatedAt` y `updatedBy`, **sin dueño**;
    - el nombre de ámbito de `realtimeMetrics` desaparece si el catálogo deja de ser una escucha.
- **Consume:** `VersionedCatalogLoader`, `CatalogSource`, `CatalogSyncStore`, `commitThenRefresh` y `refreshAfterWrite` (`APP/data/repository/VersionedCatalog.kt`).

- [ ] **Paso 1: tests en rojo**, en `UT/data/repository/CatalogIngredientFirestoreTest.kt`:
  - `an imported common ingredient without owner fields is parsed`: `{name, nameLower, category, defaultUnit, source, createdAt, updatedAt, updatedBy}` da un `CatalogIngredient` con `personal = false`.
  - `a document without name or with blank name is dropped`
  - `blank or non-string category and unit become null`
  - `personal flag is carried`
  - `catalog order ignores accents and case and ties by id`: «árbol», «Arroz» y «aceite» quedan como «aceite», «árbol», «Arroz».
  - `validation trims, nulls blanks and enforces limits`: nombre de 121 da `NameTooLong`; nombre «!!!» da `NameRequired` (slug vacío); categoría de 61 da `CategoryTooLong`; unidad de 25 da `UnitTooLong`.
  - `firestore fields carry nameLower`
- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA.
- [ ] **Paso 3: implementar.** Ajustar todo lo que use los campos de dueño:
  - **ViewModels y estados de ingredientes:** de momento, `canEdit` de un común es solo `isAdmin`.
  - **Dobles de test:** `FakeIngredientRepository`, `DetailFakeIngredientRepo` y `ShoppingFakeIngredientRepo` se adaptan a la interfaz nueva; los tests que comprobaban `ownerUid` se cambian para comprobar `isAdmin`.
  - **`ShoppingViewModel.createIngredientFromItem`:** compila sin `ingredientById`, comprobando contra `uiState.catalog` (el catálogo ya cargado). La Task 5 lo pasa a ingrediente personal.
  - **Hilt:** `FirestoreIngredientRepository` recibe `CatalogSyncStore`, que ya está enlazado en `DataModule`.
- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): catálogo común versionado y solo del admin`.

### Task 4: Marcas y «Dónde lo compro» personales; consumidores de las preferencias

**Ficheros:**
- Crear:
  - `APP/data/repository/IngredientBrandFirestore.kt` (puro);
  - `UT/data/repository/IngredientBrandFirestoreTest.kt`.
- Modificar:
  - `APP/core/model/IngredientBrand.kt`
  - `APP/core/model/IngredientPref.kt`
  - `APP/data/repository/IngredientBrandRepository.kt`
  - `APP/data/repository/FirestoreIngredientBrandRepository.kt`
  - `APP/data/repository/IngredientPrefsRepository.kt`
  - `APP/data/repository/FirestoreIngredientPrefsRepository.kt`
  - `UT/data/repository/IngredientPrefsParseTest.kt`
  - **Consumidores:**
    - `APP/feature/shopping/ShoppingViewModel.kt`, `ShoppingUiState.kt` y `ShoppingScreen.kt`
    - `APP/feature/common/IngredientReview.kt`
    - `APP/feature/meals/MealsViewModel.kt`
    - `APP/feature/recipes/RecipesViewModel.kt`
    - `APP/feature/ingredients/*`
    - sus tests y dobles
  - `RES`

**Interfaces:**
- **Produce:**
  - `data class IngredientBrand(val id: String, val ingredientId: String, val name: String, val barcode: String? = null, val nutrition: Nutrition? = null, val favorite: Boolean = false, val stores: List<Supermarket> = emptyList())`
  - `data class IngredientPref(val stores: List<Supermarket> = emptyList())`
  - En `IngredientBrandFirestore.kt`:
    - `internal fun normalizeStores(stores: List<Supermarket>): List<Supermarket>`: quita `INDIFERENTE` y los repetidos, mantiene el orden y se queda con 6 como máximo;
    - `data class BrandInput(val ingredientId: String, val name: String, val barcode: String?, val nutrition: Nutrition?, val favorite: Boolean, val stores: List<Supermarket>)`, público porque lo usan los ViewModels;
    - `internal fun validateBrandInput(input: BrandInput): BrandInput`: recorta los textos, convierte el código blanco en `null` y normaliza las tiendas; lanza `BrandRepositoryException` con `NameRequired`, `NameTooLong` (más de 60) o `BarcodeTooLong` (más de 32);
    - `internal fun BrandInput.toFirestoreFields(): Map<String, Any?>` → `{ingredientId, name, favorite, stores: [names], barcode?}` y los nutrientes que no son `null` (reutiliza `NUTRITION_FIELDS` y `toNonNullFields()`);
    - `internal fun parseIngredientBrand(id: String, data: Map<String, Any?>): IngredientBrand?`: `null` sin `ingredientId` o `name`; las tiendas desconocidas se ignoran; `favorite` es `false` si no es bool;
    - `internal fun List<IngredientBrand>.sortedForBrands(): List<IngredientBrand>`: favoritas primero y, después, por nombre con `Collator` español e id.
  - `IngredientBrandRepository`:
    - `fun brands(): Flow<List<IngredientBrand>>`: todas las del usuario, escucha sobre `users/{uid}/brands` con `limit(1000)`; sin sesión, `flowOf(emptyList())`;
    - `suspend fun addBrand(input: BrandInput): String`: devuelve el id;
    - `suspend fun updateBrand(brandId: String, input: BrandInput)`: no escribe `ingredientId`;
    - `suspend fun setFavorite(brandId: String, favorite: Boolean)`;
    - `suspend fun deleteBrand(brandId: String)`.
  - `IngredientPrefsRepository`:
    - `fun prefs(): Flow<Map<String, IngredientPref>>`;
    - `suspend fun setStores(ingredientId: String, stores: List<Supermarket>)`: con la lista vacía borra la entrada; si no, escribe `{stores: [names]}` normalizado, con `merge`;
    - **desaparecen** `setPref` y `clearPref`.
  - `internal fun parseIngredientPrefs(entries: Map<String, Any?>?): Map<String, IngredientPref>`: lee `stores`, normaliza e **ignora** las entradas con la forma antigua o vacías.
- **Consumidores** (Ruling 1 del plan):
  - **Compra:**
    - fuera `applyIngredientPref`, `saveItemAsPreference`, `suggestedPref`, la fila «Sugerido: … [Aplicar]», el menú «Guardar como preferencia» y el ajuste automático de `addItem` (`:148-162`);
    - fuera `ingredientPrefs` de `ShoppingUiState` y `prefFor` del editor (`:336` y `:668-674`): al tocar una sugerencia de nombre, solo se rellena el nombre;
    - fuera los textos y mensajes que ya no se usen: `shopping_suggested`, `shopping_apply`, `shopping_save_as_pref`, `shopping_notice_pref_saved` y `ShoppingUiMessage.PrefSaved`.
  - **`IngredientReview.applyIngredientReview`:** pasa a `prefs[slug(row.name)]?.stores?.firstOrNull()` como súper y `null` como marca.
  - **Ingredientes (para que compile):**
    - el detalle muestra las marcas con `brands().map { it.filter { b -> b.ingredientId == id }.sortedForBrands() }`;
    - se quita la sección «Tu preferencia», con sus textos `ingredients_pref_*` y sus mensajes `PrefSaved`/`PrefCleared`;
    - las altas de marca usan `BrandInput`, con `favorite = false` y `stores = emptyList()` hasta la Task 7.

- [ ] **Paso 1: tests en rojo.**
  - **`UT/data/repository/IngredientBrandFirestoreTest.kt`:**
    - `normalizeStores drops INDIFERENTE and duplicates keeping order and caps at six`
    - `validation trims, nulls blank barcode and enforces limits` (nombre vacío, nombre de 61 y código de 33)
    - `firestore fields carry favorite, store names and only present nutrients`
    - `parse reads favorite and stores and skips unknown stores`
    - `parse drops a brand without ingredientId or name`
    - `brands sort favorites first then by name ignoring accents`
  - **`UT/data/repository/IngredientPrefsParseTest.kt`**, reescrito:
    - `parses stores in order`
    - `old supermarket and brand entries are ignored`
    - `unknown, INDIFERENTE and duplicate stores are dropped and empty entries removed`
    - `null map yields empty`
  - **Ajustar los tests de los consumidores:**
    - `ShoppingViewModelTest`: quitar los 4 tests de preferencias (`:70`, `:84`, `:96` y `:110`) y añadir `manual add no longer applies any preference`;
    - `MealsViewModelTest:179` pasa a `dumping ingredients uses the ingredient's first store and no brand`;
    - `RecipesViewModelTest:209`, igual;
    - adaptar los dobles de preferencias y de marcas a las interfaces nuevas.
- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA.
- [ ] **Paso 3: implementar** los repositorios y los consumidores.
  - Las rutas son `users/{uid}/brands` e `ingredientPrefs/{uid}`.
  - Los ámbitos de `realtimeMetrics` son `"ingredients:brands"` y `"ingredientPrefs"`.
  - `setFavorite` escribe `{favorite, updatedAt: serverTimestamp()}`.
  - `updateBrand` escribe los campos de `toFirestoreFields()` **sin** `ingredientId`. El código y los nutrientes ausentes se borran con `FieldValue.delete()`, como hoy. Añade `updatedAt`.
  - `addBrand` escribe `createdAt` y `updatedAt` con `serverTimestamp()`.
- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): marcas y tiendas personales; la Compra deja las preferencias antiguas`.

### Task 5: Ingredientes personales y borrado de cuenta

**Ficheros:**
- Crear:
  - `APP/data/repository/PersonalIngredientRepository.kt`;
  - `APP/data/repository/FirestorePersonalIngredientRepository.kt`;
  - `UT/data/repository/PersonalDataPathsTest.kt`.
- Modificar:
  - `APP/data/di/DataModule.kt`
  - `APP/data/repository/FirebaseAccountRepository.kt`
  - `APP/feature/shopping/ShoppingViewModel.kt` («Crear ficha de ingrediente» crea uno personal) y su test
  - `RES`

**Interfaces:**
- **Produce:**
  - `interface PersonalIngredientRepository`:
    - `fun ingredients(): Flow<List<CatalogIngredient>>`: escucha `users/{uid}/ingredients` con `limit(500)`, parsea con `personal = true` y ordena con `sortedForIngredientCatalog()`; sin sesión, `flowOf(emptyList())`;
    - `suspend fun create(name: String, category: String?, defaultUnit: String?): String`:
      - valida con `validateIngredientInput`; el id es `slug(name)`;
      - en una transacción, si el documento existe lanza `AlreadyExists`; si no, escribe `toFirestoreFields()` más `createdAt` y `updatedAt`;
      - si las reglas responden `PERMISSION_DENIED` porque existe un común con ese id, también `AlreadyExists` (comprobado con un `get` del común);
      - devuelve el id;
    - `suspend fun update(id: String, name: String, category: String?, defaultUnit: String?)`: no cambia el id;
    - `suspend fun delete(id: String)`: lee `users/{uid}/brands where ingredientId == id` y, en un lote, borra esas marcas y el ingrediente, y quita `entries.<id>` de `ingredientPrefs/{uid}` (`set` con `merge` y `FieldValue.delete()`).
  - En `FirebaseAccountRepository.kt`:
    - `internal val PERSONAL_USER_COLLECTIONS = listOf("ingredients", "brands")`;
    - antes de `user.delete()`, `deletePersonalData(uid)`, que borra en lotes de 400 todos los documentos de `users/{uid}/{colección}` para cada colección de la lista y, después, `ingredientPrefs/{uid}`;
    - se puede repetir: si algo falla, la cuenta no se borra y el usuario lo intenta otra vez.
- **Consumidores:** `ShoppingViewModel.createIngredientFromItem(item)`:
  - si el nombre ya existe por `ingredientKey` en el catálogo común o en los personales cargados, aviso `IngredientExists` y no escribe;
  - si no, `personalIngredients.create(item.name, null, null)` y aviso `IngredientCreated`;
  - el ViewModel recibe `PersonalIngredientRepository` y observa sus ingredientes junto al catálogo mientras el editor está abierto (`startIngredientAssist`);
  - las sugerencias de nombre de la Compra incluyen también los personales.

- [ ] **Paso 1: tests en rojo.**
  - **`UT/data/repository/PersonalDataPathsTest.kt`:**
    - `personal collections are the ones the rules give to the owner`: lee `../firestore.rules` y comprueba que existen `match /users/{userId}` y, dentro, `match /ingredients/{` y `match /brands/{` para cada elemento de `PERSONAL_USER_COLLECTIONS`, siguiendo el modelo de `AccountAnonymizationTest`;
    - `account deletion also clears ingredientPrefs`: la constante de la ruta de preferencias del repositorio es `"ingredientPrefs"`, y se borra.
  - **`ShoppingViewModelTest`:**
    - `creating an ingredient from an item creates it as personal`
    - `creating an ingredient whose key matches a common one shows a notice and does not write` («Tomates» frente a «Tomate» del común)
- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA.
- [ ] **Paso 3: implementar.**
  - Los errores usan `IngredientFailure` y `IngredientRepositoryException`.
  - El ámbito de métricas es `"ingredients:personal"`.
  - En `DataModule`, `@Binds @Singleton` de `FirestorePersonalIngredientRepository`.
- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): ingredientes personales y borrado de lo personal con la cuenta`.

### Task 6: Lista de Ingredientes

**Ficheros:**
- Crear:
  - `APP/feature/ingredients/IngredientListLogic.kt` (puro);
  - `UT/feature/ingredients/IngredientListLogicTest.kt`.
- Modificar:
  - `APP/feature/ingredients/IngredientsViewModel.kt`, `IngredientsUiState.kt`, `IngredientsScreen.kt` e `IngredientUiMessage.kt`
  - `UT/feature/ingredients/IngredientsViewModelTest.kt`
  - `RES`

**Interfaces:**
- **Produce** (`IngredientListLogic.kt`):
  - `internal fun mergeIngredients(common: List<CatalogIngredient>, personal: List<CatalogIngredient>): List<CatalogIngredient>`: se oculta el personal cuyo `id` está entre los comunes, y el resultado se ordena con `sortedForIngredientCatalog()`.
  - `internal fun findByKey(ingredients: List<CatalogIngredient>, name: String): CatalogIngredient?`: el primero cuyo `ingredientKey(it.name) == ingredientKey(name)`, o `null` si la clave es vacía.
  - `internal sealed interface IngredientRowLine`:
    - `data class Favorite(val brand: String, val store: Supermarket?)`
    - `data class Brands(val count: Int)`
    - `data class Store(val store: Supermarket)`
    - `data object None`
  - `internal fun ingredientRowLine(brands: List<IngredientBrand>, pref: IngredientPref?): IngredientRowLine`:
    - con alguna favorita, toma la primera de `sortedForBrands()` como favorita, con `stores.firstOrNull() ?: pref?.stores?.firstOrNull()`;
    - con marcas pero ninguna favorita, `Brands(n)`;
    - sin marcas y con tienda en `pref`, `Store`;
    - si no, `None`.
    - Las marcas se pasan ya filtradas por ingrediente.
  - `internal fun List<CatalogIngredient>.matching(query: String)`: usa `matchesQuery` de `APP/core/common/TextSearch.kt` sobre el nombre y la categoría.
- **Estado y ViewModel:**
  - `IngredientsUiState` pasa a tener:
    - `ingredients: UiState<List<CatalogIngredient>>` (la mezcla de comunes y personales);
    - `brands: List<IngredientBrand>`, `prefs` y `catalogError: IngredientUiMessage?`;
    - `isAdmin`;
    - `openIngredientId: String?`: un evento para navegar al existente.
  - `IngredientsViewModel` observa el catálogo común, los personales, las marcas y las preferencias, y los combina.
    - Un error del catálogo común da `UiState.Error` y `catalogError = CatalogUnavailable` (mensaje nuevo, igual que Ejercicios). Los personales vacíos no son error.
    - `retryCatalog()`, como en Ejercicios.
    - `createIngredient(name, category, unit, common: Boolean)`:
      - con `common` y siendo admin, escribe en `IngredientRepository`; si no, en `PersonalIngredientRepository`;
      - antes, `findByKey(mezcla, name)`: si encuentra uno, **no escribe**, pone `openIngredientId = encontrado.id` y el aviso `AlreadyExists`; el editor queda en `FAILED` para que siga abierto con el mensaje;
      - el texto `ingredients_error_already_exists` pasa a «Ya existe «%1$s» en tus ingredientes o en el catálogo.», con el nombre encontrado;
    - `consumeOpenIngredient()`.
    - Fuera `setPref`, `clearPref`, `updateIngredient` y `deleteIngredient`, que no usa ninguna pantalla. El escaneo sigue igual hasta la Task 8.
- **Pantalla:**
  - **Búsqueda:** `matching`.
  - **Fila:** el nombre, la categoría, un `InfoChip("Personal")` (texto `ingredients_personal`) si `personal`, y la línea de `ingredientRowLine`:
    - `Favorite`: «★ Valor · Mercadona», usando `SupermarketIcon` y la etiqueta de la tienda si la hay;
    - `Brands`: «N marcas», con un plural (`<plurals name="ingredients_brand_count">`);
    - `Store`: icono y etiqueta;
    - `None`: nada.
  - **Formulario de alta:**
    - con un interruptor «Común (catálogo)» (`ingredients_common_switch`) que **solo ve el admin** y que por defecto está apagado;
    - un `LaunchedEffect(openIngredientId)` navega al existente (`onOpenIngredient`) y llama a `consume`.
  - **Error del catálogo:** `ErrorState` con `onRetry = viewModel::retryCatalog`.

- [ ] **Paso 1: tests en rojo.**
  - **`IngredientListLogicTest`:**
    - `merge hides a personal ingredient whose id is common and sorts by name`
    - `findByKey matches plural, accents and case`
    - `findByKey ignores blank names`
    - `row line shows the first favorite with its store or the ingredient store`
    - `row line counts brands when none is favorite`
    - `row line falls back to the ingredient first store and then to nothing`
    - `matching uses name and category ignoring accents`
  - **`IngredientsViewModelTest`**, reescrito con dobles de los 4 repositorios y el cliente de Open Food Facts:
    - `observe merges common and personal and carries brands and prefs`
    - `a catalog error shows CatalogUnavailable and retry reloads it`
    - `creating a new ingredient creates it as personal`
    - `an admin can create a common ingredient`
    - `a non-admin asking for common still creates a personal ingredient`
    - `a name matching an existing ingredient by key opens it and does not write`
    - `create is SAVED only after the repository answers`, que mantiene el patrón de puertas que ya existe
    - `editor validation errors and timeouts leave FAILED`
- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): lista con comunes y personales, línea de marca y tienda`.

### Task 7: Ficha del ingrediente: «Dónde lo compro», «Mis marcas» y editor de marca

**Ficheros:**
- Crear:
  - `APP/feature/ingredients/StoreSelection.kt` (puro);
  - `UT/feature/ingredients/StoreSelectionTest.kt`.
- Modificar:
  - `APP/feature/ingredients/IngredientDetailViewModel.kt`, `IngredientDetailUiState.kt`, `IngredientDetailScreen.kt`, `BrandDraft.kt` e `IngredientUiMessage.kt`
  - `UT/feature/ingredients/IngredientDetailViewModelTest.kt` y `BrandDraftTest.kt`
  - `UT/feature/DraftsAreParcelableTest.kt`, si hace falta
  - `RES`

**Interfaces:**
- **Produce:**
  - `internal val SELECTABLE_STORES: List<Supermarket>` = `Supermarket.entries - INDIFERENTE`.
  - `internal fun toggleStore(stores: List<Supermarket>, store: Supermarket): List<Supermarket>`: si está, lo quita; si no, lo añade al final; nunca acepta `INDIFERENTE`.
  - `BrandDraft` añade `favorite: Boolean = false` y `stores: List<Supermarket> = emptyList()`, como `@Parcelize`; `of(...)` recibe la marca completa o los datos del escaneo, y `toInput(ingredientId)` devuelve `BrandInput`.
- **Estado y ViewModel:**
  - `IngredientDetailUiState`:
    - `ingredient: CatalogIngredient?`, resuelto por id: primero el común y, si no, el personal;
    - `brands` (las del ingrediente, con `sortedForBrands()`), `stores: List<Supermarket>` (de las preferencias) e `isAdmin`;
    - `canEditIngredient = ingredient?.personal == true || isAdmin`.
  - `IngredientDetailViewModel`:
    - `updateIngredient` y `deleteIngredient` van al repositorio personal o al común según `ingredient.personal`;
    - `toggleStore(store)`: con `setStores(id, toggleStore(stores, store))` (no es un editor);
    - `setFavorite(brandId, favorite)` (no es un editor);
    - `saveBrand(brandId: String?, draft: BrandDraft)`: es un editor; alta o edición con `BrandInput`;
    - `deleteBrand(brandId)`;
    - el escaneo sigue igual hasta la Task 8.
- **Pantalla:**
  - **Datos:** se editan y borran si `canEditIngredient`. El diálogo de borrado de un común avisa de que es del catálogo.
  - **«Dónde lo compro»** (`ingredients_stores_title`, con la pista `ingredients_stores_hint` «Tus tiendas, en tu orden de preferencia»):
    - una fila con `FilterChip` para cada tienda de `SELECTABLE_STORES`, con `SupermarketIcon`;
    - las elegidas, con su número de orden delante («1 · Mercadona»).
  - **«Mis marcas»:** cada tarjeta muestra:
    - el nombre;
    - un `IconToggleButton` de estrella (favorita) con su descripción accesible;
    - las tiendas como `InfoChip` con icono;
    - la nutrición, plegada por defecto (un botón «Ver nutrición» la despliega);
    - editar y borrar.
  - **Editor de marca:**
    - nombre (60 como máximo, con el error del repositorio si se pasa) y código;
    - **tiendas**: los mismos chips con orden;
    - el interruptor **«Favorita»**;
    - la nutrición.
  - **Desaparece** todo lo que quedara de «Tu preferencia».

- [ ] **Paso 1: tests en rojo.**
  - **`StoreSelectionTest`:**
    - `toggle appends at the end and removes when present`
    - `INDIFERENTE is never selectable or added`
  - **`BrandDraftTest`:**
    - `a draft from a brand keeps favorite and stores`
    - `toInput carries ingredient, favorite, stores and nutrition`
  - **`IngredientDetailViewModelTest`**, reescrito con dobles:
    - `observe resolves a common ingredient before a personal one with the same id`
    - `observe shows only this ingredient's brands, favorites first`
    - `canEditIngredient is true for personal and for admins on common`
    - `toggling a store saves the new ordered list and is not an editor action`
    - `setting favorite forwards it`
    - `saving a new brand sends favorite and stores`
    - `editing a brand never changes its ingredient`
    - `deleting a personal ingredient uses the personal repository and finishes`
    - `deleting a common ingredient as admin uses the catalog repository`
    - los tests de editor, `SAVED` solo cuando el repositorio responde y `FAILED` por validación o tiempo agotado, adaptados.
- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): ficha con dónde lo compro, mis marcas, favorita y tiendas`.

### Task 8: Escaneo: marca ya conocida y «¿De qué ingrediente es?»

**Ficheros:**
- Crear:
  - `APP/feature/ingredients/ScanLogic.kt` (puro);
  - `UT/feature/ingredients/ScanLogicTest.kt`.
- Modificar:
  - `APP/feature/ingredients/IngredientsViewModel.kt`, `IngredientsUiState.kt`, `IngredientsScreen.kt`, `IngredientDetailViewModel.kt`, `IngredientDetailScreen.kt` e `IngredientUiMessage.kt`
  - `APP/core/navigation/BobitosDestination.kt` y `BobitosNavHost.kt`: el argumento opcional `brandId` en el detalle
  - sus tests
  - `RES`

**Interfaces:**
- **Produce** (`ScanLogic.kt`):
  - `internal fun findBrandByBarcode(brands: List<IngredientBrand>, barcode: String): IngredientBrand?`: compara solo los dígitos.
  - `internal fun suggestIngredients(ingredients: List<CatalogIngredient>, productName: String, max: Int = 8): List<CatalogIngredient>`:
    - las palabras de `ingredientKey(productName)` forman un conjunto;
    - un ingrediente es candidato si **todas** las palabras de su `ingredientKey(name)` están en ese conjunto;
    - se ordenan por número de palabras de mayor a menor (más específico primero) y, después, por nombre;
    - devuelve como mucho `max`.
- **Navegación:** `INGREDIENT_DETAIL_ROUTE = "ingredient-detail/{ingredientId}?brandId={brandId}"`, con `brandId` opcional (`nullable = true`, `defaultValue = null`). `IngredientDetailScreen(ingredientId, brandId: String?, …)` abre el editor de esa marca una vez, cuando la carga.
- **ViewModel de la lista:**
  - `lookupBarcode(barcode)`:
    1. si `findBrandByBarcode(brands, barcode)` encuentra una marca, emite `openBrand = (ingredientId, brandId)` y **no** consulta Open Food Facts;
    2. si no, consulta Open Food Facts (como hoy) y deja `scanPick = ScanPick(barcode, productName, brandName, nutrition)`, con el aviso `ScanNotFound` si no está y el error `ScanFailed` si falla la red. Con `ScanFailed` también deja `scanPick`, con el nombre vacío y el código, para seguir a mano.
  - `pickIngredientForScan(ingredientId)` y `createPersonalForScan(name, category, unit)`, que crea el personal con la comprobación de clave de la Task 6 y, si ya existe, usa el existente. Los dos dejan `scanBrandTarget = ingredientId`.
  - `saveScannedBrand(draft: BrandDraft)`: un editor, `addBrand(draft.toInput(scanBrandTarget))`; al acabar, limpia `scanPick` y `scanBrandTarget`.
  - `cancelScan()`.
- **Pantalla de la lista:**
  - con `scanPick` y sin destino, un diálogo «¿De qué ingrediente es?» (`ingredients_scan_pick_title`) con:
    - el buscador vacío;
    - la sección «Sugerencias» con `suggestIngredients(mezcla, productName)` mientras el buscador está vacío, y `matching(query)` sobre la mezcla cuando se escribe;
    - el botón «Crear ingrediente personal» (`ingredients_scan_create_personal`), que abre `IngredientEditorDialog` con `initialName = productName`.
  - con destino, el **editor de marca** (el mismo composable de la Task 7, ahora `internal`) relleno con la marca, el código y la nutrición.
  - con `openBrand`, navega a `ingredient-detail/{id}?brandId={brandId}`.
  - El estado del flujo vive en el ViewModel, así que sobrevive a una rotación.
- **ViewModel del detalle**, al escanear:
  - si el código ya está en una marca de **este** ingrediente, abre su editor;
  - si está en **otro**, muestra el aviso `BarcodeInOtherIngredient` («Ese código ya está en tu marca «%1$s» de «%2$s».») y no abre nada;
  - si no, hace lo de hoy (el editor relleno).

- [ ] **Paso 1: tests en rojo.**
  - **`ScanLogicTest`:**
    - `finds a brand by barcode ignoring spaces and non digits`
    - `suggests ingredients whose key words are all in the product name, most specific first`: «Chocolate negro 85 % cacao Lindt» da «Chocolate negro» y «Cacao», en ese orden, y no «Leche».
    - `suggestions are capped and empty for a blank product`
  - **`IngredientsViewModelTest`:**
    - `scanning a barcode already in my brands opens it without calling Open Food Facts`
    - `scanning a new barcode leaves a pick with the product data`
    - `a network error still leaves a pick with only the barcode`
    - `picking an ingredient and saving the brand adds it to that ingredient`
    - `creating a personal ingredient from a scan then saving adds the brand to it`
    - `creating from a scan with a name that already exists uses the existing ingredient`
    - `cancelling the scan clears the pick`
  - **`IngredientDetailViewModelTest`:**
    - `scanning a barcode of this ingredient's brand opens its editor`
    - `scanning a barcode of another ingredient's brand shows a notice`
  - **Navegación:** si `RealtimeScopeForTest` o `WorkspaceTabsTest` mencionan la ruta del detalle, se adaptan.
- [ ] **Paso 2:** ejecutar GRADLE. Esperado: FALLA.
- [ ] **Paso 3:** implementar. Se retira `createIngredientFromScan` y `ScannedProduct` pasa a ser `ScanPick`.
- [ ] **Paso 4:** ejecutar GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(ingredientes): escaneo que reconoce tus marcas y pregunta de qué ingrediente es`.

### Task 9: Documentación y privacidad

**Ficheros:**
- `docs/DATA_MODEL.md`
- `PRIVACY_POLICY.md`
- `docs/INGREDIENT_CATALOG.md`

**Interfaces:** solo documentación, sin código.

- [ ] **Paso 1: `docs/DATA_MODEL.md`.**
  - Subir la versión a 0.9.0 y la fecha al 11 de octubre de 2026.
  - En el árbol de la sección 2, añadir `users/{userId}/ingredients/{slug}`, `users/{userId}/brands/{brandId}` y `catalogMeta/ingredients`.
  - Una sección nueva, **«Ingredientes»**, con:
    - el catálogo común (campos, solo del admin, `source` del importador, versión y caché);
    - los ingredientes personales (campos, id = slug, sin choque con un común y lo que pasa si aparece uno después);
    - las marcas personales (campos, `stores` y `favorite`, `ingredientId` inmutable);
    - «Dónde lo compro» (`ingredientPrefs/{uid}`, la forma `slug → {stores}`, que las entradas antiguas se ignoran);
    - la clave tolerante (`ingredientKey`, con la tabla compartida);
    - las reglas de cada colección, en viñetas.
  - En «Lista de la compra», añadir los campos `supermarket` y `brand`, que faltaban, y explicar que la marca se guarda como texto.
  - En la sección 14, «Eliminación de cuenta», añadir el borrado de `users/{uid}/ingredients`, `users/{uid}/brands` e `ingredientPrefs/{uid}`.
- [ ] **Paso 2: `PRIVACY_POLICY.md`.**
  - Actualizar la fecha.
  - En los datos tratados, añadir que los ingredientes personales, las marcas (con su código de barras y su nutrición) y las tiendas de cada usuario **solo los ve ese usuario**, y que el catálogo común no contiene datos personales.
  - En el párrafo del borrado de cuenta, decir que también se borran.
- [ ] **Paso 3: `docs/INGREDIENT_CATALOG.md`.**
  - En «En la app», que el catálogo se carga con `VersionedCatalogLoader` y que el admin escribe en una transacción con la versión.
  - Quitar la frase de que la beta 18 dejará de poder crear preferencias, que no es así.
  - Añadir que la beta 18 verá Ingredientes vacío tras la importación hasta la beta 19.
- [ ] **Paso 4:** ejecutar GRADLE, EMU, NODE e IMP. Esperado: todo en verde.
- [ ] **Paso 5:** commit `docs: modelo de ingredientes personales, marcas y privacidad`.

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. Crear la rama `agent/marcas-personales-ingredientes` desde `main` y hacer commit de este plan.
2. Las tareas de la 1 a la 9, una tras otra, cada una con un `sonnet`. El coordinador hace `task-done` con la suite que nombra cada tarea:
   - GRADLE en las tareas 1 y 3 a 9;
   - EMU en la 2;
   - NODE en las 1, 2 y 9.
3. **Revisión final** con `opus`, más los arreglos. **Re-revisión acotada** si algún arreglo toca `firestore.rules` o el aislamiento de datos personales (`users/{uid}/…` o el borrado de cuenta).
4. **`simplify`:** un revisor `sonnet` con los 4 ángulos y un aplicador `sonnet` de la lista (a).
5. **`code-review`** en nivel `high`, más los arreglos.
6. **Cierre:** el backlog y la decisión sobre la rama, que es del usuario. Después viene el plan 3. El despliegue espera a que estén los tres.

## Verificación de punta a punta (coordinador, al final)

- GRADLE, EMU, NODE e IMP en verde.
- **En el emulador:**
  1. `npm run emulators`;
  2. importar el catálogo: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-ingredients.mjs --project demo-bobitos --apply`;
  3. instalar la app debug con un usuario normal y con el admin.
- **Recorrido**, con capturas en tema claro y oscuro:
  1. la lista muestra unos 356 comunes;
  2. crear «Salsa de la abuela» como personal: sale con «Personal»;
  3. intentar crear «Tomates»: lleva a «Tomate»;
  4. en «Tomate», marcar las tiendas Mercadona y DIA, en ese orden;
  5. añadir una marca a mano, marcarla como favorita y ponerle tiendas;
  6. la fila de la lista muestra «★ Marca · Mercadona»;
  7. escanear el código de una marca ya creada: abre esa marca;
  8. borrar el personal: sus marcas desaparecen;
  9. con el admin, editar un común: se ve en la otra sesión tras volver a la pantalla;
  10. con un usuario normal, no aparece ni el interruptor «Común» ni editar o borrar en los comunes.
