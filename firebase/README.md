# Firebase setup

**English** | [Español](README.es.md)

Create your own Firebase Realtime Database project and use its configuration in the clients. Keep passwords, tokens and administrative credentials outside the repository.

## Device account and rules

In your project's Firebase console, enable **Authentication → Sign-in method → Email/Password**. Create an account for the ESP32 under **Users → Add user**, choose a unique password and copy its UID.

With Node.js 22 or later, run this command from `firebase`, replacing the argument with that account's UID:

```powershell
node generar-reglas.mjs UID_DEL_USUARIO_WOKWI
```

This creates `database.rules.local.json`, which is excluded from Git. Review it before deploying. You can paste the generated file into **Realtime Database → Rules**, or deploy from this directory with the Firebase CLI:

```powershell
npx firebase-tools login
npx firebase-tools deploy --only database --project YOUR_FIREBASE_PROJECT_ID
```

Replace `YOUR_FIREBASE_PROJECT_ID` with your own project ID. Deployment changes that project's database permissions; generating the file or running tests does not deploy it.

## Wokwi token

First add your own `google-services.json` following the [Android setup](../APP-MOVIL/README.md). The token helper reads that local file; you can use `-Configuracion` to supply another path.

From the repository root in PowerShell:

```powershell
.\firebase\Obtener-TokenWokwi.ps1 -Correo 'CORREO_DE_LA_CUENTA_ESP32'
```

Use the device account's email. The script asks for its password with hidden input and copies a temporary token to the clipboard. Paste it into the firmware's serial monitor when requested. It lasts approximately one hour; generate another when needed. Keep passwords and tokens out of source files and shared captures, and clear the clipboard after use:

```powershell
Set-Clipboard -Value ""
```

The firmware and circuit are maintained separately. Reading timestamps must use Firebase's server value, `{".sv":"timestamp"}`, rather than the device clock.

## Local tests

Install Node.js 22 or later and Java 21 or later. From `firebase`:

```powershell
npm ci
npm test
```

The first run downloads the official Firebase emulator. Tests use the demonstration project `demo-consulta-ambiental` and do not connect to your deployed database.
