# Política de privacidad de Bobitos

Última actualización: 16 de julio de 2026.

## Responsable y finalidad

Bobitos es una aplicación privada en desarrollo gestionada por DLunaUNIZAR. Utiliza los datos exclusivamente para autenticar usuarios y permitir que familias, parejas o compañeros compartan listas de la compra, tareas y calendarios dentro de espacios privados.

## Datos tratados

- Correo electrónico y nombre visible.
- Identificador técnico de la cuenta y pertenencia a espacios.
- Productos, tareas, eventos y participantes introducidos por los usuarios.
- Datos técnicos mínimos de seguridad, sincronización y funcionamiento proporcionados por Firebase.

Bobitos no vende datos, no muestra publicidad y no contiene perfiles públicos ni contenido accesible a desconocidos. Google Analytics no está integrado en la aplicación Android del MVP.

## Infraestructura y conservación

La autenticación y los datos compartidos se gestionan con Firebase. Firestore está configurado en la región `europe-southwest1` (Madrid). Los datos de un espacio se conservan hasta que su propietario lo elimina. Al borrar una cuenta se eliminan sus credenciales y perfil, se abandonan sus espacios y su nombre se sustituye por “Usuario eliminado” en el contenido histórico necesario para mantener la coherencia compartida.

## Acceso y seguridad

Solo usuarios con correo verificado y membresía activa pueden acceder a un espacio. Las reglas de Firestore aíslan los espacios y App Check con Play Integrity protege el entorno de producción frente a clientes no legítimos.

## Derechos y eliminación

El usuario puede modificar su nombre, abandonar espacios y eliminar su cuenta desde Perfil. Un propietario debe transferir la propiedad o eliminar sus espacios antes de borrar la cuenta. La eliminación de un espacio borra miembros, invitaciones, compra, tareas y eventos asociados.

Para consultas sobre privacidad o ejercicio de derechos puede abrirse una comunicación privada con el responsable del repositorio. Esta política deberá completarse con un correo de contacto antes de distribuir la beta fuera del grupo de desarrollo.
