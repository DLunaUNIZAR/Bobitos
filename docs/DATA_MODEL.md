# Modelo de datos inicial de Bobitos

> Diseño implementado para espacios, membresías, invitaciones y los módulos compartidos (compra, tareas, calendario y comidas).

| Campo | Valor |
| --- | --- |
| Estado | Modelo multiusuario y módulos compartidos implementados (incluye el planificador de comidas, Fase 1) |
| Versión | 0.8.0 |
| Fecha | 10 de octubre de 2026 |

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
spaces/{spaceId}
memberships/{spaceId_userId}
spaces/{spaceId}/shoppingItems/{itemId}
spaces/{spaceId}/tasks/{taskId}
spaces/{spaceId}/events/{eventId}
spaces/{spaceId}/meals/{mealId}
recipes/{recipeId}
routines/{routineId}
ingredients/{ingredientId}
exercises/{exerciseId}
ingredientPrefs/{userId}
catalogMeta/exercises
invitations/{inviteToken}
```

Las colecciones **top-level globales** (no cuelgan de un espacio) son `recipes`, `routines`, `ingredients`, `exercises`, `catalogMeta` e `ingredientPrefs`. `recipes` agrupa el catálogo común y las recetas personales de todos los usuarios (ver sección 11 · Recetario); `exercises` es el catálogo común de ejercicios (ver la sección «Ejercicios» tras el Recetario).

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
```

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
ingredients: array<Ingredient>?        # embebido, opcional, ≤50 (Fase 3)
createdBy: string
createdByName: string
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
```

Cada `Ingredient` es un mapa embebido `{ name: string, quantity: string?, unit: string? }` (cantidad y unidad son texto libre). El array es **opcional y retrocompatible**: una receta sin el campo se lee como «sin ingredientes».

- **`PRIVATE`** — receta personal: la crea cualquier usuario verificado (`ownerUid == uid`), solo la ve su dueño y aparece en **todos sus espacios** con una única consulta `where ownerUid == me` (sin `collectionGroup` ni fan-out). El *fork* de otra receta produce una copia `PRIVATE` con `sourceRecipeId` (copia también sus ingredientes).
- **`GLOBAL`** — receta del **catálogo común**, visible para todos. Solo la publica/cura una cuenta admin (allowlist de UID). Al planificar una comida se puede referenciar cualquier receta visible (`Meal.recipeId`).
- **Curación y admins:** la política y el procedimiento para añadir/rotar admins están en [`RECIPES_ADMIN.md`](RECIPES_ADMIN.md). La frontera de seguridad es `recipeAdmins()` en `firestore.rules`; la app replica la allowlist solo para enseñar la opción de publicar.
- **Validación de `ingredients` (Fase 3):** las reglas validan de forma **gruesa** (`is list` + `size() <= 50`); Firestore Rules no puede inspeccionar cada elemento del array. El parseo por elemento (descartar mapas sin `name`) lo hace el cliente al leer.

#### Integración con la Compra (Fase 3)

Desde una comida enlazada a una receta (`Meal.recipeId`) se pueden **volcar sus ingredientes a la lista de la Compra** del espacio activo: se crea un `ShoppingItem` por ingrediente reutilizando `ShoppingRepository.addItem` (`name`=nombre, `quantity`=cantidad, `notes`=unidad). No hay lecturas extra (la receta ya está observada) ni dedupe: el usuario ajusta en la Compra. La acción respeta `canWrite`.

#### Consultas previstas

- Catálogo común: `where visibility == "GLOBAL"` (acotada con `.limit`).
- Mis recetas: `where ownerUid == me`.
- Ambas son igualdades sobre un único campo → índice de campo único automático; **no requieren índice compuesto**. Las reglas de `recipes` no usan `get()`/`exists()` (coste 0).

### Ejercicios (colección `exercises`)

Catálogo común de ejercicios, **top-level** y compartido (parecido a `ingredients`). La documentación de cómo se siembra y se importa está en [`EXERCISE_CATALOG.md`](EXERCISE_CATALOG.md).

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
- La estrategia de actualización por lotes se probará con los límites reales de Firestore.

### Eliminación de espacio

- Debe eliminar el contenido (compra, tareas, eventos y comidas), las membresías y las invitaciones relacionadas.
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
