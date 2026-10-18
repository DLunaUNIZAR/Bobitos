# Logo de Bobitos: la casa de bloques

Fecha: 2026-10-18. Diseño hecho en el chat con el usuario, con maquetas en el navegador y el método de la skill `taste-skill:brandkit`. La sesión no tenía una herramienta para generar imágenes, así que el logo y el tablero se dibujaron en SVG.

## Intención

El icono actual es una «B» blanca sobre teal (`app/src/main/res/drawable/ic_launcher.xml`). El usuario quiere un logo sencillo que cuente **lo que hace la app, no su nombre**: varias personas bajo un mismo techo, con lo de cada día en un solo sitio (compra, tareas, comidas, deporte…).

## Decisiones del usuario

- **Concepto: «casa de bloques».** Es una casa hecha de piezas. El tejado va en el teal de la marca y debajo hay cuatro bloques en los colores de los módulos. Se eligió entre tres ideas (tejado con personas, casa de piezas y casa con un check).
- **Con calidez, no rígida.** La primera versión de bloques rectos «parecía un esquema de cajitas». La elegida usa bloques blandos (esquinas muy redondeadas) y un poco torcidos, como apilados a mano. Se descartaron las variantes de ventanas encendidas y de personas en círculos.
- **Fondo claro.** Fondo crema, tejado teal y bloques en los colores intensos del tema claro. Se descartaron el fondo oscuro, el fondo teal y la variante «con más juego» (más giro).
- **Alcance: solo el icono de la app.** Hoy el logo no aparece dentro de la app. El alcance es:
  - el icono adaptable del lanzador, también en su versión redonda;
  - la versión monocroma para los iconos temáticos de Android 13 o superior;
  - el icono que se ve al abrir la app.

## Fuente de verdad del dibujo

- **`docs/superpowers/specs/2026-10-18-logo-icono.svg`**: el icono exacto sobre el lienzo adaptable de 108 × 108. Tiene la capa de fondo y la frontal, con coordenadas, giros y colores.
- **`docs/superpowers/specs/2026-10-18-logo-tablero.html`**: tablero de marca de referencia, con portada, construcción, pantalla de inicio, idea, color, tipografía, formas del lanzador, icono temático y arranque. No es código del producto. La palabra «Bobitos» del tablero no entra en la app.

Geometría, en unidades del lienzo de 108:

| Pieza | Forma | Color | Giro (centro) |
|---|---|---|---|
| Fondo | todo el lienzo | `#FBFAF8` (crema, fondo del tema claro) | — |
| Tejado | triángulo `M54 30 L74 45 H34 Z`, relleno y trazo de 6 con uniones redondeadas | `#0F766E` (teal de la marca) | — |
| Compra | rectángulo x 36, y 51, 16 × 13, radio 5 | `#A9461A` | −5° (44, 57.5) |
| Tareas | rectángulo x 56, y 51, 16 × 13, radio 5 | `#6C43B2` | 4° (64, 57.5) |
| Comidas | rectángulo x 36, y 67, 16 × 13, radio 5 | `#AD1457` | 3° (44, 73.5) |
| Deporte | rectángulo x 56, y 67, 16 × 13, radio 5 | `#1565C0` | −4° (64, 73.5) |

Los colores de los bloques son los acentos claros de `ModuleColors.kt` y el teal es `primaryLight` de `Color.kt`. Todo el dibujo cabe en la zona segura (círculo de 66 de diámetro centrado), así que ninguna máscara del lanzador lo corta.

## Dirección visual

- **Tono:** doméstico, cercano y algo juguetón, sin llegar a infantil. Es sencillo: cinco formas planas, sin degradados, sombras ni texto.
- **Referencias:** el tablero y el SVG de arriba. Las maquetas descartadas quedan fuera del repositorio.
- **Lo que no debe parecer:** un esquema de cajitas rígido, el logo de Microsoft (cuatro cuadrados rectos de colores), un icono genérico de «casa» de Material, ni una letra.
- **Pantallas afectadas:** ninguna pantalla de la app. Cambian el icono del lanzador, el icono temático y el icono al abrir la app.

## Requisitos

1. **Icono adaptable** que sustituye a la «B». Lleva capa de fondo crema y capa frontal con el tejado y los bloques, en vectorial y sin PNG: con minSdk 26, todos los dispositivos admiten iconos adaptables. `android:icon` y `android:roundIcon` usan el nuevo icono y el `ic_launcher.xml` antiguo desaparece.
2. **Capa monocroma** (`<monochrome>`) con la misma silueta: tejado y cuatro bloques con sus giros, en un solo color. Android 13 o superior la tiñe cuando el usuario activa los iconos temáticos.
3. **Al abrir la app** (Android 12 o superior), el icono se ve completo: la casa de bloques sobre su círculo crema, como en el panel 09 del tablero. La ventana sigue con el fondo oscuro de siempre. Sobre el oscuro (`#141513`), los bloques de Tareas y Comidas quedan por debajo de 3:1, así que no deben verse sin su fondo crema.
4. **El tema oscuro por defecto no cambia** (decisión intocable del usuario, en `PRODUCT.md`). El fondo de la ventana sigue siendo `@color/bobitos_window`.
5. **Contraste (WCAG AA, ≥ 3:1 para gráficos):** cada pieza sobre el crema supera 5:1. No se cambian colores.

## Fuera del alcance

- El logo en la pantalla de bienvenida, la de inicio de sesión u otras pantallas.
- El icono pequeño de las notificaciones (hoy el genérico de Android, `android.R.drawable.ic_popup_reminder`).
- Imágenes PNG o de tienda (no se publica en Google Play) y una palabra de marca dentro de la app.
- Distribuir una beta: lo decide el usuario después.

## Lo que se deja a quien lo construya

- Los nombres y las carpetas de los recursos (por ejemplo `mipmap-anydpi-v26/ic_launcher.xml` y capas en `drawable/`). También si el fondo es un color o un vectorial.
- Cómo se traduce el SVG a VectorDrawable: rectángulos redondeados como trazados y giros con `<group android:rotation android:pivotX android:pivotY>`.
- Cómo se cumple el requisito 3: con el comportamiento por defecto del sistema, si ya muestra el círculo crema, o con `android:windowSplashScreenIconBackgroundColor` en un tema `values-v31`.
- La comprobación visual: capturas en el emulador del lanzador, los iconos temáticos y el arranque en Android 12 o superior.
