# Evolución visual de Bobitos — diseño

Fecha: 2026-10-17. Origen: crítica y auditoría de la interfaz con Impeccable (2026-10-17), con propuestas filtradas por soft-skill y taste-skill. La fase A se aprobó en el chat con maquetas de antes y después («Está perfecta»).

## Por qué

La crítica de la app real (162 capturas en el emulador Pixel_10 con Firebase local, en oscuro, claro y letra al 130 %) dio:
- **Heurísticas de Nielsen:** 22/40, «aceptable».
- **Auditoría técnica nativa:** 10/20.

La base técnica es sólida: tokens claro/oscuro completos, un único set de iconos (`Icons.Rounded`), todas las listas con `key` y ningún `Toast`. Lo que falla es la capa de diseño:
- defectos visibles que hacen parecer la app a medio hacer;
- un color que no tiene sistema;
- una jerarquía aplanada por los duplicados;
- un aspecto de «app de ejemplo de Material 3 con teal».

El informe de la crítica está en `.impeccable/critique/2026-10-17T08-52-25Z__app-src-main-java-com-dlunaunizar-bobitos-feature.md`.

## Lo que decidió el usuario

- **Orden:** primero los **defectos visibles** (fase A). El resto se apunta para arreglarlo después (fases B, C y D de este documento).
- **Dirección visual:** **evolucionar**. Se conserva lo que funciona, pero cada módulo pasa a ser dueño de su color, hay menos losas y más jerarquía. Esto aplica sobre todo a la fase C.
- **Alcance del programa:** P1 + P2 de la crítica.
- **Intocable:** el **tema oscuro por defecto**. Todo lo demás (teal, Nunito, barra de 5 pestañas) puede cambiar si una fase lo justifica.
- **Fase A:** aprobada tal como se mostró en las maquetas (seis casos con antes y después, más la lista «También en la fase A»).
  - Las maquetas están en `docs/superpowers/specs/2026-10-17-evolucion-visual-maquetas-fase-a.html`. Son ilustrativas: si difieren de este texto, manda el texto.
- **Deshacer al añadir:** después del builder check, el usuario pidió que en la fase A **también se pueda deshacer lo que se acaba de añadir**, en todas las áreas (respuesta «Sí»). Ver A6.

## Restricciones

- **Plataforma:** Android, Kotlin, Jetpack Compose y Material 3 (BOM `2026.06.00`), minSdk 26 y targetSdk 37. Material 3 manda en estructura, navegación y componentes. La marca se expresa a través del tema (roles de color, tipografía, formas), no con controles inventados.
- **Skills web adaptadas:** soft-skill y taste-skill están pensadas para webs. Se adoptan solo sus reglas que valen en una app nativa:
  - sin sombras en reposo;
  - superficies separadas por tono, no por líneas;
  - un acento por pantalla;
  - texto en tinta completa;
  - sin emoji como icono;
  - sin filas de tarjetas idénticas con icono encima;
  - jerarquía de pocos tamaños con contraste claro;
  - movimiento con muelles y respetando «Quitar animaciones».

  Se descartan las que chocan con Android: prohibir Roboto o Material Symbols, barras de navegación flotantes de cristal, «double-bezel», marquesinas web.
- **Idioma:** la app solo tiene textos en español (`res/values/strings.xml`). Todo texto nuevo va en `strings.xml`. Los conteos van con `<plurals>`.
- **Tema:** el tema oscuro sigue siendo el de por defecto (`ThemeMode`). Todo cambio de color se comprueba en claro y en oscuro.
- **Tests:** tests JVM (`./gradlew testDebugUnitTest`).
  - Ya existe el patrón de tests de contraste: `feature/shopping/SupermarketContrastTest.kt`, `core/designsystem/theme/ModuleColorsTest.kt`.
  - Ya existen tests de validación y de ViewModel por área.
- **Verificación y lint:** `./gradlew ktlintCheck detekt testDebugUnitTest lintDebug assembleDebug` con `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  - Si cambian firmas que están en `config/detekt/baseline.xml`, se regenera con `./gradlew detektBaseline`.
  - `lintDebug` falla ya en `main` por `RestrictedApi currentBackStack` en `BobitosNavHost`: es un problema previo, no de este plan.
- **Dependencias:** ninguna nueva salvo que una tarea lo justifique.

## Fase A — Defectos visibles (hecha: mergeada en `main`, ddfa827)

Rutas relativas a `app/src/main/java/com/dlunaunizar/bobitos/`.

### A1. Cabeceras e insets

**Problema.** Varias pantallas no usan `Scaffold` ni insets, así que su contenido se mete bajo la barra de estado o la de navegación:
- `feature/spaces/SpacesScreen.kt:127` (Tus espacios);
- `feature/calendar/PersonalCalendarScreen.kt:114` (Mi calendario);
- `feature/spaces/SpaceSettingsScreen.kt:~95` (Ajustes del espacio, que además usa «Atrás» como botón de texto con el título a la derecha);
- las de acceso: `feature/auth/AuthComponents.kt:34-40` (Login, Registro, Recuperar contraseña, Verificar correo) y `WelcomeScreen.kt:34`.

**Decisión.**
- **Tus espacios, Mi calendario y Ajustes del espacio** pasan a `Scaffold` + `BobitosTopBar`, con la flecha atrás como icono de navegación y el título a la izquierda, igual que Perfil. Su FAB va en `Scaffold.floatingActionButton`.
- **Las pantallas de acceso** conservan su composición centrada, sin barra superior, pero respetan las barras del sistema (insets `safeDrawing`) y el teclado.

**Hecho cuando** ninguna pantalla pinta contenido bajo la barra de estado ni bajo la de navegación, tanto con navegación por gestos como con 3 botones.

### A2. El FAB no tapa la última fila

**Problema.** Hay 14 `contentPadding` de `88.dp` literales y el FAB se coloca de tres formas distintas:
- `Scaffold.floatingActionButton`;
- `Box.align(BottomEnd).padding(16.dp)`;
- `Spacing.lg`.

En Calendario (Semana y Día), Recetario, Rutinas, Ingredientes, Ejercicios y Mi calendario, el FAB tapa la última tarjeta.

**Decisión.**
- Una única forma de colocar el FAB.
- Un único valor compartido (token o helper en `core/designsystem`) para el hueco inferior que deja una lista con FAB.
- Todas las listas con FAB lo usan.

**Hecho cuando**, al desplazarse hasta el final de cualquier lista con FAB, la última fila queda entera por encima del FAB. No queda ningún `88.dp` literal.

### A3. Colores de categoría con contraste en los dos temas

**Problema.** Hay 40 hex fijos, iguales en claro y oscuro, en:
- `TaskTypeTokens.kt:38-42,48-50` (tipos y prioridades);
- `SupermarketTokens.kt:34-44`;
- `EventColorTokens.kt:12-17`;
- `SportTokens.kt:42-48`;
- `ExerciseTokens.kt:48-52`;
- `MealTokens.kt:30-32`.

Fallan, entre otros:
- «Peso libre», 1,95:1 sobre oscuro;
- los 5 chips de tipo de ejercicio en oscuro;
- la prioridad Media, 1,89:1 en claro;
- el desayuno, 2,95:1 en claro;
- Cena, Carrefour, Caminata y Hogar, por debajo de 3:1 en oscuro.

La prioridad de una tarea solo se comunica con un punto de color (`TasksScreen.kt:578` `PriorityDot`).

**Decisión.**
- Cada color de categoría pasa a ser **consciente del tema**, con una variante clara y otra oscura, como ya hace `ModulePalette` con `LocalDarkTheme`.
- **Requisito medible:**
  - Usado como texto (por ejemplo, el texto de `InfoChip`): al menos 4,5:1 contra el fondo real donde se pinta, incluida la tarjeta teñida al 12 % de `CategoryTint.categoryCardColors`.
  - Usado como icono, punto o barra: al menos 3:1.
- **`InfoChip`** deja el borde `outlineVariant` (1,6 y 2,0:1) y pasa a fondo tonal de su categoría con el texto de la categoría.
- **Prioridad:** el punto de color se sustituye por un **icono de bandera, teñido con el color de la prioridad, más el nombre** («Alta», «Media», «Baja») en la línea de metadatos de la tarea, como en la maqueta: «⚑ Media · David Demo».
- **Imagen de ejercicio** (`ExerciseImageBlock.kt:79`): deja de ir sobre blanco puro en tema oscuro. Usa un tono claro atenuado que siga sirviendo de fondo a los dibujos con transparencia. En claro puede seguir en blanco.

**Hecho cuando** un test recorre **todas** las categorías de todas las tablas en los dos temas y comprueba esos umbrales contra sus fondos reales (extendiendo el patrón de `SupermarketContrastTest`).

### A4. Barra inferior legible

**Problema** (`core/navigation/WorkspaceScaffold.kt:87-118`). En la pestaña activa, el icono y el texto van en el color de acento del módulo sobre un indicador de ese mismo color al 20 %. En claro eso da entre 2,9 y 3,7:1 en 4 de los 5 módulos. Con letra al 130 %, «Calendario» se corta en «Calenda…».

**Decisión.**
- **Indicador** opaco con el `container` del módulo.
- **Icono seleccionado** en `onContainer` del módulo.
- **Etiqueta seleccionada** en `onSurface` (tinta completa) y semibold.
- **Sin selección:** `onSurfaceVariant`.
- Hoy y Más, que no tienen módulo, usan los colores por defecto de Material.
- **Etiquetas:** encogen un poco antes de cortarse (`autoSize` por pasos, con un mínimo que siga siendo legible).

**Hecho cuando:**
- un test comprueba icono sobre indicador ≥ 3:1 y etiqueta sobre la barra ≥ 4,5:1 en los 5 módulos y los dos temas;
- con `font_scale 1.3` en el Pixel 10, las cinco etiquetas se leen enteras.

### A5. Formularios que no regañan al abrirse

**Problema.** Tarea, Nota, Rutina, Evento y Producto muestran el error en rojo («Introduce un título.» o el campo Producto en rojo) nada más abrirse, lejos del campo, y con Guardar desactivado. El aviso de fechas del editor de eventos no se limpia al corregirlas (menor anotado en el rediseño anterior).

**Decisión**, válida para todos los formularios de `BobitosFormSheet` (12 ficheros) y para los diálogos de crear, unirse a y renombrar un espacio:
- La hoja se abre **sin errores**.
- **Guardar** está siempre activo, salvo mientras guarda y salvo donde hoy ya se desactiva sin conexión (`canWrite`: Evento, Rutina, Receta, Comida, Deporte, Ajustes del espacio). Se mantiene la política de no escribir sin conexión: el plan separa «datos válidos» de «se puede escribir».
- Al pulsar Guardar con datos no válidos:
  - no se guarda;
  - cada error aparece **debajo de su campo** (`isError` + `supportingText`);
  - el foco va al primer campo con error.
- A partir de ese primer intento, los errores se recalculan al escribir y desaparecen en cuanto se corrigen.
- Los errores que no son de un campo concreto (por ejemplo, un rango de fechas) van junto a los controles a los que se refieren, con `liveRegion` para TalkBack.

**Hecho cuando** los tests de validación y de editor demuestran:
- sin errores visibles antes del primer intento;
- errores tras intentar guardar;
- que el error desaparece al corregir.

### A6. Avisos que flotan

**Problema.** Unos 12 bloques de «Feedback» están reimplementados a mano dentro del contenido:
- `PantryScreen.kt:234`, `TasksScreen.kt:1048`, `ShoppingScreen.kt:449`, `SportScreen.kt:297`;
- `RoutinesScreen.kt:415`, `NotesScreen.kt:288`, `ExercisesScreen.kt:326`, `RecipesScreen.kt:534`;
- `MealsScreen.kt:724`, `IngredientsScreen.kt:427`, `AuthComponents.kt:79`, `SpaceFeedback.kt:32`.

Empujan la lista y en Rutinas y Recetario no se cierran solos. Además, `ErrorState` enseña `error.message` de la excepción en 7 ViewModels o más (`TasksViewModel.kt:47`, `CalendarViewModel.kt:73,185`, `PersonalCalendarViewModel.kt:193`, `SportViewModel.kt:61,101`, `MealsViewModel.kt:80,382`, `NotesViewModel.kt:40`, `RecipesViewModel.kt:77,82`…), aunque `BobitosApp.kt` documenta que no debe hacerse.

**Decisión.**
- **El resultado de una acción** que el usuario acaba de hacer, bien o mal («Producto añadido», «Actividad creada», «No tienes permiso…»), se muestra en el **snackbar de Material 3** del `Scaffold` de esa pantalla. Lo flotante no desplaza nada.
  - Los informativos se cierran solos con la duración estándar corta.
  - Los que tienen Deshacer o son errores duran más, pero **todos se cierran solos**.
- **El fallo al cargar el contenido** de una pantalla usa `ErrorState` (común) con texto humano y «Reintentar».
- **Desaparecen** los bloques de feedback incrustados de las pantallas de lista.
- **Errores de un guardado con la hoja o el diálogo abiertos** (sin permiso, sin respuesta del servidor, código caducado, espacio lleno…): siguen **dentro** de la hoja o del diálogo, en el hueco de error que ya tienen (`BobitosFormSheet.errorMessage`), con texto humano. El snackbar del `Scaffold` quedaría tapado por la hoja, y cerrarla perdería el borrador.
- **Fuera del alcance de A6:** las pantallas de acceso y Perfil (`AuthComponents.kt` `AuthFeedback`). Conservan su aviso dentro del formulario, porque no empuja ninguna lista y la crítica no lo señaló. Solo se comprueba que no muestren texto de excepción.
- **Deshacer al añadir** (decisión del usuario). Cuando se crea **un** elemento desde su hoja, el snackbar dice «Has añadido «<nombre>»» (forma neutra, sin concordancia de género) y ofrece **Deshacer**, que borra justo ese elemento.
  - **Áreas:** tarea, producto de la compra, evento (del espacio y personal), comida, actividad de Deporte, nota, receta (crear, importar y «Guardar como mía»), ingrediente y marca personales, ejercicio, rutina.
  - **Quedan fuera:**
    - crear un espacio, porque deshacerlo es borrar un espacio;
    - las altas en lote, que llevan aviso sin Deshacer: «Añadir a la compra» desde receta o comida, el selector de la Despensa, Plantillas del hogar, duplicar día o semana.
  - **Ediciones:** no llevan Deshacer.
  - **Cómo deshace:** usa el mismo camino de borrado que ya tiene cada área, con sus efectos (por ejemplo, cancelar el recordatorio de una tarea).
  - **Si ya no existe:** si el elemento cambió o desapareció entretanto, Deshacer no falla en silencio: avisa con un snackbar.
- **Mensajes humanos.** Un único traductor de errores a mensajes (en `core/common` o donde encaje) convierte:
  - permiso denegado, sin conexión o no disponible, y no encontrado, en su mensaje concreto;
  - cualquier otro error, en uno genérico en lenguaje normal.

  Ningún texto de excepción llega a la pantalla.

**Hecho cuando:**
- tests del traductor y de los ViewModels afectados demuestran que no sale `error.message`;
- tests de ViewModel de cada área demuestran que crear un elemento emite «Has añadido…» con Deshacer, y que Deshacer borra ese elemento;
- tras A6 no queda ningún bloque de feedback incrustado en las pantallas de lista listadas.

### A7. Fechas en español, días y plurales

**Problema.**
- Hay 7 formatos de fecha distintos: fijos (`dd/MM/yyyy` en `TasksScreen.kt:896,1066`, `dd/MM HH:mm` en `CalendarScreen.kt:675` y `PersonalCalendarScreen.kt:329`), localizados con el idioma del dispositivo y `es-ES` forzado (`SportScreen.kt:618`, `MealsScreen.kt:750`).
- Con el móvil en inglés, calendario y selector de fecha salen en inglés y la semana empieza en domingo.
- El miércoles es «X» en Comidas y «M» en Deporte.
- Hay conteos sin plural: «1 ejercicios», «1 hechas».

**Decisión.**
- **Un único punto de formato de fechas** con locale español, para todas las pantallas. Formatos por uso:
  - fecha larga del día («sábado, 17 de octubre»);
  - mes («octubre de 2026»);
  - rango de semana;
  - cabecera de día en listas;
  - fecha corta en campos y chips;
  - fecha con hora;
  - hora `HH:mm`.
- **Mayúscula inicial** coherente al principio de frase.
- **Selectores de fecha** en español y con la semana empezando en lunes.
- **Iniciales de los días:** L M X J V S D en toda la app.
- **Conteos:** todos van con `<plurals>`.

**Hecho cuando:**
- un test del formateador, con el locale por defecto en inglés, devuelve las cadenas en español;
- no quedan patrones de fecha sueltos en las pantallas;
- un test de recursos o de cadenas cubre los plurales nuevos.

### A8. Pequeños defectos visibles

- **Hoy mientras carga:** dice «Hoy no tienes nada pendiente» y «Calendario 0» antes de tener los datos. Debe mostrar un estado de carga hasta la primera carga completa (test en `SpaceHomeDigestTest` o en el ViewModel de Hoy).
- **«Mi día»:** usa el emoji 📅 como icono (`SpaceTodayScreen`) y repite «Gimnasio, Gimnasio». Pasa a un icono de Material y agrupa los títulos iguales («Gimnasio (2)»).
- **Compra vacía** (`ShoppingScreen.kt:484` `ShoppingEmptyState`): título centrado y texto a la izquierda. Pasa al `EmptyState` común, sin botón (el FAB ya está).
- **Deporte:** tocar la tarjeta de una actividad no hace nada. Debe abrir su edición, igual que una tarea.
- **Filas con casilla:**
  - Participantes (`MemberCheckboxes.kt`) y «Todo el día» (`CalendarScreen.kt:794`): la fila entera marca, con `toggleable(role = Checkbox)`, no solo la casilla.
  - El participante de Deporte, que hoy solo se marca tocando la casilla, entra en este mismo arreglo.

## Fases siguientes (apuntadas; cada una con su propio plan cuando el usuario lo pida)

### Fase B — Jerarquía, ruido y accesibilidad no visual

> **Diseño detallado:** `docs/superpowers/specs/2026-10-17-evolucion-visual-fase-b-design.md` (2026-10-17). Allí manda lo decidido; esta lista es el esbozo original.

- **Un título por pantalla.**
  - La barra superior ya dice «Casa Bobitos / Tareas»: sobran «Tareas compartidas» (`TasksScreen.kt:172`) y «Lista compartida» (`ShoppingScreen.kt:167`).
  - El contador pasa a la cabecera de sección.
- **Un CTA por pantalla.**
  - Fuera el botón del estado vacío cuando hay FAB.
  - En Comidas, tres «+ Añadir» más el FAB «Nueva comida»: quedarse con uno.
- **Metadatos solo si aportan.**
  - «Añadido por …» solo con varios miembros.
  - Fuera «por Catálogo Bobitos» en cada receta común: ya lo dice la sección.
- **Comida en una línea:** la tarjeta de comida resume («12 ingredientes · Sin asignar») en vez de volcar la lista entera.
- **Semana sin ruido:** los días vacíos se pliegan en vez de repetir «Sin eventos» cinco veces.
- **Filtros de Tareas:** estado (Pendientes/Completadas) separado de prioridad, que hoy van en la misma fila de chips y cortan «Alta».
- **Detalle de receta:** una acción principal y el resto secundarias, en vez de tres botones de texto iguales. Además, «Categoría: Saludables» deja de ir como frase.
- **Perfil:** secciones alineadas a la izquierda; «Eliminar mi cuenta» separado de «Créditos».
- **Accesibilidad TalkBack:**
  - `DayChip` de Comidas y Deporte con `selectable` y el nombre completo del día; además se unifican, porque hoy están duplicados y son distintos.
  - Celdas de calendario con rol y estado seleccionado.
  - Cabecera plegable de Tareas que anuncia expandido/colapsado.
  - «Más opciones» con el nombre del elemento (9 sitios).
  - `Card(onClick)` en vez de `Card(Modifier.clickable)`.
- **Deshacer** en Notas, Rutinas, Ejercicios, Ingredientes, Despensa y Espacios, y un solo camino para borrar en Tareas y Compra.

### Fase C — Identidad («evolucionar»)

- **Cada módulo es dueño de su color en toda su pantalla:** FAB, selección (día, chips), indicadores y acentos usan `ModuleColors` (`accent`/`container`/`onContainer`, hoy casi sin usar). El teal queda como color de marca en las pantallas globales y en Hoy.
- **Listas en vez de losas.**
  - Los catálogos largos (Ingredientes 367, Recetario 160, Ejercicios 214, Rutinas 29) pasan a filas `ListItem` agrupadas, con cabeceras fijas A-Z o por categoría, separadas por tono y no por líneas.
  - Las tarjetas quedan solo para objetos: una comida, un evento, una actividad.
  - El hub global y «Más» dejan de ser losas idénticas y distinguen «tu espacio» de «catálogos».
- **Hoy como agenda:** lo que toca hoy (eventos, tareas que vencen, comidas, actividades) en una lista, en lugar de cuatro contadores con números grandes, casi siempre 0.
- **Jerarquía tipográfica:** pocos tamaños con contraste claro entre título de pantalla, sección y fila (por ejemplo, barra superior grande que colapsa en las pantallas de módulo).
- **Superficies:** chips sin contorno sobre un escalón de tono; nada de sombras en reposo.
- **Tema claro** sin baldosas pastel.
- **Movimiento:** respuesta al pulsar con muelle, transiciones Material (shared axis entre pestañas) y respeto a «Quitar animaciones» (`rememberReduceMotion`).
- **Navegación:** los cuatro catálogos se abren desde un único sitio, en vez de tres (hub, cabecera de Comidas, cabecera de Deporte). Deporte y Ejercicios dejan de compartir icono.

Esta fase merece maquetas propias y su propia lluvia de ideas antes de planificarse.

### Fase D — Técnico

- **Adaptividad:** `WindowSizeClass`, ancho máximo de contenido en tablet y horizontal, `NavigationRail` en pantallas anchas, y diálogos sin alturas fijas (`heightIn(max = 280…420.dp)`).
- **Rendimiento:**
  - `remember` para filtrados y ordenaciones en composición: `ShoppingScreen.kt:141-144`, `TasksScreen.kt:198`, `MealsScreen.kt:191,198,207`, `CalendarScreen.kt:574-576`, `eventsOn()` ×7, `PersonalCalendarScreen.kt:85-87`, `RoutinesScreen.kt:231`.
  - Imágenes de ejercicio con `inSampleSize` y caché (`ExerciseImageBlock.kt:61-69`).
- **Tokens:** adopción completa de `Spacing` (hoy 46 %) y textos sueltos («·», «•», «★») a `strings.xml`.
- **Atrás predictivo:** declararlo y revisar el `BackHandler` de Hoy (`BobitosNavHost.kt:259`).

## Anti-objetivos

- Que la fase A cambie el aspecto general (paleta, tipografía, navegación). Eso es la fase C.
- Componentes que imitan a Material en lugar de usarlo.
- Colores que solo funcionan en uno de los dos temas.
- Cambiar el tema oscuro por defecto.

## Lo que queda al implementador

- Los valores exactos de cada color de categoría claro/oscuro, siempre que el test de contraste pase y cada categoría siga reconociéndose (misma familia de tono que hoy).
- El nombre y la ubicación de los helpers nuevos (traductor de errores, formateador de fechas, hueco del FAB), siguiendo la estructura de `core/`.
- El tamaño mínimo del `autoSize` de las etiquetas de la barra inferior, siempre que sea legible.
- Cómo se dan español y lunes al selector de fecha de Material, siempre que se cumpla el resultado.

## Llamadas mías (no del usuario)

- **Guardar** siempre activo y validación al intentar guardar (A5), en lugar de solo esconder el error hasta tocar el campo.
- **Resultados de acciones** (éxito o error) en snackbar; **fallos de carga** con `ErrorState` (A6).
- **Etiquetas de la barra inferior** con `autoSize` antes que acortar nombres (A4).
- **Prioridad** con bandera más nombre en todas las prioridades, incluida Media, como en la maqueta aprobada (A3).
- **«Mi día»** agrupa títulos repetidos como «Gimnasio (2)» (A8).
- La **accesibilidad que no se ve** (roles, estados, «Más opciones») pasa a la fase B, para que la fase A sean solo defectos visibles.
- **Errores de guardado** con la hoja abierta: se quedan en la hoja, no en el snackbar (A6).
- **Acceso y Perfil:** conservan su aviso en el formulario (A6).
- **Guardar sin conexión:** sigue desactivado donde ya lo está (A5).
- **Deshacer al añadir:**
  - mensaje neutro «Has añadido «<nombre>»»;
  - sin Deshacer en las altas en lote ni al crear un espacio;
  - las ediciones tampoco lo llevan.
- **Verificación visual:** al terminar la fase A se repiten en el emulador las capturas de las pantallas afectadas en oscuro, en claro y con letra al 130 %, y se comparan con las de la crítica.
