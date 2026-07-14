# Firebase de desarrollo

Bobitos usa dos entornos separados:

| Entorno | Proyecto | Uso | Datos o facturación reales |
| --- | --- | --- | --- |
| Local | `demo-bobitos` | Desarrollo y pruebas automatizadas | No |
| Desarrollo remoto | `bobitos-dev-<sufijo>` | Pruebas posteriores entre dispositivos | Sí, dentro de Spark |

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

La prueba levanta y detiene los servicios necesarios, crea un usuario en Authentication y verifica que Firestore rechaza tanto una lectura no autenticada como una autenticada mientras las reglas permanezcan cerradas.

## Datos locales

`npm run seed:emulators` crea un usuario, un espacio, una membresía y un producto de compra de demostración. El proceso solo escribe en el emulador local y puede repetirse: limpia Firestore antes de cargar los datos.

## Proyecto remoto de desarrollo

La creación se realizará en Firebase Console cuando sea necesario validar varios dispositivos reales:

1. Crear un proyecto con un identificador global único similar a `bobitos-dev-<sufijo>` y mantener el plan Spark.
2. Registrar una aplicación Android con el identificador `com.dlunaunizar.bobitos`.
3. Habilitar Authentication mediante correo y contraseña.
4. Crear una base Cloud Firestore Standard en `europe-southwest1` (Madrid) con reglas cerradas.
5. Descargar `google-services.json` en `app/` sin versionarlo; el patrón ya está incluido en `.gitignore`.

La región de Firestore no puede cambiarse después de crear la base. No deben activarse Blaze, Cloud Functions, autenticación SMS ni otros servicios de pago para completar esta fase.

## Política de configuración

- Los identificadores deben coincidir entre `.firebaserc`, Android y los comandos de pruebas.
- `demo-bobitos` nunca se usará como proyecto remoto.
- Las reglas se prueban localmente antes de desplegarse.
- `google-services.json`, claves privadas y datos reales no se suben al repositorio.
- Las compilaciones `release` no usan la configuración ficticia de los emuladores.
