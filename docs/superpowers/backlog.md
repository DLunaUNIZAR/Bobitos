# Backlog de menores aplazados

Menores que las revisiones de cada rama dejaron sin arreglar. Cada línea dice si tiene **efecto visible** para un usuario o es **interno**.

## 2026-10-10 — `agent/tareas-sin-alta-rapida`

- **Efecto visible:** el editor de tareas se cierra en cuanto se pulsa «Guardar», sin esperar a que se complete la escritura. Si la escritura en Firestore falla, solo sale un snackbar y se pierde todo lo escrito en el formulario. Viene de antes (commit `7067111`); al quitar el alta rápida, el editor es la única vía de crear tareas (`TasksScreen.kt`, `onSave` del `TaskEditor`).
- **Interno:** el plan `2026-10-10-quitar-alta-rapida-tareas.md` y el mensaje del commit `58387a2` llaman «Añadir tarea» al FAB, pero su texto (`tasks_add`) es «Nueva tarea».
