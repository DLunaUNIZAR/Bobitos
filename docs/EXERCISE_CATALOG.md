# Catálogo común de ejercicios

El catálogo de ejercicios (`exercises/{exerciseId}`, colección top-level global) se siembra con unos 200 ejercicios de [wger](https://wger.de), en español, con su descripción, material, atribución y, en parte de ellos, una miniatura almacenada en Firestore, más unas pocas fichas propias del proyecto. Este documento explica de dónde salen los datos, cómo regenerarlos, revisarlos e importarlos, y qué esperar de la importación.

El esquema de la colección está en la sección «Ejercicios» de [`DATA_MODEL.md`](DATA_MODEL.md).

## Fuente y licencia

- Fuente: API pública de ejercicios de wger (`https://wger.de/api/v2/exerciseinfo/`), solo el idioma español.
- Licencia del catálogo resultante: **CC BY-SA 4.0** (obra derivada de wger y de sus autores). El texto y la lista de cambios están en `data/catalog/LICENSE.md`.
- Cada ficha conserva su procedencia en el campo `source` (`provider`, `id` de wger, `author`, `license` y `url` de la ficha en wger). Las fichas propias llevan `provider` = `bobitos` (ver «Fichas propias»). La licencia por ficha puede ser `CC-BY-SA-3.0`, `CC-BY-SA-4.0`, `CC-BY-4.0` o `CC0-1.0`.
- La app muestra la atribución en la ficha del ejercicio (y la de la imagen, si la hay) y en **Perfil → Créditos y licencias**.
- Cambios respecto a wger: el HTML de las descripciones pasa a texto plano, las descripciones largas se recortan (hasta 1500 caracteres), los nombres se normalizan y se asignan tipo, grupo muscular y material.

## Estructura de `data/catalog/`

| Fichero | Qué es | ¿Se edita a mano? |
| --- | --- | --- |
| `exercises-selection.json` | Selección curada: qué fichas de wger entran y con qué ajustes. | Sí |
| `exercises.json` | Catálogo generado, el que se importa a Firestore. | No (`catalog:build`) |
| `exercises-review.md` | Informe legible para revisar el catálogo generado. | No (`catalog:build`) |
| `LICENSE.md` | Licencia y cambios realizados. | Rara vez |

`exercises-selection.json` tiene estas claves:

- `include`: lista de `{ "wgerId": 123, ... }`. Los campos opcionales `name`, `type`, `muscleGroup`, `equipment` y `description` sustituyen a lo que se deduce de wger. `measure` (`"SECONDS"`) marca los ejercicios que se miden por tiempo y `image` elige la imagen (ver «Medida» e «Imágenes»).
- `exclude`: lista de `{ "wgerId": 123, "reason": "..." }` para dejar constancia de lo descartado a propósito.
- `custom`: lista de fichas propias del proyecto, con `bobitosId`, `name`, `type`, `muscleGroup`, `equipment`, `description` y, opcionalmente, `measure` (ver «Fichas propias»).
- `notes`: lista de textos que se copian al informe de revisión (carencias de wger, fichas dudosas...).

Los ficheros intermedios (descarga completa de wger y candidatos) viven en `build/catalog/`, que está ignorado por Git.

## Regenerar el catálogo

```bash
npm run catalog:fetch        # descarga todo wger a build/catalog/wger-exerciseinfo.json
npm run catalog:candidates   # build/catalog/candidates.md y candidates.json para elegir
# editar data/catalog/exercises-selection.json
npm run catalog:build        # genera data/catalog/exercises.json y exercises-review.md
```

1. `catalog:fetch` pagina la API (200 por página). Es robusto: reintenta hasta 3 veces los errores de red y los 5xx (con espera creciente), respeta `Retry-After` en un 429 (con un máximo de 60 s), **no reintenta** un 4xx permanente (falla enseguida) y solo sigue enlaces `page.next` que sean `https://wger.de/api/v2/…`. Falla si el total recibido no coincide con el anunciado (valida la paginación).
2. `catalog:candidates` normaliza lo descargado y escribe los candidatos con una puntuación orientativa, y cuenta los descartados por motivo.
3. Cura `exercises-selection.json`: añade a `include` los `wgerId` que quieras (con ajustes si hace falta) y usa `exclude` para lo descartado.
4. `catalog:build` falla si la selección tiene problemas (ids inexistentes, tipos o material inválidos, duplicados...) y avisa si el total queda fuera de 200-300 ejercicios.

Los ids de ficha (`abduccion-de-cadera-en-maquina`) son el *slug* del nombre, igual que en los ingredientes. Cambiar el nombre de un ejercicio cambia su id y, por tanto, se importaría como ficha nueva (la antigua quedaría huérfana, ver más abajo). Los slugs no pueden repetirse entre fichas de wger y fichas propias: `catalog:build` lo señala como problema.

## Revisar `exercises-review.md`

Tras cada `catalog:build`, revisa `data/catalog/exercises-review.md`: recuento por grupo, por tipo y por licencia, las notas de la selección, los avisos del generador y cada ficha con su texto. Fíjate sobre todo en:

- Fichas cuya descripción describe otra variante (por ejemplo, una ficha genérica para un ejercicio con barra).
- Descripciones muy breves.
- El tipo y el material asignados.

Si algo no te convence, corrígelo en `exercises-selection.json` (ajuste por ficha, `exclude` o más `include`) y vuelve a ejecutar `catalog:build`. Después de la importación, las correcciones también se pueden hacer a mano en la app, pero **una ficha guardada en la app queda congelada**: el importador ya no la toca (ver «Reimportar y fichas editadas»).

El informe incluye además las columnas «Medida» e «Imagen», una sección «Imágenes» (cuántas fichas tienen imagen, con autor y licencia) y otra «Fichas propias (Catálogo Bobitos)» con el texto completo, y los avisos de posibles duplicados («Press banca» frente a «Press de banca con barra»: nombres que solo se diferencian en palabras de material).

## Importar

El importador es `scripts/catalog/import-exercises.mjs` (`npm run catalog:import --`). Usa `firebase-admin`, que se salta las reglas de seguridad.

```text
node scripts/catalog/import-exercises.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
```

- `--project`: `demo-bobitos` (emulador), `bobitos-dev` o su alias `dev`. Cualquier otro valor se rechaza.
- `--apply`: sin él, el importador solo **simula**: lee Firestore, imprime el plan y no escribe nada.
- `--catalog`: ruta alternativa al JSON (por defecto `data/catalog/exercises.json`).

Es idempotente y **nunca borra**. Sube antes las imágenes que falten o hayan cambiado (ver «Imágenes») y escribe las fichas por lotes de 400 y las actualizaciones llevan precondición de `lastUpdateTime` (si alguien edita una ficha entre la lectura y la escritura, falla en lugar de pisarla). Cada lote con operaciones sube además la versión del catálogo (`catalogMeta/exercises`, ver «Versión del catálogo y caché»); si no hay cambios, no la toca. La simulación no escribe nada, tampoco la versión, pero imprime una línea indicando si subiría.

### En el emulador

```bash
npm run emulators            # en otra terminal
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-exercises.mjs --project demo-bobitos --apply
```

Un proyecto `demo-*` exige `FIRESTORE_EMULATOR_HOST`. Para ver el resultado en la app usa `npm run android:connect-emulators`. Una segunda ejecución sin cambios debe informar de 0 creadas, 0 actualizadas y 0 imágenes a subir, y la versión no sube.

### En `bobitos-dev`

1. En Firebase Console de `bobitos-dev`: **Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada**.
2. Guarda el JSON **fuera del repositorio** (por ejemplo `~/.config/bobitos/bobitos-dev-sa.json`). Nunca se versiona.
3. Expórtala y simula:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/bobitos/bobitos-dev-sa.json"
   node scripts/catalog/import-exercises.mjs --project dev
   ```
4. Revisa el informe y, si es lo esperado, repite con `--apply`.
5. Opcional: revoca la clave en la consola cuando termines.

Despliega antes las reglas nuevas (ver «Orden de despliegue»).

### Qué hace con cada ficha existente

El informe de la simulación agrupa las fichas así:

| Situación de la ficha en Firestore | Qué hace el importador |
| --- | --- |
| No existe | **Crea** la ficha (dueño: la cuenta admin, autor «Catálogo Bobitos»). |
| Existe, importada, sin editar en la app y con datos distintos | **Actualiza** los campos gestionados (nombre, tipo, grupo, descripción, material, `measure`, `image` y `source`). |
| Existe, importada y con los mismos datos | Sin cambios. |
| Existe con el mismo id pero de **otro usuario** | **Omite**; no toca la ficha del usuario. |
| Existe del admin pero **sin `source`** (creada a mano en la app) | **Omite**. |
| Importada y **guardada en la app** después (`updatedAt` posterior a `source.importedAt`) | **Omite**; la ficha queda congelada (ver abajo). |
| Importada, del admin, pero **ya no está en el JSON** (huérfana) | La **lista**; no la borra. |
| Nombre casi igual a otra ficha existente con distinto id | La crea y la señala como posible duplicado. |

Al terminar con `--apply` imprime cuántas fichas se han creado y actualizado y cuántas imágenes ha subido, y si ha subido la versión.

### Reimportar y fichas editadas

El importador decide si una ficha «se ha editado» comparando `updatedAt` con `source.importedAt`. Cualquier guardado desde la app, **aunque no cambie nada**, actualiza `updatedAt`; la ficha queda **congelada** y las reimportaciones la omiten, con sus textos, medida e imagen tal como estaban. Para recuperarla del catálogo:

1. Bórrala en la app con la cuenta admin.
2. Reimporta (simulación y `--apply`): la ficha se crea de nuevo con los datos del JSON.

Choques de slug con fichas de usuarios: si un usuario creó a mano una ficha con el mismo nombre (mismo id) que una del catálogo, el importador la **omite** y la lista como «de otro usuario» (o «sin `source`», si es del admin). Para que entre la del catálogo, borra la ficha del usuario (con la cuenta admin o pidiéndoselo a su dueño) y reimporta; si prefieres conservar la suya, renombra la del catálogo en `exercises-selection.json` (cambia el id).

### Quitar un ejercicio del catálogo

El importador nunca borra. Si un ejercicio sale del catálogo, hay que **sacarlo de `exercises-selection.json`** (y regenerar), y además borrar la ficha huérfana en la app con la cuenta admin. Borrar una ficha solo en la app **no basta**: mientras siga en la selección, la próxima importación la volverá a crear.

## Tipos y material

El tipo decide qué se registra en una sesión de gimnasio. El material es una lista (sin repetidos) de valores canónicos.

| Tipo (`type`) | Etiqueta en la app | Qué registra |
| --- | --- | --- |
| `MAQUINA` | Máquina | Series con peso |
| `PESO_LIBRE` | Peso libre | Series con peso |
| `PESO_CORPORAL` | Peso corporal | Series de repeticiones, con lastre opcional |
| `CARDIO` | Cardio | Tiempo y nivel |
| `OTROS` | Otros | Tiempo y nivel (como Cardio) |

| Material (`equipment`) | Etiqueta en la app |
| --- | --- |
| `BARRA` | Barra |
| `BARRA_Z` | Barra Z |
| `MANCUERNAS` | Mancuernas |
| `KETTLEBELL` | Kettlebell |
| `DISCO` | Disco |
| `POLEA` | Polea |
| `MAQUINA` | Máquina |
| `BANCO` | Banco |
| `BANCO_INCLINADO` | Banco inclinado |
| `BARRA_DOMINADAS` | Barra de dominadas |
| `ESTERILLA` | Esterilla |
| `FITBALL` | Fitball |
| `BANDA_ELASTICA` | Banda elástica |

El orden de la tabla es el canónico (el mismo en `scripts/catalog/normalize.mjs`, en `ExerciseEquipment` de Kotlin y en `firestore.rules`).

### Añadir un material

Hay que tocar los cinco sitios; si falta alguno, la importación o el guardado en la app fallan:

1. `scripts/catalog/normalize.mjs`: añadirlo a `EQUIPMENT` (y, si procede, al mapa `WGER_EQUIPMENT`).
2. `ExerciseEquipment` (Kotlin, `core/model`): nuevo valor del enum, en el mismo orden.
3. `firestore.rules`: subir el límite `equipment.size() <= 13` (en `validExerciseShape`) al nuevo total. Después, desplegar las reglas.
4. `app/src/main/res/values/strings.xml`: la etiqueta en español.
5. `ExerciseTokens.labelRes` (`feature/exercises`): asociar el valor con esa cadena.

Actualiza también la tabla de arriba y `DATA_MODEL.md` (lista de valores y límite).

## Medida: repeticiones o segundos

Cada ficha lleva `measure`: `REPS` (por defecto) o `SECONDS`. Indica en qué se registran las series de los ejercicios de fuerza: el editor de rutinas y de sesiones de gimnasio ofrece un interruptor «Repeticiones / Segundos» y arranca con la medida de la ficha elegida del catálogo. `SECONDS` solo es válido en `MAQUINA`, `PESO_LIBRE` y `PESO_CORPORAL` (los tipos con series); en `CARDIO` y `OTROS` se guarda `REPS`.

Los isométricos (plancha, plancha lateral, hollow hold, L-sit y sentadilla en la pared; wger 1307, 1019, 297, 1852 y 1408) llevan `"measure": "SECONDS"` en `exercises-selection.json`, y la ficha muestra el chip «Por tiempo». Cada entrada de `exercises.json` lleva `measure` explícito.

## Imágenes

Las miniaturas **se almacenan en Firestore** (colección `exerciseImages`, un documento por ejercicio con imagen) y las copia el importador; la app nunca pide nada a wger.de. El campo `image` de la ficha es `null` o `{ hash, author?, license, sourceUrl }`:

- `hash`: sha256 (hexadecimal) del WebP. Es lo que usa el importador para saber si una imagen cambió.
- `author` y `license`: de esa imagen concreta; la licencia es una de las cuatro admitidas. Sin autor, o si solo había un correo, se muestra «colaboradores de wger» (los correos se eliminan).
- `sourceUrl`: procedencia de la imagen en `https://wger.de/media/` (máximo 300 caracteres). Solo se usa para informar y para descargarla con `catalog:images`; la app no la abre.
- Se **excluyen las imágenes generadas por IA** (y las que no tienen miniatura o licencia admitida). Por defecto se toma la primera imagen que cumple (las principales primero, luego por id). En la selección, `"image": false` quita la imagen de una ficha y `"image": <id de imagen>` elige otra; un id que no existe es un problema de `catalog:build`.

### Descargar y versionar: `catalog:images`

```bash
npm run catalog:images   # descarga de wger lo que falta y escribe data/catalog/images/<id>.webp
npm run catalog:build    # recalcula image.hash en exercises.json a partir de esos ficheros
```

`catalog:images` solo descarga las imágenes que aún no están en `data/catalog/images/`. Convierte cada una a **WebP de 400 px como máximo de lado** (sin ampliar), **calidad 80** y sin metadatos, y falla en esa imagen si pasa de 200 KB. Los WebP se versionan en el repositorio: hoy son 93 ficheros que suman unos 1,3 MB (14 KB de media, 43 KB la mayor). Si una imagen deja de usarse, el comando la informa como sin usar, pero no borra el fichero.

### Subida: el importador

El importador (ver «Importar») compara el `hash` de cada ficha con el de `exerciseImages/<id>`:

- Antes de escribir nada, y también en la simulación, comprueba que cada WebP a subir existe en `data/catalog/images/` y que su sha256 coincide con `image.hash`; si no, aborta.
- Con `--apply`, sube las imágenes **antes** que las fichas que apuntan a ellas, en lotes de como mucho 50 documentos y 5 MiB. Cada documento guarda `data` (los bytes), `contentType`, `hash`, `width`, `height`, `author`, `license`, `sourceUrl` y `updatedAt` (esquema en `DATA_MODEL.md`).
- Sube solo las imágenes nuevas o con otro hash. Las **huérfanas** (en `exerciseImages` pero sin ficha con imagen en el catálogo) se informan en el plan y **no se borran**.
- Ningún cliente puede escribir `exerciseImages` ni cambiar `image`: solo el importador. Las reglas validan `image` (hash, autor, licencia y `sourceUrl`) si está presente.

### En la app

- Solo se muestran en la ficha del ejercicio (no en las listas), con su crédito (autor y enlace a la licencia) debajo. Si no hay imagen o no se puede leer, el bloque entero, crédito incluido, no aparece.
- La app lee `exerciseImages/<id>` con un `get` (las reglas no permiten listar) y la guarda en caché local: **1 lectura por imagen y dispositivo**, y sin conexión sigue viéndose si ya se abrió. Si el servidor devuelve una imagen con otro hash que la ficha, muestra la del servidor.
- Privacidad: el dispositivo no contacta con wger.de; las imágenes salen de Firebase (ver `PRIVACY_POLICY.md`).

## Fichas propias (Catálogo Bobitos)

Para los básicos que wger no ofrece en español hay fichas redactadas en el repo, bajo la clave `custom` de `exercises-selection.json` (`bobitosId` entero mayor que 0 y único, `name`, `type`, `muscleGroup`, `equipment`, `description` y opcionalmente `measure`). Hoy son seis: remo ergómetro, dominadas con lastre, elevación de gemelos de pie en máquina, curl femoral de pie, natación y caminata.

- Su `source` es `provider` = `bobitos`, `id` = `bobitosId`, licencia `CC-BY-SA-4.0`, autor «Catálogo Bobitos» y **sin `url`**. No llevan imagen.
- La app las atribuye como «Texto original del Catálogo Bobitos · Licencia CC BY-SA 4.0», sin enlace, y **Perfil → Créditos y licencias** tiene su bloque.
- Se importan y se congelan igual que las de wger; las edita el admin como cualquier otra.
- El informe de revisión muestra su texto completo, para revisarlo antes de importar.

## Orden de despliegue y compatibilidad

1. **Reglas primero**: `npx firebase deploy --only firestore:rules --project dev`. Con las reglas viejas, la app nueva no puede leer `exerciseImages` (el importador sí escribe, porque el Admin SDK se salta las reglas) y recibe `PERMISSION_DENIED` al guardar un ejercicio (descripción, material, `measure` o la versión del catálogo).
2. **Importación** del catálogo (simulación, revisión, `--apply`).
3. **Beta** con la app nueva, justo después. Las notas de la beta piden que **todos los miembros de cada espacio actualicen**.

Compatibilidad con versiones antiguas:

- La app antigua ignora los campos nuevos (`description`, `equipment`, `source`, `measure`, `image`) y la colección `exerciseImages` y puede seguir creando y editando fichas, porque son opcionales.
- La app antigua ignora `measure` y `seconds`: al guardar una rutina o una sesión, los pierde (igual que pasa con `PESO_CORPORAL`).
- Las escrituras de la app antigua no suben la versión del catálogo: la caché de la app nueva las verá como tarde a los 7 días.
- La app antigua **descarta las fichas `PESO_CORPORAL`** (su parser exige un tipo conocido). Las lee como «Otros» donde ya hay rutinas guardadas y, al guardar, **pierde las series** de esos ejercicios. Por eso hay que actualizar todos los dispositivos.
- La app nueva con las reglas viejas **lee** el catálogo (sin `catalogMeta` lo trata como si no hubiera versión y lee del servidor), pero **falla al guardar** ejercicios; de ahí el orden anterior.

## Versión del catálogo y caché

Para no leer el catálogo entero en cada apertura, existe el documento `catalogMeta/exercises` (`version`, `updatedAt`, `updatedBy`; esquema en `DATA_MODEL.md`).

- **Quién sube la versión:** el importador (una vez por lote con operaciones) y cada guardado de un ejercicio desde la app (crear, editar o borrar), en la misma transacción que la ficha. Las reglas exigen `version` 1 al crearlo y `anterior + 1` al actualizarlo, y no permiten borrarlo ni listarlo. No exigen subir la versión al escribir en `exercises`, para no romper la app antigua.
- **Qué hace la app:** al abrir Ejercicios o un selector, muestra primero la caché local y lee `catalogMeta/exercises` (con un máximo de 10 s). Si la versión coincide con la guardada y la caché está completa, no lee ni un ejercicio más. Si la versión es distinta, o la caché tiene menos documentos de los que había, o han pasado más de 7 días (o el reloj ha retrocedido), relee el catálogo del servidor y guarda la versión y el recuento. Sin conexión, o si falla la lectura de la versión, se queda con la caché.
- **Sin `catalogMeta`** (o sin permiso para leerlo): se trata como «sin versión» y se lee del servidor, sin guardar nada. Por eso la app nueva sigue leyendo con las reglas viejas.
- **Cambios de otros dispositivos:** el catálogo **ya no se actualiza en directo**. Los cambios hechos desde otro dispositivo llegan en la **siguiente apertura**. Los propios se ven al momento.
- **Límite y orden:** la consulta es `collection("exercises").limit(1000)`, sin `orderBy`, y se ordena en el cliente. Así no se pierden las fichas que no tengan `nameLower` (un `orderBy` las excluiría).

## Coste en Firestore (plan Spark)

- Importación: unas 300 lecturas (fichas del catálogo más las imágenes ya subidas) y unas 310 escrituras (214 fichas y 93 imágenes), una sola vez. Cada lote de fichas suma además una escritura de la versión, sin lectura (usa `increment`). Una simulación cuesta solo las lecturas.
- Ver la imagen de un ejercicio: **1 lectura por imagen y dispositivo**; después sale de la caché local. La colección pesa unos 1,3 MB, muy por debajo de los 1 GiB gratuitos de Spark.
- Abrir el catálogo **sin cambios**: **1 lectura** (la de `catalogMeta/exercises`). Antes costaba unas 250-300.
- Abrir con cambios (versión nueva, caché incompleta o caducada): 1 + N lecturas, con N el tamaño del catálogo (unas 250-300 para 214 fichas más las de usuarios). El catálogo se carga de forma diferida: solo lo pagan la pantalla Ejercicios y los editores de rutinas y de gimnasio cuando se abren.
- Cada guardado de un ejercicio (crear, editar o borrar): **+2 lecturas** (la versión dentro de la transacción y la relectura de la ficha para meterla en la caché) y **+1 escritura** (la versión, además de la de la ficha).
- Las reglas de `exercises` no usan `get()`/`exists()`; las de `catalogMeta` tampoco.

## Resolución de problemas

| Mensaje | Causa y solución |
| --- | --- |
| `Falta build/catalog/wger-exerciseinfo.json: ejecuta antes npm run catalog:fetch.` | Aún no has descargado wger. Ejecuta `npm run catalog:fetch`. |
| `Falta data/catalog/exercises-selection.json.` | Falta el fichero de selección. |
| `Problemas:` seguido de una lista (en `catalog:build`) | La selección tiene errores; corrige cada punto. |
| `Aviso: N ejercicios, fuera del rango 200-300.` | Solo un aviso: la selección es demasiado corta o larga. |
| `Catálogo inválido:` (en el importador) | `exercises.json` no pasa la validación; regenera con `catalog:build`. |
| `Un proyecto demo-* exige FIRESTORE_EMULATOR_HOST (emulador).` | Arranca `npm run emulators` y exporta `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`. |
| `FIRESTORE_EMULATOR_HOST está definida: no se importa a bobitos-dev.` | Tienes la variable del emulador en el entorno; haz `unset FIRESTORE_EMULATOR_HOST`. |
| `Falta GOOGLE_APPLICATION_CREDENTIALS (clave de cuenta de servicio fuera del repo).` | Exporta la ruta de la clave JSON de `bobitos-dev`. |
| `La clave es del proyecto «X», no de bobitos-dev.` | Has generado la clave en otro proyecto; genera una en `bobitos-dev`. |
| `Usa --project demo-bobitos, bobitos-dev o dev.` | Falta `--project` o su valor no es válido. |
| `Argumento desconocido: ...` | Solo existen `--project`, `--apply` y `--catalog`. |
| `HTTP 404` (o otro 4xx) en `catalog:fetch` | Error permanente: no se reintenta. Revisa la URL o el bloqueo de wger. |
| `URL fuera de https://wger.de/api/v2/` en `catalog:fetch` | La API devolvió un `page.next` ajeno; se rechaza a propósito. |
| `PERMISSION_DENIED` al guardar un ejercicio, aunque no cambie la ficha | Las reglas desplegadas no incluyen `catalogMeta`; despliega `firestore.rules`. |
| Error de precondición (`FAILED_PRECONDITION`) al aplicar | Alguien editó una ficha entre la lectura y la escritura; repite la simulación y el `--apply`. |
| `PERMISSION_DENIED` en la app al guardar | Las reglas desplegadas son las antiguas; despliega `firestore.rules`. |

El uid admin con el que se importan las fichas (`CATALOG_ADMIN_UID` en `scripts/catalog/import-plan.mjs`) debe coincidir con `recipeAdmins()`; ver [`RECIPES_ADMIN.md`](RECIPES_ADMIN.md).
