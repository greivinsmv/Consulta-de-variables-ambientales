# Acceso a datos y revisión del repositorio

La aplicación web y Android leen directamente Firebase Realtime Database. La dirección de la base y la configuración cliente se pueden ver desde el navegador o desde la aplicación instalada. La protección depende de las reglas de Firebase y de la autenticación del dispositivo.

## Qué es público y qué debe mantenerse privado

| Elemento | Tratamiento |
| --- | --- |
| URL de Realtime Database y ruta `lecturas/equipo01` | Configuración pública del cliente |
| `google-services.json` y su API key cliente | Identifican el proyecto; no autorizan escrituras ni administración |
| Certificados raíz TLS | Material público para verificar al servidor; no son claves privadas |
| UID de Firebase Authentication | Identificador, no credencial; los ejemplos utilizan una marca genérica |
| Contraseña del ESP32, ID token y refresh token | Secretos: fuera del código, Git, capturas y registros |
| Cuenta de servicio, clave privada o secreto administrativo | Nunca deben incluirse en la web, la app o Wokwi |

Una variable de entorno que termine dentro del JavaScript servido al navegador tampoco oculta su valor. Si las mediciones deben dejar de ser públicas, hace falta cambiar el acceso y los clientes: ocultar la URL o limpiar Git no basta.

## Permisos previstos

La plantilla de [reglas](firebase/database.rules.template.json) deniega lectura y escritura en la raíz. Solo permite lectura pública bajo `lecturas/equipo01` y creación de registros al UID configurado para el ESP32. Ese usuario no puede editar o borrar lecturas, escribir en otras rutas ni añadir campos distintos de las cuatro mediciones previstas.

La web no inicia sesión ni utiliza la cuenta del dispositivo. Sus peticiones a Firebase son GET a la ruta pública, sin contraseña, token o cabecera Authorization. La [guía de Firebase](firebase/README.md) explica cómo generar y publicar las reglas; tener esa plantilla en GitHub no actualiza los permisos de la base real.

La lectura pública incluye todos los registros y campos existentes en esa ruta. No se debe guardar allí información personal ni credenciales; la validación de nuevas escrituras no sanea datos históricos.

## Revisión del 21 de octubre de 2026

Se revisaron las cinco ramas remotas disponibles y su historial compartido, además de las referencias locales: 42 commits y 233 versiones de archivos de texto. La búsqueda de patrones y la revisión del cliente web, la configuración y el firmware histórico no identificaron contraseñas, tokens de sesión, claves privadas o credenciales administrativas. La coincidencia de API key corresponde a la configuración pública del cliente Android; las demás coincidencias eran ejemplos, datos de pruebas o metadatos de dependencias.

El firmware antiguo estaba guardado bajo `APP MOVIL/wokwi/` y `APP-MOVIL/wokwi/`. Enviaba lecturas sin autenticación y usaba `setInsecure()`. Esas carpetas se retiran del historial de las ramas y quedan ignoradas. El firmware autenticado se mantiene aparte. También se sustituye el UID concreto de los ejemplos por `UID_DEL_USUARIO_WOKWI`.

La revisión automatizada no garantiza que cualquier formato de secreto pueda detectarse. Borrar commits tampoco revoca credenciales ni cambia las reglas desplegadas.

### Comprobaciones locales

- La importación de la plantilla en el emulador oficial de Realtime Database y 23 comprobaciones REST locales terminaron correctamente, incluida la preparación de datos de prueba. Se comprobó lectura pública, rechazo de raíz y otras rutas, creación por el UID permitido, rechazo de otro usuario y del acceso anónimo, edición, borrado y validación de campos y fechas.
- La regresión de interfaz añadida recorre Inicio, Por hora, Por día y Extremos y comprueba método GET, ruta permitida y ausencia de credenciales en las peticiones.
- Las comprobaciones de la web usan respuestas simuladas y no escriben en producción.

El runner original del SDK no terminó en este entorno; las comprobaciones de reglas anteriores se realizaron por REST contra el emulador local, no con una cuenta real.

### Verificación de producción pendiente

El proxy del entorno rechazó las conexiones al dominio de Firebase con `Tunnel connection failed: 403 Forbidden`. Ese rechazo no procede de Firebase y no demuestra que la base esté protegida ni abierta. No se modificaron reglas, usuarios ni datos de producción durante esta revisión.

Desde una conexión con acceso a Firebase, ejecuta desde la raíz del repositorio:

```sh
python firebase/comprobar-acceso-publico.py
```

El script solo realiza GET, limita la consulta pública a una lectura y no muestra valores ni necesita credenciales. Sale con código distinto de cero si falla una comprobación o la conexión. Un resultado correcto revisa esas rutas concretas, no los permisos de escritura ni el resto del contenido histórico.

Desde la consola de Firebase hay que comprobar que las reglas publicadas coinciden con el archivo generado para el UID correcto. Desde una conexión con acceso al servicio, la raíz y otras rutas deben devolver permiso denegado, y solo las mediciones previstas deben poder leerse sin iniciar sesión. Las pruebas autenticadas deben usar la cuenta del dispositivo, manteniendo su contraseña y token fuera de capturas y archivos compartidos.

## Después de limpiar el historial

Los colaboradores deben guardar aparte cualquier cambio pendiente y volver a clonar el repositorio. Un push desde una copia antigua puede reintroducir los commits retirados. El respaldo de la revisión se conserva fuera del proyecto y no se debe subir.

Las referencias internas de PR y las vistas en caché de GitHub pueden conservar commits antiguos aunque se hayan reescrito todas las ramas. No forman parte de lo que puede borrarse mediante un push normal. Esta revisión distingue la limpieza de ramas de la purga interna del proveedor.
