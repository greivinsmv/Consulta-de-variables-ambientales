import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { get, ref, set, remove, update, serverTimestamp, query, orderByChild, startAt, endAt } from 'firebase/database';

const uidWokwi = 'dispositivo-wokwi-prueba';
const ruta = 'lecturas/equipo01';
let entorno;

before(async () => {
  const plantilla = await readFile(new URL('../database.rules.template.json', import.meta.url), 'utf8');
  const [host, port] = (process.env.FIREBASE_DATABASE_EMULATOR_HOST || '127.0.0.1:9000').split(':');
  entorno = await initializeTestEnvironment({
    projectId: 'demo-consulta-ambiental',
    database: { host, port: Number(port), rules: plantilla.replaceAll('__UID_WOKWI__', uidWokwi) }
  });
});
beforeEach(async () => {
  await entorno.clearDatabase();
  await entorno.withSecurityRulesDisabled(async contexto => {
    await set(ref(contexto.database(), `${ruta}/anterior`), {
      temperatura: 24, humedad: 40, puntoRocio: 9.6, ts: 1
    });
    await set(ref(contexto.database(), 'privado/configuracion'), { dato: 'no-publico' });
  });
});
after(async () => { await entorno?.cleanup(); });

const lectura = () => ({ temperatura: 24, humedad: 40, puntoRocio: 9.6, ts: serverTimestamp() });
const anonimo = () => entorno.unauthenticatedContext().database();
const escritor = () => entorno.authenticatedContext(uidWokwi).database();

test('la app lee las lecturas sin iniciar sesión', async () => {
  const resultado = await assertSucceeds(get(ref(anonimo(), ruta)));
  assert.equal(resultado.child('anterior/temperatura').val(), 24);
});

test('las consultas por fecha conservan acceso y utilizan el índice ts', async () => {
  const consulta = query(ref(anonimo(), ruta), orderByChild('ts'), startAt(0), endAt(2));
  const resultado = await assertSucceeds(get(consulta));
  assert.equal(resultado.numChildren(), 1);
});

test('la lectura pública no alcanza la raíz ni otras rutas', async () => {
  await assertFails(get(ref(anonimo())));
  await assertFails(get(ref(anonimo(), 'privado')));
  await assertFails(get(ref(anonimo(), 'lecturas/equipo02')));
});

test('sin token no se pueden crear lecturas', async () => {
  await assertFails(set(ref(anonimo(), `${ruta}/nueva`), lectura()));
});

test('tener otra cuenta autenticada no concede escritura', async () => {
  const otraCuenta = entorno.authenticatedContext('otro-usuario').database();
  await assertFails(set(ref(otraCuenta, `${ruta}/nueva`), lectura()));
});

test('la cuenta del ESP32 crea lecturas con fecha del servidor', async () => {
  await assertSucceeds(set(ref(escritor(), `${ruta}/nueva`), lectura()));
  const resultado = await get(ref(anonimo(), `${ruta}/nueva`));
  assert.equal(typeof resultado.child('ts').val(), 'number');
});

test('el ESP32 no puede borrar ni reemplazar lecturas anteriores', async () => {
  await assertFails(remove(ref(escritor(), `${ruta}/anterior`)));
  await assertFails(set(ref(escritor(), `${ruta}/anterior`), lectura()));
  await assertFails(update(ref(escritor(), `${ruta}/anterior`), { temperatura: 80 }));
  await assertFails(remove(ref(escritor(), ruta)));
});

test('la cuenta del ESP32 tampoco escribe en otros equipos o rutas', async () => {
  await assertFails(set(ref(escritor(), 'lecturas/equipo02/nueva'), lectura()));
  await assertFails(set(ref(escritor(), 'privado/nueva'), lectura()));
});

test('no se aceptan valores incompletos, texto o campos adicionales', async () => {
  const sinHumedad = lectura(); delete sinHumedad.humedad;
  await assertFails(set(ref(escritor(), `${ruta}/sin-humedad`), sinHumedad));
  await assertFails(set(ref(escritor(), `${ruta}/texto`), { ...lectura(), temperatura: '24' }));
  await assertFails(set(ref(escritor(), `${ruta}/extra`), { ...lectura(), password: 'campo-no-admitido' }));
});

test('se rechaza humedad fuera del rango utilizado por el DHT22', async () => {
  for (const humedad of [0, -1, 101]) {
    await assertFails(set(ref(escritor(), `${ruta}/humedad-${humedad}`), { ...lectura(), humedad }));
  }
});

test('se rechazan fechas antiguas o futuras enviadas por el cliente', async () => {
  await assertFails(set(ref(escritor(), `${ruta}/pasada`), { ...lectura(), ts: 1 }));
  await assertFails(set(ref(escritor(), `${ruta}/futura`), { ...lectura(), ts: Date.now() + 3600000 }));
});
