# Avisos e instalación de betas nuevas desde la app

Fecha: 2026-10-18. Estado: diseño aprobado en la conversación, pendiente de plan.

## Por qué

Bobitos se distribuye como APK firmado por Firebase App Distribution al grupo `bobitos-beta` (ver `docs/BETA_DISTRIBUTION.md`). No se publica en Google Play. Hoy, para instalar una beta, el tester se entera por el correo, abre App Tester o el enlace, busca la versión, la descarga y la instala. El usuario lo hizo así con la beta 24.

El objetivo es que la propia app avise de la beta nueva y la instale sin pasar por el correo ni por App Tester.

**Lo que no cambia:** el análisis del móvil al instalar fue la parte más lenta para el usuario. Lo hace Android (Google Play Protect) con cualquier APK que no venga de Google Play, también cuando la descarga la inicia la app. Esta función no lo acorta. Tampoco cambia la confirmación de la instalación, que Android pide siempre.

## Objetivos

1. **Aviso automático al abrir la app.** Si este móvil ya inició sesión como tester, la app comprueba en silencio si hay una beta nueva y, si la hay, avisa con su versión y «Actualizar» / «Más tarde».
2. **Comprobación manual en «Perfil».** Una fila con la versión instalada y «Buscar actualizaciones». La primera vez inicia la sesión de tester. Si hay una versión nueva, muestra sus notas y permite descargarla e instalarla.

## Fuera del alcance

- **Notificaciones en segundo plano** (WorkManager, push…). Descartado por el usuario: App Distribution ya avisa por correo y App Tester.
- **Remote Config con el enlace de tester.** Descartado por el usuario en favor del SDK de App Distribution.
- **La primera instalación de cada móvil.** Sigue llegando por la invitación de correo o por App Tester.
- **Google Play.** La app no se publica allí (ver «Restricciones»).
- **Cerrar la sesión de tester desde la app.** El SDK la conserva hasta desinstalar la app; no se pidió.

## Lo que no debe pasar (anti-objetivos)

- **No abrir nunca el inicio de sesión de tester al arrancar.** Solo se abre desde «Buscar actualizaciones».
- **No insistir.** El aviso automático sale como mucho una vez al día, y nunca con una versión que el usuario pospuso. No hay puntos rojos, insignias ni recordatorios.
- **No bloquear la app** con una pantalla completa.
- **No añadir notificaciones propias.** Solo existe la notificación de progreso de descarga que muestra el SDK.
- **No romper la versión de desarrollo.** Esa versión usa el emulador y el proyecto `demo-bobitos`, y debe seguir compilando y funcionando sin App Distribution.

## Comportamiento

### Aviso automático

- **Cuándo se comprueba.** Cuando la app pasa a primer plano (al arrancar o al volver desde segundo plano), si se cumplen las tres condiciones:
  - es una beta firmada (no la versión de desarrollo);
  - el SDK dice que el tester ya inició sesión (`isTesterSignedIn`);
  - hoy aún no se ha hecho una comprobación automática que terminara bien.
- **El límite diario** es por día del calendario, con la zona horaria del móvil (decisión mía).
  - Se guarda la fecha de la última comprobación automática que terminó (haya o no versión nueva).
  - Una comprobación que falla, por ejemplo sin conexión, no gasta el día: se reintenta la próxima vez que la app pase a primer plano (decisión mía).
- **Silenciosa.** Mientras comprueba no se muestra nada, y si falla tampoco.
- **Si hay versión nueva**, sale el diálogo, salvo que el usuario pospusiera esa misma versión. La versión se identifica por su `versionCode` (`VERSION_CODE` = nº de beta). En el diálogo:
  - **«Actualizar»** abre la hoja de la actualización y la descarga empieza sola.
  - **«Más tarde»** guarda esa versión como pospuesta. El aviso automático no vuelve a salir con ella. Una versión más nueva sí avisa. La comprobación manual de «Perfil» la muestra siempre.
  - **Cerrar con Atrás o tocando fuera** cierra el diálogo sin posponer (decisión mía). Como el día ya está gastado, no vuelve a salir hasta otro día. Así un toque sin querer no oculta la versión para siempre.
- **Encima de qué sale.** El diálogo aparece sobre la pantalla en la que esté la app, sea la que sea: «Hoy», el inicio de sesión de Bobitos, la bienvenida u otra.

### Comprobación manual en «Perfil»

- **La fila** va en la sección «Información», encima de «Política de privacidad». Muestra «Versión» con el `versionName` instalado (por ejemplo, `0.1.0-beta.24`) y el botón «Buscar actualizaciones».
- **En la versión de desarrollo** la fila muestra solo la versión, sin botón ni texto de ayuda.
- **Línea de ayuda** (decisión mía). Mientras el tester no haya iniciado sesión, una línea bajo la fila avisa de que la primera vez se pedirá la cuenta de Google en la que se reciben las invitaciones de la beta.
- **Al pulsar el botón:**
  - Si el tester no ha iniciado sesión, se inicia la sesión (`signInTester`). La pantalla la abre el SDK y se hace una vez por móvil. Si el usuario la cancela o falla, se muestra en la fila y no se sigue.
  - Con la sesión iniciada, se comprueba si hay una versión nueva (`checkForNewRelease`).
- **Estados de la fila.** Se muestran en la propia fila y TalkBack los anuncia:
  - **Buscando:** indicador de progreso y botón desactivado.
  - **Sin novedades:** «Tienes la última versión».
  - **Error:** una explicación con palabras normales (sin conexión, inicio de sesión cancelado, esta cuenta no está en la beta, error desconocido…). El usuario puede volver a pulsar el botón.
  - **Versión nueva:** se abre la hoja de la actualización. La descarga no empieza sola: la inicia el botón «Descargar e instalar».
- Lo pospuesto en el aviso automático no afecta a la comprobación manual.

### Hoja de la actualización

Es la misma hoja inferior para el aviso automático y para «Perfil». Sigue el patrón de `BobitosInfoSheet`.

- **Título:** la versión nueva (`displayVersion` del SDK).
- **Zona de estado**, arriba:
  - **Lista para descargar** (solo al venir de «Perfil»): botón «Descargar e instalar».
  - **Descargando:** barra de progreso con el porcentaje y los MB descargados de los totales.
  - **Descargada:** Android pide confirmar la instalación. Si es la primera vez, Android puede pedir antes que se permitan instalaciones desde Bobitos; lo gestionan el SDK y el sistema.
  - **Instalación cancelada:** botón «Instalar», que vuelve a lanzar la actualización.
  - **Error en la descarga o la instalación:** explicación y «Reintentar».
- **«Novedades»:** las notas de la versión (`releaseNotes`, el texto de `distribution/release-notes.txt`) tal cual, conservando los saltos de línea. Se desplazan. Ocupan unos 3.000 caracteres en una beta típica. Si vienen vacías, se omite la sección.
- **«Cerrar»** siempre se puede pulsar. Cerrar no cancela la descarga, porque el SDK no tiene forma de cancelarla. Si se cierra mientras descarga, se avisa de que Android pedirá instalar al terminar (decisión mía).

## Dirección visual

Viene del brief de `/impeccable shape`, confirmado por el usuario.

- **Para quién y en qué modo.** El usuario y la gente de `bobitos-beta`, en el día a día. Es una superficie de tipo «Operate»: el éxito es instalar la beta nueva con el menor número de toques.
- **Autoridad visual: el mundo que ya tiene la app.** No hay `DESIGN.md`, pero la app tiene una identidad coherente en el código: Material 3, paleta teal, Nunito y tema oscuro por defecto. Esto es una función dentro de ese mundo: se hereda y no se inventa un estilo nuevo.
  - Componentes a reutilizar: `BobitosDialog` (o su mismo patrón) para el aviso, `BobitosInfoSheet` (o su patrón) para la hoja, y `SectionHeader` y las filas de «Perfil» para la fila.
- **Aviso.** Un diálogo Material corto. Título «Hay una beta nueva», mensaje con la versión, «Actualizar» como botón relleno y «Más tarde» como botón de texto.
- **Hoja.** Inferior y modal. Arriba, el título y la zona de estado, que es lo primero que se ve. Debajo, el encabezado «Novedades» y las notas con scroll. Abajo, «Cerrar».
- **Fila en «Perfil».** En la sección «Información». Etiqueta «Versión» con su valor y el botón «Buscar actualizaciones». El estado y la línea de ayuda van debajo, dentro de la misma fila.
- **Anti-referencias:**
  - pantallas completas de «actualización obligatoria»;
  - puntos rojos o insignias en la barra inferior o en el avatar;
  - banners que se repiten;
  - controles que no sean de Material.
- **Accesibilidad** (requisitos de `PRODUCT.md`):
  - contraste AA en los dos temas;
  - nada se corta con la letra al 130 %;
  - zonas táctiles de al menos 48 dp;
  - TalkBack lee el progreso de la descarga y cada cambio de estado de la fila y de la hoja;
  - se respeta «Quitar animaciones».
- **Textos** en español de España, con tuteo y frases cortas. Quedan fijados por la spec: «Hay una beta nueva», «Actualizar», «Más tarde», «Versión», «Buscar actualizaciones», «Tienes la última versión», «Descargar e instalar», «Instalar», «Reintentar», «Novedades» y «Cerrar». El resto (mensajes de error, línea de ayuda, aviso al cerrar durante la descarga) lo escribe quien implementa, con esa voz.

## Diseño técnico

### Dependencias y variantes

- **El SDK.** Se añaden `com.google.firebase:firebase-appdistribution-api` y `com.google.firebase:firebase-appdistribution`, versión `16.0.0-beta21` (la última en Google Maven a 2026-10-18).
  - No vienen en `firebase-bom` 34.15.0, así que la versión va explícita en `gradle/libs.versions.toml`.
  - Desde octubre de 2023 no hay módulo `-ktx` aparte: las extensiones de Kotlin (`Firebase.appDistribution`) están en el módulo principal.
- **La API** (`firebase-appdistribution-api`) va en todas las variantes (`implementation`). En la versión de desarrollo no hace nada: sus llamadas fallan con `Status.NOT_IMPLEMENTED`.
- **El SDK completo** (`firebase-appdistribution`) va solo en `release` (`releaseImplementation`). Añade al manifiesto `REQUEST_INSTALL_PACKAGES` y su actividad de inicio de sesión.
  - Ese código de auto-actualización incumple la política de Google Play aunque no se ejecute. Como la app no se publica en Play, se acepta.
  - `docs/BETA_DISTRIBUTION.md` lo deja escrito: si algún día se publicara en Play, haría falta una variante de beta propia.
- **Cuándo se considera disponible.** La función solo funciona si la compilación no usa el emulador (`BuildConfig.USE_FIREBASE_EMULATORS == false`). Cómo se exprese (ese mismo flag o uno nuevo de `BuildConfig`) lo decide quien implementa. En desarrollo no se llama al SDK.
- **Permisos.** `POST_NOTIFICATIONS` ya está declarado (lo usan los recordatorios). La notificación de progreso del SDK lo necesita en Android 13 o superior. No se pide aposta para esta función: si no está concedido, basta el progreso dentro de la hoja.

### Estructura

Quien implementa decide los nombres y el reparto exacto. Lo que tiene que cumplirse:

- **Una interfaz propia envuelve al SDK** (`FirebaseAppDistribution`), para que la lógica se pruebe en la JVM con un doble. La interfaz expone:
  - si el tester ha iniciado sesión;
  - iniciar la sesión;
  - comprobar si hay una versión nueva, que devuelve versión visible, `versionCode` y notas, o nada;
  - lanzar la actualización y seguir su progreso: bytes descargados, bytes totales y estado.
  - Los errores del SDK (`FirebaseAppDistributionException.Status`: `AUTHENTICATION_CANCELED`, `AUTHENTICATION_FAILURE`, `NETWORK_FAILURE`, `DOWNLOAD_FAILURE`, `INSTALLATION_FAILURE`, `INSTALLATION_CANCELED`, `API_DISABLED`, `NOT_IMPLEMENTED`, `UNKNOWN`…) se traducen a un tipo propio de la app, y cada uno lleva su mensaje.
- **Preferencias en DataStore**, con el mismo patrón que `DataStoreOnboardingPreferenceRepository`: la fecha de la última comprobación automática terminada y el `versionCode` pospuesto.
- **La decisión del aviso automático es una función pura.** A partir de «disponible», «sesión iniciada», «fecha de hoy», «última fecha» y «pospuesta» decide si se comprueba. A partir del resultado decide si se avisa. Se prueba con tests unitarios.
- **Un estado a nivel de app compartido por las tres superficies** (por ejemplo, un ViewModel de la actividad): el aviso, la hoja y la fila de «Perfil».
  - El aviso y la hoja se dibujan en la raíz (`BobitosApp`), porque pueden salir sobre cualquier pantalla.
  - «Perfil» abre la misma hoja.
  - El SDK exige que `updateApp()` vaya después de un `checkForNewRelease()` con resultado en el mismo proceso. El estado guarda la versión encontrada.
- **El primer plano** se detecta con el ciclo de vida (`ON_START`) de la actividad o del proceso. Quien implementa decide cuál; si añade `lifecycle-process`, va en el catálogo.
- **La fila de «Perfil»** muestra `BuildConfig.VERSION_NAME`.

### Tests

- **Unitarios (JVM)**, con dobles de la interfaz del SDK y de las preferencias:
  - la regla del límite diario;
  - la regla de la versión pospuesta: «Más tarde» guarda; cerrar no guarda; una versión más nueva sí avisa; la manual ignora lo pospuesto;
  - que no se compruebe sin sesión iniciada ni en desarrollo;
  - que una comprobación fallida no gaste el día;
  - los estados de la fila: sin sesión, buscando, sin novedades, error y versión nueva;
  - los estados de la hoja: lista, descargando con progreso, cancelada y error.
- **De interfaz (Compose, en `androidTest`), si el proyecto ya los tiene para pantallas parecidas:** los textos fijados por la spec y que el aviso tenga sus dos botones.
- **El flujo real** con el SDK (inicio de sesión, descarga, instalación) solo se puede probar a mano en un móvil con una beta firmada: se apunta en `docs/BETA_TEST_PLAN.md`.

### Configuración que hace el usuario (no la hace el código)

- **Activar una vez la API «Firebase App Testers API»** en Google Cloud Console para el proyecto `bobitos-dev`. Sin ella, las llamadas fallan con `API_DISABLED`.
- **Instalar a mano, como siempre, la primera beta que traiga esta función.** Las siguientes ya se pueden instalar desde la app.

### Documentación

- **`docs/BETA_DISTRIBUTION.md`:** la activación de la API, cómo se actualiza desde la app (primera vez desde «Perfil», luego el aviso automático), que la primera instalación sigue llegando por correo, que las notas de `distribution/release-notes.txt` se ven en la app, y la advertencia sobre Google Play.
- **`PRIVACY_POLICY.md`** (decisión mía, siguiendo cómo ya se documentan Crashlytics y Open Food Facts):
  - Solo en las compilaciones de distribución, si el tester inicia sesión desde «Perfil», la app usa Firebase App Distribution con su cuenta de Google para comprobar y descargar betas nuevas.
  - Las compilaciones de desarrollo no lo usan.
- **`docs/BETA_TEST_PLAN.md`:** la prueba manual del flujo completo.

## Restricciones

- Android nativo: Kotlin, Compose y Material 3. minSdk 26, targetSdk 37. Hilt, DataStore y corrutinas, como el resto de la app.
- Solo en español (`res/values/strings.xml`), con tuteo.
- Coste cero: App Distribution no tiene coste.
- La versión de desarrollo y el CI (`ktlintCheck`, `detekt`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`) no tienen `google-services.json`: todo debe compilar y pasar sin él.
- Hay que comprobar también que `assembleRelease` compila. Necesita `google-services.json` y la firma locales, que están en el equipo del usuario.

## Decisiones tomadas

**Del usuario:**
- el SDK de App Distribution, no Remote Config;
- sin notificaciones en segundo plano;
- el aviso automático solo con sesión de tester, como mucho una vez al día y sin repetir una versión pospuesta;
- la comprobación manual en «Perfil», que inicia la sesión la primera vez;
- el aviso como diálogo;
- «Actualizar» abre la hoja con las notas y la descarga empieza sola.

**Mías** (marcadas en el texto):
- el día es del calendario;
- una comprobación fallida no gasta el día;
- cerrar el aviso sin elegir no pospone;
- la línea de ayuda antes del primer inicio de sesión;
- cerrar la hoja no cancela la descarga y se avisa;
- en desarrollo, la fila muestra solo la versión;
- la nota en `PRIVACY_POLICY.md`.

## Lo que queda para quien implementa

- Los nombres de clases, ficheros y cadenas.
- El reparto exacto entre ViewModel, repositorio y composables.
- Cómo detectar el primer plano.
- Los textos de error y de ayuda, con la voz de la app.
- Cómo se expresa en `BuildConfig` que la función está disponible.
