# Bobitos

Bobitos es una aplicación Android privada para coordinar la vida cotidiana de familias, parejas y pisos compartidos mediante espacios independientes y sincronizados prácticamente en tiempo real.

> El proyecto se encuentra actualmente en fase de planificación.

## Objetivo

Cada usuario podrá crear o unirse a varios espacios privados de entre 2 y 10 miembros. Dentro de cada espacio se compartirán:

- Una lista de la compra.
- Tareas asignables.
- Un calendario común.

La aplicación está diseñada para mantener un coste de infraestructura de 0 € o prácticamente 0 €, evitando un servidor propio y utilizando los niveles gratuitos de Firebase durante las primeras etapas.

## Estado del proyecto

`Planificación`

El alcance, la arquitectura y la hoja de ruta inicial están documentados en [PROJECT_PLAN.md](PROJECT_PLAN.md).

Repositorio oficial: [DLunaUNIZAR/Bobitos](https://github.com/DLunaUNIZAR/Bobitos).

## Funcionalidades previstas para el MVP

- Registro e inicio de sesión.
- Verificación de correo y recuperación de contraseña.
- Creación y selección de varios espacios.
- Invitaciones privadas mediante código o enlace.
- Roles de propietario y miembro.
- Gestión básica de miembros.
- Lista de la compra compartida.
- Tareas con responsables, fecha y prioridad.
- Calendario compartido con vistas mensual y agenda.
- Actualizaciones prácticamente en tiempo real.
- Modo sin conexión de solo lectura.
- Eliminación de cuenta y datos personales.
- Reglas de acceso y pruebas de seguridad.

## Fuera del alcance inicial

- iOS y web.
- Pagos, suscripciones y publicidad.
- Contenido público o chat.
- Archivos y fotografías subidas por usuarios.
- Inicio de sesión mediante SMS.
- Tareas y eventos recurrentes complejos.
- Sincronización con calendarios externos.
- Notificaciones push con la aplicación cerrada, salvo aprobación posterior.

## Tecnologías previstas

| Área | Tecnología |
| --- | --- |
| Plataforma | Android |
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose y Material 3 |
| Estado | ViewModel, Coroutines y StateFlow |
| Inyección de dependencias | Hilt |
| Autenticación | Firebase Authentication |
| Base de datos | Cloud Firestore |
| Preferencias locales | DataStore |
| Seguridad | Firebase Security Rules y App Check |
| Backend opcional | Cloud Functions con TypeScript |
| Notificaciones opcionales | Firebase Cloud Messaging |
| Control de versiones | Git y GitHub |

## Arquitectura

La aplicación seguirá una arquitectura por capas:

```text
Jetpack Compose
      ↓
ViewModels + StateFlow
      ↓
Repositorios
      ↓
Firebase / DataStore
```

Las pantallas no accederán directamente a Firebase. Los repositorios encapsularán el acceso a autenticación, espacios, compra, tareas y eventos.

## Comportamiento sin conexión

Cuando el dispositivo no tenga conexión:

- Se mostrará la última información disponible como desactualizada.
- La aplicación pasará a modo de solo lectura.
- No se podrán crear, editar ni eliminar elementos.
- No se generarán cambios nuevos para sincronizarlos posteriormente.
- Al recuperar la conexión se actualizará el espacio activo.

## Hoja de ruta resumida

1. Definición funcional.
2. Aprendizaje de Kotlin, Android y Git.
3. Diseño de pantallas y navegación.
4. Preparación técnica del proyecto.
5. Autenticación y perfil.
6. Espacios, miembros e invitaciones.
7. Tiempo real y conectividad.
8. Lista de la compra.
9. Tareas.
10. Calendario.
11. Avisos y notificaciones opcionales.
12. Seguridad, privacidad y calidad.
13. Beta y distribución.

La planificación detallada, los criterios de salida y las estimaciones se mantienen en [PROJECT_PLAN.md](PROJECT_PLAN.md).

## Hitos

### 1. Prototipo técnico multiusuario

Dos usuarios autenticados comparten un espacio y reciben una modificación en tiempo real, mientras un usuario ajeno no puede acceder.

### 2. Primera beta utilizable

Usuarios, espacios, invitaciones, miembros y lista de la compra en funcionamiento.

### 3. MVP completo

Lista de la compra, tareas, calendario, gestión de miembros y eliminación de cuenta.

### 4. Versión distribuible

Aplicación revisada, firmada, documentada y preparada para el canal privado elegido.

## Estructura prevista del repositorio

```text
.
├── README.md
├── PROJECT_PLAN.md
├── app/
│   └── src/
├── functions/          # Solo si se aprueba el backend opcional
├── firestore.rules
├── firestore.indexes.json
└── firebase.json
```

La estructura podrá modificarse cuando se cree el proyecto Android real.

## Desarrollo local

El proyecto todavía no contiene código ejecutable. Cuando comience la implementación, esta sección incluirá:

- Requisitos de Android Studio y Java.
- Configuración del proyecto Firebase de desarrollo.
- Variables y archivos locales necesarios.
- Ejecución de Firebase Emulator Suite.
- Compilación y ejecución de la app.
- Pruebas automatizadas.

## Costes

El núcleo se diseñará para funcionar dentro del plan gratuito de Firebase:

- Sin servidor propio.
- Sin almacenamiento de archivos.
- Sin autenticación por SMS.
- Sin Cloud Functions obligatorias.
- Consultas y listeners limitados.

La publicación mediante APK privado puede realizarse sin coste. Google Play requeriría el pago único de la cuenta de desarrollador.

## Documentación

- [Plan completo del proyecto](PROJECT_PLAN.md)
- Guía de contribución: pendiente.
- Decisiones de arquitectura: se añadirán cuando comience la implementación.

## Licencia

Aplicación destinada a espacios de uso privado y repositorio de código público. No se ha concedido por el momento una licencia de uso, copia o distribución.
