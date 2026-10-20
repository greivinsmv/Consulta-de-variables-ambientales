import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

// El UID identifica al usuario autorizado; no es una contraseña ni un token.
const uid = process.argv[2];
if (!uid || uid === '__UID_WOKWI__' || !/^[A-Za-z0-9_-]{1,128}$/.test(uid)) {
  console.error('Uso: node generar-reglas.mjs UID_DEL_USUARIO_WOKWI');
  process.exit(1);
}
const plantilla = JSON.parse(await readFile(new URL('database.rules.template.json', import.meta.url), 'utf8'));
plantilla.rules.lecturas.equipo01.$lectura['.write'] =
  `auth != null && auth.uid === '${uid}' && !data.exists() && newData.exists()`;
const destino = new URL('database.rules.local.json', import.meta.url);
await writeFile(destino, JSON.stringify(plantilla, null, 2) + '\n');
console.log(`Reglas generadas: ${fileURLToPath(destino)}. Todavía no se publicaron en Firebase.`);
