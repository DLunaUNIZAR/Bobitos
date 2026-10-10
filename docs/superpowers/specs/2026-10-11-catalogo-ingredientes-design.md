# Catálogo de ingredientes con marcas personales — Spec de diseño

> Resultado de `superpowers:brainstorming` (camino **arquitectónico**), aprobado por el usuario el 2026-10-11. Los planes de implementación salen de esta spec con `superpowers:writing-plans` y se ejecutan con `delegating-plan-execution`.

## Contexto

El catálogo común de ingredientes está vacío, y el usuario lo considera el principal problema de la app (lo mismo pasaba con Ejercicios, ya resuelto con wger). La idea del usuario (2026-10-10):
- Ingredientes **genéricos y comunes**, como «Chocolate negro».
- Cada usuario tiene **sus marcas** (las que escanea), **sus favoritas** y **dónde comprarlas**.
- **Al añadir a la compra** se elige entre las marcas disponibles o «Indiferente».

**Hoy:**
- Ya existe un catálogo común, `ingredients/{slug}`, pero vacío. Cualquier usuario verificado puede crear ingredientes en él. Tiene una escucha de 500 documentos y no tiene importador ni caché por versión.
- Las marcas (`ingredients/{id}/brands`) son **comunes**: todos ven todas.
- Lo único personal es `ingredientPrefs/{uid}`, que guarda un súper y una marca en texto por ingrediente.
- La Compra (`spaces/{id}/shoppingItems`) guarda la marca como texto (60 caracteres como máximo) y una tienda del enum `Supermarket`.
- No se puede añadir a la compra desde Ingredientes.
- El diálogo de revisión de recetas y comidas no deja elegir marca.

## Decisiones del usuario

1. **Marcas solo del usuario:** cada uno ve únicamente las que ha escaneado o creado.
2. **Fuente de los genéricos:** una **lista propia** de unos 350 ingredientes, revisada por el usuario y **sin nutrición**. La nutrición sigue en las marcas.
3. **Ingredientes comunes:** los crea y edita **solo el admin** (el importador y el admin desde la app). Cada usuario puede crear ingredientes **personales**.
4. **Tiendas:** cada marca puede tener **varias**.
5. **Elección de marca en los tres sitios:** «Añadir» en la Compra, el diálogo de revisión de recetas y comidas, y la ficha del ingrediente.
6. **Marca que sale elegida:** la **favorita, si solo hay una**; si hay varias o ninguna, «Indiferente».
7. **Datos de la beta: empezar de cero.** Se borran los ingredientes, marcas y preferencias actuales de `bobitos-dev`.
8. **Enfoque A:** cada usuario tiene su propio apartado `users/{uid}/…`.
9. **«Dónde lo compro»:** las tiendas van **por ingrediente y por marca**. Si hay marca elegida, mandan las suyas; con «Indiferente», o si la marca no tiene tiendas, se usan las del ingrediente.

## 1. Modelo de datos

### Catálogo común: `ingredients/{slug}`

- **Campos:** `name` (≤120), `nameLower`, `category` (de la lista cerrada), `defaultUnit` (≤24), `source {provider: "bobitos", importedAt}` (solo lo escribe el importador), `createdAt`, `updatedAt` y `updatedBy`.
- **El id** es `slug(name)`, la función que ya existe en `core/model/CatalogIngredient.kt` y en `scripts/catalog/normalize.mjs`.
- **Versión:** `catalogMeta/ingredients {version, updatedAt}`. Sube cuando importa el importador o edita el admin, en la misma transacción, igual que en `FirestoreExerciseRepository.kt:93-155`.
- **Carga en la app:** con `VersionedCatalogLoader` (`data/repository/VersionedCatalog.kt`) y la clave `"ingredients"` en `CatalogSyncStore`. La fuente sigue el modelo de `FirestoreExerciseCatalogSource.kt`, con `limit(1000)`. Sustituye a la escucha de 500.
- **Desaparece** la subcolección `ingredients/{id}/brands`.

### Ingredientes personales: `users/{uid}/ingredients/{slug}`

- **Campos:** `name` (≤120), `nameLower`, `category?` (≤60, texto libre; el editor sugiere las de la lista), `defaultUnit?` (≤24), `createdAt` y `updatedAt`.
- **Choque con un común:** no se puede crear uno con el slug de un ingrediente común.
  - La app lo comprueba contra el catálogo en caché y lleva al común.
  - Las reglas lo impiden con `!exists(/ingredients/$(id))`.
- **Si después aparece un común con el mismo slug:**
  - en las listas gana el común y el personal se oculta;
  - las marcas siguen viéndose, porque se enlazan por slug.

### Marcas personales: `users/{uid}/brands/{autoId}`

- **Campos:**
  - `ingredientId`: el slug (≤120) de un ingrediente común o personal;
  - `name`: 60 caracteres como máximo, el mismo límite que en la Compra, para que nunca se corte;
  - `barcode?` (≤32);
  - la nutrición por 100 g/ml, opcional: `energyKcal`, `fat`, `carbohydrates`, `sugars`, `protein` y `salt`;
  - `favorite` (bool);
  - `stores`: lista de valores de `Supermarket`, sin `INDIFERENTE` y sin repetidos (6 como máximo), en el orden de preferencia;
  - `createdAt` y `updatedAt`.
- **Carga:** una escucha de todas tus marcas, con `limit(1000)`.

### Dónde lo compro: `ingredientPrefs/{uid}`

- Se reutiliza el documento con **una forma nueva**: `entries: { slug → {stores: [Supermarket…]} }`, con las mismas restricciones de tienda que en las marcas y 2000 entradas como máximo.
- Vale para ingredientes comunes y personales, y solo lo ve su dueño.
- Al leer, las entradas con la forma antigua (`supermarket` y `brand`) se ignoran. De todos modos, el borrado inicial las elimina.

### Compra

**Sin cambios de esquema ni de reglas.** La marca elegida se guarda como texto en `brand`, y «Indiferente» se guarda como `null`. La tienda va en `supermarket`.

### Borrar un ingrediente personal

Un lote borra el ingrediente, sus marcas y su entrada en `ingredientPrefs`.

### Limitación aceptada

Si el admin borra un ingrediente común, las marcas que los usuarios tengan de él quedan **sin ingrediente**: no se muestran, pero se borran junto con la cuenta. Queda documentado.

## 2. Reglas (`firestore.rules`)

- **`ingredients/{id}`:**
  - `get` y `list` para usuarios verificados;
  - crear, editar y borrar, solo con `isRecipeAdmin()`, la allowlist que ya existe;
  - la forma se valida con `hasOnly`;
  - `source` no se puede escribir desde el cliente.
- **`ingredients/{id}/brands`:** cerrada.
- **`catalogMeta/{catalogId}`:** admite `exercises` e `ingredients`.
- **`users/{uid}/ingredients/{id}`:**
  - solo el dueño verificado;
  - forma validada;
  - el `id` tiene que ser el slug del nombre (en el cliente; en las reglas, solo el formato);
  - al crear, `!exists` del común.
- **`users/{uid}/brands/{id}`:**
  - solo el dueño verificado;
  - forma validada: tiendas dentro del enum, lista de 6 como máximo, `favorite` bool y los números con `optionalNumberField`.
- **`ingredientPrefs/{uid}`:** igual que hoy (dueño, `entries` es un mapa con 2000 entradas como máximo).

## 3. Catálogo e importador (scripts)

### Contenido

- **Fichero:** `data/catalog/ingredients.json`, con unos 350 genéricos de una cocina española.
- **Categorías:** una lista cerrada de 14: Frutas · Verduras y hortalizas · Carnes · Pescados y mariscos · Lácteos y huevos · Legumbres · Cereales, pasta y arroz · Panadería · Aceites, salsas y condimentos · Especias y hierbas · Frutos secos · Dulces y chocolate · Bebidas · Congelados y otros.
- **Unidades:** `g`, `ml` o `ud`.
- **Revisión:** `data/catalog/ingredients-review.md` es la **parada**: lo revisa el usuario antes de importar.
- **Validación:** sin duplicados por slug ni casi duplicados (se reutiliza `nearDuplicateKey`), con la categoría dentro de la lista y la unidad dentro del conjunto.

### Importador

- **Se generaliza `scripts/catalog/import-plan.mjs`:** `CATALOG_META_PATH` y el uid del admin pasan a ser parámetros. Ejercicios sigue funcionando igual.
- **`scripts/catalog/import-ingredients.mjs`** tiene la misma semántica que `import-exercises.mjs`:
  - simulación por defecto y escritura solo con `--apply`;
  - no pisa una ficha editada después de importarla (`updatedAt > source.importedAt`);
  - los ingredientes que sobran se informan, no se borran;
  - sube la versión solo si hay cambios;
  - mantiene las protecciones de proyecto y de emulador.

### Empezar de cero

- **`scripts/catalog/reset-ingredients.mjs`**, de un solo uso: borra todos los `ingredients` con sus `brands` y todos los `ingredientPrefs`.
- Por defecto hace una simulación con los recuentos, y con `--apply` borra por lotes.
- Tiene las mismas protecciones de proyecto que el importador.

## 4. Pantalla Ingredientes

### Lista

- **Contenido:** una sola lista, en orden alfabético del español, con los comunes y tus personales mezclados. Los personales llevan la etiqueta «Personal».
- **Búsqueda:** por nombre y categoría, como hoy.
- **Línea de cada fila:**
  - tu favorita y su primera tienda: «★ Valor · Mercadona»;
  - si no tienes favorita, «N marcas»;
  - si no tienes marcas, la primera tienda del ingrediente;
  - si no tienes nada, ninguna línea.
- **«Nuevo ingrediente»:** crea uno personal. Si eres admin, el formulario tiene además el interruptor «Común (catálogo)».

### Escanear (barra superior)

1. Se lee el código con ML Kit y se consulta Open Food Facts, como hoy.
2. Si el código ya está entre tus marcas, se abre la ficha de su ingrediente con esa marca en edición.
3. Si no, sale **«¿De qué ingrediente es?»**: un buscador de comunes y personales, ya relleno con el nombre del producto, más la opción «Crear ingrediente personal».
4. Después se abre el **editor de marca**, relleno con la marca (el primer valor de `brands` de Open Food Facts), el código y la nutrición.
5. Si Open Food Facts falla, se puede seguir con el editor vacío y el código ya puesto.

### Ficha

- **Datos:** categoría y unidad. Puede editarlos y borrarlos el dueño, si el ingrediente es personal, o el admin, si es común.
- **«Dónde lo compro»:** chips de tiendas de selección múltiple; el orden es el de selección. Se guarda en `ingredientPrefs`.
- **«Mis marcas»:**
  - cada marca muestra su nombre, una ★ que se marca y desmarca de un toque, sus tiendas como chips y la nutrición, plegable;
  - se pueden añadir (a mano o escaneando), editar y borrar.
- **Editor de marca:** nombre, código, tiendas (chips en orden de selección), «Favorita» y la nutrición.
- **«Añadir a la compra»:**
  - navega a la Compra del espacio activo con el formulario de añadir ya relleno: nombre, marca y tienda por defecto;
  - así se reutilizan el aviso de duplicados y la validación;
  - si no hay espacio activo, el botón no aparece.
- **Desaparece** la sección «Tu preferencia».

### Editores

Todos esperan la confirmación del servidor, con el mecanismo común (`EditorSaveStatus` y `withEditorSaveTimeout`).

## 5. Compra

### Lógica pura del selector (testeable en la JVM)

- **Opciones de marca:**
  1. tus favoritas con ★, en orden alfabético;
  2. el resto de tus marcas de ese ingrediente, en orden alfabético;
  3. «Indiferente».
- **Opciones de tienda:**
  1. las de la marca elegida;
  2. las del ingrediente;
  3. el resto de `Supermarket`.
- **Por defecto:**
  - si tienes una sola favorita, sale elegida esa marca y su primera tienda (y si la marca no tiene tiendas, la primera del ingrediente);
  - si tienes varias favoritas o ninguna, sale «Indiferente» con la primera tienda del ingrediente, o ninguna.
- **Al cambiar de marca:** la tienda pasa a la primera de la nueva marca. Si no tiene tiendas y la elegida venía de la marca anterior, pasa a la primera del ingrediente.

### Componente

Dos desplegables en una fila, marca y tienda. Si no tienes marcas del ingrediente, la marca es un campo de texto libre y la tienda sigue siendo un desplegable.

### Dónde aparece

1. **«Añadir» en la Compra:** el selector aparece cuando el nombre corresponde a un ingrediente, al tocar una sugerencia o porque el slug de lo escrito coincide con uno. Para nombres que no son de ningún ingrediente, queda el formulario actual.
2. **Diálogo de revisión** (`feature/common/IngredientReview.kt`): cada fila **nueva** lleva el selector bajo la cantidad. Las filas que ya están en la lista conservan su marca y su tienda, como hoy.
3. **Ficha del ingrediente:** el botón «Añadir a la compra» abre la Compra con el formulario de añadir ya relleno.

### Lo que desaparece

- **Sugerencia por preferencia:** se quitan la fila «Sugerido: … [Aplicar]» y el ajuste automático de `ShoppingViewModel` que la aplicaba (`:148-162`).
- **Menú de cada artículo:**
  - se quita «Guardar como preferencia»;
  - «Crear ficha de ingrediente» se mantiene, pero ahora crea un ingrediente **personal**.

## 6. Privacidad, cuenta y documentación

- **Borrado de cuenta** (`FirebaseAccountRepository`): borra `users/{uid}/ingredients`, `users/{uid}/brands` e `ingredientPrefs/{uid}`. Esto último también arregla un hueco que existe hoy.
- **`PRIVACY_POLICY.md`:** los ingredientes personales, las marcas, los códigos de barras y las tiendas son privados de cada usuario. El catálogo común no contiene datos personales.
- **Documentación:**
  - `docs/DATA_MODEL.md`, con las secciones nuevas y una versión más;
  - `docs/INGREDIENT_CATALOG.md`, nuevo, siguiendo el modelo de `EXERCISE_CATALOG.md`;
  - una fila de decisión en `PROJECT_PLAN.md`.

## 7. Pruebas

- **JVM:**
  - opciones y valores por defecto del selector, y el cambio de marca;
  - mezcla de comunes y personales, con el oculto que coincide por slug;
  - choque de nombre con un común;
  - escaneo de un código que ya tienes;
  - lectura de `ingredientPrefs` con la forma nueva y con la antigua;
  - la línea de cada fila de la lista;
  - los ViewModels con dobles a mano.
- **Reglas en el emulador:**
  - solo el dueño entra en `users/{uid}/…`;
  - un personal no puede tener el slug de un común;
  - el catálogo común solo lo escribe el admin;
  - `source` está prohibido para el cliente;
  - las marcas comunes están cerradas;
  - `catalogMeta/ingredients`;
  - las tiendas fuera del enum y los repetidos se rechazan.
- **Scripts:**
  - el plan de importación generalizado, con Ejercicios sin regresiones;
  - la validación del catálogo;
  - la importación en el emulador: primera vez, reimportación sin cambios, ficha congelada;
  - la simulación y el `--apply` del borrado en el emulador.
- **Punta a punta en el emulador Android**, con capturas en tema claro y oscuro: escanear, elegir el ingrediente, crear la marca, marcar favorita y tiendas, añadir a la compra desde los tres sitios y comprobar la marca y la tienda que salen por defecto.

## 8. Planes, en este orden

Cada uno es un **plan grande**, ejecutado con `delegating-plan-execution` (pasos 1 a 6), en su propia rama.

1. **Catálogo común** (`agent/catalogo-ingredientes`), solo scripts y datos:
   - generalizar el importador;
   - la lista y su validación, con la **parada** de `ingredients-review.md`;
   - `import-ingredients.mjs`;
   - `reset-ingredients.mjs`;
   - la documentación.
2. **Marcas personales y pantalla Ingredientes:**
   - reglas;
   - modelos y repositorios: común con versión, personales, marcas y la forma nueva de las preferencias;
   - borrado de cuenta;
   - lista, ficha, editor de marca, «Dónde lo compro» y escaneo;
   - privacidad y documentación.
3. **Marca al añadir a la compra:**
   - la lógica y el componente del selector;
   - «Añadir» en la Compra;
   - el diálogo de revisión;
   - el botón de la ficha con navegación y formulario relleno;
   - quitar las preferencias de la Compra.

## 9. Despliegue único, al terminar los tres planes

Cada acción la confirma el usuario.

1. `npx firebase deploy --only firestore:rules --project dev`.
2. `reset-ingredients.mjs`: primero la simulación y después `--apply`.
3. `import-ingredients.mjs`: primero la simulación y después `--apply`. Se espera una versión 1 con unos 350 ingredientes.
4. Beta 19, justo después de las reglas, porque la beta 18 deja de poder crear ingredientes comunes, marcas y preferencias.

## Fuera de alcance

- Nutrición de los genéricos y fuentes externas (CIQUAL, BEDCA, Open Food Facts como catálogo).
- Precios.
- Tiendas personalizadas.
- Enlazar por id los ingredientes de las recetas (sigue el slug del nombre).
- Despensa.
- Marcas compartidas con el espacio.

## Verificación de esta spec

- Cada decisión del usuario (1 a 9) tiene su sección.
- Se reutilizan piezas que ya existen:
  - `slug` (Kotlin y Node);
  - `VersionedCatalogLoader` y `CatalogSyncStore`;
  - la transacción con la versión de `catalogMeta`;
  - `import-plan.mjs`;
  - `isRecipeAdmin()`;
  - `optionalNumberField`;
  - el enum `Supermarket` y `SupermarketTokens`;
  - el cliente de Open Food Facts y `BarcodeScanner`;
  - `IngredientReview.kt`;
  - el mecanismo común de los editores.
