# Plan de pruebas de la beta privada

Este documento no contiene nombres, correos ni identificadores personales. Los participantes autorizados se gestionan en el grupo `bobitos-beta` de Firebase App Distribution.

## Matriz de dispositivos

| Código | Fabricante/modelo | Android | Instalación | Actualización | Tiempo real | Offline/reconexión | Resultado |
| --- | --- | --- | --- | --- | --- | --- | --- |
| D1 | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |
| D2 | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente | Pendiente |

## Recorrido mínimo

1. Aceptar la invitación e instalar la beta.
2. Registrar dos cuentas y verificar sus correos.
3. Crear un espacio en D1 e incorporar D2 mediante una invitación.
4. Crear, editar y completar productos, tareas y eventos desde ambos dispositivos.
5. Confirmar que cada cambio aparece en el otro dispositivo en pocos segundos.
6. Desconectar un dispositivo, comprobar el modo de solo lectura y reconectarlo.
7. Cerrar y abrir la app y comprobar que se conserva la sesión y el espacio activo.
8. Instalar una beta con `VERSION_CODE` superior sobre la anterior.
9. Confirmar que la actualización conserva la sesión y permite acceder a los mismos datos.
10. Probar transferencia de propiedad, abandono, eliminación de espacio y eliminación de cuenta.

## Registro y prioridad de incidencias

Las incidencias se crean en GitHub sin incluir correos, contraseñas, códigos de invitación ni datos privados de los espacios.

| Prioridad | Criterio |
| --- | --- |
| Crítica | Pérdida de datos, acceso indebido, bloqueo general o imposibilidad de iniciar sesión |
| Alta | Una función principal no puede utilizarse y no existe alternativa razonable |
| Media | Fallo funcional con alternativa o impacto limitado |
| Baja | Problema visual, de texto o mejora de experiencia |

Una incidencia debe indicar versión de Bobitos, dispositivo, Android, estado de conexión, pasos, resultado esperado y resultado observado. No se prepara una versión estable mientras exista una incidencia crítica abierta.
