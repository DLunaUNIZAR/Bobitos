# Evaluación de pendientes del rediseño

**Fecha:** 2026-10-08 · **Base evaluada:** `main` en `f5e77ec` (tras fusionar la simplificación, PR #235) · **Plan:** `docs/superpowers/plans/2026-10-08-evaluacion-pendientes-rediseno.md`

Se evalúan 13 puntos que quedaron sin aplicar: los 4 problemas reales de `/code-review` (A–D) y los 9 hallazgos de `/simplify` no aplicados (S1–S9). Cada uno se mide en el código y, si el veredicto no es obvio, se prototipa en una rama `spike/…` desechable (borrada al acabar). No hay dispositivo Android en la máquina: lo que dependa de pantalla, rotación, back o TalkBack figura como **«sin verificar en dispositivo»** y no cuenta como evidencia.

## Rúbrica

| Eje | 1 | 2 | 3 |
|---|---|---|---|
| **Beneficio** | cosmético / ahorro < 50 líneas | evita un fallo raro o ahorra 50–200 líneas o lecturas ocasionales | evita un fallo que verá un usuario normal, o ahorra > 200 líneas / lecturas en cada uso |
| **Coste** | ≤ 3 archivos, < 1 h | 4–10 archivos o toca código fuera del rediseño | > 10 archivos, migración de datos o reglas |
| **Riesgo** | sin cambio de comportamiento y cubierto por tests | cambia comportamiento interno sin test automático | cambia lo que ve el usuario o los datos persistidos, sin test |

**Veredicto:** `Hacer ya` si Beneficio ≥ 2, Beneficio ≥ Coste y Riesgo ≤ 2 · `Más adelante` si Beneficio ≥ 2 pero no cumple lo anterior · `No` si Beneficio = 1 y (Coste ≥ 2 o Riesgo ≥ 2). En A–D, Beneficio ≥ 2 por definición.

## Resumen

| Punto | Qué | Beneficio | Coste | Riesgo | Veredicto |
|---|---|---|---|---|---|
| A | `values-night` y parpadeo oscuro al arrancar | | | | |
| B | Evento huérfano tras deshacer su borrado | | | | |
| C | Recordatorio a medianoche por cada actividad | | | | |
| D | Nombre de cuenta borrada reescrito en el evento | | | | |
| S1 | Borradores con `@Parcelize` | | | | |
| S2 | ViewModels sin volver a «cargando» al reobservar | | | | |
| S3 | Grafo anidado para «Más» | | | | |
| S4 | Metadatos de ruta en el enum | | | | |
| S5 | Deporte reutiliza el documento de evento del calendario | | | | |
| S6 | Fuente propia de espacios para el selector | | | | |
| S7 | Validación por campo en los editores | | | | |
| S8 | detekt: ignorar `@Composable` en métodos largos | | | | |
| S9 | Renombres del borrador en el editor de Compra | | | | |

## Orden recomendado

_(se rellena al final)_

---

## A — `values-night` y parpadeo oscuro al arrancar
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## B — Evento huérfano tras deshacer su borrado
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## C — Recordatorio a medianoche por cada actividad
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## D — Nombre de cuenta borrada reescrito en el evento
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S1 — Borradores con `@Parcelize`
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S2 — ViewModels sin volver a «cargando» al reobservar
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S3 — Grafo anidado para «Más»
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S4 — Metadatos de ruta en el enum
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S5 — Deporte reutiliza el documento de evento del calendario
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S6 — Fuente propia de espacios para el selector
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S7 — Validación por campo en los editores
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S8 — detekt: ignorar `@Composable` en métodos largos
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**

## S9 — Renombres del borrador en el editor de Compra
**Qué es** ·  **Medido** ·  **Prototipo** ·  **Puntuación** ·  **Veredicto** ·  **Si se hace**
