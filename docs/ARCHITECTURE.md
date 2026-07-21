# Arquitectura de Bobitos

Este documento recoge la arquitectura implementada y las reglas que deben mantenerse al añadir funcionalidades.

## Base técnica

- Identificador de aplicación y `namespace`: `com.dlunaunizar.bobitos`.
- Kotlin y Jetpack Compose.
- Material 3 y Navigation Compose.
- ViewModel, Coroutines y StateFlow para el estado observable.
- Hilt para inyección de dependencias.
- Android 8.0 (API 26) como versión mínima.
- Android 17 (API 37.0) como SDK de compilación y objetivo inicial.

## Capas y paquetes

```text
app/             Entrada de la aplicación y estado global mínimo
core/common/     Tipos reutilizables, como UiState
core/model/      Modelos compartidos independientes de la fuente de datos
core/navigation/ Rutas y navegación principal
core/designsystem/ Tema y elementos visuales comunes
data/            Repositorios, implementaciones y módulos de inyección
feature/         Pantallas y lógica de cada funcionalidad
```

El flujo de dependencias es `feature/app -> repositorios -> fuentes de datos`. Las capas de datos no dependen de Compose y una pantalla no accede directamente a Firebase.

## Estado de interfaz

Las operaciones asíncronas deben representarse mediante `UiState`:

- `Loading`: todavía no hay un resultado disponible.
- `Content`: hay contenido válido, incluida una colección vacía.
- `Error`: la operación no pudo completarse.

Los ViewModel exponen `StateFlow` inmutable. Los composables reciben estado y callbacks; no conocen la implementación concreta del repositorio.

## Navegación y sesión

`BobitosApp` actúa como barrera de sesión. Muestra el flujo de acceso cuando no existe usuario, la pantalla de verificación cuando el correo está pendiente y solo expone el contenido privado a cuentas verificadas. Al cerrar sesión desaparece inmediatamente el árbol de navegación privado.

Una cuenta verificada comienza en el último espacio válido guardado para ese usuario o, si no existe, en la selección de espacio. El nivel principal dispone de navegación inferior entre Espacios y Mi calendario. Una vez elegido un espacio, presenta navegación inferior entre Compra, Tareas, Calendario y Comidas, además de acciones para cambiar de espacio, administrarlo y abrir el perfil.

Esta navegación es funcional pero provisional: se ajustará cuando se cierre el diseño del issue correspondiente.

## Fuente de datos actual

`FirebaseAuthRepository` encapsula Firebase Authentication y publica la sesión mediante `StateFlow`. Implementa registro, verificación, acceso, recuperación de contraseña, actualización de nombre y cierre de sesión. La respuesta de recuperación es deliberadamente neutra para no revelar si una cuenta existe.

Al restaurar una sesión, `AppViewModel` renueva primero el usuario y su token de ID antes de activar listeners privados. Esto garantiza que las reglas reciban el claim actualizado `email_verified`; si el arranque es realmente offline, conserva el usuario almacenado para permitir la lectura de la caché sin habilitar escrituras.

`FirestoreSpaceRepository` observa las membresías, los espacios y las invitaciones que administra un propietario. Implementa creación, renombrado, abandono, expulsión, transferencia de propiedad y el ciclo completo de invitaciones. Las tareas pendientes del miembro que sale quedan sin responsable dentro de la misma transacción.

La observación cambia con la navegación. La selección de espacios activa la membresía del usuario y los espacios de esa lista; el área de trabajo mantiene únicamente la membresía y el documento del espacio activo; el perfil no necesita listeners de espacios. Los listeners de miembros e invitaciones solo existen mientras está visible la gestión del espacio. Los cambios de pantalla, espacio o sesión cancelan el alcance anterior antes de crear el nuevo.

El calendario de un espacio mantiene un único listener limitado al día, semana o cuadrícula mensual visibles. Los filtros por miembro se aplican sobre ese snapshot y no crean lecturas adicionales. `PersonalCalendarViewModel` combina un listener acotado por cada espacio activo, conserva únicamente eventos cuyo `participantIds` contiene el UID actual y cancela todos los listeners al abandonar Mi calendario o cambiar de intervalo.

Los tokens de invitación se generan localmente con 160 bits de entropía y Base32. `MainActivity` conserva los deep links `bobitos://invite/...` hasta que la cuenta está autenticada y verificada; la navegación privada entrega después el código al flujo de aceptación.

`DataStoreActiveSpaceRepository` conserva el espacio activo de forma independiente para cada UID. El valor solo se utiliza si el usuario sigue perteneciendo al espacio.

En compilaciones `debug`, `FirebaseInitializer` crea una aplicación Firebase para el proyecto ficticio `demo-bobitos` y conecta Authentication y Firestore a Emulator Suite. Firestore usa una caché persistente limitada a 20 MiB, igual que requiere el modo de consulta offline. Authentication y Firestore son las fuentes reales de la sesión y de los espacios.

## Conectividad y escrituras

`AndroidConnectivityRepository` solo considera útil una red con capacidades `INTERNET` y `VALIDATED`. `FirestoreSyncRepository` traduce ese transporte a tres estados: `OFFLINE`, `REFRESHING` y `ONLINE`.

Recuperar la red no habilita cambios inmediatamente. Primero se fuerza una lectura `SERVER` de la membresía y el espacio activos; solo después se publica `ONLINE`. La interfaz deshabilita las acciones mientras tanto y `FirestoreSpaceRepository` vuelve a comprobar el estado antes de cada mutación. Las mutaciones usan transacciones de Firestore, que fallan si la conexión desaparece en lugar de confirmar un cambio local encolado.

Los snapshots ya disponibles permanecen visibles cuando se pierde la red y se identifican mediante el banner de datos desactualizados. Los estados de presentación distinguen guardando, guardado y error.

## Métricas de tiempo real

`RealtimeMetrics` registra en Logcat, con la etiqueta `BobitosRealtime`, cada alta y baja de listener, el total activo, el origen caché/servidor de cada snapshot y los documentos cambiados recibidos. La misma etiqueta informa de las lecturas explícitas de resincronización:

- selección de espacios: un listener de membresías y uno por espacio mostrado;
- módulo activo: un listener de membresía y uno del espacio seleccionado;
- gestión: añade miembros y, para el propietario, invitaciones;
- cambio entre Compra, Tareas, Calendario y Comidas: cero listeners o lecturas adicionales mientras no cambie el espacio;
- Mi calendario: un listener de eventos por espacio activo, siempre limitado al intervalo visible;
- Recetario: destino independiente del espacio (no es un módulo de espacio); mientras está abierto mantiene dos listeners acotados de la colección global `recipes` (catálogo común y recetas propias) y los libera al salir;
- resincronización del espacio: dos lecturas de servidor; una si la membresía ya no existe.

Las Security Rules exigen correo verificado y una membresía activa para leer un espacio. Solo el propietario puede renombrar, expulsar, transferir la propiedad y administrar invitaciones. El consumo de una invitación valida con `getAfter()` la transición a `USED`, la membresía nueva y el incremento del contador como una única operación. El emulador prueba además que dos cuentas simultáneas no pueden consumir el mismo token.

Las compilaciones `release` no inicializan el proyecto ficticio ni se conectan a emuladores. La configuración de un proyecto real se incorporará explícitamente cuando se habilite el entorno remoto.

Los archivos locales, claves y credenciales no deben añadirse al repositorio. La configuración y los comandos del entorno local se mantienen en [FIREBASE_DEVELOPMENT.md](FIREBASE_DEVELOPMENT.md).
