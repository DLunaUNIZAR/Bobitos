# Catálogo común de rutinas

El catálogo común de rutinas (`routines/{slug}`) se siembra con unas 29 rutinas de gimnasio y de casa, de una lista propia de Bobitos. Este documento explica cómo se edita, se valida y se importa.

## Qué es

- Unas 29 **rutinas comunes** (`routines/{slug}`, `visibility: GLOBAL`, dueño el admin del catálogo) que viven en la **misma colección** que las rutinas personales de los usuarios. Las crea **solo el admin**, con el importador.
- Cada ejercicio de una rutina enlaza una ficha del catálogo de ejercicios por su id y repite su nombre, su tipo y su medida. Nunca llevan pesos (`weight: null`).
- El diseño completo está en [`superpowers/specs/2026-10-11-catalogo-rutinas-design.md`](superpowers/specs/2026-10-11-catalogo-rutinas-design.md).

## Fuente y licencia

- Fuente: lista propia de Bobitos. No hay datos de terceros.
- La licencia está en `data/catalog/LICENSE.md` (sección «Rutinas»).

## Estructura de `data/catalog/`

| Fichero | Qué es | ¿Se edita a mano? |
| --- | --- | --- |
| `routines.json` | La lista, la que se importa a Firestore. | Sí |
| `routines-review.md` | Informe legible para revisar la lista. | No (`catalog:routines`) |

## Formato

```json
{
  "schemaVersion": 1,
  "routines": [
    {
      "title": "Cuerpo completo para empezar",
      "description": "Principiante · 45 min · máquinas · descansa 90 s entre series · 2 o 3 veces por semana.",
      "exercises": [
        { "exerciseId": "prensa-de-piernas", "series": 3, "reps": 12 },
        { "exerciseId": "plancha", "series": 3, "seconds": 30 },
        { "exerciseId": "cinta-de-correr", "durationMinutes": 10, "level": "Suave" }
      ]
    }
  ]
}
```

- Un ejercicio de fuerza lleva `series` y `reps` (medida de repeticiones) o `series` y `seconds` (medida por tiempo); uno de cardio u otros lleva `durationMinutes` y, opcionalmente, `level`. `notes` es opcional.
- El `id` del documento es el *slug* del `title` (`slug()` de `scripts/catalog/normalize.mjs`, igual que el de la app). Cambiar el título crea otra rutina y deja huérfana la anterior.
- Plantilla de la descripción: «nivel · duración · material · descanso · frecuencia» (por ejemplo, «Principiante · 45 min · máquinas · descansa 90 s entre series · 2 o 3 veces por semana.»).

## Validación

`npm run catalog:routines` valida la lista y escribe `routines-review.md`. Si hay problemas, los imprime y termina con error sin escribir nada. El importador aplica la misma validación antes de leer Firestore. Reglas:

- cada `exerciseId` existe en `exercises.json`;
- la medida del ejercicio manda: repeticiones (`reps`) o tiempo (`seconds`) en fuerza, sin mezclarlas;
- fuerza (`MAQUINA`, `PESO_LIBRE`, `PESO_CORPORAL`) lleva series; cardio y otros llevan minutos y no llevan series, repeticiones ni segundos; `level` solo en cardio y otros;
- límites (`ROUTINE_LIMITS` en `scripts/catalog/routines.mjs`): título 120, descripción 1000, hasta 30 ejercicios por rutina, `series` 1–20, `reps` 1–100, `seconds` 1–600, `durationMinutes` 1–180, `level` 30 y `notes` 200 caracteres.

El nombre, el tipo y la medida de cada ejercicio no se escriben en `routines.json`: se copian de `exercises.json` al construir el documento.

## Importar

El importador es `scripts/catalog/import-routines.mjs` (`npm run catalog:import-routines --`). Usa `firebase-admin`, que se salta las reglas de seguridad. Admite `--project demo-bobitos|bobitos-dev|dev`, `--apply` (sin él solo **simula**) y `--catalog ruta`.

### En el emulador

```bash
npm run emulators            # en otra terminal
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-routines -- --project demo-bobitos [--apply]
```

### En `bobitos-dev`

```bash
GOOGLE_APPLICATION_CREDENTIALS=~/.config/bobitos/bobitos-dev-adminsdk.json npm run catalog:import-routines -- --project dev [--apply]
```

Primero la simulación; si el plan es el esperado, repetir con `--apply`. Las protecciones de proyecto son las de los demás importadores (`scripts/catalog/admin-cli.mjs`).

### Qué hace con cada documento existente

- **Crea** las rutinas que no existen y **actualiza** las importadas cuyo contenido ha cambiado; las idénticas quedan **sin cambios**.
- **Omite** las rutinas de **usuarios** con el mismo id, las comunes del admin **sin `source`** (creadas a mano) y las comunes **editadas en la app** después de importarse.
- **Nunca borra.** Las **huérfanas** (ya no están en el JSON) y las «**Comunes creadas a mano en la app**» (GLOBAL del admin sin `source` y con otro id) solo se informan, para que el usuario decida.
- **No usa `catalogMeta`**: la app lee las rutinas en vivo, así que no hay versión de catálogo que subir.
