# App Check en Bobitos

La compilación de producción instala `PlayIntegrityAppCheckProviderFactory` antes de utilizar Authentication o Firestore. Las compilaciones de desarrollo conectadas a Emulator Suite no solicitan tokens de App Check.

## Activación del entorno remoto

Antes de distribuir la beta:

1. Crear la clave de firma definitiva y obtener su huella SHA-256.
2. Añadir la huella a la aplicación Android `com.dlunaunizar.bobitos` en Firebase.
3. Registrar la aplicación en **App Check → Play Integrity**.
4. Distribuir primero una compilación con App Check y observar las métricas de solicitudes válidas.
5. Activar la aplicación de App Check para Authentication y Cloud Firestore cuando los dispositivos de beta aparezcan como válidos.

No debe activarse la exigencia en consola antes de registrar la firma: bloquearía también a clientes legítimos. Este último cambio remoto forma parte de la preparación de la beta porque depende de la clave y del canal de distribución elegidos en el issue #13.

Referencia: [Firebase App Check con Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider).
