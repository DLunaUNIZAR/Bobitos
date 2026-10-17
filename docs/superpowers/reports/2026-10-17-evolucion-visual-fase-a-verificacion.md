# Verificación visual de la fase A (evolución visual)

Fecha: 2026-10-17. Rama `agent/evolucion-visual-fase-a`. Emulador Pixel 10 (en-US, 1080x2424), variante debug con Firebase local (`demo-bobitos`, usuario `david@bobitos.invalid`, espacio «Casa Bobitos»).

Capturas fuera del repositorio, en `.superpowers/sdd/2026-10-17-evolucion-visual-fase-a/capturas/` (rutas de la tabla relativas a esa carpeta). Sufijos: `-oscuro`, `-claro`, `-130` (letra al 130 %, tema claro). Para cambiar de tema sin tocar la app se dejó el tema en «Sistema» y se alternó `cmd uimode night`; al acabar se volvió a «Oscuro».

## Paso 1: suite instrumentada completa

`./gradlew :app:connectedDebugAndroidTest`: 28 tests, 27 verdes, 1 fallo:
`feature.auth.ProfileScreenTest.privacyPolicyAndAccountDeletionAreReachable`, con `IllegalStateException: Given component holder class androidx.activity.ComponentActivity does not implement interface dagger.hilt.internal.GeneratedComponent ...`.

Ruling R7 confirmado en `main` (d2b5eba, worktree temporal fuera del repo, ya eliminado): ejecutando solo ese test falla con exactamente el mismo error. Es un fallo previo al plan; no hay otros fallos, por lo que el paso 1 cuenta como verde bajo R7.

## Tabla A1-A8

| Punto | Cumple maqueta / «Hecho cuando» | Captura | Observación |
| --- | --- | --- | --- |
| A1 Cabeceras e insets | Sí | `04-mi-calendario-*`, `06-tus-espacios-*`, `21-ajustes-espacio-*`, `01-login-*` | Mi calendario, Tus espacios y Ajustes del espacio con barra superior y flecha atrás; nada bajo la barra de estado ni la de gestos. Login centrado y respetando las barras. Solo comprobado con navegación por gestos (el emulador no se pasó a 3 botones). |
| A2 FAB no tapa la última fila | Sí | `05-mical-semana-final-*`, `09-calendario-semana-final-*`, `19-ejercicios-final-peso-libre-*`, `20-recetario-final-*`, `06-tus-espacios-*` | Al final de la lista la última fila queda entera sobre el FAB (Mi calendario Semana, Calendario Semana, Ejercicios, Recetario). No se capturaron Rutinas, Ingredientes ni Calendario en vista Día. |
| A3 Colores de categoría | Sí | `19-ejercicios-final-peso-libre-oscuro/claro`, `13-tarea-prioridad-media-*`, `16-comidas-*`, `17-deporte-*` | Chips de tipo de ejercicio con fondo tonal y texto legible en oscuro (incluido «Peso libre») y en claro. Prioridad Media con bandera y el nombre «Media» en la línea de metadatos; legible en ambos temas. |
| A4 Barra inferior | Sí | `09-calendario-semana-final-*`, `13-tarea-prioridad-media-*`, `14-compra-snackbar-claro`, `16-comidas-130`, `08-hoy-130` | Indicador opaco del módulo, etiqueta en tinta completa. Con letra al 130 % las cinco etiquetas se leen enteras («Calendario» sin cortar). |
| A5 Formularios | Sí | `10-crear-tarea-abierta-*`, `11-crear-tarea-guardar-vacio-*` | Crear tarea se abre sin errores y con Guardar activo; Guardar en vacío muestra «Introduce un título.» bajo el campo, en rojo. Al escribir el título y guardar se crea. No se comprobó el foco ni el resto de hojas. |
| A6 Avisos que flotan | Sí | `12-tarea-creada-snackbar`, `14-compra-snackbar-claro` | Snackbar «Has añadido «Sacar la basura»» y «Has añadido «Leche de avena»» con Deshacer, flotando sobre la lista sin empujarla. Deshacer borró el producto. Con «Leche» el producto ya existía y salió el diálogo «Ya en la lista», por lo que se usó «Leche de avena». |
| A7 Fechas, días y plurales | Sí, con 1 reserva | `04-mi-calendario-*`, `16-comidas-*`, `17-deporte-*`, `05-mical-semana-final-*` | Con el sistema en inglés: «Octubre de 2026», «12–18 oct 2026», «Sábado, 17 de octubre», semana desde lunes, iniciales L M X J V S D en Comidas, Deporte y calendario. «1 ejercicio», «1 hecha» correctos. Reserva en A7 abajo. |
| A8 Pequeños defectos | Sí | `08-hoy-cargando`, `08-hoy-*`, `18-deporte-tap-tarjeta` | Hoy muestra «Cargando...» hasta tener datos. «Mi día» con icono de Material y «Gimnasio (2)». Tocar una actividad de Deporte abre «Editar actividad». No se probó Compra vacía ni filas con casilla (participantes se ve con casilla en `18-...`). |

## Observaciones

- A7, plural sin resolver: el contador de Tareas dice «0 de 1 tareas» y «1 de 2 tarea» (el plural se decide con el primer número, no con el total). Visible en `13-tarea-prioridad-media-*`. No se arregló.
- Hoy muestra «Calendario 0» con 3 eventos de todo el día hoy en Mi calendario/Calendario (`08-hoy-*` frente a `09-calendario-semana-final-*`). Puede ser la definición del resumen (solo próximos con hora), no es un punto de A1-A8; se anota por si es un defecto.
- No hay filas «no» entre los puntos comprobados. Quedan sin capturar varios puntos secundarios citados arriba (3 botones, Rutinas, Ingredientes, Compra vacía, casillas de fila entera, foco al primer error).

## Entorno

Emuladores de Firebase ya en marcha y con datos del seed; `adb reverse` reconectado con `npm run android:connect-emulators`. Se creó una tarea y un producto de prueba y se borraron al terminar. Restaurado: `font_scale` 1.0, modo claro del sistema, tema de la app en «Oscuro».
