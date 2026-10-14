# Especificación funcional de Bobitos

> Documento funcional del MVP. Describe qué debe hacer el producto sin imponer todavía detalles de implementación Android.

| Campo | Valor |
| --- | --- |
| Estado | Aprobado para diseño |
| Versión | 0.1.0 |
| Fecha | 14 de julio de 2026 |
| Alcance | MVP Android |

## 1. Propósito

Bobitos permite que pequeños grupos privados organicen información cotidiana dentro de espacios compartidos. Cada espacio contiene una lista de la compra, tareas asignables y un calendario común.

El producto está orientado a familias, parejas y pisos compartidos de entre 2 y 10 miembros. Un usuario puede pertenecer a varios espacios.

## 2. Principios funcionales

- Todo el contenido es privado y pertenece a un espacio.
- Solo los miembros activos pueden acceder al contenido del espacio.
- Los cambios se muestran prácticamente en tiempo real cuando existe conexión.
- Sin conexión, la información disponible es de solo lectura.
- La lista de la compra es común y no tiene un responsable asignado.
- Las tareas sí tienen un responsable principal.
- Los eventos son visibles para todos los miembros; los participantes son informativos.
- Todos los miembros pueden editar compra, tareas y calendario.
- La administración del espacio corresponde al propietario.

## 3. Terminología

| Término | Definición |
| --- | --- |
| Usuario | Persona con una cuenta de Bobitos |
| Espacio | Grupo privado que contiene miembros y módulos compartidos |
| Propietario | Miembro que administra el espacio y sus integrantes |
| Miembro | Usuario aceptado dentro del espacio |
| Invitación | Credencial temporal de un solo uso para incorporarse a un espacio |
| Responsable | Miembro al que se asigna una tarea |
| Participante | Miembro relacionado opcionalmente con un evento, sin afectar a su visibilidad |

## 4. Autenticación y cuenta

### AUTH-01. Registro

El usuario podrá registrarse con:

- Nombre visible.
- Dirección de correo electrónico.
- Contraseña.

La app deberá validar el formato del correo y los requisitos mínimos de contraseña antes de enviar la solicitud.

### AUTH-02. Verificación de correo

- Después del registro se enviará un correo de verificación.
- Una cuenta sin verificar podrá iniciar sesión para completar la verificación.
- Una cuenta sin verificar no podrá crear espacios ni aceptar invitaciones.
- La app permitirá volver a enviar el correo dentro de los límites del proveedor.

### AUTH-03. Inicio y cierre de sesión

- La sesión se conservará entre aperturas de la app.
- El usuario podrá cerrar sesión desde ajustes.
- Al cerrar sesión se cancelarán los listeners activos y se ocultará el contenido privado.

### AUTH-04. Recuperación de contraseña

El usuario podrá solicitar un enlace de recuperación introduciendo su correo.

La respuesta visible no deberá revelar si una dirección pertenece o no a una cuenta cuando esto pueda facilitar la enumeración de usuarios.

### AUTH-05. Perfil

El perfil del MVP tendrá:

- Nombre visible.
- Avatar generado a partir de iniciales.
- Correo procedente del sistema de autenticación.

No se subirán fotografías.

### AUTH-06. Eliminación de cuenta

- Un miembro podrá eliminar su cuenta después de abandonar sus espacios.
- Un propietario deberá transferir o eliminar todos los espacios de su propiedad.
- El perfil y la autenticación se eliminarán.
- El contenido compartido necesario para otros miembros podrá conservarse, pero su atribución deberá quedar anonimizada como “Usuario eliminado”.

## 5. Espacios

### SPACE-01. Crear espacio

- Solo una cuenta verificada podrá crear un espacio.
- El creador se convertirá en propietario.
- El nombre será obligatorio y tendrá longitud limitada.

### SPACE-02. Listar y seleccionar

- La app mostrará todos los espacios activos del usuario.
- El usuario podrá cambiar de espacio.
- La app recordará localmente el último espacio seleccionado.
- Al cambiar, se cancelarán las suscripciones del espacio anterior.

### SPACE-03. Renombrar

Solo el propietario podrá renombrar el espacio.

### SPACE-04. Abandonar

- Un miembro podrá abandonar el espacio con confirmación.
- Un propietario no podrá abandonarlo mientras conserve la propiedad.
- Antes deberá transferir la propiedad o eliminar el espacio.
- Las tareas asignadas al miembro saliente quedarán sin responsable hasta ser reasignadas.

### SPACE-05. Eliminar

- Solo el propietario podrá eliminar el espacio.
- La app pedirá una confirmación reforzada.
- Se eliminarán membresías, invitaciones y contenido compartido asociado.

## 6. Invitaciones

### INV-01. Crear invitación

- Solo el propietario podrá crearla.
- Será de un solo uso.
- Caducará 72 horas después de su creación.
- Se ofrecerá como enlace compartible y como código alternativo.
- El token deberá ser aleatorio y no predecible.

### INV-02. Revocar invitación

El propietario podrá revocarla antes de que sea utilizada o caduque.

### INV-03. Aceptar invitación

- El usuario deberá estar autenticado y tener el correo verificado.
- La app comprobará que la invitación existe, sigue activa y no ha caducado.
- La aceptación y la creación de la membresía serán una única operación atómica.
- Una invitación utilizada no podrá aceptarse otra vez.
- Si el usuario ya pertenece al espacio, la app abrirá ese espacio sin crear otra membresía.

## 7. Miembros y roles

### MEMBER-01. Listar miembros

Todos los miembros podrán consultar:

- Nombre visible.
- Avatar de iniciales.
- Rol.

### MEMBER-02. Expulsar miembro

- Solo el propietario podrá expulsar.
- No podrá expulsarse a sí mismo mediante esta acción.
- Las tareas asignadas a la persona expulsada quedarán sin responsable.

### MEMBER-03. Transferir propiedad

- Solo el propietario actual podrá transferirla.
- El destinatario deberá ser un miembro activo.
- El cambio de propietario y de roles será atómico.

## 8. Lista de la compra

### SHOP-01. Naturaleza compartida

- La lista pertenece al espacio completo.
- No existe responsable de hacer la compra.
- Ningún producto tendrá un campo de asignación.
- El usuario que marca un producto como comprado se registra solo como información de actividad, no como responsable.

### SHOP-02. Crear producto

El producto podrá contener:

- Nombre obligatorio.
- Cantidad opcional.
- Observaciones opcionales.

Se registrará quién lo creó y cuándo.

### SHOP-03. Editar y eliminar

Cualquier miembro podrá editar o eliminar un producto. Las eliminaciones requerirán confirmación o permitirán deshacer durante un breve periodo.

### SHOP-04. Marcar como comprado

- Cualquier miembro podrá marcar o desmarcar.
- Los comprados se mostrarán debajo de los pendientes.
- Se registrarán la persona y la fecha que realizaron la acción.
- Si el producto es un ingrediente reconocido del catálogo (común o personal), al marcarlo entra en la Despensa del espacio; al desmarcarlo, sale si lo trajo esa compra.

### SHOP-05. Limpiar comprados

- Los productos comprados permanecerán visibles hasta que un miembro pulse “Limpiar comprados”.
- La acción afectará solamente a los productos que continúen marcados en el momento de confirmarla.
- La app pedirá confirmación e indicará el número de productos afectados.

### SHOP-06. Orden predeterminado

1. Productos pendientes.
2. Productos comprados.

Dentro de cada grupo se utilizará inicialmente la fecha de creación, salvo que el diseño posterior determine otro orden.

### PANTRY-01. Despensa del espacio

- Cada espacio tiene una Despensa, en «Más»: lo que hay en casa, compartido por sus miembros.
- Solo dice si algo **hay o no hay**, sin cantidades ni caducidades.
- Se agrupa por las 14 categorías del catálogo de ingredientes, con «Otros» al final, y tiene buscador por nombre y categoría.

### PANTRY-02. Llenar la despensa

- «Añadir» abre un selector de ingredientes (comunes y personales) con buscador y casillas; lo que ya hay sale marcado y deshabilitado.
- Si lo escrito no es ningún ingrediente, se puede apuntar tal cual («Añadir «Papel de cocina»»).
- Al marcar como comprado un ingrediente reconocido en la Compra, entra solo (SHOP-04).

### PANTRY-03. Se acabó

- «Se acabó» quita la entrada y ofrece «A la compra» durante unos segundos.
- «A la compra» lo apunta en la Compra: si ya está pendiente, solo avisa; si está comprado, lo vuelve a apuntar con su cantidad, notas, tienda y marca; si no está, lo añade sin cantidad, con la marca y la tienda por defecto.

### PANTRY-04. La despensa en la Compra

- Al añadir a la Compra los ingredientes de una receta o de las comidas, lo que hay en la despensa sale como «Hay en la despensa», con «Omitir» marcado; con «Añadir» se apunta como siempre (o se vuelve a apuntar, si estaba comprado). Si todo queda en «Omitir», la app dice «No hace falta añadir nada».
- Al crear un producto en la Compra, si está en la despensa sale la pista «Hay en la despensa», que no impide guardarlo.

### PANTRY-05. Al cocinar

- Al marcar una comida como cocinada, el diálogo «Has cocinado …» ofrece tachar de la compra lo pendiente de su receta (marcado por defecto; lo tachado entra en la despensa si es un ingrediente) y pregunta «¿Se ha acabado algo?» con lo que de la receta hay en la despensa (sin marcar).
- Lo que se marca como acabado sale de la despensa y, con «Apuntar a la compra lo que se ha acabado» (marcada por defecto), se apunta en la Compra como en PANTRY-03. Si es un ingrediente, se apunta con el nombre del catálogo.
- «Listo» lo escribe todo a la vez: si algo falla (por ejemplo, sin conexión), no se escribe nada y el diálogo sigue abierto, con lo marcado y el motivo, para volver a intentarlo. Lo que otro miembro ya ha tachado o ha quitado de la lista se salta. Si a la vez otro miembro cambia algo de lo que se vuelve a apuntar, el diálogo avisa de que la lista ha cambiado, nombrando el artículo si se sabe, y basta con volver a pulsar «Listo». «Cancelar» cierra el diálogo y su aviso. Si se cancela mientras guarda y la lista ha cambiado, la pantalla avisa de que la compra y la despensa se han quedado como estaban.

### PANTRY-06. Qué cocino con lo que hay

- El Recetario y el selector de receta de Comidas tienen el filtro «Con lo que hay»: solo las recetas con ingredientes, de la que menos falta a la que más, con «Lo tienes todo» o lo que falta («Faltan 2: huevos y leche»).
- En la Despensa, «¿Qué cocino?» abre el Recetario con ese filtro activado.

## 9. Tareas

### TASK-01. Crear tarea

Una tarea tendrá:

- Título obligatorio.
- Descripción opcional.
- Un responsable principal.
- Fecha límite opcional.
- Prioridad.
- Estado pendiente o completada.

El responsable deberá ser un miembro activo. La persona creadora podrá asignársela a sí misma.

### TASK-02. Edición y reasignación

- Cualquier miembro podrá editar la tarea.
- Cualquier miembro podrá reasignarla a otro miembro activo.
- Solo existirá un responsable a la vez en el MVP.
- Si el responsable abandona el espacio, la tarea podrá quedar temporalmente sin responsable.

### TASK-03. Completar y reabrir

- Cualquier miembro podrá completar una tarea, aunque no sea responsable.
- Se registrará quién la completó y cuándo.
- Cualquier miembro podrá reabrirla.

### TASK-04. Filtros

La lista permitirá filtrar por:

- Responsable.
- Estado.
- Fecha.
- Prioridad.

Las tareas vencidas y pendientes deberán distinguirse visualmente.

### TASK-05. Fuera del MVP

- Varios responsables.
- Subtareas.
- Recurrencia.
- Comentarios.
- Archivos adjuntos.

## 10. Calendario

### EVENT-01. Crear evento

Un evento tendrá:

- Título obligatorio.
- Descripción opcional.
- Inicio y final.
- Opción de día completo.
- Color.
- Participantes opcionales.

### EVENT-02. Visibilidad y participantes

- Todos los eventos serán visibles para todos los miembros.
- No ser participante no limita la lectura ni la edición.
- Los participantes solo indican quién está relacionado con el evento.
- Puede existir un evento sin participantes seleccionados.
- El calendario de un espacio muestra inicialmente todos sus eventos.
- Se podrán seleccionar o deseleccionar miembros para filtrar por participantes.
- Los eventos sin participantes son generales y permanecen visibles en el calendario del espacio.

### EVENT-03. Editar y eliminar

Cualquier miembro podrá editar o eliminar cualquier evento del espacio.

### EVENT-04. Vistas

El MVP incluirá:

- Vista diaria.
- Vista semanal, de lunes a domingo.
- Vista mensual.
- Consultas limitadas al intervalo visible en cada vista.

### EVENT-05. Mi calendario

- La navegación principal incluirá “Espacios” y “Mi calendario”.
- “Mi calendario” combinará los eventos de todos los espacios activos donde el usuario figure como participante.
- No mostrará eventos sin participantes ni eventos donde participen únicamente otros miembros.
- Permitirá filtrar por espacio y mostrará el espacio de procedencia.
- Al seleccionar un evento se abrirá dentro de su espacio para poder editarlo.

### EVENT-06. Fechas

- Los instantes se almacenarán en UTC.
- Se conservará la zona horaria necesaria.
- La fecha final no podrá ser anterior a la inicial.
- Los eventos de día completo se tratarán como fechas, evitando desplazamientos por zona horaria.

### EVENT-07. Fuera del MVP

- Repetición de eventos.
- Sincronización externa.
- Invitados ajenos al espacio.
- Adjuntos.

## 11. Conectividad y tiempo real

### CONN-01. Actualización conectada

- La pantalla activa escuchará los cambios del espacio actual.
- Los cambios aparecerán sin actualización manual en condiciones normales.
- Los listeners se cancelarán al salir de la pantalla, cambiar de espacio o cerrar sesión.

### CONN-02. Modo sin conexión

- Se mostrará un aviso persistente.
- Los datos descargados podrán mostrarse como desactualizados.
- Todas las acciones de escritura quedarán deshabilitadas.
- No se crearán intencionadamente operaciones para sincronizar después.

### CONN-03. Interrupción durante un guardado

- La interfaz mostrará “Guardando” hasta recibir confirmación.
- Si no puede confirmarse, mostrará un error y no presentará el cambio como definitivo.
- Al recuperar conexión se recargará el estado remoto.

## 12. Avisos

- El MVP no incluirá notificaciones push con la app cerrada.
- Los cambios se reflejarán mientras la app esté abierta mediante Firestore.
- Los avisos persistentes, contadores de novedades y notificaciones push se evaluarán después de la beta.

## 13. Criterios globales de aceptación del MVP

- Dos usuarios verificados pueden compartir un espacio.
- Un usuario no miembro no puede leer ni modificar datos del espacio.
- Un usuario puede pertenecer y cambiar entre varios espacios.
- Compra, tareas y calendario se actualizan prácticamente en tiempo real.
- La lista de la compra no muestra responsables.
- Una tarea admite un único responsable principal.
- Los eventos son visibles para todos, con participantes opcionales.
- Todos los miembros pueden editar los tres módulos.
- Sin conexión no se pueden crear modificaciones nuevas.
- Las invitaciones caducan a las 72 horas y solo funcionan una vez.
- El propietario no puede abandonar sin transferir o eliminar el espacio.
