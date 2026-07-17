# Flujos de usuario de Bobitos

| Campo | Valor |
| --- | --- |
| Estado | Aprobado para wireframes |
| Versión | 0.1.0 |
| Fecha | 14 de julio de 2026 |

## 1. Registro y verificación

1. El usuario abre Bobitos sin sesión.
2. Selecciona “Crear cuenta”.
3. Introduce nombre, correo y contraseña.
4. Acepta la información de privacidad requerida.
5. La app crea la cuenta y envía el correo de verificación.
6. Se muestra una pantalla de verificación pendiente.
7. El usuario abre el enlace recibido.
8. Regresa a Bobitos y pulsa “Ya he verificado mi correo”.
9. La app comprueba el estado.
10. Si está verificado, abre el selector de espacios.

### Estados alternativos

- Correo con formato incorrecto.
- Correo ya registrado.
- Contraseña no válida.
- Sin conexión.
- Correo no recibido: opción de reenvío con espera.

## 2. Inicio de sesión

1. Introduce correo y contraseña.
2. La app autentica.
3. Si no está verificado, abre la pantalla de verificación.
4. Si está verificado y tiene espacios, abre el último espacio utilizado o el selector.
5. Si no tiene espacios, ofrece “Crear espacio” y “Unirme con invitación”.

## 3. Crear un espacio

1. El usuario verificado selecciona “Crear espacio”.
2. Introduce un nombre.
3. Confirma.
4. La app crea el espacio y su membresía como propietario en una operación consistente.
5. Se abre el inicio del nuevo espacio.
6. Se ofrece invitar a otra persona o continuar.

## 4. Crear y compartir una invitación

1. El propietario abre “Miembros”.
2. Pulsa “Invitar”.
3. La app genera una invitación de un solo uso durante 72 horas.
4. Muestra:
   - Enlace compartible.
   - Código alternativo.
   - Fecha y hora de caducidad.
5. El propietario comparte el enlace o copia el código.
6. Puede revocar la invitación mientras siga activa.

## 5. Aceptar una invitación

### Mediante enlace

1. El usuario abre el enlace.
2. Si Bobitos no está abierto, la app procesa el enlace profundo.
3. Si no ha iniciado sesión, se le pide acceder o registrarse.
4. Si su correo no está verificado, debe verificarlo.
5. La app muestra el nombre del espacio y solicita confirmación.
6. Al aceptar, comprueba vigencia y uso.
7. Crea la membresía y consume la invitación atómicamente.
8. Abre el espacio.

### Mediante código

1. Desde el selector, pulsa “Unirme con un código”.
2. Introduce o pega el código.
3. Continúa con las mismas validaciones y confirmación.

### Errores

- Invitación inexistente.
- Invitación revocada.
- Invitación caducada.
- Invitación ya utilizada.
- Usuario ya miembro: se abre el espacio existente.
- Sin conexión: no se puede aceptar.

## 6. Cambiar de espacio

1. El usuario abre el selector desde la cabecera o navegación.
2. Ve los espacios a los que pertenece.
3. Selecciona otro.
4. La app cancela listeners del anterior.
5. Carga y escucha el nuevo espacio.
6. Guarda localmente la selección.

## 7. Añadir un producto a la compra

1. Abre “Compra”.
2. Pulsa “Añadir”.
3. Introduce nombre y, opcionalmente, cantidad y observaciones.
4. Pulsa “Guardar”.
5. La app muestra “Guardando”.
6. Tras confirmación, el producto aparece en pendientes para todos los miembros conectados.

La app no pide responsable porque la compra pertenece al espacio completo.

## 8. Marcar y limpiar productos

### Marcar

1. Un miembro marca el producto.
2. La app espera confirmación.
3. El producto se mueve a “Comprados”.
4. Se registra quién lo marcó y cuándo.

### Limpiar

1. Un miembro pulsa “Limpiar comprados”.
2. La app indica cuántos productos se eliminarán o archivarán.
3. El usuario confirma.
4. La operación afecta a los productos que sigan comprados.
5. La lista se actualiza para todos.

## 9. Crear y asignar una tarea

1. Abre “Tareas”.
2. Pulsa “Nueva tarea”.
3. Introduce título y, opcionalmente, descripción y fecha.
4. Selecciona prioridad.
5. Selecciona un responsable entre los miembros activos.
6. Guarda.
7. La tarea aparece como pendiente y asignada a esa persona.

## 10. Reasignar o completar una tarea

### Reasignar

1. Un miembro abre la tarea.
2. Selecciona otro responsable activo.
3. Guarda.
4. La actualización aparece para todos.

### Completar

1. Cualquier miembro marca la tarea como completada.
2. La app registra quién la completó y cuándo.
3. La tarea pasa a completadas.
4. Puede reabrirse posteriormente.

Ser responsable no concede exclusividad para completar o editar.

## 11. Crear un evento

1. Abre “Calendario”.
2. Pulsa “Nuevo evento” o selecciona una fecha.
3. Introduce título.
4. Configura inicio, final o día completo.
5. Añade descripción y color si lo desea.
6. Selecciona participantes opcionales.
7. Guarda.
8. El evento aparece para todos los miembros, participen o no.

### Consultar el calendario de un espacio

1. Abre el calendario del espacio.
2. Elige la vista diaria, semanal o mensual.
3. La app muestra todos los eventos del intervalo, incluidos aquellos donde no participa.
4. Puede deseleccionar miembros para ocultar los eventos asociados únicamente a esas personas.
5. Los eventos generales sin participantes permanecen visibles.

### Consultar “Mi calendario”

1. Desde la navegación inferior principal abre “Mi calendario”.
2. La app combina los eventos de todos sus espacios donde figura como participante.
3. Puede cambiar entre día, semana y mes, y ocultar espacios concretos.
4. Selecciona un evento para abrirlo y editarlo dentro del espacio correspondiente.

## 12. Editar o eliminar contenido compartido

1. Un miembro abre un producto, tarea o evento.
2. Selecciona editar o eliminar.
3. La app valida que sigue perteneciendo al espacio.
4. Guarda o solicita confirmación de eliminación.
5. El cambio se propaga a los demás dispositivos conectados.

Todos los miembros tienen permiso de edición sobre estos módulos.

## 13. Pérdida y recuperación de conexión

### Pérdida

1. La app detecta que no dispone de conexión útil.
2. Muestra “Sin conexión. La información puede estar desactualizada”.
3. Mantiene visible la información disponible.
4. Deshabilita añadir, editar, marcar, completar y eliminar.

### Recuperación

1. La app detecta conexión.
2. Reactiva los listeners del espacio activo.
3. Actualiza el contenido.
4. Habilita las acciones de escritura.
5. Retira el aviso cuando la sincronización inicial termina.

## 14. Abandonar un espacio como miembro

1. El miembro abre los ajustes del espacio.
2. Pulsa “Abandonar espacio”.
3. La app explica que perderá el acceso.
4. El usuario confirma.
5. Se elimina su membresía.
6. Sus tareas pendientes quedan sin responsable.
7. El espacio desaparece de su selector.
8. El contenido compartido permanece para el resto.

## 15. Abandonar como propietario

1. El propietario pulsa “Abandonar espacio”.
2. La app impide continuar directamente.
3. Ofrece:
   - Transferir la propiedad.
   - Eliminar el espacio.
   - Cancelar.
4. Si transfiere, elige un miembro y confirma.
5. Una vez completada la transferencia, puede abandonar como miembro normal.

## 16. Eliminar la cuenta

1. El usuario abre ajustes de cuenta.
2. Pulsa “Eliminar cuenta”.
3. La app comprueba sus espacios.
4. Si es propietario, exige transferir o eliminar cada espacio.
5. Si todavía es miembro, informa de que abandonará esos espacios.
6. Muestra las consecuencias y solicita una confirmación reforzada.
7. Elimina membresías y anonimiza atribuciones necesarias.
8. Elimina perfil y cuenta de autenticación.
9. Regresa a la pantalla inicial.
