# Catálogo común de ingredientes

El catálogo común de ingredientes (`ingredients/{slug}`, colección top-level global) se siembra con unos 340 ingredientes genéricos de una cocina española, de una lista propia de Bobitos. Este documento explica cómo se edita, se revisa, se importa y se borra para empezar de cero.

## Qué es

- Los **genéricos comunes** («Tomate», «Leche entera», «Garbanzos»…) los crea **solo el admin**, con el importador. No llevan marca, ni nutrición, ni dueño.
- Las **marcas**, las **tiendas** y los **ingredientes personales** son de cada usuario (`users/{uid}/…`). Son los planes 2 y 3; este documento no los cubre.
- El diseño completo está en [`superpowers/specs/2026-10-11-catalogo-ingredientes-design.md`](superpowers/specs/2026-10-11-catalogo-ingredientes-design.md).

## Fuente y licencia

- Fuente: lista propia de Bobitos. No hay datos de terceros ni de nutrición.
- BEDCA se descartó por sus condiciones de uso.
- La licencia está en `data/catalog/LICENSE.md` (sección «Ingredientes»).

## Estructura de `data/catalog/`

| Fichero | Qué es | ¿Se edita a mano? |
| --- | --- | --- |
| `ingredients.json` | La lista, la que se importa a Firestore. | Sí |
| `ingredients-review.md` | Informe legible para revisar la lista. | No (`catalog:ingredients`) |

Formato de `ingredients.json`:

```json
{ "schemaVersion": 1, "ingredients": [{ "id": "chocolate-negro", "name": "Chocolate negro", "category": "Dulces y chocolate", "defaultUnit": "g" }] }
```

- Cada entrada tiene exactamente `id`, `name`, `category` y `defaultUnit`.
- El `id` es el *slug* de `name` (`slug()` de `scripts/catalog/normalize.mjs`, igual que el de la app). Cambiar un nombre cambia el id, así que se importaría como ingrediente nuevo y el antiguo quedaría huérfano.
- Orden: por categoría (en el orden de `INGREDIENT_CATEGORIES`) y después por nombre, con `Intl.Collator("es")`.
- `ingredients-review.md` tiene el recuento por categoría y las tablas de nombre, unidad e id. Se genera y no se edita.

## Categorías y unidades

Las 14 categorías, en este orden (`INGREDIENT_CATEGORIES` en `scripts/catalog/ingredients.mjs`):

1. Frutas
2. Verduras y hortalizas
3. Carnes
4. Pescados y mariscos
5. Lácteos y huevos
6. Legumbres
7. Cereales, pasta y arroz
8. Panadería
9. Aceites, salsas y condimentos
10. Especias y hierbas
11. Frutos secos
12. Dulces y chocolate
13. Bebidas
14. Congelados y otros

Las 3 unidades (`defaultUnit`): `g`, `ml` y `ud`. El nombre tiene entre 1 y 120 caracteres, sin espacios de más y empezando por mayúscula.

Criterios para añadir ingredientes:

- **Nombre:** el que se apunta en la lista de la compra en España. Singular para piezas («Tomate», «Cebolla», «Limón», «Pimiento rojo»). Plural cuando es lo habitual («Garbanzos», «Lentejas», «Espaguetis», «Huevos», «Guisantes»).
- **Genérico, sin marcas ni formatos comerciales:** «Chocolate negro», no «Chocolate 85 % Lindt». Las variantes solo si se compran por separado («Leche entera», «Leche semidesnatada», «Pan de molde», «Barra de pan»).
- **Unidad:** `ud` lo que se compra por piezas (frutas y verduras sueltas, huevos, barra de pan, latas, yogures). `ml` los líquidos (leche, aceites, vinagre, bebidas, caldos). `g` todo lo demás.
- **Categoría:**
  - embutidos y fiambres → «Carnes»;
  - conservas de pescado → «Pescados y mariscos»;
  - conservas vegetales y tomate triturado → «Verduras y hortalizas»;
  - harinas y levadura → «Cereales, pasta y arroz»;
  - azúcar, miel, cacao y mermelada → «Dulces y chocolate»;
  - sal, vinagre, caldos, mayonesa y tomate frito → «Aceites, salsas y condimentos»;
  - café, té, infusiones y zumos → «Bebidas»;
  - congelados, tofu, seitán, levadura nutricional y lo que no encaje → «Congelados y otros».

La validación (`npm run catalog:ingredients`) también rechaza ids repetidos y **casi duplicados** («Limón» y «Limones», «Tomate» y «Tomates»). Las variantes («Leche entera» frente a «Leche semidesnatada», «Pan» frente a «Pan de molde») no cuentan como duplicados.

**Añadir una categoría:** cambiar `INGREDIENT_CATEGORIES` en `scripts/catalog/ingredients.mjs` y, en el plan 2, la lista equivalente de la app (mismo texto y mismo orden). Después, regenerar la revisión.

## Editar el catálogo

1. Edita `data/catalog/ingredients.json`.
2. Ejecuta `npm run catalog:ingredients`. Valida la lista y escribe `ingredients-review.md`. Si hay problemas, los imprime (uno por línea, con el id) y termina con error sin escribir nada.
3. Revisa el diff de `data/catalog/ingredients-review.md`.
4. Importa (ver más abajo).

## Importar

El importador es `scripts/catalog/import-ingredients.mjs` (`npm run catalog:import-ingredients --`). Usa `firebase-admin`, que se salta las reglas de seguridad.

```text
node scripts/catalog/import-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
```

- `--project`: `demo-bobitos` (emulador), `bobitos-dev` o su alias `dev`. Cualquier otro valor se rechaza.
- `--apply`: sin él solo **simula**: lee Firestore, imprime el plan y no escribe nada.
- `--catalog`: ruta alternativa al JSON (por defecto `data/catalog/ingredients.json`).

Valida el JSON antes de leer nada de Firestore. Es idempotente y **nunca borra**. Escribe por lotes de 400, y las actualizaciones llevan precondición de `lastUpdateTime` (si alguien edita el documento entre la lectura y la escritura, falla en lugar de pisarlo). Cada documento común lleva `name`, `nameLower`, `category`, `defaultUnit`, `source` (`provider` = `bobitos` e `importedAt`), `updatedAt` y `updatedBy` (el uid admin), y `createdAt` solo al crear. No lleva `ownerUid`.

### En el emulador

```bash
npm run emulators            # en otra terminal
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-ingredients -- --project demo-bobitos [--apply]
```

Un proyecto `demo-*` exige `FIRESTORE_EMULATOR_HOST`.

### En `bobitos-dev`

1. Usa la clave de cuenta de servicio guardada **fuera del repositorio**, en `~/.config/bobitos/bobitos-dev-adminsdk.json`. Nunca se versiona ni se pega en ningún sitio.
2. Simula y revisa el plan:
   ```bash
   GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-ingredients -- --project dev
   ```
3. Si es lo esperado, repite el mismo comando añadiendo `--apply`.

Las protecciones son las mismas que en ejercicios (`scripts/catalog/admin-cli.mjs`): `bobitos-dev` se rechaza si `FIRESTORE_EMULATOR_HOST` está definida, y exige una clave cuyo `project_id` sea `bobitos-dev`.

### Qué hace con cada documento existente

| Situación del documento en Firestore | Qué hace el importador |
| --- | --- |
| No existe | **Crea** el ingrediente. |
| Existe, importado, sin editar en la app y con datos distintos | **Actualiza** los campos gestionados (`name`, `nameLower`, `category`, `defaultUnit` y `source.provider`). |
| Existe, importado y con los mismos datos | **Sin cambios**. |
| Existe con el mismo id pero con `ownerUid` de **otro usuario** (documento antiguo) | **Omite** (de usuario); no lo toca. |
| Existe del admin pero **sin `source`** (creado a mano) | **Omite** (manual del admin). |
| Importado y **guardado en la app** después (`updatedAt` posterior a `source.importedAt`) | **Omite** (editado en la app); queda congelado. |
| Importado, pero **ya no está en el JSON** | Lo **lista como huérfano**; no lo borra. |
| Nombre casi igual al de otro documento existente con distinto id | Lo crea y lo señala como **posible duplicado**. |

Al terminar con `--apply` imprime cuántos ha creado y actualizado. Si no hay nada que escribir, no sube la versión y la reimportación no hace ninguna escritura.

## Empezar de cero (`catalog:reset-ingredients`)

`scripts/catalog/reset-ingredients.mjs` (`npm run catalog:reset-ingredients --`) es de **un solo uso**: deja los ingredientes en blanco antes de la primera importación.

```text
node scripts/catalog/reset-ingredients.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--repetir]
```

**Qué borra:**

- toda la colección `ingredients`, con sus subcolecciones `brands` (también las que cuelgan de documentos padre sin datos);
- toda la colección `ingredientPrefs`.

**Qué no borra:** nada más. No toca `users/{uid}/…`, `exercises`, `exerciseImages`, `catalogMeta` (tampoco `catalogMeta/ingredients`), `recipes` ni ninguna otra ruta.

Cómo se usa:

1. Primero la **simulación** (sin `--apply`): cuenta ingredientes, marcas y preferencias y no escribe nada.
2. Si los recuentos son los esperados, repetir con `--apply`.
3. Se ejecuta **una sola vez**: después de desplegar las reglas del plan 2 y justo antes de la primera importación. Tiene las mismas protecciones de proyecto que el importador.

**Protección contra repetirlo:** si existe `catalogMeta/ingredients` (el catálogo ya se importó), `ingredientPrefs` guarda las tiendas de los usuarios por ingrediente y borrarlas las perdería (además dejaría el catálogo vacío sin subir su versión). Por eso la simulación imprime un aviso y `--apply` se niega a borrar y falla. Solo se fuerza con `--repetir`, y únicamente si de verdad se quiere perder esos datos.

## Orden de despliegue

Cada acción contra `bobitos-dev` la confirma el usuario una a una, y solo se despliega cuando están los tres planes.

1. **Reglas:** `npx firebase deploy --only firestore:rules --project dev`.
2. **Borrado:** `catalog:reset-ingredients`, primero la simulación y después `--apply`.
3. **Importación:** `catalog:import-ingredients`, primero la simulación y después `--apply`. Se espera la versión 1 con unos 340 ingredientes.
4. **Beta 19**, justo después: la beta 18 deja de poder crear ingredientes comunes, marcas y preferencias con las reglas nuevas.

## Versión del catálogo y caché

- El documento `catalogMeta/ingredients` (`version`, `updatedAt`, `updatedBy`) sube con **cada lote que escribe algo**, y se crea en 1 si no existe. Una importación sin cambios no lo toca, y la simulación tampoco.
- La app (plan 2) lee ese documento con `VersionedCatalogLoader` y la clave `"ingredients"`: si la versión coincide con la guardada, usa la caché local y no relee el catálogo.

## Coste en Firestore (plan Spark)

- Primera importación: unas 340 escrituras de ingredientes más 1 de la versión, y una lectura de la colección (vacía tras el borrado).
- Reimportar sin cambios: 0 escrituras.
- Cada dispositivo: una lectura de unos 340 documentos cuando cambia la versión; sin cambios, solo la lectura de `catalogMeta/ingredients`.
- El borrado cuesta una lectura por documento más una escritura (borrado) por documento, una sola vez.

## Resolución de problemas

| Mensaje | Causa y solución |
| --- | --- |
| `N problemas en data/catalog/ingredients.json.` (tras una lista de `- …`) | La lista tiene errores; corrige cada punto y repite `npm run catalog:ingredients`. |
| `Catálogo inválido:` (en el importador) | `ingredients.json` no pasa la validación; ejecuta `npm run catalog:ingredients`. |
| `Un proyecto demo-* exige FIRESTORE_EMULATOR_HOST (emulador).` | Arranca `npm run emulators` y exporta `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`. |
| `FIRESTORE_EMULATOR_HOST está definida: no se importa a bobitos-dev.` | Haz `unset FIRESTORE_EMULATOR_HOST`. |
| `Falta GOOGLE_APPLICATION_CREDENTIALS (clave de cuenta de servicio fuera del repo).` | Exporta la ruta de la clave JSON de `bobitos-dev`. |
| `La clave es del proyecto «X», no de bobitos-dev.` | La clave es de otro proyecto; usa la de `bobitos-dev`. |
| `Usa --project demo-bobitos, bobitos-dev o dev.` | Falta `--project` o su valor no es válido. |
| `Argumento desconocido: ...` | Solo existen `--project`, `--apply` y, en el importador, `--catalog`; en el borrado, `--repetir`. |
| `El catálogo de ingredientes ya se importó ... No se ha borrado nada. Para forzarlo, añade --repetir.` | El borrado inicial es de un solo uso y destruiría las tiendas de los usuarios (`ingredientPrefs`). No lo repitas; solo con `--repetir` si se quiere perder esos datos. |
| Error de precondición (`FAILED_PRECONDITION`) al aplicar | Alguien editó un documento entre la lectura y la escritura; repite la simulación y el `--apply`. |

El uid admin con el que se importa (`CATALOG_ADMIN_UID` en `scripts/catalog/import-plan.mjs`) es el mismo que en el catálogo de ejercicios; ver [`EXERCISE_CATALOG.md`](EXERCISE_CATALOG.md) y [`RECIPES_ADMIN.md`](RECIPES_ADMIN.md).
