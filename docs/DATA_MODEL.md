# Modelo de datos inicial de Bobitos

> Diseño implementado para espacios, membresías e invitaciones. Los módulos compartidos continúan siendo provisionales.

| Campo | Valor |
| --- | --- |
| Estado | Espacios, membresías e invitaciones implementados; módulos compartidos provisionales |
| Versión | 0.3.0 |
| Fecha | 16 de julio de 2026 |

## 1. Objetivos

- Separar completamente los datos de cada espacio.
- Permitir que un usuario pertenezca a varios espacios.
- Facilitar comprobaciones de membresía en Security Rules.
- Actualizar compra, tareas y calendario prácticamente en tiempo real.
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
invitations/{inviteToken}
```

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
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
purchasedBy: string | null
purchasedAt: timestamp | null
```

### Decisión importante

No existirá `assigneeId`. La lista es compartida y no se asigna a nadie.

`purchasedBy` solo registra quién marcó el producto, sin convertirlo en responsable.

### Consultas previstas

- Todos los productos del espacio activo, ordenados por `purchased` y fecha.
- El volumen esperado es pequeño; si crece, se separarán activos y archivados.

### Limpiar comprados

Se ejecutará una operación por lotes sobre los elementos que sigan teniendo `purchased == true`. Si el número excede el límite de un lote, se procesará de forma paginada.

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
dueAt: timestamp | null
priority: "LOW" | "MEDIUM" | "HIGH"
status: "TODO" | "DONE"
createdBy: string
createdAt: timestamp
updatedBy: string
updatedAt: timestamp
completedBy: string | null
completedAt: timestamp | null
```

### Responsable

- En la creación, `assigneeId` deberá ser un miembro activo.
- Solo habrá un responsable principal.
- El campo admite `null` exclusivamente para tratar la salida o expulsión del responsable.
- Una tarea sin responsable deberá destacarse para ser reasignada.

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
endDate: string    # YYYY-MM-DD, criterio inclusivo o exclusivo por decidir en implementación
timeZone: string
```

### Participantes

- `participantIds` puede estar vacío.
- Como el espacio tiene un máximo previsto de 10 miembros, un array es suficiente para el MVP.
- Todos los identificadores deben pertenecer al espacio.
- La visibilidad no depende de este campo.

### Consultas previstas

- Eventos cuyo inicio o rango afecte al intervalo visible.
- Próximos eventos con límite.

La consulta de eventos que atraviesan un intervalo necesita validarse con datos reales e índices antes de fijar el esquema definitivo.

## 11. Acceso desde Security Rules

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

## 12. Sincronización y conflictos

- Cada producto, tarea y evento es un documento independiente.
- Dos cambios sobre documentos distintos no compiten entre sí.
- Si dos usuarios editan el mismo documento, el último cambio confirmado podrá prevalecer.
- La interfaz mostrará siempre el estado confirmado por el backend.
- Sin conexión se bloquearán las escrituras desde la interfaz.
- Al recuperar conexión se recargarán snapshots del espacio activo.

## 13. Eliminación y anonimización

### Abandono de un miembro

- Se elimina su membresía.
- Las tareas pendientes asignadas a él pasan a `assigneeId: null`.
- El contenido compartido permanece.

### Eliminación de cuenta

- El usuario debe resolver primero los espacios de su propiedad.
- Se eliminan sus membresías y perfil.
- Las referencias personales necesarias se sustituyen por `null` o una representación anónima.
- La estrategia de actualización por lotes se probará con los límites reales de Firestore.

### Eliminación de espacio

- Debe eliminar contenido, membresías e invitaciones relacionadas.
- Al no existir borrado en cascada automático, se implementará una eliminación paginada y verificable.
- Si la implementación exclusivamente cliente no resulta suficientemente segura, esta operación será candidata a una función backend mínima.

## 14. Índices iniciales a validar

- Membresías por `userId + status`.
- Membresías por `spaceId + status`.
- Invitaciones por `spaceId + status`.
- Compra por `purchased + createdAt`.
- Tareas por `status + dueAt`.
- Tareas por `assigneeId + status + dueAt`.
- Eventos por rango temporal.

Solo se crearán los índices que requieran las consultas reales.

## 15. Preguntas técnicas para el prototipo

- [x] Validar aceptación atómica de invitaciones únicamente con Security Rules.
- [ ] Elegir representación definitiva de eventos de día completo.
- [ ] Diseñar consulta eficiente para eventos que atraviesan el intervalo visible.
- [ ] Validar la eliminación por lotes de un espacio sin Cloud Functions.
- [ ] Confirmar cómo impedir escrituras diferidas si la conexión cae durante la petición.
- [ ] Medir lecturas producidas por listeners al cambiar entre espacios y módulos.
