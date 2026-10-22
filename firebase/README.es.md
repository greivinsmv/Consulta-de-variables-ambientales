# Configuración de Firebase

[English](README.md) | **Español**

Crea tu propio proyecto de Firebase Realtime Database y utiliza su configuración en los clientes. Mantén las contraseñas, los tokens y las credenciales administrativas fuera del repositorio.

## Cuenta del dispositivo y reglas

En la consola de tu proyecto, habilita **Authentication → Sign-in method → Correo/Contraseña**. Crea una cuenta para el ESP32 en **Users → Add user**, elige una contraseña única y copia su UID.

Con Node.js 22 o superior, ejecuta desde `firebase`, sustituyendo el argumento por el UID de esa cuenta:

```powershell
node generar-reglas.mjs UID_DEL_USUARIO_WOKWI
```

Se genera `database.rules.local.json`, excluido de Git. Revísalo antes de publicarlo. Puedes pegar el archivo generado en **Realtime Database → Rules** o publicarlo desde esta carpeta con Firebase CLI:

```powershell
npx firebase-tools login
npx firebase-tools deploy --only database --project ID_DE_TU_PROYECTO
```

Sustituye `ID_DE_TU_PROYECTO` por el ID de tu proyecto. El despliegue modifica los permisos de esa base; generar el archivo o ejecutar los tests no lo publica.

## Token para Wokwi

Primero añade tu propio `google-services.json` siguiendo la [configuración de Android](../APP-MOVIL/README.es.md). El script lee ese archivo local; puedes indicar otra ruta con `-Configuracion`.

Desde la raíz del repositorio en PowerShell:

```powershell
.\firebase\Obtener-TokenWokwi.ps1 -Correo 'CORREO_DE_LA_CUENTA_ESP32'
```

Utiliza el correo de la cuenta del dispositivo. El script pide su contraseña con entrada oculta y copia un token temporal al portapapeles. Pégalo en el monitor serial cuando el firmware lo solicite. Dura aproximadamente una hora; genera otro cuando sea necesario. Mantén las contraseñas y los tokens fuera del código y de las capturas compartidas, y vacía el portapapeles después de usarlo:

```powershell
Set-Clipboard -Value ""
```

El firmware y el circuito se mantienen aparte. Las lecturas deben utilizar la fecha del servidor de Firebase, `{".sv":"timestamp"}`, en lugar del reloj del dispositivo.

## Pruebas locales

Instala Node.js 22 o superior y Java 21 o superior. Desde `firebase`:

```powershell
npm ci
npm test
```

La primera ejecución descarga el emulador oficial de Firebase. Los tests usan el proyecto de demostración `demo-consulta-ambiental` y no se conectan a tu base desplegada.
