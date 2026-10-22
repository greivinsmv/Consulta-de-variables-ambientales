/** Consultas REST de las lecturas del ESP32 y normalización de sus respuestas. */

import {
  esNumeroValido, esTimestampValido, fechaCR, inicioDiaCR, finDiaExclusivoCR
} from "./modelo.js";
import { pedirJson } from "./red.js";

async function leerConfiguracion() {
  const controlador = new AbortController();
  const espera = setTimeout(() => controlador.abort(), 3000);
  try {
    const respuesta = await fetch(new URL("../config.local.json", import.meta.url), {
      cache: "no-store", signal: controlador.signal
    });
    if (!respuesta.ok) return null;
    const { firebase } = await respuesta.json();
    if (!firebase?.databaseURL) return null;
    const url = new URL(firebase.databaseURL);
    const ruta = firebase.readingsPath ?? "lecturas/equipo01";
    if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash ||
        url.pathname !== "/" || url.port ||
        !/^[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:firebaseio\.com|firebasedatabase\.app)$/.test(url.hostname) ||
        typeof ruta !== "string" || !/^[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*$/.test(ruta)) return null;
    return { base: url.origin, ruta };
  } catch {
    return null;
  } finally {
    clearTimeout(espera);
  }
}

// La configuración local es opcional: Open-Meteo funciona sin ella.
const configuracion = await leerConfiguracion();
export const BASE_DATOS = configuracion?.base ?? "";
export const RUTA_LECTURAS = configuracion?.ruta ?? "lecturas/equipo01";
export const firebaseConfigurado = () => configuracion !== null;

const CAMPOS = ["temperatura", "humedad", "puntoRocio"];

/** Construye la URL de consulta sin dejar fechas ni parámetros fijos en el código. */
function urlConsulta(parametros = {}) {
  if (!firebaseConfigurado()) throw new Error("Configura Firebase para consultar el ESP32.");
  const url = new URL(`${BASE_DATOS}/${RUTA_LECTURAS}.json`);
  for (const [clave, valor] of Object.entries(parametros)) {
    url.searchParams.set(clave, String(valor));
  }
  return url.toString();
}

/**
 * Convierte la respuesta de Firebase al modelo común de la aplicación.
 *
 * La respuesta REST es un objeto de identificadores generados por Firebase, no un array:
 * su orden de enumeración no es fiable, así que se ordena por `ts` explícitamente. Los
 * registros con fecha inválida se descartan y se informan aparte.
 */
export function normalizarLecturas(respuesta) {
  const registros = [];
  let descartados = 0;

  for (const [id, lectura] of Object.entries(respuesta ?? {})) {
    if (lectura === null || typeof lectura !== "object") {
      descartados += 1;
      continue;
    }
    if (!esTimestampValido(lectura.ts)) {
      descartados += 1;
      continue;
    }
    const valores = {};
    for (const campo of CAMPOS) {
      // Un cero es un dato válido: solo se omite lo que no sea un número finito.
      if (esNumeroValido(lectura[campo])) valores[campo] = lectura[campo];
    }
    registros.push({ id, ts: lectura.ts, valores });
  }

  registros.sort((a, b) => a.ts - b.ts);
  return { registros, descartados };
}

const DENEGADO = "Firebase denegó la lectura. Revisa la configuración de tu proyecto.";

const pedir = (url, señal) => pedirJson(url, {
  señal,
  servicio: "Firebase",
  errores: { 401: DENEGADO, 403: DENEGADO }
});

/** Todas las lecturas almacenadas. La consulta de extremos necesita el historial completo. */
export async function leerHistorialCompleto({ señal } = {}) {
  return normalizarLecturas(await pedir(urlConsulta(), señal));
}

/**
 * Lecturas de un rango de fechas, ambos extremos incluidos.
 *
 * `endAt` de Realtime Database es inclusivo, así que el límite superior es la medianoche
 * siguiente a la fecha final MENOS un milisegundo. Sin ese ajuste entraría la primera
 * lectura del día siguiente al rango.
 */
export async function leerRangoFechas(inicioISO, finISO, { señal } = {}) {
  const desde = inicioDiaCR(inicioISO);
  const hastaExclusivo = finDiaExclusivoCR(finISO);
  if (desde > hastaExclusivo) throw new Error("El inicio del rango es posterior al final.");

  const url = urlConsulta({
    orderBy: '"ts"',
    startAt: desde,
    endAt: hastaExclusivo - 1
  });
  return normalizarLecturas(await pedir(url, señal));
}

/** Lecturas de un solo día. */
export const leerDia = (fechaISO, opciones) => leerRangoFechas(fechaISO, fechaISO, opciones);

/**
 * Primera y última fecha con lecturas, para acotar los selectores de fecha.
 *
 * Son dos consultas de un registro cada una, aprovechando el índice de `ts`: no hace
 * falta descargar el historial entero solo para saber dónde empieza y dónde acaba.
 *
 * Ojo con lo que esto NO dice: el ESP32 solo envía mientras la simulación está corriendo,
 * así que dentro de ese rango puede haber días enteros sin ninguna lectura. Sirve para
 * descartar fechas imposibles, no para garantizar que un día concreto tenga datos.
 */
export async function rangoDisponible({ señal } = {}) {
  const extremo = async (limite) => {
    const { registros } = normalizarLecturas(
      await pedir(urlConsulta({ orderBy: '"ts"', [limite]: 1 }), señal)
    );
    return registros[0] ?? null;
  };

  const [primera, ultima] = await Promise.all([
    extremo("limitToFirst"),
    extremo("limitToLast")
  ]);
  if (!primera || !ultima) return null;
  return { inicio: fechaCR(primera.ts), fin: fechaCR(ultima.ts) };
}

/**
 * Última lectura recibida.
 *
 * Se ordena por identificador, igual que la app Android (`orderByKey().limitToLast(1)`).
 * Los identificadores de Firebase siguen el orden de creación, así que el resultado es la
 * lectura más reciente enviada por el dispositivo. Si en algún momento se importara un
 * historial con fechas antiguas, "última por identificador" y "última por `ts`" podrían
 * dejar de coincidir; se mantiene el criterio del móvil para que ambas apps muestren lo
 * mismo en Inicio.
 */
export async function leerUltimaLectura({ señal } = {}) {
  const url = urlConsulta({ orderBy: '"$key"', limitToLast: 1 });
  const { registros } = normalizarLecturas(await pedir(url, señal));
  return registros.at(-1) ?? null;
}

/**
 * Consulta la última lectura cada cierto tiempo y avisa cuando cambia.
 *
 * Solo consulta mientras la pestaña está visible y devuelve una función para cancelar
 * el sondeo al cambiar de vista.
 */
export function observarUltimaLectura(alRecibir, alFallar, intervaloMs = 30000) {
  let cancelado = false;
  let temporizador = null;
  let controlador = null;
  let ultimoId = null;
  let recibidaPrimeraRespuesta = false;
  let recuperando = false;

  const consultar = async () => {
    if (cancelado || document.visibilityState !== "visible") return;
    controlador?.abort();
    controlador = new AbortController();
    try {
      const lectura = await leerUltimaLectura({ señal: controlador.signal });
      if (cancelado) return;
      const id = lectura?.id ?? null;
      // Una respuesta vacía termina la carga inicial. Después de un fallo también
      // se avisa, aunque el ESP32 aún no haya enviado una lectura distinta.
      if (!recibidaPrimeraRespuesta || recuperando || id !== ultimoId) {
        ultimoId = id;
        recibidaPrimeraRespuesta = true;
        recuperando = false;
        alRecibir(lectura);
      }
    } catch (error) {
      if (!cancelado && error.name !== "AbortError") {
        recuperando = true;
        alFallar?.(error);
      }
    }
  };

  const alCambiarVisibilidad = () => {
    if (document.visibilityState === "visible") consultar();
  };

  consultar();
  temporizador = setInterval(consultar, intervaloMs);
  document.addEventListener("visibilitychange", alCambiarVisibilidad);

  return () => {
    cancelado = true;
    clearInterval(temporizador);
    controlador?.abort();
    document.removeEventListener("visibilitychange", alCambiarVisibilidad);
  };
}
