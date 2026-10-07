# Rediseño UI · Fase 3c-1: confirmaciones, editores sencillos y selector de espacio — plan detallado

> **Para quien ejecute:** superpowers:executing-plans (nativo). Continúa el plan de la Fase 3 (`2026-10-07-rediseno-ui-fase-3-editores.md`). La 3c se divide en dos PRs: **3c-1** (este) y **3c-2** (editores con listas dinámicas: Recetas, Rutinas, sesión de Deporte, Marcas; y detalles de receta/rutina). Reutiliza `BobitosFormSheet`, `BobitosDialog`, `rememberEditorItem` y el patrón «borrador inmutable + `Saver` + test».

**Goal:** que todas las confirmaciones destructivas de la app sean iguales, que los editores sencillos (Nota, Ejercicio, Ingrediente) sean sheets que no pierden lo escrito, y que cambiar de espacio no obligue a salir a otra pantalla.

**Global Constraints:** los de la Fase 3. Sin cambios en repositorios, ViewModels (salvo lo indicado), `firestore.rules` ni modelos. Enums persistidos por `name`.

## Hallazgos (inventario + lectura del código)
- Confirmaciones destructivas que siguen siendo `AlertDialog` propio: borrar rutina, ejercicio, nota, receta (con deshacer), ingrediente y marca (vía `ConfirmDialog` privado de `IngredientDetailScreen`), borrar espacio, `ConfirmMemberActionDialog` (transferir, quitar y salir) y eliminar cuenta (con contraseña). Los botones de confirmar mezclan `TextButton` y `Button` y ninguno usa el color de error.
- Eliminar cuenta no cierra el diálogo al confirmar (espera al resultado) y limpia el aviso al escribir la contraseña: `BobitosDialog` ya admite el slot `content` y `confirmEnabled`.
- `NoteEditor`, `ExerciseEditorDialog` e `IngredientEditorDialog` son formularios de 2–3 campos de texto, todo `remember`, sin scroll.
- `ExercisesScreen` tiene dos instancias del editor (`showNew` y `editing`) y su FAB no depende de `canWrite` (la pantalla no lo recibe): fuera de alcance.
- `IngredientEditorDialog` se usa en 3 sitios; el flujo «desde escaneo» (`scanCreate: ScannedProduct`) contiene `Nutrition?` (no cabe en un Bundle): ese anfitrión sigue en `remember`.
- El chip del espacio (`SpaceTitle`, 3a/2) lleva a la pantalla completa de espacios. `AppUiState` ya tiene `spaces` y `selectedSpace`; el `NavHost` ya tiene `onSpaceSelected`.

## Review Focus
1. **Rotación con el editor abierto** (Nota, Ejercicio, Ingrediente) conserva lo escrito; al editar, el sheet no desaparece durante la recarga de la lista (`rememberEditorItem`).
2. **Descartar:** con cambios, deslizar/atrás/Cancelar pide confirmación; sin cambios cierra.
3. **Eliminar cuenta:** el diálogo no se cierra al confirmar; la contraseña se conserva al girar; el botón de confirmar sigue deshabilitado sin contraseña, sin conexión o cargando.
4. **ConfirmMemberActionDialog:** transferir no es destructivo; quitar y salir sí; los textos de cada caso no cambian.
5. **Deshacer** de borrar receta se conserva.
6. **Selector de espacio:** muestra todos los espacios con el actual marcado; elegir otro cambia de espacio y lleva a «Hoy»; hay acceso a la pantalla completa (crear/unirse); con la lista cargando no falla.
7. **Solo lectura:** las confirmaciones y editores siguen sin ser alcanzables sin conexión donde ya no lo eran.

## Tareas
- **T1 — Confirmaciones a `BobitosDialog`** (rutina, ejercicio, nota, receta con deshacer, ingrediente, marca, borrar espacio, `ConfirmMemberActionDialog`, eliminar cuenta con slot de contraseña). Se elimina el `ConfirmDialog` privado.
- **T2 — Editores sencillos a sheet.** `NoteDraft`, `CatalogExerciseDraft`, `CatalogIngredientDraft` (inmutables, `listSaver`, con tests); anfitriones con estado guardable por id + `rememberEditorItem`.
- **T3 — Selector de espacio en bottom sheet.** `SpaceTitle` abre un sheet con la lista de espacios (actual marcado), un acceso a «Gestionar espacios» (pantalla completa) y al elegir otro se cambia de espacio y se vuelve a «Hoy».

## Fuera de alcance (3c-2)
`RecipeEditor`/`IngredientsEditor` (lista dinámica), `RoutineEditor` y `ExerciseDraft`/`SetDraft` con su `Saver` (también hace guardable la sesión de `ActivityEditor`), `BrandEditorDialog` (nutrición), `RecipeDetailDialog` y `RoutineDetailDialog` a sheet de solo lectura, `ExercisePickerDialog`/`RoutinePickerDialog`/`RecipePickerDialog` a sheet.
