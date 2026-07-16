# Firebase de desarrollo

Bobitos usa dos entornos separados:

| Entorno | Proyecto | Uso | Datos o facturación reales |
| --- | --- | --- | --- |
| Local | `demo-bobitos` | Desarrollo y pruebas automatizadas | No |
| Desarrollo remoto | `bobitos-dev` (n.º `853960607744`) | Pruebas posteriores entre dispositivos | Sí, dentro de Spark |

El prefijo `demo-` identifica un proyecto ficticio de Firebase Emulator Suite. Los comandos locales fallan antes de acceder accidentalmente a recursos reales y no requieren iniciar sesión en Firebase.

## Requisitos

- Node.js 20 o posterior.
- JDK 21 para Firebase Emulator Suite.
- JDK 17 para compilar la aplicación Android.

En macOS, el JDK de Android Studio puede seguir configurado como JDK 17. Si falta Java 21 para los emuladores:

```bash
brew install --cask microsoft-openjdk@21
```

## Ejecución local

Instalar las dependencias versionadas:

```bash
npm install
```

Iniciar Authentication, Firestore y la interfaz de Emulator Suite:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
npm run emulators
```

Con los emuladores activos, los datos de demostración pueden cargarse desde otra terminal:

```bash
npm run seed:emulators
```

La interfaz local queda disponible en `http://127.0.0.1:4000`. Authentication escucha en el puerto `9099` y Firestore en `8080`.

El emulador de Android accede al Mac mediante `10.0.2.2`. La compilación `debug` usa esa dirección automáticamente y permite tráfico HTTP únicamente en su manifiesto de depuración.

## Pruebas

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
npm run test:emulators
```

La prueba levanta y detiene los servicios necesarios. Además del ciclo de Authentication y la gestión segura de espacios, valida tokens de invitación, consultas limitadas, correo verificado, caducidad, revocación, uso único, límite de 10 miembros, aceptación simultánea y propagación en tiempo real entre dos clientes.

Para inspeccionar listeners y lecturas de resincronización durante una prueba manual:

```bash
adb logcat -s BobitosRealtime
```

## Datos locales

`npm run seed:emulators` crea un usuario, un espacio, una membresía y un producto de compra de demostración. El proceso solo escribe en el emulador local y puede repetirse: limpia Firestore antes de cargar los datos.

## Proyecto remoto de desarrollo

El proyecto remoto de desarrollo ya está creado en el plan Spark:

| Campo | Valor |
| --- | --- |
| ID del proyecto | `bobitos-dev` |
| Número del proyecto | `853960607744` |
| Google Analytics | Activado en Firebase; SDK Android no integrado |

Estado de la configuración remota:

- [x] Crear el proyecto `bobitos-dev` en el plan Spark.
- [x] Registrar una aplicación Android con el identificador `com.dlunaunizar.bobitos`.
- [x] Habilitar Authentication mediante correo y contraseña.
- [x] Crear una base Cloud Firestore Standard en `europe-southwest1` (Madrid) con reglas cerradas.
- [x] Descargar `google-services.json` en `app/` sin versionarlo; el patrón ya está incluido en `.gitignore`.

La región de Firestore no puede cambiarse después de crear la base. No deben activarse Blaze, Cloud Functions, autenticación SMS ni otros servicios de pago para completar esta fase.

Google Analytics está habilitado a nivel de proyecto, pero Bobitos no incluye actualmente su SDK ni envía eventos. Cualquier integración futura requerirá una decisión explícita y su correspondiente revisión de privacidad.

## Política de configuración

- Los identificadores deben coincidir entre `.firebaserc`, Android y los comandos de pruebas.
- `demo-bobitos` nunca se usará como proyecto remoto.
- El alias `dev` identifica exclusivamente el proyecto remoto `bobitos-dev`.
- Las reglas se prueban localmente antes de desplegarse.
- `google-services.json`, claves privadas y datos reales no se suben al repositorio.
- Las compilaciones `release` no usan la configuración ficticia de los emuladores.
