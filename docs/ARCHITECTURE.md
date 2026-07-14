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

## Navegación provisional

La aplicación comienza en la selección de espacio. Una vez elegido, presenta navegación inferior entre Compra, Tareas y Calendario, además de una acción para cambiar de espacio.

Esta navegación es funcional pero provisional: se ajustará cuando se cierre el diseño del issue correspondiente.

## Fuente de datos actual

`InMemorySpaceRepository` permite ejecutar y probar la estructura sin servicios externos ni credenciales. Se sustituirá mediante inyección por una implementación Firebase en las fases de autenticación y espacios.

Los archivos locales, claves y credenciales no deben añadirse al repositorio. La configuración de Firebase de desarrollo se documentará cuando se incorpore.
