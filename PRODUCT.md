# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

- **Quién:** el usuario y su gente cercana (su hogar, familia y amigos), en una beta privada distribuida por Firebase App Distribution (grupo `bobitos-beta`). No hay planes de publicarla en Google Play por ahora.
- **Situación:** grupos pequeños que comparten la vida diaria: una pareja, una familia o un piso compartido. Usan el móvil en casa, en el súper y en el gimnasio, a menudo con una mano y con prisa.
- **Trabajo:** coordinar lo cotidiano entre varias personas: qué hay que comprar, quién hace qué tarea, qué se come, qué hay en el calendario y qué entrenamiento toca.

## Product Purpose

Bobitos es una app Android privada para coordinar el hogar dentro de **espacios** compartidos de 2 a 10 miembros. Un usuario puede pertenecer a varios espacios y cambiar entre ellos. Tiene éxito cuando el grupo deja de necesitar varias apps sueltas y mensajes de chat para organizarse.

## Positioning

- **Todo el hogar en una app, conectado entre sí:** compra, tareas, calendario, comidas (planificador, recetario e ingredientes), despensa, deporte (actividades, ejercicios, rutinas y sesión de gimnasio) y notas. Los módulos se alimentan unos a otros: de una receta a la compra, de la compra a la despensa, de una comida a «Mi día».
- **Compartido en tiempo real:** lo que apunta un miembro lo ven los demás al momento.
- **Privada y sin coste:** sin anuncios, sin servidor propio y sin datos fuera del grupo; el coste de infraestructura es cero (niveles gratuitos de Firebase).

## Operating Context

- Navegación con barra inferior de 5 pestañas: Hoy, Calendario, Tareas, Compra y Más (Comidas, Deporte, Notas y Despensa). Los catálogos (Recetario, Ingredientes, Ejercicios y Rutinas) tienen elementos comunes, que mantiene el administrador, y personales.
- «Mi calendario» reúne los eventos de todos los espacios del usuario.
- Sin conexión, la app es de solo lectura: no se escriben cambios.
- Se usa en el día a día real: apuntar algo en la compra de pie en el súper, marcar una tarea al hacerla, consultar el plan de comidas antes de cocinar.

## Capabilities and Constraints

- Android nativo: Kotlin, Jetpack Compose y Material 3. minSdk 26, targetSdk 37. Firebase (Auth, Firestore) como único backend.
- Solo en español (`res/values/strings.xml`), con tuteo. Los conteos van con plurales.
- Roles de propietario y miembro dentro de cada espacio; invitaciones privadas por código o enlace.
- Fuera del alcance: iOS, web, pagos, publicidad, contenido público, chat y archivos subidos por los usuarios.

## Brand Commitments

- **Nombre:** Bobitos.
- **Voz:** cercana y doméstica, en español de España, con tuteo y frases cortas.
- **Tema oscuro por defecto:** decisión del usuario, intocable. El tema claro sigue disponible (claro, oscuro o sistema, elegible en Perfil).

## Evidence on Hand

- Crítica de la interfaz del 2026-10-17 (162 capturas reales en emulador): `.impeccable/critique/2026-10-17T08-52-25Z__app-src-main-java-com-dlunaunizar-bobitos-feature.md`.
- No hay testimonios, métricas de uso ni datos de clientes: no se deben inventar.

## Product Principles

1. **Lo compartido primero:** cada pantalla deja claro qué es del grupo y quién lo hizo, sin repetirlo donde no aporta.
2. **Rapidez con una mano:** las acciones frecuentes (apuntar, marcar, completar) cuestan un toque o dos.
3. **Conectar, no duplicar:** un dato se escribe una vez y los módulos lo reutilizan.
4. **Nada se pierde por error:** lo que se borra o se añade por error se puede deshacer.
5. **Privacidad y coste cero** pesan más que cualquier función que los comprometa.

## Accessibility & Inclusion

- **WCAG AA como requisito permanente:** texto ≥ 4,5:1 e iconos, puntos y barras ≥ 3:1 contra su fondo real, en tema claro y oscuro.
- **Letra grande:** con la letra del sistema al 130 % nada se corta ni se solapa.
- **TalkBack usable:** todo se puede hacer con TalkBack. Cada control tiene nombre, rol y estado, y los menús dicen de qué elemento son. Hoy nadie del grupo lo usa, pero el usuario lo fija como requisito.
- Se respeta «Quitar animaciones» del sistema.
