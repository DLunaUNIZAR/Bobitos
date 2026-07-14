# Roles y permisos de Bobitos

| Campo | Valor |
| --- | --- |
| Estado | Aprobado para diseño |
| Versión | 0.1.0 |
| Fecha | 14 de julio de 2026 |

## 1. Principios

- La autenticación no concede por sí sola acceso a ningún espacio.
- El acceso se obtiene mediante una membresía activa.
- Los usuarios deben tener el correo verificado para crear espacios o aceptar invitaciones.
- Todo contenido compartido pertenece a un único espacio.
- Los participantes de un evento no determinan sus permisos.
- El responsable de una tarea tampoco determina quién puede editarla o completarla.
- El registro de quién compró un producto no supone responsabilidad sobre la compra.

## 2. Roles

### Propietario

Administra el espacio, sus invitaciones y sus miembros. También participa en los módulos compartidos como cualquier otro miembro.

### Miembro

Participa en compra, tareas y calendario, pero no administra la estructura ni los integrantes del espacio.

## 3. Matriz de permisos

| Acción | Propietario | Miembro | No miembro |
| --- | :---: | :---: | :---: |
| Ver el espacio | Sí | Sí | No |
| Ver miembros | Sí | Sí | No |
| Cambiar entre sus espacios | Sí | Sí | No aplica |
| Renombrar el espacio | Sí | No | No |
| Crear invitación | Sí | No | No |
| Revocar invitación | Sí | No | No |
| Expulsar miembro | Sí | No | No |
| Transferir propiedad | Sí | No | No |
| Eliminar espacio | Sí | No | No |
| Abandonar espacio | Tras transferir o eliminar | Sí | No aplica |
| Ver lista de la compra | Sí | Sí | No |
| Añadir producto | Sí | Sí | No |
| Editar producto | Sí | Sí | No |
| Marcar producto comprado | Sí | Sí | No |
| Limpiar productos comprados | Sí | Sí | No |
| Eliminar producto | Sí | Sí | No |
| Ver tareas | Sí | Sí | No |
| Crear tarea | Sí | Sí | No |
| Asignar o reasignar tarea | Sí | Sí | No |
| Completar o reabrir tarea | Sí | Sí | No |
| Editar o eliminar tarea | Sí | Sí | No |
| Ver calendario | Sí | Sí | No |
| Crear evento | Sí | Sí | No |
| Editar o eliminar evento | Sí | Sí | No |
| Añadir participantes | Sí | Sí | No |

## 4. Reglas específicas

### 4.1. Invitaciones

- Solo el propietario puede crear o revocar.
- Una invitación permite exclusivamente incorporarse al espacio indicado.
- Su conocimiento no concede acceso permanente: debe aceptarse con una cuenta verificada.
- Solo puede aceptarse si está activa, no ha caducado y no se ha utilizado.
- La aceptación debe crear la membresía y consumir la invitación atómicamente.

### 4.2. Propiedad

- Un espacio siempre debe tener un propietario mientras exista.
- La transferencia debe actualizar de forma atómica el propietario del espacio y los roles implicados.
- El propietario actual no puede degradarse o abandonar sin completar la transferencia.

### 4.3. Compra

- No existe el concepto de responsable de compra.
- `purchasedBy` registra una acción histórica y no concede permisos adicionales.
- Cualquier miembro puede revertir la marca de comprado.

### 4.4. Tareas

- Una tarea tiene como máximo un responsable principal.
- En la creación, el responsable debe ser miembro activo.
- Si esa persona abandona o es expulsada, la tarea puede quedar sin responsable.
- Ser responsable no concede permiso exclusivo: cualquier miembro puede editar, reasignar, completar o reabrir.

### 4.5. Calendario

- Todos los miembros ven todos los eventos.
- Los participantes son una selección informativa.
- Cualquier miembro puede editar la selección de participantes.

## 5. Acciones sobre la cuenta propia

Todo usuario autenticado podrá:

- Consultar su perfil.
- Cambiar su nombre visible.
- Cerrar sesión.
- Solicitar recuperación de contraseña.
- Eliminar su cuenta cuando no sea propietario de espacios.

No podrá:

- Cambiar su identificador interno.
- Escribir roles en sus propias membresías.
- Añadirse directamente a un espacio sin invitación válida.
- Leer invitaciones mediante búsquedas generales.

## 6. Principios para Firebase Security Rules

Las reglas deberán validar como mínimo:

1. `request.auth` existe.
2. El correo está verificado cuando la operación lo requiere.
3. Existe una membresía activa para el espacio afectado.
4. El rol es `OWNER` para operaciones administrativas.
5. El `spaceId` y los campos de autoría no se cambian después de crear el documento.
6. Los tipos, longitudes y valores enumerados son válidos.
7. Los usuarios referenciados como responsables o participantes son miembros activos.
8. Las operaciones de invitación y transferencia son atómicas.

## 7. Pruebas mínimas de permisos

- [ ] Usuario no autenticado rechazado.
- [ ] Usuario autenticado no miembro rechazado.
- [ ] Miembro puede operar sobre los tres módulos.
- [ ] Miembro no puede administrar espacio ni integrantes.
- [ ] Propietario puede administrar el espacio.
- [ ] Cuenta no verificada no puede crear espacio ni aceptar invitación.
- [ ] Invitación caducada, usada o revocada rechazada.
- [ ] Miembro de un espacio no puede acceder a otro.
- [ ] Responsable de tarea debe ser miembro activo.
- [ ] Participantes de evento deben pertenecer al espacio.
- [ ] Propietario no puede abandonar sin transferencia o eliminación.

