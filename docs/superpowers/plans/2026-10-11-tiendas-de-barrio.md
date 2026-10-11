# Tiendas de barrio en la Compra — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan pequeño** (2 tareas): pasos 1, 2, 3, 5 y 6, sin `simplify`. Cada tarea la hace un subagente `sonnet`, en serie; la revisión final, `opus` (si falla, `fable`). La tarea 1 toca reglas (permisos): si un arreglo de la revisión toca `firestore.rules`, hay re-revisión acotada. Los pasos usan casillas (`- [ ]`).

**Objetivo:** además de los supermercados, poder elegir como sitio de compra **Carnicería, Pescadería, Frutería y Panadería**, en todo lo que usa la lista de tiendas (artículo de la Compra, filtro por tienda, tiendas de cada marca, «Dónde lo compro», selector de marca y tienda).

**Arquitectura:** se amplía el enum `Supermarket` (el nombre del enum en el código y el campo `supermarket` de Firestore no cambian: no hay migración), con sus etiquetas, colores e iconos, y la lista de valores admitidos en `firestore.rules`. Un test JVM lee `firestore.rules` y comprueba que las listas de las reglas coinciden con el enum, para que no vuelvan a desincronizarse.

**Stack:** Kotlin, Jetpack Compose (Material 3, `material-icons-extended`), reglas de Firestore, Node (`node:test`) con Firebase Emulator Suite.

**Spec:** no hay documento de spec; el diseño se aprobó en la conversación el 11/10/2026:
- tipos de tienda **fijos**, iguales para todos (no tiendas con nombre propio);
- los cuatro tipos elegidos por el usuario: Carnicería, Frutería, Pescadería y Panadería; el resto sigue en «Otros»;
- orden: los cinco supermercados, después Carnicería, Pescadería, Frutería y Panadería, después Otros e Indiferente;
- los supermercados conservan el icono de tienda con su color; los tipos nuevos llevan icono propio y un color sobrio;
- en la Compra, «Supermercado (opcional)» pasa a «Tienda (opcional)»;
- el límite de 6 tiendas por marca y por ingrediente se mantiene;
- la beta 18 muestra sin tienda un artículo con un tipo nuevo (y si lo edita, la borra): aceptado, porque las reglas se despliegan junto con la beta 19.

## Restricciones globales

- **Rutas:** `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`; `RES` = `app/src/main/res/values/strings.xml`.
- **Comandos** (cada uno en su propia llamada, sin `sh -c`):
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **EMU:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && npm run test:emulators`
  - **NODE:** `npm run test:scripts`
- **Valores nuevos** (nombres exactos en Firestore y en el enum): `CARNICERIA`, `PESCADERIA`, `FRUTERIA`, `PANADERIA`. Orden completo del enum: `DIA, EROSKI, MERCADONA, CARREFOUR, ALCAMPO, CARNICERIA, PESCADERIA, FRUTERIA, PANADERIA, OTROS, INDIFERENTE`.
- **Prohibido:** nada contra `bobitos-dev` (`--project dev`), desplegar reglas, distribuir betas, push.
- **Tests:** JVM puros (JUnit 4); TDD obligatorio (test primero, en rojo, después el código). detekt: extraer antes que tocar el baseline; ktlint: sin imports sin usar. Textos en español de España, en `RES`.

## Review Focus

1. **Reglas y enum sincronizados:** un artículo con cada valor nuevo se guarda; una marca y «Dónde lo compro» admiten los valores nuevos (sin `INDIFERENTE`); un valor desconocido sigue rechazándose.
2. **Límite de 6:** con 10 tiendas elegibles, una marca o un ingrediente siguen sin poder tener más de 6.
3. **Filtro de la Compra:** con artículos de Carnicería y Mercadona, aparecen los dos chips y cada uno filtra lo suyo (los artículos sin tienda o «Indiferente» siguen saliendo en todos, como hoy).
4. **Aspecto:** los iconos y colores nuevos se distinguen y se leen en tema oscuro (por defecto) y claro; las chips de «Dónde lo compro» (ahora 10) se reparten en varias filas sin cortarse.
5. **Lectura de valores desconocidos:** la app sigue ignorando sin fallar un valor que no conoce.

---

### Task 1: Reglas de Firestore y sus tests

**Ficheros:**
- `firestore.rules`:
  - ~:205, artículo de la Compra: `request.resource.data.supermarket in [...]` añade los cuatro valores nuevos (orden del enum);
  - ~:1247, `validStoreList`: `stores.hasOnly([...])` añade los cuatro valores nuevos (sin `INDIFERENTE`);
  - comentarios en español que digan que estas listas deben coincidir con el enum `Supermarket` (`APP/core/model/ShoppingItem.kt`).
- `tests/firebase-emulators.test.mjs`.
- `docs/DATA_MODEL.md`: ~:210 (valores de `supermarket`) y ~:582 (valores de `stores`).

**Interfaces:**
- **Produce:** las listas de valores admitidos en las reglas, en el orden del enum, que la Task 2 vigila con un test JVM que lee `firestore.rules`.

- [ ] **Paso 1: tests en rojo** (estilo del fichero: `verifiedFirestore`, `assertSucceeds`, `assertFails`):
  - `un artículo de la compra admite las tiendas de barrio`: crear un artículo con cada uno de `CARNICERIA`, `PESCADERIA`, `FRUTERIA`, `PANADERIA` → se acepta; con `"LIDL"` → se rechaza.
  - `una marca personal admite tiendas de barrio y sigue limitada a 6`: `stores: ["CARNICERIA", "MERCADONA"]` → se acepta; 7 tiendas distintas (incluidas las nuevas) → se rechaza; `["FRUTERIA", "INDIFERENTE"]` → se rechaza.
- [ ] **Paso 2:** EMU. Esperado: FALLAN los tests nuevos de aceptación.
- [ ] **Paso 3:** implementar en `firestore.rules` y actualizar `docs/DATA_MODEL.md`.
- [ ] **Paso 4:** EMU y NODE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(reglas): tiendas de barrio en la compra y en las tiendas de las marcas`.

### Task 2: Enum, aspecto y textos en la app

**Ficheros:**
- `APP/core/model/ShoppingItem.kt`: el enum `Supermarket` con el orden de las restricciones globales.
- `APP/feature/shopping/SupermarketTokens.kt`: `labelRes` y `brandColor()` para los cuatro valores, y una función nueva para el icono.
- `APP/feature/shopping/ShoppingScreen.kt`: `SupermarketIcon` (~:853) usa el icono de cada valor.
- `RES`: `supermarket_carniceria` («Carnicería»), `supermarket_pescaderia` («Pescadería»), `supermarket_fruteria` («Frutería»), `supermarket_panaderia` («Panadería»); `shopping_supermarket_label` pasa a «Tienda (opcional)».
- Todo `when` sobre `Supermarket` que deje de ser exhaustivo.
- Crear: `UT/core/model/SupermarketRulesSyncTest.kt`.

**Interfaces:**
- **Consume (Task 1):** las listas de `firestore.rules`.
- **Produce:**

```kotlin
// feature/shopping/SupermarketTokens.kt
internal fun Supermarket.icon(): ImageVector   // supermercados, Otros e Indiferente: Icons.Rounded.Storefront
```

- **Iconos** (de `androidx.compose.material.icons.rounded`, `material-icons-extended`; si alguno no existe en la versión del proyecto, elegir el más parecido que compile y decirlo en el informe): Carnicería → `KebabDining`; Pescadería → `SetMeal`; Frutería → `Eco`; Panadería → `BakeryDining`.
- **Colores** (tonos medios legibles en claro y oscuro, como los de los supermercados): Carnicería `0xFFA33B3B`, Pescadería `0xFF2F7DA8`, Frutería `0xFF5E9A2F`, Panadería `0xFFB07A2A`.

- [ ] **Paso 1: tests en rojo.** `SupermarketRulesSyncTest` (lee `File("../firestore.rules")`, como `PersonalDataPathsTest`/`AccountAnonymizationTest`):
  - `the shopping item rule lists exactly the Supermarket values`: extrae la lista de la regla del artículo (`supermarket in [...]`) y la compara con `Supermarket.entries.map { it.name }` (mismo contenido y mismo orden).
  - `the brand store rule lists every value but INDIFERENTE`: extrae la lista de `stores.hasOnly([...])` y la compara con `Supermarket.entries.filter { it != Supermarket.INDIFERENTE }.map { it.name }`.
  - `every Supermarket has a label, a color and an icon` (llamar a `labelRes`, `brandColor()` e `icon()` para cada valor; que no lance).
- [ ] **Paso 2:** GRADLE. Esperado: FALLA (faltan los valores en el enum).
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `feat(compra): carnicería, pescadería, frutería y panadería como sitios de compra`.

---

## Ejecución (`delegating-plan-execution`, plan pequeño)

1. Rama `agent/tiendas-de-barrio` desde `main`; commit de este plan.
2. Tareas 1 y 2 con un `sonnet` cada una; `task-done` con EMU (Task 1) y GRADLE (Task 2).
3. Revisión final con `opus`, más arreglos (re-revisión acotada si un arreglo toca `firestore.rules`).
4. `code-review` en nivel `high`, más arreglos.
5. Cierre: backlog, rulings y la decisión sobre la rama, que es del usuario. Entra en el despliegue único pendiente (reglas → borrado inicial → importación → beta 19).

## Verificación de punta a punta (coordinador, al final)

En el emulador Android con los emuladores de Firebase:
1. Compra → Añadir «Filetes» con tienda Carnicería y «Plátanos» con Frutería; el formulario dice «Tienda (opcional)».
2. El filtro muestra los chips de Carnicería y Frutería; cada uno filtra lo suyo.
3. Ficha de un ingrediente: «Dónde lo compro» muestra las 10 tiendas repartidas en filas; elegir Pescadería.
4. Editor de marca: elegir Panadería como tienda.
5. Capturas en tema oscuro y claro.
