# Despensa — Spec de diseño

> Resultado de `superpowers:brainstorming` (camino **arquitectónico**), aprobado por el usuario el 2026-10-13. Es la idea D5 de la épica #168 («mejoras 2»), que se dejó como opcional por el riesgo de que los datos no se mantengan al día; la spec del catálogo de ingredientes la dejó fuera de alcance. Sale en **dos planes en serie**, cada uno con `superpowers:writing-plans` y ejecutado con `delegating-plan-execution`.

## Contexto

Una despensa solo sirve si está al día, y el riesgo de D5 era que mantenerla costara más de lo que aporta. Por eso el diseño parte de dos ideas: guardar lo mínimo («hay» o «no hay») y que se mantenga sola con lo que el usuario ya hace (marcar comprado en la Compra, marcar una comida como cocinada).

**Hoy (código):**
- La Compra es del espacio (`spaces/{id}/shoppingItems`). `FirestoreShoppingRepository.setPurchased` marca y desmarca en una transacción. Los repetidos se reconocen con `shoppingMatchKey(name) = ingredientKey(name).ifEmpty { name.trim().lowercase() }` (`core/model/ShoppingMatch.kt`).
- `IngredientShoppingSource` (`feature/common`) junta el catálogo común, tus ingredientes personales, tus marcas y «Dónde lo compro» en `IngredientShoppingData` (`BrandStoreSelection.kt`, con su índice por clave tolerante). La usan la Compra, Comidas y el Recetario para el selector de marca y tienda.
- La revisión de «Añadir a la compra» (`feature/common/IngredientReview.kt`, compartida por el Recetario y Comidas) tiene tres tipos de fila (`ReviewRowKind`: `NEW`, `PENDING`, `PURCHASED`) y lee la lista del servidor con `currentItems`.
- `MealsViewModel.markCooked` marca la comida y ofrece «Tachar de la compra» los artículos **pendientes** de la receta (todo o nada, `cookedCrossOff`); tachar los marca como comprados.
- El Recetario está en el menú principal, fuera del espacio, pero recibe el `spaceId` activo para «Añadir a la compra». Tiene buscador y una fila de `FilterChip` de categorías (`RecipesScreen.kt` `CategoryFilter`).
- El selector de receta del planificador (`feature/meals/RecipePicker.kt`) tiene buscador y orden «Mías» → «Comunes».
- Los módulos del espacio que no son pestaña están en «Más» (`BobitosDestination.moreDestinations`: Comidas, Deporte, Notas).
- **Hueco encontrado:** `FirestoreSpaceRepository.deleteSpace` vacía `shoppingItems`, `tasks`, `events` y `meals`, pero no `notes` ni `activities`: al borrar un espacio, sus notas y sus sesiones de deporte se quedan huérfanas en Firestore.

## Decisiones del usuario

1. **Para qué:** no comprar lo que ya hay, saber qué hay en casa y saber qué se puede cocinar con lo que hay. **Sin caducidades.**
2. **Detalle:** solo **«hay» o «no hay»**, sin cantidades.
3. **Despensa del espacio**, compartida por sus miembros como la Compra.
4. **Tres automatismos:** comprado → despensa; cocinar → «¿Se ha acabado algo?»; «Se acabó» → ofrecer apuntarlo a la compra. Siempre se puede añadir y quitar a mano.
5. **Solo ingredientes entran solos:** al marcar comprado, entra lo que se reconoce como ingrediente (catálogo común o personal, con la clave tolerante). Lo demás solo a mano.
6. **Revisión de «Añadir a la compra»:** fila «En la despensa» con **Omitir / Añadir**, **Omitir por defecto**.
7. **«Con lo que hay»** en el Recetario (filtro) y en el selector de receta del planificador; sin lista propia en la Despensa.
8. **Enfoque A:** una subcolección con un documento por ingrediente presente.
9. **Arreglar el hueco** de borrar un espacio (`notes` y `activities`) dentro de este trabajo.
10. **Dos planes en serie**, sin desplegar nada en este trabajo.

## 1. Datos y reglas

### Documento `spaces/{spaceId}/pantryItems/{id}`

Un documento por cada cosa que hay en casa. Que exista es «hay»; «Se acabó» lo borra.

| Campo | Tipo | Notas |
|---|---|---|
| `name` | string 1–120 | Nombre canónico del catálogo si se reconoce; si no, lo escrito (recortado). |
| `category` | string ≤60 o `null` | La del ingrediente común o personal; `null` se agrupa en «Otros». |
| `createdBy` | string | uid de quien lo apuntó. |
| `createdAt` | timestamp | Hora del servidor. |
| `shoppingItemId` | string ≤128 o `null` | El artículo de la Compra cuya compra lo trajo; `null` si se añadió a mano o al cocinar. |

- **Id:** `pantryId(name) = slug(shoppingMatchKey(name))` (función pura nueva en `core/model`). «Tomates» y «tomate» dan `tomat`; «Aceite de oliva» da `aceit-oliva` (la clave tolerante quita la «-e» final y las palabras vacías, y ordena las palabras; los ids no se ven en la app). Si queda vacío, no se puede apuntar (el editor lo impide). Mismo id → no hay duplicados y apuntar lo que ya está no hace nada.
- **Sin nombre de quien lo apuntó:** la pantalla no lo muestra, así que borrar la cuenta no tiene nada que anonimizar aquí. El uid se queda, como en las demás colecciones del espacio.
- **Nunca se edita:** se crea o se borra.

### Reglas (`firestore.rules`, dentro de `match /spaces/{spaceId}`)

```text
match /pantryItems/{itemId} {
  allow read: if isActiveMember(spaceId);
  allow create: if validNewPantryItem(spaceId, itemId);
  allow update: if false;
  allow delete: if isActiveMember(spaceId);
}
```

`validNewPantryItem`: miembro activo; `keys().hasOnly([...])` con los cinco campos y `hasAll` de `name`, `createdBy` y `createdAt`; `name` string 1–120; `category` null o string ≤60; `createdBy == request.auth.uid`; `createdAt == request.time`; `shoppingItemId` null o string ≤128; `itemId` con formato de slug (`^[a-z0-9]+(-[a-z0-9]+)*$`, ≤120). Que el id sea el slug del nombre lo garantiza el cliente, no las reglas.

### Repositorio

`PantryRepository` (interfaz) + `FirestorePantryRepository` + dobles a mano para los tests:
- `items(spaceId): Flow<List<PantryItem>>`: escucha con `limit(1000)`.
- `currentIds(spaceId): Set<String>`: lectura del servidor (para la revisión y el diálogo de cocinar, como `currentItems`).
- `add(spaceId, entries: List<PantryEntry>)`: en un lote; las que ya existen se saltan (se leen antes por id).
- `remove(spaceId, ids: List<String>)`: en un lote.

`PantryEntry(name, category)` es lo que se quiere apuntar; el repositorio calcula el id con `pantryId`.

### Borrar un espacio

`FirestoreSpaceRepository.deleteSpace` vacía también `pantryItems`, `notes` y `activities` (decisión 9), con el mismo `deleteDocumentsInChunks`.

## 2. Pantalla Despensa (`feature/pantry`)

**Dónde está:** destino nuevo `BobitosDestination.Pantry` («Despensa»), en `moreDestinations` después de Notas, con su icono y su `ModuleCard`.

### Lista

- Agrupada por las 14 categorías del catálogo de ingredientes, en su orden, y «Otros» al final (sin categoría o con una que no es de la lista). Dentro de cada grupo, orden alfabético del español.
- Buscador por nombre (como en Ingredientes).
- Cada fila: el nombre y un botón **«Se acabó»**. Al tocarlo se borra la entrada y sale un Snackbar «Arroz: se ha acabado» con la acción **«A la compra»**, que lo apunta en la Compra del espacio:
  - con la comprobación de repetidos de siempre (`findShoppingMatch` sobre `currentItems`): si hay uno **pendiente**, no escribe y avisa «Ya estaba en la lista»; si hay uno **comprado**, lo vuelve a apuntar conservando su cantidad, notas, tienda y marca (lo que se compró la última vez);
  - si es nuevo, con la marca y la tienda por defecto de `BrandStoreSelection` (la favorita si solo hay una; datos de `IngredientShoppingSource`) y sin cantidad.
  - Esta escritura («apuntar lo que se ha acabado») es una función compartida con el diálogo de cocinar (3c).
- **Vacía:** `EmptyState` con el botón «Llenar la despensa», que abre el selector.
- **Errores de lectura:** como la Compra, «No se ha podido leer la despensa» con «Reintentar».

### Añadir (FAB) → selector múltiple

- Pantalla completa con buscador sobre el catálogo común y tus ingredientes personales (sin los personales ocultos por un común con el mismo slug), agrupados por categoría, con casillas.
- Lo que ya está en la despensa sale marcado y deshabilitado.
- Si lo escrito no coincide con ningún ingrediente (por `ingredientKey`) ni con algo de la despensa, aparece la fila «Añadir "lo escrito"» (sin categoría → «Otros»).
- «Añadir (N)» escribe todo en un lote y vuelve a la lista.

### «¿Qué cocino?»

Botón en la barra superior: abre el Recetario con el filtro «Con lo que hay» activado (sección 4c).

## 3. Automatismos

### 3a. Comprado → despensa

- Función pura compartida `pantryEntryFor(name, data: IngredientShoppingData): PantryEntry?`: busca el nombre por clave tolerante en los ingredientes (comunes y personales) y, si lo encuentra, devuelve una `PantryEntry` con el nombre canónico y la categoría; si no, `null`. La usan la Compra (aquí) y Comidas (3c).
- `ShoppingViewModel` la aplica al marcar comprado y pasa el resultado a `setPurchased`.
- `ShoppingRepository.setPurchased(spaceId, itemId, purchased, pantryEntry: PantryEntry? = null)`: en **la misma transacción**, al marcar comprado, si la entrada de la despensa **no existe** la crea con `shoppingItemId = itemId`; si existe, no la toca.
- Lo que no se reconoce como ingrediente no entra.

### 3b. Desmarcar un comprado

En la misma transacción de `setPurchased(…, false)`: si la entrada `pantryId(nombre del artículo)` existe y su `shoppingItemId` es ese artículo, se borra (no hace falta el catálogo: un nombre reconocido y su nombre canónico tienen la misma clave tolerante y, por tanto, el mismo id). Un toque por error no deja «hay» en la despensa. Si la entrada ya estaba antes de la compra (otro `shoppingItemId` o `null`), no se toca. «Limpiar comprados» no toca la despensa.

### 3c. Cocinar → «Has cocinado …»

El diálogo actual «Tachar de la compra» (todo o nada) se sustituye por **uno solo**, «Has cocinado Lentejas», que sale si alguna de sus secciones tiene filas:

- **«Tachar de la compra»:** los artículos **pendientes** de la receta (como hoy), con casillas **marcadas** por defecto. Tachar uno lo marca como comprado por el camino de 3a (`MealsViewModel` usa `pantryEntryFor` con los datos de `IngredientShoppingSource`, que ya tiene), así que entra en la despensa si es un ingrediente reconocido. Si se ha gastado todo, se desmarca y sigue pendiente en la lista.
- **«¿Se ha acabado algo?»:** los ingredientes de la receta que hay en la despensa (leída del servidor con `currentIds`), con casillas **desmarcadas**. Lo marcado sale de la despensa.
- Casilla **«Apuntar a la compra lo que se ha acabado»**, marcada por defecto: apunta lo marcado en la sección anterior como en la sección 2 (repetidos, marca y tienda por defecto, sin cantidad).
- Botones «Listo» y «Cancelar».
- Un ingrediente sale en una sola sección: si está pendiente en la lista, va a «Tachar de la compra».
- Si la despensa no se puede leer, la sección «¿Se ha acabado algo?» no sale (como hoy con la lista).

Las filas y lo que hay que escribir salen de una función pura (testeable en la JVM).

## 4. La Compra y las recetas

### 4a. Revisión de «Añadir a la compra» (`IngredientReview.kt`)

- `ReviewRowKind` gana **`IN_PANTRY`**: pista «Hay en la despensa» y selector **Omitir / Añadir**, con **Omitir** marcado. Con **Añadir**, la fila se comporta como hoy: `NEW` (cantidad, marca y tienda) o, si hay un comprado, como `PURCHASED` con «Volver a apuntar».
- **Precedencia** al construir las filas (`buildIngredientReviewRows`, que recibe los ids de la despensa): pendiente en la lista → en la despensa → comprado → nueva.
- Recetario y Comidas leen la despensa del servidor al abrir la revisión (`currentIds`). Si falla, se sigue sin ella (ninguna fila `IN_PANTRY`).
- Si todo queda en Omitir, el aviso pasa de «Ya estaba todo en la lista» a **«No hace falta añadir nada»** (`meals_notice_nothing_to_add` y `recipes_notice_nothing_to_add`).

### 4b. Alta a mano en la Compra

Al **crear** (también desde la ficha de ingrediente), si el nombre está en la despensa sale la pista pasiva «Hay en la despensa» junto a la de «Ya en la lista». No bloquea nada. `ShoppingViewModel` escucha la despensa del espacio.

### 4c. Recetario: «Con lo que hay»

- `FilterChip` «Con lo que hay» al principio de la fila de filtros, independiente de la categoría. Solo sale si hay espacio activo; el Recetario escucha la despensa de ese espacio.
- **Activo:** solo las recetas con ingredientes, ordenadas por cuántos faltan y después por título; cada fila lleva una línea:
  - «Lo tienes todo»;
  - «Falta: huevos»;
  - «Faltan 2: huevos y leche»;
  - «Faltan 5: huevos, leche y 3 más».
- Se combina con el buscador, la categoría y las secciones «Mis recetas» / «Comunes».
- Desde la ficha, «Añadir a la compra» ya omite lo que hay (4a): funciona como «añadir lo que falta».
- **Función pura** `missingFromPantry(recipe, pantryIds): List<String>` (nombres de los ingredientes que faltan, sin repetir por clave, en el orden de la receta), compartida por el Recetario y el selector, y la del texto de la línea.
- La navegación desde «¿Qué cocino?» abre el Recetario con el chip activado.

### 4d. Selector de receta del planificador (`RecipePicker.kt`)

El mismo chip «Con lo que hay» junto al buscador, con el mismo orden y la misma línea por fila. Sin el chip, se queda como está («Mías» → «Comunes»).

## 5. Privacidad y documentación

- `PRIVACY_POLICY.md`: la despensa la comparten los miembros del espacio y se borra con el espacio.
- `docs/DATA_MODEL.md`: sección «Despensa» (ruta, campos, id, reglas, comprado → despensa y su deshacer) y el §14 «Eliminación de espacio» con `notes`, `activities` y `pantryItems`.
- `docs/FUNCTIONAL_SPEC.md` y `docs/USER_FLOWS.md`: el módulo y sus flujos.
- `PROJECT_PLAN.md`: una fila de decisión («Despensa: solo hay/no hay, por espacio, se mantiene con la Compra y al cocinar»).

## 6. Pruebas

| Suite | Qué |
|---|---|
| JVM | `pantryId` (plurales, mayúsculas, palabras vacías, vacío); agrupar y ordenar la lista («Otros» al final); selector múltiple (marcados, deshabilitados, fila de texto libre); «Se acabó» → compra (pendiente, comprado, nuevo con marca y tienda por defecto); `pantryEntryFor` (común, personal, plural, no reconocido) y su uso al marcar comprado; `missingFromPantry` y el texto de la línea; precedencia de las filas de revisión e `IN_PANTRY` (Omitir, Añadir como nueva o como comprado); filas y escrituras del diálogo de cocinar; ViewModels con dobles a mano (Despensa, Compra, Recetario, Comidas) |
| EMU (`tests/firebase-emulators.test.mjs`) | miembro lee, crea y borra; no miembro, nada; `update` prohibido; forma (`hasOnly`, límites, `createdBy`, `createdAt`, id con formato de slug); transacción comprado + alta en la despensa y su deshacer; borrar un espacio vacía `pantryItems`, `notes` y `activities` |
| Punta a punta (emulador Android) | llenar la despensa → comprar un ingrediente y uno que no lo es → solo entra el ingrediente → desmarcar → sale → «Se acabó» → «A la compra» → Recetario «Con lo que hay» → «Añadir a la compra» con filas «En la despensa» → planificar con el chip → cocinar → «¿Se ha acabado algo?»; capturas en claro y oscuro |

## 7. Planes, en este orden

Dos **planes grandes**, cada uno en su rama, mergeado en main antes del siguiente, y cerrado con revisión final (Opus), simplify, code-review high y punta a punta en emulador.

1. **Despensa** (`agent/despensa`): secciones 1, 2, 3a, 3b y 5 —
   - `pantryId`, `PantryItem`/`PantryEntry`, `PantryRepository` con su implementación y dobles;
   - reglas y EMU;
   - borrar un espacio vacía `pantryItems`, `notes` y `activities`;
   - pantalla Despensa (lista, «Se acabó» → «A la compra» con su función compartida, vacía, errores) y destino en «Más»;
   - selector múltiple;
   - `pantryEntryFor` y comprado → despensa con su deshacer;
   - privacidad y documentación.
2. **Despensa en recetas y compra** (`agent/despensa-recetas`): secciones 3c y 4 —
   - fila `IN_PANTRY` en la revisión (Recetario y Comidas) y el aviso nuevo;
   - pista en el alta a mano de la Compra;
   - diálogo «Has cocinado …»;
   - `missingFromPantry`, chip del Recetario y «¿Qué cocino?» con el chip activado;
   - chip del selector del planificador.

## 8. Despliegue (fuera de este trabajo)

Aplazado por el usuario. **Las reglas cambian:** hay que desplegarlas (`npx firebase deploy --only firestore:rules --project dev`) **antes** de distribuir una beta que lleve la Despensa; sin ellas, la beta no puede leer ni escribir `pantryItems`. Pueden ir en el mismo despliegue aplazado del tercer plan de menores (reglas → beta 23), cada paso confirmado por el usuario (ver las trampas de la beta: `--project dev`, JAVA_HOME de Android Studio, `beta:distribute` sube el APK viejo si falla el build). No hay datos que importar.

## Fuera de alcance

Cantidades; caducidades y avisos; «queda poco»; editar una entrada; vaciar la despensa entera; mostrar quién apuntó cada cosa o cuándo; una lista de «qué puedo cocinar» propia de la Despensa; ocultar en «Con lo que hay» las recetas a las que les faltan muchos ingredientes; restar al cocinar; básicos que cuentan como presentes sin apuntarlos; despensa personal (fuera del espacio); que entren solos los artículos que no son ingredientes.

## Verificación de esta spec

Cada decisión (1–10) tiene sección: 1→Contexto, 4a, 4c, Fuera de alcance; 2→1; 3→1; 4→3a–3c, 2; 5→3a; 6→4a; 7→4c, 4d; 8→1; 9→1 («Borrar un espacio»); 10→7, 8. Piezas reutilizadas: `slug`, `ingredientKey`, `shoppingMatchKey`, `findShoppingMatch`, `BrandStoreSelection`, `IngredientReview.kt` (`buildIngredientReviewRows`, `ReviewRowKind`), `currentItems` como modelo de `currentIds`, `deleteDocumentsInChunks`, `isActiveMember`, `EmptyState`, `ModuleCard`, `CategoryFilter` del Recetario, `RecipePicker.kt`.
