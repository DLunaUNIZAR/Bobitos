# Backlog de menores aplazados

Menores que las revisiones de cada rama dejaron sin arreglar. Cada línea dice si tiene **efecto visible** para un usuario o es **interno**.

## 2026-10-10 — `agent/tareas-sin-alta-rapida`

- **Efecto visible:** el editor de tareas se cierra en cuanto se pulsa «Guardar», sin esperar a que se complete la escritura. Si la escritura en Firestore falla, solo sale un snackbar y se pierde todo lo escrito en el formulario. Viene de antes (commit `7067111`); al quitar el alta rápida, el editor es la única vía de crear tareas (`TasksScreen.kt`, `onSave` del `TaskEditor`).
- **Interno:** el plan `2026-10-10-quitar-alta-rapida-tareas.md` y el mensaje del commit `58387a2` llaman «Añadir tarea» al FAB, pero su texto (`tasks_add`) es «Nueva tarea».

## 2026-10-10 — `agent/catalogo-ejercicios-wger`

- **Efecto visible:** los isométricos (plancha, plancha lateral, hollow hold, L-sit, sentadilla en la pared) se registran como «Peso corporal», con los segundos en el campo de repeticiones. Falta un registro de series en segundos.
- **Efecto visible:** en la ficha de ejercicio, los chips de tipo y grupo (`AssistChip(enabled = false)`) se anuncian como «desactivado» con TalkBack (`ExerciseDetailSheet.kt`).
- **Efecto visible:** las filas del selector de ejercicios (`PickerRow`, `ExercisePicker.kt`) no tienen el rol de botón para TalkBack.
- **Efecto visible:** «Personalizado…» en el selector no rellena el nombre con lo que ya se había buscado.
- **Efecto visible:** si cambias de espacio con el editor de sesión abierto, el catálogo del selector se queda congelado, porque `stopObserving` no se reanuda (`SportScreen.kt` / `SportViewModel.kt`).
- **Efecto visible:** la búsqueda solo separa palabras por espacio, tabulador y salto de línea (no por NBSP ni `\r`), y `foldForSearch` quita `\p{Mn}` donde `slug`/`foldText` quitan `\p{M}` (`TextSearch.kt`).
- **Efecto visible:** `ACCENT_FIXES` no corrige plurales ni derivados (maquinas, bulgaras…) en futuras regeneraciones del catálogo (`normalize.mjs`).
- **Efecto visible:** básicos de gimnasio que wger no ofrece en español: remo ergómetro, dominadas con lastre, gemelos de pie en máquina, curl femoral de pie, natación y caminata. Habría que crearlos desde la app con la cuenta admin.
- **Efecto visible:** imágenes de los ejercicios; filtros por grupo o tipo en el selector.
- **Interno:** `docs/EXERCISE_CATALOG.md` dice que `OTROS` registra «Sin parámetros específicos», pero registra tiempo y nivel, como Cardio.
- **Interno:** `docs/EXERCISE_CATALOG.md` tiene una frase contradictoria sobre las reimportaciones. Falta documentar que cualquier guardado en la app congela la ficha, cómo recuperarla y cómo resolver choques de slug.
- **Interno:** `docs/EXERCISE_CATALOG.md` no dice que añadir un material obliga también a subir `equipment.size() <= 13` en las reglas, y a tocar `strings.xml` y `ExerciseTokens.labelRes`.
- **Interno:** `nearDuplicateKey` solo avisa si el conjunto de palabras es idéntico; no detecta «Press banca» frente a «Press de banca con barra».
- **Interno:** `validateEntry` no comprueba que `source.id` sea un entero mayor que 0.
- **Interno:** la pantalla Ejercicios repite el filtro de `filterExercisePicker`, y ninguno de los dos usa `remember(catalog, query)`.
- **Interno:** el catálogo se descarga completo, con descripciones, también para los selectores. Mitigación prevista: un documento de versión del catálogo y lectura de la caché.
- **Interno:** `orderBy("nameLower")` excluye los documentos que no tienen ese campo, y el límite de 1000 corta por orden de code point.
- **Interno:** `fetch-wger.mjs` reintenta también los 4xx permanentes, y sigue `page.next` sin comprobar que el origen sea `https://wger.de`.
