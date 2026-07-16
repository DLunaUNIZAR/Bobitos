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

Una cuenta verificada comienza en el último espacio válido guardado para ese usuario o, si no existe, en la selección de espacio. Una vez elegido, presenta navegación inferior entre Compra, Tareas y Calendario, además de acciones para cambiar de espacio, administrarlo y abrir el perfil.

Esta navegación es funcional pero provisional: se ajustará cuando se cierre el diseño del issue correspondiente.

## Fuente de datos actual

`FirebaseAuthRepository` encapsula Firebase Authentication y publica la sesión mediante `StateFlow`. Implementa registro, verificación, acceso, recuperación de contraseña, actualización de nombre y cierre de sesión. La respuesta de recuperación es deliberadamente neutra para no revelar si una cuenta existe.

`FirestoreSpaceRepository` observa las membresías del usuario y los documentos de sus espacios. Implementa creación, renombrado, abandono, expulsión y transferencia de propiedad mediante operaciones atómicas. Las tareas pendientes del miembro que sale quedan sin responsable dentro de la misma transacción.

`DataStoreActiveSpaceRepository` conserva el espacio activo de forma independiente para cada UID. El valor solo se utiliza si el usuario sigue perteneciendo al espacio.

En compilaciones `debug`, `FirebaseInitializer` crea una aplicación Firebase para el proyecto ficticio `demo-bobitos` y conecta Authentication y Firestore a Emulator Suite. Firestore usa caché en memoria para evitar mezclar datos locales entre ejecuciones. Authentication y Firestore son las fuentes reales de la sesión y de los espacios.

Las Security Rules exigen correo verificado y una membresía activa para leer un espacio. Solo el propietario puede renombrar, expulsar o transferir la propiedad. Los cambios de propietario y las bajas de miembros se validan mediante `getAfter()` para impedir estados parciales.

Las compilaciones `release` no inicializan el proyecto ficticio ni se conectan a emuladores. La configuración de un proyecto real se incorporará explícitamente cuando se habilite el entorno remoto.

Los archivos locales, claves y credenciales no deben añadirse al repositorio. La configuración y los comandos del entorno local se mantienen en [FIREBASE_DEVELOPMENT.md](FIREBASE_DEVELOPMENT.md).
