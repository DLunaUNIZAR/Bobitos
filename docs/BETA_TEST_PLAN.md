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

### Actualizar desde la app

Solo se puede probar a mano, en un móvil con una beta firmada que traiga la función y una beta posterior distribuida (con `VERSION_CODE` mayor y notas en `distribution/release-notes.txt`).

11. Comprobación manual: en «Perfil» pulsar «Buscar actualizaciones», iniciar sesión con la cuenta de la invitación, comprobar que salen las «Novedades», descargar viendo el progreso e instalar.
12. Aviso automático: con sesión de tester y una beta nueva sin instalar, abrir la app al día siguiente y comprobar que sale «Hay una beta nueva».
13. «Más tarde» y versión siguiente: pulsar «Más tarde», comprobar que esa versión no vuelve a avisar, distribuir una más nueva y comprobar que esta sí avisa.
14. Cerrar el aviso con Atrás (no pospone, pero el día ya está gastado): abrir la app al día siguiente y comprobar que vuelve a avisar de esa misma versión.
15. Sin conexión: pulsar «Buscar actualizaciones» sin red, comprobar que la fila muestra el mensaje de sin conexión y, al volver la red, que «Buscar actualizaciones» funciona.
16. Cerrar la hoja durante la descarga, comprobar que sigue y que «Buscar actualizaciones» la reabre con su progreso.

## Registro y prioridad de incidencias

Las incidencias se crean en GitHub sin incluir correos, contraseñas, códigos de invitación ni datos privados de los espacios.

| Prioridad | Criterio |
| --- | --- |
| Crítica | Pérdida de datos, acceso indebido, bloqueo general o imposibilidad de iniciar sesión |
| Alta | Una función principal no puede utilizarse y no existe alternativa razonable |
| Media | Fallo funcional con alternativa o impacto limitado |
| Baja | Problema visual, de texto o mejora de experiencia |

Una incidencia debe indicar versión de Bobitos, dispositivo, Android, estado de conexión, pasos, resultado esperado y resultado observado. No se prepara una versión estable mientras exista una incidencia crítica abierta.
