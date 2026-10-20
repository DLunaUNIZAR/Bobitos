# ADR-0001: Tests instrumentados en la CI

**Estado:** Aceptado
**Fecha:** 2026-10-19
**Decide:** el dueño del proyecto (el disparo y el PR en borrador); lo técnico, el coordinador del plan R2

## Contexto

- La app tiene **203 tests instrumentados en 52 ficheros** (`app/src/androidTest`), todos de Compose con `createAndroidComposeRule<ComponentActivity>()` o `createComposeRule()`, sin red ni Firebase (los datos salen de `ScreenFakes.kt`). En el emulador local `Pixel_10` (API 37.1, arm64, 1080 × 2424 a 420 dpi) pasan todos en 8 min 15 s.
- La CI (`.github/workflows/ci.yml`) no ejecuta ninguno. `ProfileScreenTest` pasó tres meses roto porque ninguna máquina lo ejecutaba.
- **El repo es público:** los minutos de GitHub Actions en `ubuntu-latest` no cuestan nada, y esos runners tienen KVM, así que un emulador x86_64 va con aceleración.
- **Flujo de trabajo:** casi siempre merge local a `main` y push por lotes. Los PR son ocasionales: el último es el #242 y el anterior del 2026-10-08. De las últimas 40 ejecuciones de la CI, 26 son por push. Muchos pushes solo traen docs (backlog y notas de beta).
- La app solo tiene recursos en español (`res/values`) y fuerza el español en el selector de fechas (`AppDatePickerLocaleTest` lo comprueba con el dispositivo en inglés), así que el idioma del emulador no debería cambiar los textos.

## Decisión

1. **Un workflow aparte, `.github/workflows/instrumented.yml`,** con un único job que arranca un emulador con `reactivecircus/android-emulator-runner@v2` y ejecuta `./gradlew :app:connectedDebugAndroidTest`. Corre en paralelo a `ci.yml` y no lo retrasa.
2. **Disparo:** `push` a `main` y `pull_request`, los dos con un filtro `paths`. Solo se lanza si el push o el PR toca `app/**`, `gradle/**`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew` o el propio `instrumented.yml`. Tiene su propio grupo de concurrencia, con `cancel-in-progress`. En un push, GitHub mira todos los ficheros del lote (de `before` a `after`), así que un lote con código y un último commit de docs también lo lanza.
3. **Emulador:** x86_64 con la API 37, si hay imagen x86_64 para ella. Si no la hay, la más alta que exista y pase la suite, apuntada en el workflow. Perfil de 411 dp de ancho, como el del móvil local (por ejemplo `pixel_6`: 1080 × 2400 a 420 dpi) y animaciones desactivadas.
4. **Sin reintentos automáticos.** Un falso rojo se ve, se relanza a mano y, si se repite, el test se corrige.
5. **Informes como artefacto, siempre:** el HTML y el XML de `connectedDebugAndroidTest` se suben aunque el job falle.

## Opciones consideradas

### Disparo

| Opción | Complejidad | Coste | Cuándo avisa | Ruido |
|---|---|---|---|---|
| A. Push a `main` y PR, siempre | Baja | 0 € (repo público), unos 12–15 min por ejecución | Al llegar a `main` | Arranca el emulador también en los pushes de solo docs |
| B. Solo en los PR | Baja | 0 € | Casi nunca: apenas se abren PR | Mínimo |
| **C. Push a `main` y PR, con filtro de rutas (elegida)** | Baja | 0 € | Al llegar a `main`, si cambia la app | Solo cuando cambia algo que los tests miran |
| D. Programado (cada noche) | Baja | 0 € | Al día siguiente, sin commit concreto | GitHub desactiva los programados tras 60 días sin actividad |

- **B** repetiría lo de `ProfileScreenTest`: con este flujo, el job casi no correría.
- **A** avisa igual de pronto que C, pero lanza el emulador en los pushes de solo docs, que no pueden romper ningún instrumentado y solo suman falsos rojos.
- **D** separa el aviso del commit que lo causa.

### Cómo se arranca el emulador

| Opción | Complejidad | Coste | Lo que pesa |
|---|---|---|---|
| **R1. `reactivecircus/android-emulator-runner` (elegida)** | Baja | 0 € | La acción de referencia en GitHub Actions; no toca el build; el comando es el mismo que se usa en local (`connectedDebugAndroidTest`) |
| R2. Gradle Managed Devices | Media | 0 € | Declara el dispositivo en `app/build.gradle.kts`; las imágenes ATD, que son las rápidas, no llegan a las API recientes; sería una segunda forma de lanzar los tests, distinta de la local |
| R3. Firebase Test Lab | Media | Cuota gratuita limitada | Necesita credenciales en la CI, que hoy no tiene ningún secreto |

## Consecuencias

- **Más fácil:** un instrumentado roto pone la CI en rojo en el push que lo rompe. El ancla de «Hoy» (`SpaceHomeAnchorTest`, L62 del backlog) y los tests de G2 y de la revisión de «Añadir a la compra» quedan protegidos. G10 puede apoyarse en sus tests de Compose.
- **Más difícil:** cada push que toca la app tarda unos 12–15 min más en tener todos los checks. Puede haber falsos rojos del emulador, que se ven y no se esconden. Un test que dependa del tamaño, la densidad o la API del dispositivo puede pasar en local y fallar en la CI, y entonces hay que corregir el test.
- **Revisar más adelante:**
  - si se activa la protección de rama con checks obligatorios, el filtro `paths` deja «pendiente» el check en los PR de solo docs: entonces habrá que pasar a un filtro dentro del job;
  - si la suite pasa de unos 20 min, valorar repartirla en varios emuladores.

## Puesta en marcha

1. [ ] Workflow `instrumented.yml` (tarea del plan R2).
2. [ ] Primera ejecución en verde en un PR en borrador antes del merge (instrucción del usuario para R2).

## Actualización (2026-10-20)

Las imágenes `system-images;android-37.0;google_apis;x86_64` y `system-images;android-36.1;google_apis;x86_64` abortan en la CI: `mapper.ranchu` falla en `Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`, `surfaceflinger` se cae y el `system_server` se reinicia. No hay arreglo sin GPU (https://github.com/rock3r/clockblock/issues/122). Se aplica el plan B del punto 3 («la más alta que exista y pase la suite») con `api-level: '36'` (la 36.1 cae en la misma aserción) y `disk-size: 6000M`, porque la partición de datos por defecto (~800 MB) no deja instalar el APK. Se volverá a la 37 cuando haya un emulador arreglado.
