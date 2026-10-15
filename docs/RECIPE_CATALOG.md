# Catálogo común de recetas

El catálogo común de recetas (`recipes/{slug}`) se siembra con 160 recetas caseras de una lista propia de Bobitos. Este documento explica cómo se edita, se valida y se importa.

## Qué es

- 160 **recetas comunes** (`recipes/{slug}`, `visibility: GLOBAL`, dueño el admin del catálogo, autor «Catálogo Bobitos») que viven en la **misma colección** que las recetas personales de los usuarios. Las crea **solo el admin**, con el importador.
- Doce categorías: once por tipo de plato (Legumbres, Arroces, Pasta, Carnes, Pescados, Verduras, Ensaladas, Huevos, Sopas y cremas, Desayunos y meriendas, y Postres) y «Saludables».
- «Saludables» reúne platos ricos en proteína o ligeros, para quien entrena o quiere comer ligero: a la plancha, al horno, al vapor, en papillote o salteados, sin fritos, rebozados ni nata. No guardan datos de nutrición: la entradilla dice para qué sirven («buena después de entrenar», «ideal para táper», «cena ligera») sin cifras. Las recetas ligeras de la primera tanda siguen en su categoría por tipo de plato.
- Cada ingrediente usa el nombre de un ingrediente del catálogo común (`ingredients.json`), para que «Añadir a la compra» reconozca la marca y la tienda, y una unidad de una lista cerrada, para que las cantidades se puedan sumar.
- El diseño completo está en [`superpowers/specs/2026-10-12-recetario-y-compra-design.md`](superpowers/specs/2026-10-12-recetario-y-compra-design.md) (parte C). La segunda tanda (100 recetas y «Saludables», 2026-10-15) siguió el mismo formato.

## Fuente y licencia

- Fuente: lista propia de Bobitos. No hay datos de terceros.
- La licencia está en `data/catalog/LICENSE.md` (sección «Recetas»).

## Estructura de `data/catalog/`

| Fichero | Qué es | ¿Se edita a mano? |
| --- | --- | --- |
| `recipes.json` | La lista, la que se importa a Firestore. | Sí |
| `recipes-review.md` | Informe legible para revisar la lista. | No (`catalog:recipes`) |

## Formato

```json
{
  "schemaVersion": 1,
  "recipes": [
    {
      "title": "Lentejas estofadas",
      "category": "Legumbres",
      "description": "Las de toda la vida, con chorizo y verduras.",
      "servings": 4,
      "totalMinutes": 60,
      "ingredients": [
        { "name": "Lentejas pardinas", "quantity": "300", "unit": "g" },
        { "name": "Ajo", "quantity": "2", "unit": "dientes" },
        { "name": "Sal" }
      ],
      "steps": ["Pon las lentejas en una olla…", "…"]
    }
  ]
}
```

- Todas las claves de la receta son obligatorias. El `id` del documento es el *slug* del `title` (`slug()` de `scripts/catalog/normalize.mjs`, igual que el de la app): cambiar el título crea otra receta y deja huérfana la anterior.
- `totalMinutes` no cuenta el remojo ni el tiempo de nevera; si la receta los necesita, la entradilla (`description`) lo avisa.
- Un ingrediente sin `quantity` es «al gusto» y no lleva `unit`. El agua no se lista.

## Unidades

Si hay cantidad, la unidad es obligatoria y de esta lista: `g`, `kg`, `ml`, `l`, `ud` (no cambian) y cucharada, cucharadita, pizca, diente, rebanada, hoja y ramita, en singular si la cantidad vale 1 o menos («1 diente», «1/2 cucharadita») y en plural si pasa de 1 («2 dientes»). La cantidad es texto: un entero, un decimal con coma («1,5») o una fracción («1/2»), mayor que cero. La tabla está en `tests/fixtures/recipe-units.json` y un test de la app comprueba que la Compra suma todas.

## Validación

`npm run catalog:recipes` valida la lista y escribe `recipes-review.md`. Si hay problemas, los imprime y termina con error sin escribir nada. El importador aplica la misma validación antes de leer Firestore. Reglas:

- cada ingrediente existe en `ingredients.json` (con la clave tolerante `ingredientKey`: «Ajos» vale por «Ajo») y se escribe con el nombre del catálogo; si no existe, el error sugiere el más parecido; una receta no repite un ingrediente;
- categoría de las once; título de 1 a 120 caracteres y con mayúscula inicial; títulos (e ids) únicos;
- límites (`RECIPE_LIMITS` en `scripts/catalog/recipes.mjs`): entradilla 1–1000 caracteres, 1–50 ingredientes, 1–30 pasos de hasta 500 caracteres, `servings` 1–50, `totalMinutes` 1–1440.

Si una receta necesita un ingrediente genérico que no está en el catálogo, se añade a `ingredients.json` (ver [`INGREDIENT_CATALOG.md`](INGREDIENT_CATALOG.md)) y se importan **los ingredientes antes que las recetas**.

## Revisión

`recipes-review.md` agrupa las recetas por categoría, con su id, raciones y tiempo, la entradilla, los ingredientes («Lentejas pardinas — 300 g», «Sal — al gusto») y los pasos numerados. Se revisa antes de cualquier importación.

## Importar

El importador es `scripts/catalog/import-recipes.mjs` (`npm run catalog:import-recipes --`). Usa `firebase-admin`, que se salta las reglas de seguridad. Admite `--project demo-bobitos|bobitos-dev|dev`, `--apply` (sin él solo **simula**) y `--catalog ruta`. Las reglas de `recipes` que admiten `steps`, `servings`, `totalMinutes` y `source` tienen que estar desplegadas antes.

### En el emulador

```bash
npm run emulators            # en otra terminal
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-recipes -- --project demo-bobitos [--apply]
```

### En `bobitos-dev`

```bash
GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-recipes -- --project dev [--apply]
```

Primero la simulación; si el plan es el esperado, repetir con `--apply`. Las protecciones de proyecto son las de los demás importadores (`scripts/catalog/admin-cli.mjs`).

### Qué hace con cada documento existente

- **Crea** las recetas que no existen y **actualiza** las importadas cuyo contenido ha cambiado; las idénticas quedan **sin cambios**.
- **Omite** las recetas de **usuarios** con el mismo id, las comunes del admin **sin `source`** (creadas a mano) y las comunes **editadas en la app** después de importarse.
- **Nunca borra.** Las **huérfanas** (ya no están en el JSON) y las «**Comunes creadas a mano en la app**» (GLOBAL del admin sin `source` y con otro id) solo se informan, para que el usuario decida.
- **No usa `catalogMeta`**: el Recetario lee las recetas en vivo, así que no hay versión de catálogo que subir.

### Avisos

- Editar una común importada en la app (aunque sea guardarla sin cambios) la **congela**: el importador ya no la actualiza.
- Borrar una común solo en la app no basta: la siguiente importación la vuelve a crear mientras siga en `recipes.json`. Para retirarla, quítala también del JSON.
- «Deshacer» tras borrar una común en la app la recrea **tal como era**: mismo id, dueño, autor, fechas y `source`, aunque la deshaga otro admin. El importador la sigue tratando como antes del borrado. Las comunes que se deshicieron antes de la beta 22 quedaron sin `source` y el importador las omite como «ficha manual del admin»: para devolverlas al catálogo, bórralas en la app sin deshacer y vuelve a importar.
