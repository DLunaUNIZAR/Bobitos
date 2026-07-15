# Plan del proyecto: Bobitos

> Documento vivo de planificación. Se actualizará conforme se tomen decisiones, se complete trabajo o cambie el alcance.

| Campo | Valor |
| --- | --- |
| Nombre del producto | Bobitos |
| Plataforma inicial | Android |
| Estado | Implementación — fase 3 en curso |
| Versión del documento | 0.5.1 |
| Última actualización | 15 de julio de 2026 |
| Distribución prevista | Privada |
| Repositorio | [DLunaUNIZAR/Bobitos](https://github.com/DLunaUNIZAR/Bobitos) |
| Visibilidad del repositorio | Pública |
| Coste objetivo | 0 € o prácticamente 0 € |

## 1. Resumen

Bobitos es una aplicación Android privada de organización compartida. Los usuarios podrán crear espacios independientes —por ejemplo, una familia, una pareja o un piso compartido— e invitar a otras personas.

Dentro de cada espacio se compartirán inicialmente tres módulos:

- Lista de la compra.
- Tareas.
- Calendario.

Los cambios realizados por un miembro deberán aparecer prácticamente en tiempo real en los dispositivos conectados del resto. Un mismo usuario podrá pertenecer a varios espacios y cambiar entre ellos.

La primera versión priorizará simplicidad, privacidad, seguridad y un coste de mantenimiento nulo o muy reducido. No se construirá un servidor propio y se evitarán las funcionalidades que obliguen a contratar infraestructura innecesaria.

## 2. Objetivos

### 2.1. Objetivo principal

Crear una aplicación Android útil y estable que permita a pequeños grupos privados coordinar actividades cotidianas desde un único espacio compartido.

### 2.2. Objetivos específicos

- Permitir el registro y acceso seguro de usuarios.
- Permitir que un usuario cree y gestione varios espacios.
- Permitir la incorporación mediante invitaciones privadas.
- Sincronizar cambios entre 2 y 10 miembros prácticamente en tiempo real.
- Proporcionar una lista de la compra compartida.
- Permitir crear, asignar y completar tareas.
- Mantener un calendario compartido sencillo.
- Evitar modificaciones cuando el dispositivo no tenga conexión.
- Proteger todos los datos mediante autenticación y reglas de acceso.
- Mantener el proyecto dentro de los niveles gratuitos siempre que sea razonable.
- Construir una base mantenible que pueda ampliarse posteriormente.

## 3. Alcance confirmado

### 3.1. Condiciones de producto

1. La aplicación será inicialmente solo para Android.
2. Los usuarios se organizarán en espacios privados.
3. Cada espacio tendrá aproximadamente entre 2 y 10 miembros.
4. Un usuario podrá pertenecer a varios espacios.
5. Los cambios se mostrarán prácticamente en tiempo real.
6. Sin conexión no se podrán emitir cambios ni recibir actualizaciones de otros usuarios.
7. La información descargada previamente podrá mostrarse como información desactualizada y en modo de solo lectura.
8. “Horarios” se implementará inicialmente como un calendario compartido.
9. No habrá pagos, publicidad, chat público ni contenido abierto a desconocidos.
10. La aplicación será privada y tendrá un coste objetivo de 0 € o prácticamente 0 €.

### 3.2. Incluido en el MVP

- Registro mediante correo y contraseña.
- Verificación de correo y recuperación de contraseña.
- Inicio y cierre de sesión.
- Perfil básico con nombre y avatar generado mediante iniciales.
- Creación, selección y gestión de espacios.
- Roles de propietario y miembro.
- Invitaciones privadas mediante código o enlace.
- Lista y gestión de miembros.
- Lista de la compra en tiempo real.
- Lista de tareas con un responsable asignable.
- Calendario compartido básico.
- Indicador de conexión.
- Modo sin conexión de solo lectura.
- Estados de carga, guardado y error.
- Eliminación de cuenta y tratamiento de sus datos.
- Reglas de seguridad y pruebas de acceso.

### 3.3. Fuera del MVP

- Aplicación para iOS.
- Aplicación web.
- Pagos o suscripciones.
- Publicidad.
- Chat.
- Contenido o perfiles públicos.
- Archivos adjuntos y fotografías subidas por usuarios.
- Inicio de sesión mediante SMS.
- Tareas recurrentes.
- Eventos recurrentes complejos.
- Sincronización con Google Calendar u otros calendarios.
- Subtareas, comentarios e historial completo de actividad.
- Turnos rotatorios o cuadrantes laborales.
- Widgets de Android.
- Copias de seguridad automáticas de pago.
- Notificaciones push con la app cerrada, hasta que se apruebe su fase opcional.

## 4. Usuarios, espacios y permisos

### 4.1. Tipos de usuario dentro de un espacio

#### Propietario

- Consultar y modificar el contenido compartido.
- Renombrar el espacio.
- Crear y revocar invitaciones.
- Consultar la lista de miembros.
- Expulsar miembros.
- Transferir la propiedad.
- Eliminar el espacio.

#### Miembro

- Consultar y modificar el contenido compartido.
- Consultar los miembros del espacio.
- Abandonar el espacio.
- No puede cambiar roles, expulsar miembros ni eliminar el espacio.

### 4.2. Reglas confirmadas

- [x] Las invitaciones serán de un solo uso.
- [x] Las invitaciones caducarán a las 72 horas.
- [x] Solo podrán aceptarlas cuentas con correo verificado.
- [x] El propietario deberá transferir la propiedad o eliminar el espacio antes de abandonarlo.
- [x] Los productos comprados permanecerán al final de la lista hasta que un miembro pulse “Limpiar comprados”.
- [x] La lista de la compra no tendrá un responsable asignado.
- [x] Cada tarea tendrá un único responsable principal en el MVP.
- [x] Los eventos serán visibles para todo el espacio y podrán tener participantes opcionales.
- [x] Cualquier miembro podrá editar compra, tareas y eventos.
- [x] Las notificaciones push quedarán fuera del MVP.
- [x] El canal de distribución se decidirá durante la beta.

## 5. Requisitos funcionales

### 5.1. Autenticación y cuenta

- Crear una cuenta con correo, contraseña y nombre.
- Verificar el correo electrónico.
- Iniciar sesión.
- Recuperar la contraseña.
- Mantener la sesión iniciada.
- Cerrar sesión.
- Editar el nombre visible.
- Eliminar la cuenta.

### 5.2. Espacios

- Crear un espacio.
- Ver todos los espacios a los que pertenece el usuario.
- Cambiar de espacio activo.
- Recordar el último espacio seleccionado.
- Renombrar el espacio si se tienen permisos.
- Abandonar un espacio.
- Eliminar un espacio si se es propietario.

### 5.3. Invitaciones y miembros

- Generar una invitación segura.
- Compartirla como código o enlace.
- Consultar su caducidad y estado.
- Revocarla.
- Aceptarla con una cuenta autenticada.
- Mostrar los miembros y sus roles.
- Expulsar un miembro si se tienen permisos.
- Transferir la propiedad.

### 5.4. Lista de la compra

- Añadir un producto.
- Introducir cantidad y observaciones.
- Editar un producto.
- Marcarlo como comprado.
- Desmarcarlo.
- Mostrar quién lo añadió y quién lo marcó.
- No asignar la compra ni sus productos a un responsable.
- Eliminarlo o archivarlo.
- Mostrar primero los productos pendientes.
- Limpiar los productos comprados.

### 5.5. Tareas

- Crear una tarea.
- Introducir título y descripción.
- Asignarla a un único responsable principal.
- Permitir reasignarla a otro miembro activo.
- Añadir fecha límite y prioridad.
- Completar y reabrir la tarea.
- Mostrar quién la creó y completó.
- Filtrar por miembro, estado, fecha y prioridad.
- Identificar tareas próximas y vencidas.

### 5.6. Calendario

- Crear, editar y eliminar eventos.
- Definir fecha y hora inicial y final.
- Crear eventos de día completo.
- Seleccionar participantes.
- Mantener todos los eventos visibles para todos los miembros, participen o no.
- Añadir descripción y color.
- Mostrar una vista mensual.
- Mostrar una vista de agenda.
- Mostrar próximos eventos en el inicio.
- Tratar correctamente la zona horaria y los cambios de hora.

### 5.7. Conectividad

- Detectar si la aplicación no dispone de conexión útil.
- Informar claramente del estado sin conexión.
- Mostrar los últimos datos disponibles como información desactualizada.
- Deshabilitar todas las acciones de modificación sin conexión.
- No confirmar una operación hasta recibir respuesta del backend.
- Recuperar los listeners y actualizar los datos al volver la conexión.
- Diferenciar entre guardando, guardado y error.

## 6. Requisitos no funcionales

### 6.1. Seguridad y privacidad

- Todo acceso requiere autenticación.
- Solo los miembros pueden leer los datos de un espacio.
- Solo el propietario puede administrar roles y miembros.
- Una invitación no puede enumerarse ni adivinarse fácilmente.
- No se almacenarán secretos en el código de la aplicación.
- Las reglas de seguridad tendrán pruebas automatizadas.
- Se minimizarán los datos personales recopilados.
- La base de datos se ubicará en una región europea.
- La app proporcionará un mecanismo de eliminación de cuenta.
- El repositorio será público, pero no contendrá credenciales, claves privadas ni datos de producción.

### 6.2. Rendimiento

- Los cambios deberían aparecer en los dispositivos conectados en pocos segundos en condiciones normales.
- Los listeners solo permanecerán activos para el espacio y la pantalla necesarios.
- El calendario consultará intervalos de fechas limitados.
- Las listas no descargarán historiales ilimitados.
- La interfaz debe responder inmediatamente y mostrar el estado real de guardado.

### 6.3. Accesibilidad y experiencia de uso

- Tamaños táctiles adecuados.
- Contraste legible.
- Compatibilidad con aumento del tamaño de fuente.
- Etiquetas accesibles para iconos.
- Mensajes de error comprensibles.
- Estados vacíos y de carga consistentes.

### 6.4. Mantenibilidad

- Separación entre interfaz, estado y acceso a datos.
- Acceso a Firebase encapsulado detrás de repositorios.
- Componentes reutilizables.
- Nombres y código en inglés; documentación funcional en español.
- Historial de cambios mediante Git.
- Decisiones técnicas relevantes registradas en este documento.

## 7. Arquitectura prevista

La aplicación seguirá una arquitectura por capas sencilla:

```text
Pantallas Jetpack Compose
        ↓
ViewModels + StateFlow
        ↓
Repositorios
        ↓
Firebase Authentication / Cloud Firestore / DataStore
```

### 7.1. Interfaz

- Jetpack Compose.
- Material 3.
- Navigation Compose.
- Componentes sin acceso directo a Firebase.

### 7.2. Presentación

- Un ViewModel por pantalla o funcionalidad principal.
- Estado observable mediante StateFlow.
- Eventos de usuario enviados al ViewModel.
- Estados explícitos de carga, contenido, error y ausencia de conexión.

### 7.3. Datos

- Repositorios para autenticación, espacios, compra, tareas y eventos.
- Cloud Firestore como fuente compartida.
- DataStore para preferencias locales sencillas.
- Caché local solo para consulta cuando no exista conexión.

### 7.4. Backend opcional

Cloud Functions no formará parte obligatoria del MVP. Solo se incorporará si se aprueban notificaciones push con la app cerrada u operaciones que requieran privilegios de servidor.

## 8. Tecnologías

| Área | Tecnología prevista |
| --- | --- |
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose y Material 3 |
| Navegación | Navigation Compose |
| Estado | ViewModel, Coroutines y StateFlow |
| Inyección de dependencias | Hilt |
| Autenticación | Firebase Authentication |
| Base de datos | Cloud Firestore |
| Preferencias | DataStore |
| Seguridad backend | Firebase Security Rules |
| Protección adicional | Firebase App Check, antes de producción |
| Backend opcional | Cloud Functions con TypeScript |
| Notificaciones opcionales | Firebase Cloud Messaging |
| Repositorio | Git y GitHub |
| Diseño | Figma |
| Pruebas locales de Firebase | Firebase Emulator Suite |

## 9. Modelo de datos inicial

> Modelo provisional. Se revisará antes de implementar la fase multiusuario.

```text
users/{userId}
spaces/{spaceId}
memberships/{spaceId_userId}
spaces/{spaceId}/shoppingItems/{itemId}
spaces/{spaceId}/tasks/{taskId}
spaces/{spaceId}/events/{eventId}
invitations/{invitationId}
```

Cada producto, tarea y evento será un documento independiente. Esto reduce conflictos cuando varias personas modifican elementos diferentes a la vez.

Las fechas con hora se almacenarán como instantes UTC y conservarán la zona horaria necesaria para presentarlas correctamente.

## 10. Estrategia de costes

### 10.1. Medidas obligatorias

- Utilizar inicialmente el plan gratuito Spark.
- Mantener un único Firestore por proyecto.
- Separar desarrollo y producción en proyectos distintos.
- No activar Cloud Functions durante el desarrollo del núcleo.
- No utilizar autenticación por SMS.
- No almacenar archivos ni fotografías.
- Limitar listeners, consultas e historiales.
- Revisar periódicamente lecturas y escrituras.
- No activar servicios de pago sin una decisión registrada.

### 10.2. Distribución

- APK firmado compartido manualmente: 0 €, con actualizaciones manuales.
- Google Play: pago único de registro y actualizaciones automáticas.

La elección definitiva de distribución se tomará durante la fase de beta.

## 11. Fases de trabajo

### Fase 0. Definición funcional

Duración estimada: 1 semana.

- [x] Confirmar el nombre del producto: Bobitos.
- [x] Cerrar el alcance del MVP.
- [x] Crear la matriz de roles y permisos.
- [x] Resolver el ciclo de vida de espacios e invitaciones.
- [x] Redactar flujos de usuario.
- [x] Definir criterios de aceptación.
- [x] Revisar el modelo de datos inicial.

**Criterio de salida:** no quedan decisiones funcionales importantes ambiguas para construir el primer prototipo.

### Fase 1. Aprendizaje básico

Duración estimada: 2-4 semanas.

- [ ] Aprender fundamentos de programación.
- [ ] Aprender sintaxis básica de Kotlin.
- [ ] Trabajar con clases, colecciones y valores nulos.
- [ ] Aprender Coroutines y Flow a nivel inicial.
- [ ] Conocer Android Studio y el emulador.
- [ ] Crear interfaces sencillas con Compose.
- [ ] Aprender navegación y ViewModel.
- [ ] Aprender las operaciones esenciales de Git.
- [ ] Crear una aplicación local pequeña de práctica.

**Criterio de salida:** se puede crear y depurar una app local con varias pantallas y estado básico.

### Fase 2. Diseño de experiencia y pantallas

Duración estimada: 1-2 semanas.

- [ ] Crear el mapa de navegación.
- [ ] Diseñar las pantallas de autenticación.
- [ ] Diseñar la selección y creación de espacios.
- [ ] Diseñar compra, tareas y calendario.
- [ ] Diseñar miembros y ajustes.
- [ ] Diseñar estados vacíos, de carga, error y offline.
- [ ] Definir el sistema visual.
- [ ] Crear un prototipo navegable.

**Criterio de salida:** todos los flujos principales pueden recorrerse en el prototipo sin escribir código de producción.

### Fase 3. Base técnica

Duración estimada: 1-2 semanas.

- [x] Crear el proyecto Android.
- [x] Elegir el identificador de la aplicación: `com.dlunaunizar.bobitos`.
- [x] Configurar Compose, Material 3 y navegación.
- [x] Definir la estructura de paquetes.
- [x] Configurar Hilt, Coroutines y StateFlow.
- [x] Configurar Git y GitHub.
- [x] Crear el proyecto Firebase de desarrollo `bobitos-dev` en el plan Spark.
- [ ] Crear Firestore de desarrollo en `europe-southwest1`.
- [ ] Activar Authentication y Firestore.
- [x] Configurar reglas inicialmente cerradas.
- [x] Configurar Firebase Emulator Suite.

**Criterio de salida:** la app navega, compila y se conecta de forma segura al entorno de desarrollo.

### Fase 4. Autenticación y cuenta

Duración estimada: 1-2 semanas.

- [ ] Implementar registro.
- [ ] Implementar verificación de correo.
- [ ] Implementar inicio de sesión.
- [ ] Implementar recuperación de contraseña.
- [ ] Mantener y cerrar sesión.
- [ ] Crear y editar el perfil básico.
- [ ] Probar errores y accesos no autenticados.

**Criterio de salida:** un usuario puede completar de forma segura todo el ciclo de acceso.

### Fase 5. Espacios, miembros e invitaciones

Duración estimada: 2-3 semanas.

- [ ] Crear, listar, seleccionar y renombrar espacios.
- [ ] Implementar propietario y miembro.
- [ ] Generar, aceptar y revocar invitaciones.
- [ ] Mostrar y administrar miembros.
- [ ] Implementar abandono, expulsión y transferencia de propiedad.
- [ ] Implementar eliminación del espacio.
- [ ] Crear y probar reglas de seguridad multiusuario.

**Criterio de salida:** dos usuarios pueden compartir un espacio y un tercero no autorizado no puede acceder.

### Fase 6. Tiempo real y conectividad

Duración estimada: 1 semana.

- [ ] Crear listeners limitados al espacio activo.
- [ ] Cancelar listeners innecesarios.
- [ ] Detectar ausencia de conexión.
- [ ] Mostrar el modo offline de solo lectura.
- [ ] Bloquear modificaciones sin conexión.
- [ ] Gestionar operaciones interrumpidas.
- [ ] Actualizar datos al recuperar la conexión.

**Criterio de salida:** dos dispositivos reflejan cambios conectados y no generan nuevos cambios desconectados.

### Fase 7. Lista de la compra

Duración estimada: 1-2 semanas.

- [ ] Añadir, editar y eliminar productos.
- [ ] Añadir cantidad y observaciones.
- [ ] Marcar y desmarcar productos.
- [ ] Registrar autor y usuario que completa.
- [ ] Ordenar pendientes y comprados.
- [ ] Archivar o limpiar comprados.
- [ ] Validar entradas y conflictos básicos.
- [ ] Probar la funcionalidad con varios dispositivos.

**Criterio de salida:** la lista de la compra puede utilizarse diariamente por un espacio real.

### Fase 8. Tareas

Duración estimada: 2 semanas.

- [ ] Crear, editar y eliminar tareas.
- [ ] Asignar un responsable principal.
- [ ] Añadir fecha límite y prioridad.
- [ ] Completar y reabrir tareas.
- [ ] Mostrar autor y usuario que completa.
- [ ] Implementar filtros.
- [ ] Identificar tareas próximas y vencidas.
- [ ] Probar concurrencia y permisos.

**Criterio de salida:** los miembros pueden repartir y supervisar tareas sin ambigüedad.

### Fase 9. Calendario

Duración estimada: 2-3 semanas.

- [ ] Crear, editar y eliminar eventos.
- [ ] Implementar eventos con hora y de día completo.
- [ ] Añadir participantes, descripción y color.
- [ ] Implementar vista de agenda.
- [ ] Implementar vista mensual.
- [ ] Mostrar próximos eventos.
- [ ] Tratar UTC, zona horaria y cambios de hora.
- [ ] Limitar consultas por intervalos.

**Criterio de salida:** el espacio dispone de un calendario básico compartido y fiable.

### Fase 10. Avisos y notificaciones

Duración estimada: 1-2 semanas. La parte push es opcional.

- [ ] Añadir avisos y contadores dentro de la aplicación.
- [ ] Añadir preferencias de avisos.
- [ ] Decidir si se necesitan notificaciones con la app cerrada.
- [ ] Si se aprueban, activar Blaze con controles de coste.
- [ ] Si se aprueban, implementar Cloud Functions y FCM.
- [ ] Evitar duplicados y avisos al autor del cambio.

**Criterio de salida:** los avisos elegidos funcionan y su impacto económico está aceptado.

### Fase 11. Seguridad, privacidad y calidad

Duración estimada: 2-3 semanas.

- [ ] Auditar todas las reglas de Firestore.
- [ ] Activar App Check.
- [ ] Implementar eliminación de cuenta y datos.
- [ ] Redactar política de privacidad.
- [ ] Añadir pruebas unitarias, de integración y de interfaz.
- [ ] Probar varios espacios y dispositivos.
- [ ] Probar reinicios, modo avión y cambios de usuario.
- [ ] Revisar accesibilidad.
- [ ] Medir lecturas, escrituras y rendimiento.

**Criterio de salida:** no existen fallos críticos conocidos ni accesos indebidos en las pruebas.

### Fase 12. Beta y distribución

Duración estimada: 1-2 semanas de preparación, más el periodo de prueba.

- [ ] Crear una versión firmada.
- [ ] Definir el grupo de beta.
- [ ] Elegir APK privado o Google Play.
- [ ] Instalar en dispositivos reales.
- [ ] Recopilar y priorizar incidencias.
- [ ] Corregir fallos críticos.
- [ ] Revisar consumo y estabilidad.
- [ ] Preparar la primera versión estable.

**Criterio de salida:** la aplicación puede distribuirse y actualizarse mediante el canal elegido.

## 12. Hitos

### Hito 1. Prototipo técnico multiusuario

Incluye:

- Registro e inicio de sesión.
- Creación de un espacio.
- Incorporación de un segundo usuario.
- Un dato compartido actualizado en tiempo real.
- Reglas que impiden el acceso de un tercer usuario.

**Objetivo:** validar la arquitectura y la seguridad antes de construir módulos completos.

### Hito 2. Primera beta utilizable

Incluye:

- Usuarios.
- Espacios e invitaciones.
- Gestión básica de miembros.
- Lista de la compra.
- Tiempo real.
- Modo offline de solo lectura.

**Objetivo:** empezar a utilizar la app en un grupo privado real.

### Hito 3. MVP completo

Incluye:

- Lista de la compra.
- Tareas.
- Calendario.
- Gestión completa de miembros.
- Eliminación de cuenta.
- Seguridad y pruebas principales.

**Objetivo:** completar el producto inicialmente definido.

### Hito 4. Versión distribuible

Incluye:

- Correcciones de la beta.
- Política de privacidad.
- Protección adicional.
- Firma de producción.
- Canal de distribución elegido.

**Objetivo:** mantener una versión privada estable y actualizable.

### Hito 5. Ampliaciones opcionales

Posibles elementos:

- Notificaciones con la app cerrada.
- Tareas recurrentes.
- Eventos recurrentes.
- Widgets.
- Integración con calendarios externos.
- Mejoras solicitadas durante la beta.

## 13. Estimación general

Estimación para una dedicación aproximada de 8-12 horas semanales:

| Entrega | Tiempo acumulado orientativo |
| --- | --- |
| Prototipo técnico | 6-9 semanas |
| Beta con lista de la compra | 10-14 semanas |
| MVP con tareas y calendario | 18-24 semanas |
| Versión revisada para distribuir | 22-28 semanas |

Las estimaciones incluyen aprendizaje, desarrollo, pruebas y correcciones. Se revisarán después del primer prototipo técnico.

## 14. Riesgos y limitaciones

| Riesgo | Tratamiento previsto |
| --- | --- |
| Reglas de seguridad incorrectas | Diseñarlas junto a cada función y probarlas en el emulador |
| Lecturas excesivas de Firestore | Limitar listeners, intervalos e historiales |
| Pérdida de conexión durante un guardado | Estados explícitos y confirmación del backend |
| Conflictos simultáneos | Documentos pequeños e independientes |
| Crecimiento descontrolado del alcance | Mantener el MVP y registrar ampliaciones aparte |
| Dependencia de Firebase | Encapsularlo mediante repositorios |
| Costes inesperados | Mantener Spark y aprobar expresamente cualquier servicio de pago |
| Complejidad de fechas | UTC, zona horaria y pruebas de cambios de hora |
| Falta de experiencia inicial | Fase de aprendizaje y desarrollo por hitos pequeños |

## 15. Decisiones registradas

| Fecha | Decisión | Motivo |
| --- | --- | --- |
| 14/07/2026 | Android nativo | La primera versión será exclusivamente Android |
| 14/07/2026 | Kotlin y Jetpack Compose | Tecnologías modernas recomendadas para Android |
| 14/07/2026 | Firebase Authentication y Firestore | Reducir backend y permitir tiempo real con bajo coste |
| 14/07/2026 | Espacios privados de 2-10 miembros | Alcance objetivo confirmado |
| 14/07/2026 | Un usuario puede pertenecer a varios espacios | Requisito funcional confirmado |
| 14/07/2026 | Sin conexión, solo lectura | No se emitirán cambios desde el dispositivo desconectado |
| 14/07/2026 | Calendario compartido básico | Interpretación inicial de “horarios” |
| 14/07/2026 | Cloud Functions fuera del núcleo | Mantener el coste inicial en 0 € |
| 14/07/2026 | Sin archivos ni fotos en el MVP | Reducir coste, complejidad y datos personales |
| 14/07/2026 | El producto se llamará Bobitos | Nombre definitivo confirmado |
| 14/07/2026 | Repositorio público `DLunaUNIZAR/Bobitos` | Ubicación definitiva del código y la documentación |
| 14/07/2026 | Invitaciones de un solo uso durante 72 horas | Reducir accesos accidentales y simplificar su ciclo de vida |
| 14/07/2026 | Correo verificado para aceptar invitaciones | Evitar miembros con identidades no verificadas |
| 14/07/2026 | Compra compartida sin responsable | La lista representa una necesidad común, no una asignación |
| 14/07/2026 | Una persona responsable por tarea | Mantener una responsabilidad clara en el MVP |
| 14/07/2026 | Eventos visibles para todo el espacio | Los participantes son informativos y no limitan el acceso |
| 14/07/2026 | Edición de contenido para todos los miembros | Modelo colaborativo para compra, tareas y calendario |
| 14/07/2026 | Notificaciones push fuera del MVP | Mantener menor complejidad y coste inicial |
| 14/07/2026 | Identificador `com.dlunaunizar.bobitos` | Nombre único y estable para el proyecto Android |
| 14/07/2026 | Proyecto ficticio local `demo-bobitos` | Probar Auth y Firestore sin credenciales, recursos remotos ni facturación |
| 14/07/2026 | Firestore remoto de desarrollo en `europe-southwest1` | Mantener los datos en Madrid y reducir la latencia para los usuarios iniciales |
| 15/07/2026 | Proyecto remoto `bobitos-dev` (`853960607744`) en Spark | Disponer de un entorno real separado sin activar servicios de pago |
| 15/07/2026 | Google Analytics activado sin integrar su SDK en Android | Mantener disponible la opción gratuita sin recopilar telemetría durante el MVP |

## 16. Próximas decisiones

- [ ] Colores y diseño visual.
- [ ] Distribución mediante APK o Google Play.
- [ ] Necesidad de notificaciones push después de validar el MVP.

## 17. Historial del documento

| Versión | Fecha | Cambios |
| --- | --- | --- |
| 0.1.0 | 14/07/2026 | Creación inicial del plan, fases, hitos, alcance y arquitectura |
| 0.2.0 | 14/07/2026 | Confirmación del nombre Bobitos y asociación con el repositorio público de GitHub |
| 0.3.0 | 14/07/2026 | Cierre de decisiones funcionales y creación de especificación, permisos, flujos y modelo de datos |
| 0.4.0 | 14/07/2026 | Inicio de implementación: proyecto Android, arquitectura base y navegación provisional |
| 0.5.0 | 14/07/2026 | Firebase local: conexión Android, Emulator Suite, reglas cerradas, datos de prueba y pruebas automatizadas |
| 0.5.1 | 15/07/2026 | Registro del proyecto remoto `bobitos-dev`, alias de entorno y decisión sobre Analytics |
