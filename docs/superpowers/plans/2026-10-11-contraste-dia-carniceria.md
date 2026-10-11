# Contraste de los iconos de DIA y Carnicería — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan de menores** (1 tarea): pasos 1, 2, 3, 5 y 6, sin `simplify`. La tarea la hace un subagente `sonnet`; la revisión final, `opus` (si falla, `fable`). Los pasos usan casillas (`- [ ]`).

**Objetivo:** que los iconos de tienda de **DIA** y **Carnicería** se vean bien en tema oscuro (por defecto) sin empeorar el claro. Es el menor aplazado de `agent/tiendas-de-barrio` que el usuario pidió arreglar el 11/10/2026 («arregla el menor del color de Carnicería y DIA»).

**Arquitectura:** solo cambian dos colores en `Supermarket.brandColor()`. Un test JVM calcula el contraste WCAG de cada uno contra el fondo y contra la tarjeta teñida (el color al 12 % sobre el fondo, como `categoryCardColors`) en los dos temas.

**Stack:** Kotlin, Jetpack Compose (`androidx.compose.ui.graphics.Color`), JUnit 4.

**Spec:** no hay documento; el criterio es el de la revisión final de `agent/tiendas-de-barrio` (contraste de elementos gráficos ≥ 3:1, WCAG 1.4.11).

## Restricciones globales

- **Rutas:** `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`; `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`.
- **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
- **Colores nuevos** (calculados por el coordinador; mínimo 3,6:1 en los cuatro casos): DIA `0xFFE02947` (rojo DIA un poco más claro); Carnicería `0xFFBE5A55` (rojo carne apagado, distinto del rojo de DIA y Alcampo).
- **Fondos reales:** `backgroundLight` (`0xFFFBFAF8`) y `backgroundDark` (`0xFF141513`) en `APP/core/designsystem/theme/Color.kt`; tinte de tarjeta: `CATEGORY_TINT_ALPHA = 0.12f` en `APP/core/designsystem/theme/CategoryTint.kt`.
- Prohibido: nada contra `bobitos-dev`, desplegar, push. TDD obligatorio.

## Review Focus

1. Los dos colores llegan a 3:1 contra el fondo y contra su tarjeta teñida, en oscuro y en claro.
2. DIA sigue pareciendo DIA (rojo) y Carnicería no se confunde con DIA ni con Alcampo.
3. Ningún otro color cambia.

---

### Task 1: Colores de DIA y Carnicería con contraste suficiente

**Ficheros:**
- Modificar: `APP/feature/shopping/SupermarketTokens.kt` (`brandColor()`: `DIA` y `CARNICERIA`).
- Crear: `UT/feature/shopping/SupermarketContrastTest.kt`.

**Interfaces:** ninguna nueva (el test usa `Supermarket.brandColor()`, `backgroundLight` y `backgroundDark`, que son `internal` del mismo módulo).

- [ ] **Paso 1: test en rojo.** `SupermarketContrastTest`, con una función de ayuda privada que calcule la luminancia relativa WCAG (`0.2126 R + 0.7152 G + 0.0722 B`, con la linealización sRGB de cada canal) y el contraste `(Lmax + 0.05) / (Lmin + 0.05)`, y otra que componga el tinte de tarjeta (`fondo * 0.88 + color * 0.12`, canal a canal):
  - `DIA and butcher icons reach 3 to 1 on the dark background and their dark card`;
  - `DIA and butcher icons reach 3 to 1 on the light background and their light card`.
- [ ] **Paso 2:** GRADLE. Esperado: FALLA el test de tema oscuro (hoy DIA da 2,95 sobre su tarjeta oscura y Carnicería 2,62).
- [ ] **Paso 3:** cambiar los dos colores a los de las restricciones globales.
- [ ] **Paso 4:** GRADLE. Esperado: verde.
- [ ] **Paso 5:** commit `fix(compra): más contraste en los iconos de DIA y Carnicería en tema oscuro`.

---

## Ejecución

En la rama `agent/tiendas-de-barrio` (aún sin mergear), con commit de este plan. Tarea con un `sonnet`; `task-done` con GRADLE; revisión final `opus`; `code-review` `high`; cierre y merge en `main` en local (lo pidió el usuario).
