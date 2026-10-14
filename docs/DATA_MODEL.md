# Modelo de datos inicial de Bobitos

> Diseño implementado para espacios, membresías, invitaciones y los módulos compartidos (compra, tareas, calendario y comidas).

| Campo | Valor |
| --- | --- |
| Estado | Modelo multiusuario y módulos compartidos implementados (incluye el planificador de comidas, Fase 1) |
| Versión | 0.9.0 |
| Fecha | 11 de octubre de 2026 |

## 1. Objetivos

- Separar completamente los datos de cada espacio.
- Permitir que un usuario pertenezca a varios espacios.
- Facilitar comprobaciones de membresía en Security Rules.
- Actualizar compra, tareas, calendario y comidas prácticamente en tiempo real.
- Reducir conflictos mediante documentos pequeños.
- Limitar lecturas y mantener el uso dentro del nivel gratuito.
- No depender de Cloud Functions para las operaciones cotidianas.

## 2. Estructura general

```text
users/{userId}
users/{userId}/ingredients/{slug}
users/{userId}/brands/{brandId}
spaces/{spaceId}
memberships/{spaceId_userId}
spaces/{spaceId}/shoppingItems/{itemId}
spaces/{spaceId}/tasks/{taskId}
spaces/{spaceId}/events/{eventId}
spaces/{spaceId}/meals/{mealId}
spaces/{spaceId}/pantryItems/{pantryId}
recipes/{recipeId}
routines/{routineId}
ingredients/{ingredientId}
exercises/{exerciseId}
ingredientPrefs/{userId}
catalogMeta/exercises
catalogMeta/ingredients
invitations/{inviteToken}
```

Las colecciones **top-level globales** (no cuelgan de un espacio) son `recipes`, `routines`, `ingredients`, `exercises`, `catalogMeta` e `ingredientPrefs`. `recipes` agrupa el catálogo común y las recetas personales de todos los usuarios (ver sección 11 · Recetario); `exercises` es el catálogo común de ejercicios (ver la sección «Ejercicios» tras el Recetario). Los ingredientes personales y las marcas cuelgan de `users/{userId}` (ver «Ingredientes» tras el Recetario).

## 3. Convenciones

- Los identificadores de documentos serán generados de forma aleatoria, salvo membresías.
- Una membresía utilizará un identificador determinista: `{spaceId}_{userId}`.
- Los instantes se almacenarán como `Timestamp` UTC.
- Las fechas de día completo usarán campos de fecha independientes para evitar desplazamientos por zona horaria.
- `createdAt` y `updatedAt` utilizarán marcas temporales del servidor.
- Los valores enumerados se almacenarán en mayúsculas.
- Los campos opcionales ausentes se representarán como `null` o se omitirán de forma consistente, decisión que se fijará antes de programar los modelos Kotlin.

## 4. Usuarios

Ruta:

```text
users/{userId}
```

Propuesta:

```text
displayName: string
createdAt: timestamp
updatedAt: timestamp
```

### Consideraciones

- El identificador coincide con Firebase Authentication UID.
- El correo se obtiene de Authentication y no necesita duplicarse en Firestore.
- No se almacenará fotografía en el MVP.
- El avatar se genera localmente mediante iniciales.

## 5. Espacios

Ruta:

```text
spaces/{spaceId}
```

Propuesta:

```text
name: string
ownerId: string
createdBy: string
createdAt: timestamp
updatedAt: timestamp
memberCount: number
lastMembershipChangeUserId: string
```

### Validaciones

- `name` obligatorio y con longitud máxima.
- `ownerId` debe corresponder a una membresía activa con rol `OWNER`.
- La creación del espacio y de su primera membresía debe ser consistente.
- `memberCount` se actualiza en la misma transacción que la membresía afectada.
- `lastMembershipChangeUserId` permite que las Security Rules relacionen la variación del contador con la membresía creada o eliminada; no es un historial de actividad.

## 6. Membresías

Ruta:

```text
memberships/{spaceId_userId}
```

Propuesta:

```text
spaceId: string
userId: string
displayName: string
role: "OWNER" | "MEMBER"
status: "ACTIVE"
joinedAt: timestamp
joinedViaInvitationId: string | ausente
```

### Consultas previstas

- Espacios del usuario: `where userId == currentUserId` y `status == ACTIVE`.
- Miembros de un espacio: `where spaceId == activeSpaceId` y `status == ACTIVE`.

### Seguridad

- El usuario no puede crear una membresía libremente.
- Solo puede crearse al crear un espacio o aceptar una invitación válida.
- Los roles solo cambian durante una transferencia de propiedad autorizada.
- `displayName` es una copia visible del nombre en el momento de crear la membresía. Evita exponer perfiles globales de otros usuarios y podrá sincronizarse al editar el perfil en una fase posterior.
- El propietario no puede eliminar su membresía. Debe transferir la propiedad antes de abandonar el espacio.
- La transferencia actualiza `ownerId` y los dos roles en una única transacción.
- `joinedViaInvitationId` solo existe en membresías creadas al consumir una invitación y permite validar la operación conjunta en Security Rules.

## 7. Invitaciones

Ruta:

```text
invitations/{inviteToken}
```

Propuesta:

```text
spaceId: string
createdBy: string
createdAt: timestamp
expiresAt: timestamp
status: "ACTIVE" | "USED" | "REVOKED"
usedBy: string | null
usedAt: timestamp | null
revokedAt: timestamp | null
```

### Token

- Se generan 160 bits con `SecureRandom` y se codifican como 32 caracteres Base32.
- El enlace usa `bobitos://invite/{inviteToken}` y abre directamente la aplicación Android.
- El código manual agrupa el mismo secreto en bloques de cuatro caracteres.
- Una cuenta verificada puede recuperar una invitación únicamente si conoce el token.
- Solo el propietario puede listar invitaciones y la consulta debe estar limitada por `spaceId`; los listados generales son rechazados.

### Caducidad

- El cliente solicita una vigencia de 72 horas. Las reglas limitan `expiresAt` al intervalo entre 71 y 72 horas desde `request.time` para absorber el pequeño desfase entre el reloj del dispositivo y el servidor sin permitir vigencias arbitrarias.
- No es necesario cambiar `status` a `EXPIRED`: la caducidad se calcula comparando la hora del servidor.
- Una invitación es válida si está `ACTIVE`, no ha caducado y todavía no tiene `usedBy`.

### Aceptación atómica

La operación deberá:

1. Actualizar la invitación a `USED`.
2. Establecer `usedBy` y `usedAt`.
3. Crear la membresía del usuario.
4. Incrementar `memberCount` y registrar `lastMembershipChangeUserId`.

Las Security Rules deberán comprobar el resultado conjunto mediante una escritura atómica y `getAfter()` o una estrategia equivalente validada en el emulador.

## 8. Lista de la compra

Ruta:

```text
spaces/{spaceId}/shoppingItems/{itemId}
```

Propuesta:

```text
name: string
quantity: string | null
notes: string | null
purchased: boolean
createdBy: string
createdByName: string
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
purchasedBy: string | null
purchasedByName: string | null
purchasedAt: timestamp | null
supermarket: string | ausente         # DIA | EROSKI | MERCADONA | CARREFOUR | ALCAMPO | CARNICERIA | PESCADERIA | FRUTERIA | PANADERIA | OTROS | INDIFERENTE
brand: string | ausente               # texto libre, ≤60
```

`supermarket` y `brand` son opcionales: un producto sin ellos se lee como «sin tienda» y «sin marca». La marca se guarda **como texto** (el nombre de la marca en el momento de añadir el producto), no como referencia a `users/{uid}/brands`: la lista es compartida y las marcas son personales, así que los demás miembros no pueden leerlas ni se rompe el producto si se borra la marca.

`brand` es el nombre de la marca elegida en el selector de marca y tienda (o el texto libre cuando el producto no es un ingrediente reconocido); «Indiferente» se guarda como ausente (`null`).

**Artículos repetidos.** Al crear un artículo (a mano, desde la ficha de un ingrediente o desde la revisión de una receta o comida) se busca uno ya apuntado con `shoppingMatchKey` (la clave tolerante `ingredientKey`: «Tomates» = «tomate»; si queda vacía, el nombre en minúsculas), y manda el pendiente sobre el comprado. Si está **pendiente**, se pregunta: sumar la cantidad (`ShoppingQuantity.sum`: suma si las dos son número con la misma unidad; si no, las une con « + ») o no añadirlo. Si está **comprado**, se vuelve a apuntar ese mismo documento (`relistItem`: `purchased = false` y atribución de compra a `null`, en una sola escritura) con la cantidad nueva. No se crean duplicados. Antes de la compra sin duplicados, la revisión de una receta escribía la unidad en `notes`; esos artículos no se migran.

### Decisión importante

No existirá `assigneeId`. La lista es compartida y no se asigna a nadie.

`createdByName` y `purchasedByName` son copias del nombre visible en el momento de la
acción. Evitan lecturas adicionales y conservan el contexto histórico aunque el usuario
cambie después su perfil. `purchasedBy` solo registra quién marcó el producto, sin
convertirlo en responsable.

### Consultas previstas

- Todos los productos del espacio activo, ordenados por `purchased` y fecha.
- El volumen esperado es pequeño; si crece, se separarán activos y archivados.

### Limpiar comprados

Se consultan desde servidor los elementos con `purchased == true` y una transacción vuelve
a leer cada candidato antes de borrarlo. Así, un producto desmarcado simultáneamente no se
elimina. Cada ejecución procesa hasta 100 elementos; si quedasen más, se repite la operación.

### Despensa (`spaces/{spaceId}/pantryItems/{pantryId}`)

Lo que hay en casa, compartido por los miembros del espacio. Solo guarda **si hay o no hay**: un documento por cada cosa que hay; «Se acabó» lo borra. Nunca se edita.

```text
name: string                  # 1–120; el nombre canónico del catálogo si es un ingrediente reconocido
category: string | null       # ≤60; la del ingrediente (común o personal); null o fuera de las 14 → «Otros»
createdBy: string             # uid de quien lo apuntó
createdAt: timestamp          # hora del servidor
shoppingItemId: string | null # el artículo de la Compra cuya compra lo trajo; null si se apuntó a mano
```

**Id:** `pantryId(name) = slug(shoppingMatchKey(name))`: «Tomates» y «tomate» dan `tomat`; «Aceite de oliva», `aceit-oliva`. Mismo id → sin duplicados; apuntar lo que ya está no hace nada. Un nombre sin letras ni números no se puede apuntar.

**Reglas:** leen y borran los miembros activos; crear exige la forma (`hasOnly`), `createdBy == auth.uid`, `createdAt == request.time` y un id con formato de slug (≤120); `update` está prohibido.

## 9. Tareas

Ruta:

```text
spaces/{spaceId}/tasks/{taskId}
```

Propuesta:

```text
title: string
description: string | null
assigneeId: string | null
assigneeName: string | null
dueAt: timestamp | null
priority: "LOW" | "MEDIUM" | "HIGH"
status: "TODO" | "DONE"
createdBy: string
createdByName: string
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
completedBy: string | null
completedByName: string | null
completedAt: timestamp | null
```

### Responsable

- En la creación, `assigneeId` deberá ser un miembro activo.
- Solo habrá un responsable principal.
- El campo admite `null` exclusivamente para tratar la salida o expulsión del responsable.
- Una tarea sin responsable deberá destacarse para ser reasignada.
- `assigneeName`, `createdByName` y `completedByName` conservan el nombre visible en el
  momento de la acción y evitan lecturas adicionales para presentar la lista.

### Consultas previstas

- Tareas pendientes del espacio.
- Tareas por responsable y estado.
- Tareas por fecha límite.
- Tareas completadas con límite o paginación.

Estas combinaciones probablemente requerirán índices compuestos.

## 10. Eventos

Ruta:

```text
spaces/{spaceId}/events/{eventId}
```

Propuesta para eventos con hora:

```text
title: string
description: string | null
allDay: false
startAt: timestamp
endAt: timestamp
timeZone: string
color: string
participantIds: array<string>
createdBy: string
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
```

Para eventos de día completo se añadirán fechas locales explícitas:

```text
allDay: true
startDate: string  # YYYY-MM-DD
endDateExclusive: string  # YYYY-MM-DD, primer día que queda fuera del evento
timeZone: string
```

### Participantes

- `participantIds` puede estar vacío.
- Como el espacio tiene un máximo previsto de 10 miembros, un array es suficiente para el MVP.
- Todos los identificadores deben pertenecer al espacio.
- La visibilidad no depende de este campo.
- El calendario del espacio lee todos los eventos del intervalo y filtra participantes en el cliente.
- Mi calendario consulta el mismo intervalo en cada espacio activo y conserva en el cliente únicamente documentos donde `participantIds` contiene el UID actual. Esta decisión evita índices y colecciones duplicadas, y es adecuada para el límite de 2-10 miembros y el volumen inicial.

### Consultas previstas

- Eventos cuyo inicio o rango afecte al intervalo visible.
- Eventos de cada espacio activo dentro del intervalo visible para construir Mi calendario.

La consulta de eventos que atraviesan un intervalo necesita validarse con datos reales e índices antes de fijar el esquema definitivo.

## 11. Comidas

Ruta:

```text
spaces/{spaceId}/meals/{mealId}
```

Propuesta:

```text
date: string        # YYYY-MM-DD
slot: "DESAYUNO" | "COMIDA" | "CENA"
name: string
participantIds: array<string>
participantNames: array<string>
recipeId: string?     # opcional: referencia a recipes/{recipeId}
createdBy: string
createdByName: string
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
```

### Planificación por día

- Cada comida pertenece a un día (`date`) y una franja (`slot`): desayuno, comida o cena.
- El planificador muestra una semana (lunes→domingo) y resalta el día elegido; la app observa el rango de la semana visible por `date`.
- `name` es texto libre («qué se come»). Al planificar se puede **elegir una receta** que rellene el nombre y guarde su `recipeId` (opcional y retrocompatible); editar el nombre a mano lo desvincula (`recipeId = null`).

### Comensales

- `participantIds` indica qué miembros consumen la comida y puede estar vacío.
- Como el espacio tiene un máximo de 10 miembros, un array es suficiente.
- `participantNames` es la copia denormalizada de los nombres (mismo tamaño que `participantIds`), resuelta desde las membresías activas al escribir, igual que en eventos.
- Todos los identificadores deben pertenecer al espacio (el repositorio los valida contra las membresías activas dentro de la transacción).

### Consultas previstas

- Comidas del espacio cuyo `date` cae en la semana visible (`date >= lunes` y `date < lunes siguiente`), ordenadas por `date` y franja.
- Es un rango sobre un único campo (`date`), cubierto por el índice de campo único automático: no requiere índice compuesto.

### Recetario (colección `recipes`)

Fase 2. Colección **top-level** (no por espacio), distinguida por campos, no por ruta:

```text
recipes/{recipeId}
```

```text
ownerUid: string                       # dueño de la receta
visibility: "GLOBAL" | "PRIVATE"       # catálogo común vs personal
title: string
description: string?
category: string?
sourceRecipeId: string?                # si es un fork («guardar como mía»)
sourceUrl: string?                     # web de la que se importó (importador web)
ingredients: array<Ingredient>?        # embebido, opcional, ≤50 (Fase 3)
steps: array<string>?                  # pasos de la preparación, ≤30 de ≤500 caracteres
servings: int?                         # raciones, 1–50
totalMinutes: int?                     # tiempo total en minutos, 1–1440
source: map?                           # solo comunes del catálogo: {provider: "bobitos", importedAt}
createdBy: string
createdByName: string
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
```

Cada `Ingredient` es un mapa embebido `{ name: string, quantity: string?, unit: string? }` (cantidad y unidad son texto libre). El array es **opcional y retrocompatible**: una receta sin el campo se lee como «sin ingredientes».

Las recetas antiguas no tienen `steps`, `servings` ni `totalMinutes` (sus pasos están en la descripción) y se leen igual; editarlas añade `steps: []` y los dos números a `null`.

- **Catálogo común importado:** unas 60 comunes de lista propia (`recipes/{slug}`, autor «Catálogo Bobitos», con `source`), que crea el importador con el Admin SDK (ver [`RECIPE_CATALOG.md`](RECIPE_CATALOG.md)). Ningún cliente puede cambiar `source`, y solo un admin lo escribe, al deshacer el borrado de una común (`validRecipeRestore`); el admin puede editar el resto de campos de una común en la app, y entonces el importador deja de actualizarla. Cada ingrediente del catálogo usa el nombre canónico de `ingredients` y una unidad de lista cerrada. «Deshacer» tras borrar una receta la recrea con su mismo id (`restoreRecipe`): una común, tal como era (dueño, autor, fechas y `source`; si su última edición aún no había llegado al servidor, con la hora del servidor); una privada, como receta nueva de quien deshace.

- **`PRIVATE`** — receta personal: la crea cualquier usuario verificado (`ownerUid == uid`), solo la ve su dueño y aparece en **todos sus espacios** con una única consulta `where ownerUid == me` (sin `collectionGroup` ni fan-out). El *fork* de otra receta produce una copia `PRIVATE` con `sourceRecipeId` (copia también sus ingredientes).
- **`GLOBAL`** — receta del **catálogo común**, visible para todos. Solo la publica/cura una cuenta admin (allowlist de UID). Al planificar una comida se puede referenciar cualquier receta visible (`Meal.recipeId`).
- **Curación y admins:** la política y el procedimiento para añadir/rotar admins están en [`RECIPES_ADMIN.md`](RECIPES_ADMIN.md). La frontera de seguridad es `recipeAdmins()` en `firestore.rules`; la app replica la allowlist solo para enseñar la opción de publicar. Una `GLOBAL` solo la editan o borran los admins actuales; su dueño, si ya no es admin, no.
- **Validación de `ingredients` (Fase 3):** las reglas validan de forma **gruesa** (`is list` + `size() <= 50`); Firestore Rules no puede inspeccionar cada elemento del array. El parseo por elemento (descartar mapas sin `name`) lo hace el cliente al leer.

#### Integración con la Compra (Fase 3)

Desde la ficha de una receta o desde una comida enlazada a una receta (`Meal.recipeId`) se pueden **volcar sus ingredientes a la lista de la Compra** del espacio activo, tras una revisión (`IngredientReviewDialog`). Los ingredientes se agrupan con `shoppingMatchKey` («Tomate» y «tomates» van juntos) y sus cantidades se suman (`ShoppingQuantity.sum`). Cada fila nueva crea un `ShoppingItem` (`name`=nombre, `quantity`=cantidad con su unidad, p. ej. «200 g»; sin `notes`). Una fila que ya está pendiente en la lista sale con «Omitir» (por defecto) o «Sumar» (solo cambia la cantidad); una que está comprada se vuelve a apuntar (`relistItem`). Si todo queda en «Omitir», no se escribe nada. La acción respeta `canWrite`.

#### Consultas previstas

- Catálogo común: `where visibility == "GLOBAL"` (acotada con `.limit`).
- Mis recetas: `where ownerUid == me`.
- Ambas son igualdades sobre un único campo → índice de campo único automático; **no requieren índice compuesto**. Las reglas de `recipes` no usan `get()`/`exists()` (coste 0).

### Ejercicios (colección `exercises`)

Catálogo común de ejercicios, **top-level** y compartido (parecido a `ingredients`). La documentación de cómo se siembra y se importa está en [`EXERCISE_CATALOG.md`](EXERCISE_CATALOG.md). El catálogo común de ingredientes está documentado en [`INGREDIENT_CATALOG.md`](INGREDIENT_CATALOG.md).

```text
exercises/{exerciseId}                 # id = slug del nombre
```

```text
name: string                           # 1-120
nameLower: string                      # ≤120, para búsqueda
type: "MAQUINA" | "PESO_LIBRE" | "PESO_CORPORAL" | "CARDIO" | "OTROS"
muscleGroup: string?                   # ≤60
description: string?                   # ≤2000, texto plano
equipment: array<string>?              # ≤13, sin repetidos, valores del enum de material
measure: "REPS" | "SECONDS"?           # en qué se registran las series; ausente o null = REPS. SECONDS solo en MAQUINA, PESO_LIBRE y PESO_CORPORAL (lo valida la app y el importador, no las reglas)
image: map?                            # referencia a la imagen (el WebP vive en exerciseImages/{exerciseId}); solo la escribe el importador
  hash: string                         # sha256 del WebP, 64 caracteres hexadecimales en minúscula
  author: string?                      # ≤200, sin correos
  license: "CC-BY-SA-3.0" | "CC-BY-SA-4.0" | "CC-BY-4.0" | "CC0-1.0"
  sourceUrl: string                    # ≤300, empieza por https://wger.de/media/; solo procedencia, la app no la abre
source: map?                           # atribución; solo la escribe el importador
  provider: "wger" | "bobitos"         # bobitos = ficha propia del proyecto (Catálogo Bobitos)
  id: int                              # >0, id de la ficha en wger (o bobitosId)
  license: "CC-BY-SA-3.0" | "CC-BY-SA-4.0" | "CC-BY-4.0" | "CC0-1.0"   # bobitos: siempre CC-BY-SA-4.0
  author: string?                      # ≤200; bobitos: «Catálogo Bobitos»
  url: string?                         # ≤200, empieza por https://wger.de/; prohibida en bobitos
  importedAt: timestamp?
ownerUid: string
createdBy: string
createdByName: string                  # 1-60
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
```

Valores de `equipment`: `BARRA`, `BARRA_Z`, `MANCUERNAS`, `KETTLEBELL`, `DISCO`, `POLEA`, `MAQUINA`, `BANCO`, `BANCO_INCLINADO`, `BARRA_DOMINADAS`, `ESTERILLA`, `FITBALL`, `BANDA_ELASTICA`.

- **Lectura:** cualquier usuario verificado.
- **Creación:** el propio usuario (`ownerUid == uid`, marcas de tiempo = `request.time`). Los clientes **no pueden crear `source` ni `image`**: los escribe solo el importador con el Admin SDK, que se salta las reglas.
- **Edición:** el dueño o un admin. Solo cambian `name`, `nameLower`, `type`, `muscleGroup`, `description`, `equipment`, `measure`, `updatedBy` y `updatedAt`; `source`, `image` y la autoría son inmutables para los clientes (y, si la ficha tiene `image`, debe seguir siendo válida).
- **Borrado:** el dueño o un admin.
- Los campos `description`, `equipment`, `measure`, `image` y `source` son opcionales y retrocompatibles; la app antigua los ignora, pero descarta las fichas `PESO_CORPORAL` (ver `EXERCISE_CATALOG.md`).
- Sin `get()`/`exists()` en las reglas y sin índices compuestos.
- La app lista el catálogo con `limit(1000)` y sin `orderBy` (ordena en el cliente), para no perder las fichas sin `nameLower`.

### Rutinas (colección `routines`)

```text
ownerUid: string
visibility: "GLOBAL" | "PRIVATE"
title: string                          # 1-120
description: string?                   # ≤1000
exercises: list?                       # ≤30 (ver «Ejercicios embebidos»)
source: map?                           # solo en las comunes del catálogo: { provider: "bobitos", importedAt: timestamp }
sourceRoutineId: string?               # id de la común de la que es copia; ausente, null o 1-128; inmutable
createdBy: string
createdByName: string                  # 1-60
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
```

`source` lo escribe solo el importador con el Admin SDK (ver `ROUTINE_CATALOG.md`) y tiene la misma forma que en ingredientes. `sourceRoutineId` lo guarda «Guardar como mía» al copiar una común.

- **Lectura:** cualquier usuario verificado.
- **Creación:** el propio usuario (`ownerUid == uid`, marcas de tiempo = `request.time`); `GLOBAL` solo el admin. Ningún cliente puede crear `source`.
- **Edición:** el dueño en las `PRIVATE`; en las `GLOBAL`, solo un admin actual (aunque el dueño sea otro o un admin retirado). Solo cambian `title`, `description`, `exercises`, `updatedBy` y `updatedAt`; `source`, `sourceRoutineId` y la autoría son inmutables.
- **Borrado:** como la edición.

#### Ejercicios embebidos en `routines` y `activities.session`

La lista de ejercicios de una rutina (`routines.exercises`) y la de una sesión de gimnasio (`activities.session`) comparten contrato y las mismas guardas de reglas (la app acota a 30 ejercicios y 20 series por ejercicio):

```text
name: string
exerciseId: string?                    # ficha del catálogo, si viene de ella
type: "MAQUINA" | "PESO_LIBRE" | "PESO_CORPORAL" | "CARDIO" | "OTROS"
measure: "SECONDS"?                    # solo se escribe si es SECONDS; ausente = repeticiones
sets: array<map>
  reps: int?
  weight: number?
  seconds: int?                        # solo se escribe si hay valor (series por tiempo)
durationMinutes: int?                  # CARDIO y OTROS
level: string?
notes: string?
```

Un ejercicio de repeticiones se escribe exactamente igual que antes de `measure` y `seconds`. La app antigua ignora ambos campos y los pierde al guardar la rutina o la sesión.

### Imágenes de ejercicios (colección `exerciseImages`)

Colección **top-level** con la miniatura de cada ejercicio del catálogo que tiene imagen (ver «Imágenes» en [`EXERCISE_CATALOG.md`](EXERCISE_CATALOG.md)). El documento comparte id con la ficha.

```text
exerciseImages/{exerciseId}            # mismo id que exercises/{exerciseId}
```

```text
data: bytes                            # WebP de 400 px como máximo (≈14 KB de media, ≤200 KB)
contentType: "image/webp"
hash: string                           # sha256 de data; coincide con exercises.image.hash
width: int
height: int
author: string?                        # ≤200, sin correos
license: "CC-BY-SA-3.0" | "CC-BY-SA-4.0" | "CC-BY-4.0" | "CC0-1.0"
sourceUrl: string                      # procedencia en https://wger.de/media/
updatedAt: timestamp                   # serverTimestamp del importador
```

- **Lectura:** solo `get` de un documento, para cualquier usuario verificado. Sin `list`.
- **Escritura:** ningún cliente. La hace el importador con el Admin SDK, que se salta las reglas.
- La app lee cada imagen una vez por dispositivo y la guarda en caché (1 lectura por imagen y dispositivo).
- El importador sube las imágenes antes que las fichas y nunca borra las huérfanas.
- Sin `get()`/`exists()` en las reglas y sin índices.

### Versión del catálogo (documento `catalogMeta/exercises`)

Documento único, **top-level**, con la versión del catálogo de ejercicios para que la app cachee el catálogo en lugar de releerlo entero en cada apertura (ver «Versión del catálogo y caché» en [`EXERCISE_CATALOG.md`](EXERCISE_CATALOG.md)).

```text
catalogMeta/exercises
version: int                           # 1 al crearlo; +1 en cada cambio del catálogo
updatedAt: timestamp                   # request.time
updatedBy: string                      # uid de quien lo sube (la cuenta admin en el importador)
```

- **Lectura:** solo `get` del documento `exercises`, para cualquier usuario verificado. Sin `list`.
- **Creación:** solo `exercises`, con `version == 1`, `updatedAt == request.time`, `updatedBy == uid` y sin más campos.
- **Actualización:** `version == anterior + 1`, con las mismas guardas. **Sin borrado.**
- Lo suben el importador (una vez por lote) y cada guardado de un ejercicio desde la app, en la misma transacción que la ficha. Las reglas de `exercises` no exigen subirla, para no romper las escrituras de la app antigua; a cambio, la caché de la app caduca a los 7 días.
- Cada guardado cuesta +2 lecturas y +1 escritura; abrir el catálogo sin cambios, 1 lectura.

### Ingredientes

Tres piezas: el catálogo común (top-level, solo lo escribe el admin), y los ingredientes y las marcas personales (bajo `users/{userId}`, solo del dueño). El diseño completo está en [`superpowers/specs/2026-10-11-catalogo-ingredientes-design.md`](superpowers/specs/2026-10-11-catalogo-ingredientes-design.md) y el flujo de edición e importación del catálogo en [`INGREDIENT_CATALOG.md`](INGREDIENT_CATALOG.md).

#### Catálogo común (colección `ingredients`)

```text
ingredients/{slug}
name: string                           # 1–120
nameLower: string                      # ≤120
category: string?                      # ≤60
defaultUnit: string?                   # ≤24 (g | ml | ud en el catálogo sembrado)
source: map?                           # { provider: "bobitos", importedAt: timestamp } (solo lo pone el importador)
createdAt: timestamp
updatedAt: timestamp
updatedBy: string                      # uid del admin
```

- El id es el *slug* del nombre (`slug()`, igual en la app y en `scripts/catalog/normalize.mjs`). Sin campos de dueño ni subcolecciones: las marcas ya no cuelgan de aquí.
- **Lectura:** `get` y `list` para cualquier usuario verificado.
- **Escritura:** solo el admin del catálogo (la misma allowlist que las recetas, `RECIPES_ADMIN.md`). La app solo ofrece editar a esos uid; las reglas lo vuelven a exigir.
- **`source`:** lo añade el importador (Admin SDK, salta las reglas). Los clientes no lo crean (`create` rechaza el campo) ni lo modifican (`update` solo cambia `name`, `nameLower`, `category`, `defaultUnit`, `updatedBy` y `updatedAt`).
- **Versión y caché:** `catalogMeta/ingredients` guarda la versión del catálogo (`version`, `updatedAt`, `updatedBy`). Se crea con `version == 1` y cada cambio sube `+1`; solo la escribe el admin, de uno en uno, sin `list` ni borrado. La lee cualquier verificado con `get`. El importador la sube una vez por lote con cambios; cada alta, edición o borrado del admin en la app va en la **misma transacción** que la ficha. Las reglas de `ingredients` no exigen subirla, para no romper a la beta 18.
- La app carga el catálogo con `VersionedCatalogLoader` (clave `"ingredients"`): lee la versión (1 lectura) y, si coincide con la guardada, usa la caché local; si no, relee la colección entera (unos 358 documentos). La caché caduca a los 7 días. Los cambios de otros dispositivos llegan en la siguiente apertura, no en directo.

#### Ingredientes personales (`users/{userId}/ingredients/{slug}`)

```text
name: string                           # 1–120
nameLower: string                      # ≤120
category: string?                      # ≤60
defaultUnit: string?                   # ≤24
createdAt: timestamp
updatedAt: timestamp
```

- El id es el *slug* del nombre y el dueño es la ruta: no hay `ownerUid`, `source` ni `updatedBy`.
- **Sin choque con un común:** al crear, las reglas comprueban con `exists()` que no haya `ingredients/{slug}`. La app lo detecta antes, por la clave tolerante (ver más abajo), y lleva al ingrediente existente; si el alta falla por permisos y ese común existe, lo trata como «ya existe».
- **Si el común aparece después:** el personal sigue existiendo (las reglas solo miran al crear). La app fusiona la lista y **oculta el personal cuyo id ya es común**: prevalece el común, y el personal queda en Firestore (sus marcas siguen colgando de ese mismo slug).
- **Actualizar:** solo `name`, `nameLower`, `category`, `defaultUnit` y `updatedAt`. **Borrar:** el dueño, que en la app borra también sus marcas y su entrada de `ingredientPrefs` en el mismo lote.
- La app lee hasta 500 con un listener en tiempo real.

#### Marcas personales (`users/{userId}/brands/{brandId}`)

```text
ingredientId: string                   # slug del ingrediente (común o personal) al que pertenece; inmutable
name: string                           # 1–60
barcode: string?                       # ≤32
favorite: boolean
stores: array<string>                  # ≤6, sin repetidos, del enum de supermercados sin INDIFERENTE
energyKcal, fat, carbohydrates,
sugars, protein, salt: number?         # por 100 g/ml, opcionales
createdAt: timestamp
updatedAt: timestamp
```

- Id aleatorio. `ingredientId` no cambia tras crear la marca (`update` no lo admite entre los campos modificables, que son el resto menos `createdAt`).
- `stores` usa los valores `DIA`, `EROSKI`, `MERCADONA`, `CARREFOUR`, `ALCAMPO`, `CARNICERIA`, `PESCADERIA`, `FRUTERIA`, `PANADERIA` y `OTROS`; el orden es el que eligió el usuario. Al leer, la app ignora nombres desconocidos, `INDIFERENTE` y repetidos.
- `favorite`: marca preferida del ingrediente; se muestra en la lista como «★ Marca · Tienda». Las favoritas se ordenan primero.
- Al editar, un código de barras o un nutriente vacío se borra del documento.
- Un ingrediente puede tener varias marcas. La app las lee (hasta 1000) con un listener en tiempo real.

#### Dónde lo compro (`ingredientPrefs/{userId}`)

```text
ingredientPrefs/{userId}
entries: map<slug, { stores: array<string> }>
```

- Un documento por usuario con un único campo, `entries`, de ≤2000 entradas. La forma vigente es `slug → { stores }`; cada escritura usa `merge` y borra la entrada con `FieldValue.delete()` si se queda sin tiendas.
- Las entradas con la forma antigua (`supermarket`/`brand`), las malformadas y las que quedan sin tiendas **se ignoran al leer**; no se migran ni se borran.
- Son las tiendas del **ingrediente**; las de cada marca van en la marca (`stores`).

#### Clave tolerante (`ingredientKey`)

Para detectar casi duplicados («Limón» y «Limones», «Nuez» y «Nueces») se usa `ingredientKey(name)`: el *slug* sin palabras vacías (artículos, «de», «con»…), con cada palabra en singular aproximado (quita `-s` y `-e` finales y cambia `-z` por `-c`) y en orden alfabético. Está duplicada en Kotlin (`CatalogIngredient.kt`) y en `scripts/catalog/ingredients.mjs`; ambas implementaciones se comprueban contra la misma tabla, `tests/fixtures/ingredient-keys.json` (pares nombre → clave). La app la usa para encontrar el ingrediente existente al crear uno parecido, al escanear una marca y al añadir un ingrediente desde la Compra; el validador del catálogo, para rechazar casi duplicados.

#### Reglas

- `ingredients/{id}`: `get` y `list` a verificados; `create`, `update` y `delete` solo el admin, con forma validada y `updatedBy`/`updatedAt` del servidor.
- `catalogMeta/{id}`: `get` solo de `exercises` e `ingredients`; la escritura de `ingredients` solo el admin, con `version` 1 al crear y `+1` al actualizar; sin `list` ni borrado.
- `users/{uid}/ingredients/{slug}`: lectura (`get` y `list`) y escritura solo el dueño verificado; `create` con `slug` válido (`^[a-z0-9]+(-[a-z0-9]+)*$`, ≤120), `createdAt`/`updatedAt` del servidor y sin ingrediente común homónimo; `update` limitado a los campos editables.
- `users/{uid}/brands/{id}`: lectura y escritura solo el dueño verificado; forma validada (nombre ≤60, ≤6 tiendas del enum sin repetidos, nutrientes numéricos); `ingredientId` inmutable.
- `ingredientPrefs/{uid}`: lectura, escritura y borrado solo el dueño verificado; al escribir, solo el campo `entries` (puede faltar, p. ej. `{}`), un mapa de ≤2000 entradas.
- `ingredients/{id}/brands/**` (ruta antigua) queda cerrado por la regla final de denegación.

## 12. Acceso desde Security Rules

Conceptualmente, para acceder a un documento del espacio se comprobará una membresía determinista:

```text
memberships/{spaceId}_{request.auth.uid}
```

La regla deberá verificar:

- Usuario autenticado.
- Membresía existente y activa.
- Rol `OWNER` para operaciones administrativas.
- Campos y tipos permitidos.
- Identificadores de miembros válidos para responsables y participantes.
- Inmutabilidad de `spaceId`, `createdBy` y `createdAt` cuando corresponda.

## 13. Sincronización y conflictos

- Cada producto, tarea y evento es un documento independiente.
- Dos cambios sobre documentos distintos no compiten entre sí.
- Si dos usuarios editan el mismo documento, el último cambio confirmado podrá prevalecer.
- La interfaz mostrará siempre el estado confirmado por el backend.
- Sin conexión se bloquearán las escrituras desde la interfaz.
- Al recuperar conexión se recargarán snapshots del espacio activo.

## 14. Eliminación y anonimización

### Abandono de un miembro

- Se elimina su membresía.
- Las tareas pendientes asignadas a él pasan a `assigneeId: null`.
- El contenido compartido permanece.

### Eliminación de cuenta

- El usuario debe resolver primero los espacios de su propiedad.
- Se eliminan sus membresías y perfil.
- Las referencias personales necesarias (nombres en compra, tareas, eventos y comidas) se sustituyen por `null` o por «Usuario eliminado».
- Recetas: sus recetas `PRIVATE` (`ownerUid == me`) se **borran**; en las `GLOBAL` que haya publicado se **anonimiza** `createdByName` («Usuario eliminado»), conservando el catálogo. Se hace desde el cliente (las reglas permiten al dueño ambas operaciones).
- Ingredientes (se borra tras reautenticar y comprobar que el usuario no es propietario de ningún espacio, y antes de abandonar espacios y de anonimizar recetas): se borran sus `users/{uid}/ingredients`, sus `users/{uid}/brands` y su documento `ingredientPrefs/{uid}` (son datos personales). El catálogo común no contiene datos personales y no se toca. Se hace desde el cliente, repetible: si algo falla la cuenta no se borra.
- La estrategia de actualización por lotes se probará con los límites reales de Firestore.

### Eliminación de espacio

- Debe eliminar todas las subcolecciones de `spaces/{spaceId}` (compra, tareas, eventos, comidas, notas, sesiones de deporte y despensa: `SPACE_CHILD_COLLECTIONS`, que un test mantiene igual a las reglas), las membresías y las invitaciones relacionadas.
- Al no existir borrado en cascada automático, se implementará una eliminación paginada y verificable.
- Si la implementación exclusivamente cliente no resulta suficientemente segura, esta operación será candidata a una función backend mínima.

## 15. Índices iniciales a validar

- Membresías por `userId + status`.
- Membresías por `spaceId + status`.
- Invitaciones por `spaceId + status`.
- Compra por `purchased + createdAt`.
- Tareas por `status + dueAt`.
- Tareas por `assigneeId + status + dueAt`.
- Eventos por rango temporal.
- Comidas por rango de `date` (campo único; índice automático, sin índice compuesto).
- Recetas por `visibility` o por `ownerUid` (igualdad sobre campo único; índice automático, sin índice compuesto).

Solo se crearán los índices que requieran las consultas reales.

## 16. Preguntas técnicas para el prototipo

- [x] Validar aceptación atómica de invitaciones únicamente con Security Rules.
- [ ] Elegir representación definitiva de eventos de día completo.
- [ ] Diseñar consulta eficiente para eventos que atraviesan el intervalo visible.
- [ ] Validar la eliminación por lotes de un espacio sin Cloud Functions.
- [ ] Confirmar cómo impedir escrituras diferidas si la conexión cae durante la petición.
- [ ] Medir lecturas producidas por listeners al cambiar entre espacios y módulos.
