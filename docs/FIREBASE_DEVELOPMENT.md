# Firebase de desarrollo

Bobitos usa dos entornos separados:

| Entorno | Proyecto | Uso | Datos o facturación reales |
| --- | --- | --- | --- |
| Local | `demo-bobitos` | Desarrollo y pruebas automatizadas | No |
| Desarrollo remoto | `bobitos-dev` (n.º `853960607744`) | Pruebas posteriores entre dispositivos | Sí, dentro de Spark |

El prefijo `demo-` identifica un proyecto ficticio de Firebase Emulator Suite. Los comandos locales fallan antes de acceder accidentalmente a recursos reales y no requieren iniciar sesión en Firebase.

## Requisitos

- Node.js 20, 22 o 24 (se recomienda Node.js 22 LTS; Node.js 26 no es compatible con las dependencias actuales).
- JDK 21 para Firebase Emulator Suite.
- JDK 17 para compilar la aplicación Android.

En macOS, el JDK de Android Studio puede seguir configurado como JDK 17. Si falta Java 21 para los emuladores:

```bash
brew install --cask microsoft-openjdk@21
```

## Ejecución local

Instalar las dependencias versionadas:

```bash
npm ci
```

Iniciar Authentication, Firestore y la interfaz de Emulator Suite:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
npm run emulators
```

Esta terminal debe permanecer abierta. A continuación, iniciar el emulador Android y, desde otra terminal, crear las redirecciones hacia Authentication y Firestore:

```bash
npm run android:connect-emulators
```

El comando localiza `adb`, comprueba que haya un dispositivo disponible y configura los puertos `9099` y `8080`. Si hay varios dispositivos conectados, se puede seleccionar uno antes de repetirlo:

```bash
export ANDROID_SERIAL="emulator-5554"
npm run android:connect-emulators
```

Con los emuladores activos, los datos de demostración pueden cargarse desde otra terminal:

```bash
npm run seed:emulators
```

La interfaz local queda disponible en `http://127.0.0.1:4000`. Authentication escucha en el puerto `9099` y Firestore en `8080`.

La compilación `debug` usa `127.0.0.1` y `adb reverse` la comunica con los servicios del Mac. Las redirecciones desaparecen al reiniciar el emulador Android, por lo que debe repetirse `npm run android:connect-emulators`. La compilación `release` no usa los emuladores ni permite esta configuración local.

El orden de arranque recomendado es:

1. Abrir el emulador Android.
2. Ejecutar `npm run emulators` y mantener esa terminal abierta.
3. Ejecutar `npm run android:connect-emulators` en otra terminal.
4. Ejecutar Bobitos desde Android Studio con la variante `debug`.

`app/google-services.json` puede permanecer en su sitio. En `debug`, Bobitos crea la configuración ficticia `demo-bobitos`; en `release`, utiliza la configuración real del archivo.

## Correos en Authentication Emulator

Authentication Emulator no envía correos reales. Al solicitar la verificación de una cuenta, el enlace local aparece en la terminal donde se ejecuta `npm run emulators`. También se puede abrir `http://127.0.0.1:4000/auth`, seleccionar el usuario y marcar su correo como verificado.

Este comportamiento es exclusivo del entorno local. El proyecto remoto `bobitos-dev` sí utiliza el servicio de correo de Firebase.

## Parada y recuperación

Para detener Emulator Suite normalmente, pulsar `Control+C` en su terminal. Si un cierre anterior dejó puertos ocupados, identificar primero los procesos exactos:

```bash
for port in 4000 8080 9099 4400 4500; do
  lsof -nP -iTCP:$port -sTCP:LISTEN
done
```

Finalizar únicamente los PID mostrados que correspondan a Firebase (`kill PID`). Usar `kill -9 PID` solo si el cierre normal no funciona; no es necesario `sudo` para procesos del usuario actual.

## Resolución de problemas

- `firebase: command not found`: ejecutar `npm ci` y usar los scripts `npm run ...`, que invocan la versión local.
- Node muestra `EBADENGINE`: cambiar a Node.js 20, 22 o 24; no usar Node.js 26 con las dependencias actuales.
- Java no se encuentra: configurar temporalmente el runtime incluido con Android Studio:

  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

- La app no conecta: comprobar que Emulator Suite siga abierta y repetir `npm run android:connect-emulators` después de arrancar el emulador Android.
- Hay varios dispositivos: definir `ANDROID_SERIAL` con uno de los identificadores mostrados por `adb devices`.
- Para confirmar el destino usado por la app: `adb logcat -s BobitosFirebase`; debe aparecer `Authentication usa 127.0.0.1:9099`.
- Al reiniciar Authentication Emulator se eliminan sus usuarios y tokens salvo que se configure importación/exportación. Bobitos detecta un `INVALID_REFRESH_TOKEN`, cierra la sesión local antigua y vuelve al acceso; será necesario crear de nuevo el usuario local.

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
