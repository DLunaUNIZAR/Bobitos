# Rediseño UI · Fase 3c-2: editores con listas dinámicas — plan

> **Para quien ejecute:** superpowers:executing-plans (nativo). Cierra la Fase 3 (`2026-10-07-rediseno-ui-fase-3-editores.md`; 3c-1 en `...-3c1-...`). Reutiliza `BobitosFormSheet`, `BobitosDialog`, `rememberEditorItem` y el patrón «borrador + `Saver` + test».

**Goal:** que los editores con listas dinámicas (sesión de ejercicios de Rutinas y Deporte, ingredientes de Recetas, nutrición de Marcas) sean sheets que no pierden lo escrito al girar, y que los detalles de receta y rutina sean sheets de lectura.

**Global Constraints:** los de la Fase 3. Sin cambios en repositorios, ViewModels, `firestore.rules` ni modelos. Enums persistidos por `name`.

## Hallazgos (inventario + lectura del código)
- `ExerciseDraft`/`SetDraft` (`ExerciseListEditor.kt`) son clases con `mutableStateOf` que la UI muta en sitio (`SnapshotStateList<ExerciseDraft>`); todos sus campos son `String` salvo `type` (enum) y `exerciseId`. Compartidas por `RoutineEditor` y `ActivityEditor` (sesión de gimnasio). Un `Saver` de la lista basta; no hace falta cambiarlas a inmutables.
- `RecipeEditor`: `IngredientDraft` (privado, `mutableStateOf`) y `SnapshotStateList`; el anfitrión guarda un `RecipeEditorRequest(recipe, draft: ImportedRecipe?)` (objetos de dominio, no guardables); al guardar, el `sourceUrl` sale del borrador importado. `ImportUrlDialog` ya usa `rememberSaveable`.
- `BrandEditorDialog`: nutrición como 6 `String` en `NutritionDraft`/`NutritionRow`; el anfitrión guarda un `BrandEditorRequest` con `Nutrition?` (6 `Double?`).
- `RecipeDetailDialog` y `RoutineDetailDialog` son `AlertDialog` de lectura con scroll; el de receta lleva acciones (añadir a la compra, editar, borrar, copiar).
- El editor de actividad (Deporte) se quedó sin guardado en rotación en la 3b por la sesión (ver ledger de la 3b); aquí se cierra.

## Review Focus
1. **Rotación con el editor abierto** conserva lo escrito, incluidas las listas: ejercicios y series (Rutina y Actividad), ingredientes (Receta), campos de nutrición (Marca). Al editar, el sheet no desaparece durante la recarga (`rememberEditorItem`).
2. **Receta importada por URL:** al guardar se conserva el `sourceUrl`; girar con el editor del borrador importado abierto no lo pierde ni lo duplica.
3. **Descartar:** con cambios (incluidos añadir/quitar filas) pide confirmación; sin cambios cierra. La comparación de listas no da falsos positivos al abrir.
4. **Rutina → Deporte:** elegir una rutina en el editor de actividad sigue rellenando la sesión; limpiar la rutina la vacía; girar después conserva ambas cosas.
5. **Filas dinámicas:** añadir, borrar y editar una fila en el medio de la lista mantiene el foco/contenido correcto (las filas no tienen clave estable).
6. **Detalles en sheet:** la receta conserva sus acciones; no se pierde el deshacer de borrar.
7. **Solo lectura:** sin conexión no se abre ningún editor, como hoy.

## Tareas
- **T1 — Guardado de la sesión de ejercicios.** `Saver` de `SnapshotStateList<ExerciseDraft>` (con `SetDraft` anidados) en `ExerciseListEditor.kt`; test de ida y vuelta; `picking` de `ExerciseListEditor` a `rememberSaveable`.
- **T2 — Rutinas.** `RoutineEditor` en `BobitosFormSheet` con título, descripción, global y sesión guardables; anfitrión por id; `RoutineDetailDialog` a sheet de lectura.
- **T3 — Deporte.** `ActivityEditor` guardable (tipo, nombre, participantes, rutina, sesión, selector de rutina) con anfitrión por id.
- **T4 — Recetas.** `RecipeDraft` + ingredientes como lista inmutable con `Saver`; `RecipeEditor` en sheet; anfitrión por id (con el borrador importado guardable); `RecipeDetailDialog` a sheet.
- **T5 — Marcas.** `BrandDraft` (nombre, código y 6 cifras como texto) con `Saver`; `BrandEditorDialog` en sheet; anfitrión guardable.

## Fuera de alcance
Selectores `RecipePickerDialog`/`RoutinePickerDialog`/`ExercisePickerDialog` (diálogos sobre el sheet, que funcionan; un sheet sobre otro sheet complica el foco), flujo «ingrediente desde escaneo» (`ScannedProduct`; candidato posterior), guardado optimista, minors diferidos de las fases anteriores.
