# Beta privada con Firebase App Distribution

Bobitos se distribuye inicialmente como APK privada mediante Firebase App Distribution. El canal no tiene coste monetario, permite limitar el acceso a testers concretos y envía avisos por correo cuando hay una versión nueva.

## Decisiones

- Proyecto Firebase: `bobitos-dev` (`853960607744`).
- Aplicación Android: `com.dlunaunizar.bobitos`.
- Artefacto: APK `release` firmado.
- Grupo de Firebase App Distribution: `bobitos-beta`.
- Coste de distribución: 0 €.
- Los correos de testers, contraseñas, claves y credenciales no se versionan.
- Cada actualización incrementa `VERSION_CODE` y conserva la misma clave de firma.

## 1. Activar el canal en Firebase

En Firebase Console, abrir **App Distribution**, seleccionar la aplicación Android y pulsar **Comenzar**. En **Testers y grupos**, crear un grupo cuyo alias sea exactamente `bobitos-beta` y añadir las cuentas de correo autorizadas.

La primera beta debería cubrir, como mínimo:

- Dos fabricantes distintos.
- Una versión antigua compatible de Android y una reciente.
- Un dispositivo físico por cada cuenta que vaya a probar cambios en tiempo real.

La lista nominal de participantes se mantiene únicamente en Firebase. La matriz anónima de dispositivos se registra en `docs/BETA_TEST_PLAN.md`.

## 2. Configurar Firebase localmente

Descargar `google-services.json` desde la configuración de la aplicación Android del proyecto `bobitos-dev` y guardarlo en:

```text
app/google-services.json
```

El archivo está ignorado por Git. La compilación de desarrollo continúa usando Emulator Suite, mientras que `release` utiliza el proyecto Firebase configurado en ese archivo.

## 3. Crear y custodiar la clave de firma

Esta clave identifica todas las actualizaciones futuras. Debe guardarse fuera del repositorio y tener una copia de seguridad privada.

```bash
mkdir -p ~/.config/bobitos
keytool -genkeypair -v \
  -keystore ~/.config/bobitos/bobitos-release.jks \
  -alias bobitos \
  -keyalg RSA -keysize 2048 -validity 10000
```

Crear `keystore.properties` en la raíz del proyecto:

```properties
storeFile=/Users/TU_USUARIO/.config/bobitos/bobitos-release.jks
storePassword=CONTRASEÑA_DEL_ALMACÉN
keyAlias=bobitos
keyPassword=CONTRASEÑA_DE_LA_CLAVE
```

`keystore.properties` y los ficheros `*.jks` están ignorados por Git. No deben enviarse por chat, añadirse a issues ni copiarse al repositorio.

## 4. Preparar App Check para distribución fuera de Google Play

Obtener la huella SHA-256:

```bash
keytool -list -v \
  -keystore ~/.config/bobitos/bobitos-release.jks \
  -alias bobitos
```

Registrar esa huella en la aplicación Android de Firebase y en App Check. Como el APK se instalará fuera de Google Play, configurar Play Integrity así:

- `PLAY_RECOGNIZED`: no requerido.
- `LICENSED`: no requerido.
- Integridad mínima: integridad del dispositivo.

No activar todavía la exigencia de App Check para Authentication o Firestore. Primero se distribuye una beta, se comprueba que sus solicitudes aparecen como válidas y después se activa gradualmente.

## 5. Construir y subir una beta

Autenticarse una vez con la cuenta que administra `bobitos-dev`:

```bash
npx firebase login
```

Validar la configuración local:

```bash
./gradlew verifyBetaConfiguration
```

Construir y firmar la primera beta:

```bash
./gradlew assembleRelease
npm run beta:distribute
```

Para una actualización, modificar las notas de `distribution/release-notes.txt` e incrementar el código y el nombre de versión:

```bash
./gradlew assembleRelease \
  -PVERSION_CODE=2 \
  -PVERSION_NAME=0.1.0-beta.2
npm run beta:distribute
```

Firebase enviará un correo a los miembros de `bobitos-beta`. Los testers aceptan la invitación, instalan el APK y permiten instalaciones procedentes del navegador o de App Tester cuando Android lo solicite.

## 6. Actualización y caducidad

- Cada APK nuevo debe tener un `VERSION_CODE` mayor.
- Todas las versiones deben usar la misma clave `bobitos-release.jks`.
- Firebase notifica por correo las nuevas entregas.
- Las compilaciones permanecen disponibles en App Distribution durante 150 días.
- El tester instala la nueva versión sobre la anterior; sus datos locales y su sesión deberían conservarse.

## 7. Criterio para activar App Check

Durante los primeros días de beta:

1. Comprobar en App Check que los dispositivos reales generan solicitudes válidas.
2. Confirmar inicio de sesión, lectura y escritura en al menos dos dispositivos.
3. Revisar que no existan solicitudes legítimas sin verificar.
4. Activar primero la exigencia para Firestore.
5. Repetir las pruebas y, si no hay bloqueos, activarla para Authentication.

Si una activación bloquea usuarios legítimos, desactivar la exigencia, revisar la huella y la configuración de Play Integrity y volver a observar métricas antes de reintentarlo.

## 8. Revisión de consumo

Tras cada ronda se revisan en Firebase Console:

- Usuarios activos de Authentication.
- Lecturas, escrituras y eliminaciones de Firestore.
- Solicitudes válidas y no verificadas de App Check.
- Errores comunicados por los testers.

No se cambia del plan Spark ni se activa ningún servicio facturable sin registrar una decisión nueva en `PROJECT_PLAN.md`.
