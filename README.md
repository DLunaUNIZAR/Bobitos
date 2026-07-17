# Bobitos

Bobitos es una aplicación Android privada para coordinar la vida cotidiana de familias, parejas y pisos compartidos mediante espacios independientes y sincronizados prácticamente en tiempo real.

> El proyecto se encuentra en implementación. La compra, las tareas y el calendario compartido ya funcionan sobre la base multiusuario.

## Objetivo

Cada usuario podrá crear o unirse a varios espacios privados de entre 2 y 10 miembros. Dentro de cada espacio se compartirán:

- Una lista de la compra.
- Tareas asignables.
- Un calendario común.

La aplicación está diseñada para mantener un coste de infraestructura de 0 € o prácticamente 0 €, evitando un servidor propio y utilizando los niveles gratuitos de Firebase durante las primeras etapas.

## Estado del proyecto

`Implementación — Fase 12 preparada para distribuir la primera beta privada`

El alcance, la arquitectura y la hoja de ruta inicial están documentados en [PROJECT_PLAN.md](PROJECT_PLAN.md).

La política aplicable al MVP está en [PRIVACY_POLICY.md](PRIVACY_POLICY.md), la distribución en [docs/BETA_DISTRIBUTION.md](docs/BETA_DISTRIBUTION.md) y la activación de protección del cliente en [docs/APP_CHECK.md](docs/APP_CHECK.md).

Repositorio oficial: [DLunaUNIZAR/Bobitos](https://github.com/DLunaUNIZAR/Bobitos).

## Funcionalidades previstas para el MVP

- Registro e inicio de sesión.
- Verificación de correo y recuperación de contraseña.
- Creación y selección de varios espacios.
- Invitaciones privadas mediante código o enlace.
- Roles de propietario y miembro.
- Gestión básica de miembros.
- Lista de la compra compartida, sin responsable asignado.
- Tareas con un único responsable principal, fecha y prioridad.
- Calendario compartido con vistas diaria, semanal y mensual, y filtros por miembro.
- “Mi calendario” con los eventos de todos los espacios donde participa el usuario.
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
| Distribución beta | Firebase App Distribution |
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

## Gestión del trabajo

- [Todas las issues abiertas](https://github.com/DLunaUNIZAR/Bobitos/issues)
- [Hito 1 · Prototipo técnico multiusuario](https://github.com/DLunaUNIZAR/Bobitos/issues/15)
- [Hito 2 · Primera beta utilizable](https://github.com/DLunaUNIZAR/Bobitos/issues/16)
- [Hito 3 · MVP completo](https://github.com/DLunaUNIZAR/Bobitos/issues/17)
- [Hito 4 · Versión distribuible](https://github.com/DLunaUNIZAR/Bobitos/issues/18)
- [Hito 5 · Ampliaciones opcionales](https://github.com/DLunaUNIZAR/Bobitos/issues/19)

## Hitos

### 1. Prototipo técnico multiusuario

Dos usuarios autenticados comparten un espacio y reciben una modificación en tiempo real, mientras un usuario ajeno no puede acceder.

### 2. Primera beta utilizable

Usuarios, espacios, invitaciones, miembros y lista de la compra en funcionamiento.

### 3. MVP completo

Lista de la compra, tareas, calendario, gestión de miembros y eliminación de cuenta.

### 4. Versión distribuible

Aplicación revisada, firmada, documentada y preparada para el canal privado elegido.

## Estructura actual del repositorio

```text
.
├── README.md
├── PROJECT_PLAN.md
├── firebase.json
├── firestore.rules
├── docs/
│   ├── ARCHITECTURE.md
│   ├── FIREBASE_DEVELOPMENT.md
│   ├── FUNCTIONAL_SPEC.md
│   ├── PERMISSIONS.md
│   ├── USER_FLOWS.md
│   └── DATA_MODEL.md
├── app/
│   └── src/main/java/com/dlunaunizar/bobitos/
│       ├── app/
│       ├── core/
│       ├── data/
│       └── feature/
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

La configuración versionada de Firebase corresponde únicamente a los emuladores locales. Las credenciales de proyectos reales quedan fuera del repositorio y las pantallas no dependen directamente de Firebase.

## Desarrollo local

### Requisitos

- Android Studio compatible con Android Gradle Plugin 9.2.1.
- JDK 17; puede utilizarse el incluido en Android Studio.
- Android SDK 37.0.
- Node.js 20, 22 o 24 y JDK 21 para Firebase Emulator Suite.

### Ejecución

1. Abrir el repositorio desde Android Studio y esperar a que finalice la sincronización de Gradle.
2. Seleccionar un emulador o dispositivo Android.
3. Ejecutar la configuración `app`.

La versión actual implementa Authentication y Cloud Firestore contra los emuladores locales. Incluye el ciclo de cuenta, espacios múltiples, roles, invitaciones, listeners acotados, modo offline de solo lectura, compra compartida y tareas asignables con filtros. No requiere credenciales reales.

### Firebase local

Con el emulador Android abierto, usar dos terminales:

```bash
# Terminal 1: se mantiene abierta
npm ci
npm run emulators

# Terminal 2: repetir después de reiniciar el emulador Android
npm run android:connect-emulators
```

Después se puede ejecutar `app` desde Android Studio. La compilación `debug` se conecta a Authentication y Firestore mediante `adb reverse`; no hay que modificar direcciones ni retirar `google-services.json`.

La configuración, los datos de prueba y la separación entre entornos están descritos en [FIREBASE_DEVELOPMENT.md](docs/FIREBASE_DEVELOPMENT.md).

### Verificación

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Costes

El núcleo se diseñará para funcionar dentro del plan gratuito de Firebase:

- Sin servidor propio.
- Sin almacenamiento de archivos.
- Sin autenticación por SMS.
- Sin Cloud Functions obligatorias.
- Consultas y listeners limitados.

La beta se distribuye mediante Firebase App Distribution sin coste. El APK es privado, está firmado y sus actualizaciones se notifican a los testers autorizados.

## Documentación

- [Plan completo del proyecto](PROJECT_PLAN.md)
- [Especificación funcional](docs/FUNCTIONAL_SPEC.md)
- [Roles y permisos](docs/PERMISSIONS.md)
- [Flujos de usuario](docs/USER_FLOWS.md)
- [Modelo de datos](docs/DATA_MODEL.md)
- [Firebase de desarrollo y emuladores](docs/FIREBASE_DEVELOPMENT.md)
- [Distribución de la beta privada](docs/BETA_DISTRIBUTION.md)
- [Plan de pruebas en dispositivos reales](docs/BETA_TEST_PLAN.md)
- Guía de contribución: pendiente.
- [Arquitectura y convenciones](docs/ARCHITECTURE.md)

## Licencia

Aplicación destinada a espacios de uso privado y repositorio de código público. No se ha concedido por el momento una licencia de uso, copia o distribución.
