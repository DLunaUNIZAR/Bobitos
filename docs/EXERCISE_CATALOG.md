# Catálogo común de ejercicios

El catálogo de ejercicios (`exercises/{exerciseId}`, colección top-level global) se siembra con unos 200 ejercicios de [wger](https://wger.de), en español, con su descripción, material y atribución. Este documento explica de dónde salen los datos, cómo regenerarlos, revisarlos e importarlos, y qué esperar de la importación.

El esquema de la colección está en la sección «Ejercicios» de [`DATA_MODEL.md`](DATA_MODEL.md).

## Fuente y licencia

- Fuente: API pública de ejercicios de wger (`https://wger.de/api/v2/exerciseinfo/`), solo el idioma español.
- Licencia del catálogo resultante: **CC BY-SA 4.0** (obra derivada de wger y de sus autores). El texto y la lista de cambios están en `data/catalog/LICENSE.md`.
- Cada ficha conserva su procedencia en el campo `source` (`provider`, `id` de wger, `author`, `license` y `url` de la ficha en wger). La licencia por ficha puede ser `CC-BY-SA-3.0`, `CC-BY-SA-4.0`, `CC-BY-4.0` o `CC0-1.0`.
- La app muestra la atribución en la ficha del ejercicio y en **Perfil → Créditos y licencias**.
- Cambios respecto a wger: el HTML de las descripciones pasa a texto plano, las descripciones largas se recortan (hasta 1500 caracteres), los nombres se normalizan y se asignan tipo, grupo muscular y material.

## Estructura de `data/catalog/`

| Fichero | Qué es | ¿Se edita a mano? |
| --- | --- | --- |
| `exercises-selection.json` | Selección curada: qué fichas de wger entran y con qué ajustes. | Sí |
| `exercises.json` | Catálogo generado, el que se importa a Firestore. | No (`catalog:build`) |
| `exercises-review.md` | Informe legible para revisar el catálogo generado. | No (`catalog:build`) |
| `LICENSE.md` | Licencia y cambios realizados. | Rara vez |

`exercises-selection.json` tiene estas claves:

- `include`: lista de `{ "wgerId": 123, ... }`. Los campos opcionales `name`, `type`, `muscleGroup`, `equipment` y `description` sustituyen a lo que se deduce de wger.
- `exclude`: lista de `{ "wgerId": 123, "reason": "..." }` para dejar constancia de lo descartado a propósito.
- `notes`: lista de textos que se copian al informe de revisión (carencias de wger, fichas dudosas...).

Los ficheros intermedios (descarga completa de wger y candidatos) viven en `build/catalog/`, que está ignorado por Git.

## Regenerar el catálogo

```bash
npm run catalog:fetch        # descarga todo wger a build/catalog/wger-exerciseinfo.json
npm run catalog:candidates   # build/catalog/candidates.md y candidates.json para elegir
# editar data/catalog/exercises-selection.json
npm run catalog:build        # genera data/catalog/exercises.json y exercises-review.md
```

1. `catalog:fetch` pagina la API (200 por página) con reintentos. Falla si el total recibido no coincide con el anunciado.
2. `catalog:candidates` normaliza lo descargado y escribe los candidatos con una puntuación orientativa, y cuenta los descartados por motivo.
3. Cura `exercises-selection.json`: añade a `include` los `wgerId` que quieras (con ajustes si hace falta) y usa `exclude` para lo descartado.
4. `catalog:build` falla si la selección tiene problemas (ids inexistentes, tipos o material inválidos, duplicados...) y avisa si el total queda fuera de 200-300 ejercicios.

Los ids de ficha (`abduccion-de-cadera-en-maquina`) son el *slug* del nombre, igual que en los ingredientes. Cambiar el nombre de un ejercicio cambia su id y, por tanto, se importaría como ficha nueva (la antigua quedaría huérfana, ver más abajo).

## Revisar `exercises-review.md`

Tras cada `catalog:build`, revisa `data/catalog/exercises-review.md`: recuento por grupo, por tipo y por licencia, las notas de la selección, los avisos del generador y cada ficha con su texto. Fíjate sobre todo en:

- Fichas cuya descripción describe otra variante (por ejemplo, una ficha genérica para un ejercicio con barra).
- Descripciones muy breves.
- El tipo y el material asignados.

Si algo no te convence, corrígelo en `exercises-selection.json` (ajuste por ficha, `exclude` o más `include`) y vuelve a ejecutar `catalog:build`. Después de la importación, las correcciones también se pueden hacer a mano en la app, pero se pierden en una reimportación solo si la ficha no se ha editado (ver más abajo).

## Importar

El importador es `scripts/catalog/import-exercises.mjs` (`npm run catalog:import --`). Usa `firebase-admin`, que se salta las reglas de seguridad.

```text
node scripts/catalog/import-exercises.mjs --project demo-bobitos|bobitos-dev|dev [--apply] [--catalog ruta]
```

- `--project`: `demo-bobitos` (emulador), `bobitos-dev` o su alias `dev`. Cualquier otro valor se rechaza.
- `--apply`: sin él, el importador solo **simula**: lee Firestore, imprime el plan y no escribe nada.
- `--catalog`: ruta alternativa al JSON (por defecto `data/catalog/exercises.json`).

Es idempotente y **nunca borra**. Escribe por lotes de 400 y las actualizaciones llevan precondición de `lastUpdateTime` (si alguien edita una ficha entre la lectura y la escritura, falla en lugar de pisarla).

### En el emulador

```bash
npm run emulators            # en otra terminal
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/catalog/import-exercises.mjs --project demo-bobitos --apply
```

Un proyecto `demo-*` exige `FIRESTORE_EMULATOR_HOST`. Para ver el resultado en la app usa `npm run android:connect-emulators`. Una segunda ejecución sin cambios debe informar de 0 creadas y 0 actualizadas.

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
| Existe, importada, sin editar en la app y con datos distintos | **Actualiza** los campos gestionados (nombre, tipo, grupo, descripción, material y `source`). |
| Existe, importada y con los mismos datos | Sin cambios. |
| Existe con el mismo id pero de **otro usuario** | **Omite**; no toca la ficha del usuario. |
| Existe del admin pero **sin `source`** (creada a mano en la app) | **Omite**. |
| Importada y **editada en la app** después (`updatedAt` posterior a `source.importedAt`) | **Omite**; se conserva la edición. |
| Importada, del admin, pero **ya no está en el JSON** (huérfana) | La **lista**; no la borra. |
| Nombre casi igual a otra ficha existente con distinto id | La crea y la señala como posible duplicado. |

Al terminar con `--apply` imprime cuántas fichas se han creado y actualizado.

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
| `OTROS` | Otros | Sin parámetros específicos |

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

El orden de la tabla es el canónico (el mismo en `scripts/catalog/normalize.mjs`, en `ExerciseEquipment` de Kotlin y en `firestore.rules`). Si añades un valor, tócalos los tres.

## Orden de despliegue y compatibilidad

1. **Reglas primero**: `npx firebase deploy --only firestore:rules --project dev`. Con las reglas viejas, la app nueva recibe `PERMISSION_DENIED` al guardar descripción o material.
2. **Importación** del catálogo (simulación, revisión, `--apply`).
3. **Beta** con la app nueva, justo después. Las notas de la beta piden que **todos los miembros de cada espacio actualicen**.

Compatibilidad con versiones antiguas:

- La app antigua ignora los campos nuevos (`description`, `equipment`, `source`) y puede seguir creando y editando fichas, porque son opcionales.
- La app antigua **descarta las fichas `PESO_CORPORAL`** (su parser exige un tipo conocido). Las lee como «Otros» donde ya hay rutinas guardadas y, al guardar, **pierde las series** de esos ejercicios. Por eso hay que actualizar todos los dispositivos.
- La app nueva con las reglas viejas falla al guardar; de ahí el orden anterior.

## Coste en Firestore (plan Spark)

- Importación: unas 300 lecturas y 250 escrituras, una sola vez. Una simulación cuesta solo las lecturas.
- Uso en la app: cada apertura en frío del catálogo cuesta unas 250-300 lecturas. El catálogo se carga de forma diferida: solo lo pagan la pantalla Ejercicios y los editores de rutinas y de gimnasio cuando se abren. Con 10 usuarios, es menos del 10 % de las 50.000 lecturas diarias.
- Las reglas de `exercises` no usan `get()`/`exists()` (sin lecturas extra).

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
| Error de precondición (`FAILED_PRECONDITION`) al aplicar | Alguien editó una ficha entre la lectura y la escritura; repite la simulación y el `--apply`. |
| `PERMISSION_DENIED` en la app al guardar | Las reglas desplegadas son las antiguas; despliega `firestore.rules`. |

El uid admin con el que se importan las fichas (`CATALOG_ADMIN_UID` en `scripts/catalog/import-plan.mjs`) debe coincidir con `recipeAdmins()`; ver [`RECIPES_ADMIN.md`](RECIPES_ADMIN.md).
