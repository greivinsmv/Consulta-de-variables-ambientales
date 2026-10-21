# Database access and device authentication

**English** | [Español](README.es.md)

The Android app and web client read `lecturas/equipo01` without signing in. The ESP32 is the only authorized writer, through a dedicated Firebase Authentication account. The client API key identifies the project; it does not grant write access.

## Access rules

`database.rules.template.json` denies access at the root and allows public reads only under `lecturas/equipo01`. The selected UID can create records in that path, but cannot modify or delete existing records or write to other teams' paths.

Each reading must contain temperature, humidity, dew point and a server timestamp. The rules reject extra fields, text in place of numbers and humidity outside `(0, 100]`. Temperature and dew point remain compatible with the project's test values; the optional interface filters do not constrain database writes.

The `ts` validation requires the server time of the request. The firmware must send `{".sv":"timestamp"}` instead of using its own clock to timestamp the reading.

The authorized account is not an administrator. Firebase administration tools can still manage the database through IAM; their credentials must never be copied to the ESP32.

## 1. Create the device account

In the Firebase console for `proyecto-iot-fa353`:

1. Open **Authentication → Sign-in method** and enable **Email/Password**.
2. Under **Users → Add user**, create a dedicated ESP32 account. Use a long, unique password stored in a password manager, separate from your Google account password.
3. Copy the user's **UID**. The UID is an identifier, not a secret; the password and tokens are secrets.

Enabling the provider does not grant write access to every account. The rules check the exact device UID.

## 2. Generate the rules

From `firebase`, with Node.js 22 or later:

```powershell
node generar-reglas.mjs UID_DEL_USUARIO_WOKWI
```

Replace `UID_DEL_USUARIO_WOKWI` with the device account's UID. The generated `database.rules.local.json` is excluded from Git. Supply the UID rather than an API key, email or password. The script rejects empty identifiers and the template marker.

Review the generated file and prepare the authenticated firmware before deploying it. Under **Realtime Database → Rules**, replace the previous rules with the full generated file. A public root `.write` permission would override the device restriction and must be removed.

If you use the Firebase CLI, you can also deploy from this directory after signing in:

```powershell
npx firebase-tools login
npx firebase-tools deploy --only database --project proyecto-iot-fa353
```

The deploy command changes permissions on the real database. Generating JSON, running tests or merging code does not deploy rules.

## 3. Get a token for Wokwi web

A Wokwi project accessible through a link is not a private secret store. Keep passwords and permanent tokens out of it. The firmware requests an ID token at startup; paste it into the serial monitor. It stays in memory for that run.

From the repository root in PowerShell:

```powershell
.\firebase\Obtener-TokenWokwi.ps1 -Correo 'CORREO_DE_LA_CUENTA_ESP32'
```

Replace `CORREO_DE_LA_CUENTA_ESP32` with the device account's email. The script prompts for the password without displaying it, signs in over HTTPS, copies the ID token to the clipboard and prints only the UID and token lifetime. It does not save the password, refresh token or ID token to files. Avoid console transcription and HTTP tracing while using it.

The token lasts approximately one hour. Paste it into the serial monitor when requested and press Enter. Then clear the clipboard:

```powershell
Set-Clipboard -Value ""
```

If the simulation keeps running after expiry, generate another token and paste it when Firebase rejects the previous one. The account and UID stay the same; the rules do not need hourly changes. Keep tokens, clipboard contents and traffic captures out of screenshots and shared files.

The script reads the client API key from `google-services.json`. If its restrictions prevent REST sign-in from your PC, review the key configuration in Google Cloud and use a client key compatible with Firebase Authentication. Do not substitute a service account or administrative secret.

## 4. Keep firmware separate

The firmware and circuit are maintained outside this repository. Removing files from the current version does not erase their Git history or revoke database access. Firebase rules block the old unauthenticated requests.

Authenticated firmware must:

- Receive the ID token at runtime, without embedding it in the sketch or a shared file.
- Send the token over HTTPS to Firebase REST using `?auth=ID_TOKEN`.
- Verify the server certificate and synchronize the clock before the first request.
- Avoid logging authenticated URLs, tokens or sign-in request bodies.
- Stop unauthenticated sends: a rejected token requires a new token or an account check.

Public root certificates can be included in the Wokwi project. They are TLS trust material rather than private keys, and need updating if the provider's certificate chain changes.

## 5. Test the rules locally

Install Node.js 22 or later and Java 21 or later. From `firebase`:

```powershell
npm ci
npm test
```

The first run downloads the official emulator. Tests use `demo-consulta-ambiental` without touching the real project or requiring a password. They cover app reads, date queries, anonymous writes, another account, authorized creation, edits, deletion, other paths and data validation.

`firebase.test.json` uses the template for the emulator. `firebase.json` points to the generated file for deployment; tests do not overwrite the configured UID.

## Switching from the old write permissions

1. Prepare the account, token and authenticated firmware.
2. Check that Wokwi synchronizes its clock and receives HTTP 200 with the token.
3. Deploy the new rules and repeat the authorized request.
4. Try the old firmware without a token; Firebase must deny permission.
5. Verify that the ESP32 cannot modify or delete earlier readings.
6. Open the app and check Home, queries and CSV export without signing in.

If a password or token is shared accidentally, change the device account's password and revoke its sessions in Firebase Authentication. Removing text from Git or Wokwi does not revoke access. Temporarily removing the UID's write permission in the rules blocks its writes immediately.

## Completed checks

On October 14, 2026, Wokwi sent a reading using the ESP32 account's ID token, received HTTP 200 and created a Realtime Database record. After the UID-based rules were deployed, an anonymous PowerShell POST returned `Permission denied`.

On October 21, the rules template was checked with the official emulator and local REST requests: authorized creation, anonymous and other-account rejection, edits, deletion, field validation and timestamps. The original SDK runner did not complete in that environment. The same day, read access to the real database was checked from the PC, and the authorized Wokwi request and rejected anonymous POST were repeated. Edits, deletion and the remaining cases were checked only in the emulator. See the [review scope (Spanish)](../SEGURIDAD.md).

The serial reader in the separately maintained firmware handles long tokens by configuring an 8192-byte buffer before starting the port and draining available bytes without a per-character delay. It reports token receipt and the HTTP result without printing the token.
