---
target: interfaz gráfica de la app Android Bobitos
total_score: 22
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/dluna/Developer/Bobitos/app/src/main/java/com/dlunaunizar/bobitos/feature"
timestamp: 2026-10-17T08-52-25Z
slug: app-src-main-java-com-dlunaunizar-bobitos-feature
---
Method: design review only (detector not applicable, native platform)
Evidencia: 162 capturas reales (emulador Pixel_10, Firebase local; 137 oscuro, 14 claro, 10 con font 1.3) + auditoría de código Compose (scratchpad/code-audit.md).

## Design Health Score
| # | Heurística | Nota | Problema clave |
|---|---|---|---|
| 1 | Visibilidad del estado | 2 | Hoy dice «nada pendiente» y «Calendario 0» mientras carga; snackbars incrustados que no se cierran |
| 2 | Lenguaje del mundo real | 3 | Buen vocabulario doméstico; fechas en inglés con UI en español; miércoles «X» vs «M» |
| 3 | Control y libertad | 3 | Deshacer al deslizar; Notas, Rutinas, Ejercicios, Ingredientes, Despensa y Espacios sin deshacer |
| 4 | Consistencia | 1 | 3 cabeceras, 3 colocaciones de FAB, 2 tiras de días, vacíos distintos, 2 formas de borrar |
| 5 | Prevención de errores | 2 | Error en rojo antes de escribir |
| 6 | Reconocer vs recordar | 3 | Chips de filtro cortados («Alta» oculto) |
| 7 | Eficiencia | 2 | Apuntar «Leche» = hoja de 5 campos |
| 8 | Estética y minimalismo | 2 | Títulos y CTA duplicados, metadatos repetidos, tarjeta de comida con todos los ingredientes |
| 9 | Recuperación de errores | 2 | ErrorState muestra error.message de la excepción |
| 10 | Ayuda | 2 | Vacíos desiguales |
| Total | | 22/40 | Aceptable |

## Design Specificity Verdict
Intercambiable: app de muestra Material 3 con semilla teal. Mismas 5 piezas en todas las pantallas (top bar, buscador, chips, losas grises, FAB teal). Carácter solo en la copia y en colores de módulo confinados a la barra inferior y Hoy. FAB/selección teal en todas las áreas. Hoy = plantilla de métrica protagonista con ceros. Detector: not applicable (native platform).

## Priority Issues
1. [P1] Cabeceras rotas e insets: Mi calendario y Ajustes del espacio bajo la barra de estado; «Atrás» como texto; Espacios y auth sin insets (SpacesScreen.kt:127, PersonalCalendarScreen.kt:114, SpaceSettingsScreen.kt:~95). Fix: Scaffold + BobitosTopBar en todas. /impeccable adapt
2. [P1] Color sin sistema y contraste: identidad de módulo solo en barra inferior; día seleccionado distinto en Comidas y Deporte; 42 hex de categoría sin variante oscura («Peso libre» 1,95:1 oscuro, prioridad Media 1,89:1 claro); etiquetas de barra 2,9-3,7:1; prioridad solo por color. Fix: roles de color por módulo en FAB/selección/chips; pares claro/oscuro AA; prioridad con icono/texto. /impeccable colorize
3. [P1] Formularios que regañan al abrir y avisos que estorban: error rojo antes de escribir (Tarea, Nota, Rutina, Evento, Producto); snackbars incrustados que empujan la lista y no se cierran; error.message crudo. Fix: validar al guardar/al salir del campo; SnackbarHost flotante; mensajes humanos. /impeccable harden, /impeccable clarify
4. [P2] Ruido y duplicados: doble título; doble CTA (vacío + FAB; 3 «+ Añadir» + FAB en Comidas); «Añadido por…» y «por Catálogo Bobitos» por fila; tarjeta de comida con ingredientes; Semana con «Sin eventos» x5; FAB tapa última fila. /impeccable distill, /impeccable layout
5. [P2] Todo es la misma losa gris: catálogos de 367 y 160 elementos en tarjetas de ~80dp sin agrupar ni imagen; hub global de 6 losas; Hoy con 4 contadores. Fix: ListItem con grupos; tarjetas solo para objetos; Hoy como lista de pendientes; jerarquía tipográfica. /impeccable layout, /impeccable typeset, /impeccable bolder

## Persona Red Flags
Casey (súper, una mano): hoja de 5 campos + error antes de teclear para apuntar «Leche»; el snackbar desplaza la lista al marcar; filtros de tienda arriba.
Jordan (miembro nuevo): hub mezcla espacios y catálogos; Comidas y Deporte en «Más» pero Hoy muestra Comidas; Recetario desde 3 sitios; Hoy «nada pendiente» y luego cambia.
Sam (TalkBack/baja visión): «Peso libre» 1,95:1; chips de día sin estado ni nombre del día; 9 «Más opciones» sin nombre; celdas de calendario sin rol; prioridad solo por color; «Calenda…» a 1.3.

## Minor Observations
Fechas en inglés según locale con UI solo en español, 7 formatos, picker empieza en domingo; «1 ejercicios»; «Mi día» con emoji 📅 y «Gimnasio, Gimnasio»; Compra vacía desalineada; chips de Tareas mezclan estado y prioridad; detalle de receta con 3 botones de texto iguales y «Categoría: Saludables» como frase; imagen de ejercicio en caja blanca en oscuro; Perfil columna centrada de enlaces con «Eliminar mi cuenta» junto a Créditos; tarjeta de actividad de Deporte no responde y participante solo se marca en la casilla; tema claro con baldosas pastel.

## Questions to Consider
¿Y si Hoy listara las 3-5 cosas de hoy? ¿Compra necesita hoja de 5 campos o basta un apuntar rápido abajo? ¿Y si cada módulo fuera dueño de su color en toda su pantalla? ¿Deberían los 4 catálogos vivir en un solo sitio?

## Audit (native Android)
Accesibilidad 2, Rendimiento 3, Tema 2, Conformidad 2, Adaptividad 1 → 10/20 Aceptable. P0 0, P1 3, P2 4.
