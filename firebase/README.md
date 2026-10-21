# Acceso de lectura y dispositivo autorizado

La aplicación Android y la web consultan `lecturas/equipo01` sin iniciar sesión. El ESP32 es el único escritor autorizado mediante una cuenta de Firebase Authentication. La API key del cliente identifica el proyecto; no concede este permiso.

## Qué cambian las reglas

`database.rules.template.json` deniega el acceso en la raíz y permite lectura pública únicamente en `lecturas/equipo01`. El UID elegido puede crear registros nuevos en esa ruta, pero no modificar o borrar los existentes ni escribir en otros equipos.

Cada lectura debe incluir temperatura, humedad, punto de rocío y fecha del servidor. No admite campos adicionales, texto en lugar de números ni humedad fuera de `(0, 100]`. Temperatura y rocío conservan los valores de prueba del proyecto, sin imponer los límites opcionales de la interfaz.

La validación de `ts` requiere la fecha de la petición en el servidor. El firmware debe enviar `{".sv":"timestamp"}`; no utiliza el reloj del dispositivo para fechar la lectura.

La cuenta autorizada no tiene permisos de administrador. Las herramientas de administración de Firebase siguen pudiendo gestionar la base de datos mediante IAM; nunca se deben copiar sus claves al ESP32.

## 1. Crear la cuenta del dispositivo

En la consola del proyecto `proyecto-iot-fa353`:

1. Abre **Authentication → Sign-in method** y habilita **Correo/Contraseña**.
2. En **Users → Add user**, crea una cuenta exclusiva del ESP32. Utiliza una contraseña larga, única y guardada en tu gestor de contraseñas. No uses la de tu cuenta de Google.
3. Copia el **UID** de ese usuario. El UID no es secreto; la contraseña y los tokens sí lo son.

Habilitar el proveedor no da permiso de escritura a todas sus cuentas: las reglas comprueban el UID exacto del dispositivo.

## 2. Generar las reglas

Desde la carpeta `firebase`, con Node.js 22 o superior:

```powershell
node generar-reglas.mjs UID_DEL_USUARIO_WOKWI
```

Sustituye `UID_DEL_USUARIO_WOKWI` por el UID de la cuenta del dispositivo en tu proyecto. El archivo generado es `database.rules.local.json`, excluido de Git. No reemplaces el UID por una API key, correo o contraseña. El script rechaza identificadores vacíos y la marca de la plantilla.

Revisa el archivo y reserva su publicación hasta haber preparado el firmware autenticado. En **Realtime Database → Rules**, pega el archivo completo en lugar de las reglas anteriores. No conserves un permiso `.write` público en la raíz: anularía la restricción por dispositivo.

Si ya utilizas Firebase CLI, también puedes publicar desde esta carpeta después de iniciar sesión:

```powershell
npx firebase-tools login
npx firebase-tools deploy --only database --project proyecto-iot-fa353
```

Este comando modifica los permisos de la base real. La generación del JSON, los tests y el merge de esta rama no publican reglas por sí solos.

## 3. Obtener un token para Wokwi web

Un proyecto Wokwi que se abre mediante un enlace no es un almacén privado de secretos. Por eso no se guarda allí ni la contraseña ni un token permanente. El firmware solicita un ID token al iniciar; se introduce en el monitor serial y se conserva solo en memoria durante esa ejecución.

Desde la raíz del repositorio en PowerShell:

```powershell
.\firebase\Obtener-TokenWokwi.ps1 -Correo 'CORREO_DE_LA_CUENTA_ESP32'
```

El script pide la contraseña con entrada oculta, inicia sesión por HTTPS, copia el ID token al portapapeles y muestra solamente el UID y la vigencia. No guarda la contraseña, el refresh token ni el ID token en archivos. No ejecutes la consola con transcripción o trazas HTTP activas.

El token dura aproximadamente una hora. Pégalo en el monitor serial cuando el firmware lo solicite y pulsa Enter. Después vacía el portapapeles:

```powershell
Set-Clipboard -Value ""
```

Si la simulación continúa cuando vence, genera otro token y pégalo cuando Firebase rechace el anterior. La cuenta y el UID se mantienen; no hace falta cambiar las reglas cada hora. Nunca compartas capturas del token, el portapapeles o archivos de tráfico de esa ejecución.

La API key se obtiene de `google-services.json`. Si tiene restricciones que impiden el inicio de sesión REST desde tu PC, revisa la configuración de la clave en Google Cloud y utiliza una clave de cliente compatible con Firebase Authentication. No uses una cuenta de servicio ni un secreto administrativo para resolver ese error.

## 4. Firmware mantenido aparte

El firmware y el circuito se mantienen fuera de este repositorio, según el alcance del componente móvil. Retirarlos de la versión actual no borra el historial de Git ni cierra el acceso a Firebase. Las reglas son las que bloquean los envíos antiguos sin autenticación.

La versión autenticada debe:

- Recibir el ID token en tiempo de ejecución, sin incluirlo en el sketch o un archivo compartido.
- Enviar el token por HTTPS a Firebase REST con `?auth=ID_TOKEN`.
- Verificar el certificado del servidor y sincronizar el reloj antes del primer envío.
- No imprimir la URL autenticada, tokens ni cuerpos de inicio de sesión.
- Detener los envíos anónimos: un token rechazado requiere otro token o revisar la cuenta.

Los certificados raíz públicos pueden estar en el proyecto Wokwi. Son material de confianza para TLS, no claves privadas. Deben actualizarse si cambia la cadena de certificados del proveedor.

## 5. Probar las reglas localmente

Requiere Node.js 22 o superior y Java 21 o superior. Desde `firebase`:

```powershell
npm ci
npm test
```

La primera ejecución descarga el emulador oficial. Los tests utilizan `demo-consulta-ambiental`, sin tocar el proyecto real ni necesitar una contraseña. Se prueban lectura de la app, consulta por fecha, escrituras anónimas, otra cuenta, creación autorizada, edición, borrado, otras rutas y validación de los datos.

`firebase.test.json` usa la plantilla para el emulador. `firebase.json` apunta al archivo generado para el despliegue; los tests no sobrescriben el UID configurado.

## Comprobación antes de cerrar la escritura anterior

1. Preparar la cuenta, el token y el firmware autenticado.
2. Comprobar en Wokwi que sincroniza el reloj y devuelve HTTP 200 con el token.
3. Publicar las reglas nuevas y repetir el envío autorizado.
4. Ejecutar una copia del firmware anterior sin token: debe recibir permiso denegado.
5. Verificar que el ESP32 no modifica ni borra lecturas anteriores.
6. Abrir la app y comprobar Inicio, consultas y CSV sin iniciar sesión.

Si una contraseña o token se comparte por error, cambia la contraseña de la cuenta del dispositivo y revoca sus sesiones en Firebase Authentication. No basta con borrar el texto de Git o de Wokwi. Retirar temporalmente el permiso del UID en las reglas bloquea de inmediato sus escrituras.

## Verificación realizada

El 14 de octubre de 2026 se comprobó el envío desde Wokwi con un ID token de la cuenta del ESP32: devolvió HTTP 200 y creó una lectura en Realtime Database. Tras publicar las reglas con su UID, una petición POST sin token desde PowerShell fue rechazada con `Permission denied`.

Estas pruebas confirman el envío autenticado y el bloqueo de escritura anónima en el proyecto real. El 21 de octubre se verificó la plantilla con el emulador oficial y peticiones REST locales: creación por el UID permitido, rechazo anónimo y de otra cuenta, edición, borrado y validación de campos y fechas. El runner original del SDK no terminó en este entorno. Ese mismo día se comprobaron desde la PC las lecturas permitidas y bloqueadas de la base real, y se repitieron el envío autorizado de Wokwi y el rechazo de un POST anónimo. La edición, el borrado y los demás casos se comprobaron solo en el emulador; consulta [el alcance de la revisión](../SEGURIDAD.md).

El lector serial del firmware mantenido aparte se ajustó para tokens largos: configura un búfer de 8192 bytes antes de iniciar el puerto y vacía los bytes disponibles sin una demora por carácter. No imprime el token; informa su recepción y el resultado HTTP.
