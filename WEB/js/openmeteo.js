/**
 * Cliente de Open-Meteo: historial de diez días y pronóstico, por separado.
 *
 * La API pública no necesita clave para uso académico no comercial. Los valores son
 * estimaciones de un modelo meteorológico para unas coordenadas aproximadas, no
 * mediciones de una estación del campus.
 *
 * El pronóstico NUNCA entra en los promedios académicos: son dos peticiones y dos
 * conjuntos de datos distintos. Portado de ClimaSantaClara.kt, ClimaActual.kt y
 * PronosticoClima.kt, conservando sus validaciones de unidades, tamaños y fechas.
 *
 * Datos: Open-Meteo (https://open-meteo.com/) · Licencia CC BY 4.0.
 */

import {
  esNumeroValido, fechaCR, hoyCR, sumarDias, diasEntre, inicioDiaCR, ZONA_CONSULTA
} from "./modelo.js";
import { pedirJson } from "./red.js";

const ENDPOINT = "https://api.open-meteo.com/v1/forecast";
const DIAS_PRONOSTICO = 10;
export const INTERVALO_CONSULTA_CLIMA = 15 * 60 * 1000;

const CAMPOS = {
  temperatura: "temperature_2m",
  humedad: "relative_humidity_2m",
  puntoRocio: "dew_point_2m"
};

/** Los diez días completos anteriores. Hoy queda excluido: su día aún no ha terminado. */
export const ultimosDiezDias = (hoyISO = hoyCR()) => ({
  inicio: sumarDias(hoyISO, -10),
  fin: sumarDias(hoyISO, -1)
});

/**
 * Periodo que se puede consultar.
 *
 * El endpoint acepta fechas de hasta unos tres meses atrás, pero más allá de unos sesenta
 * días devuelve las horas con el valor vacío: la petición responde 200 y el análisis
 * saldría en blanco. Se limita a ese margen comprobado para que el selector no ofrezca
 * fechas que no van a dar nada, y se deja un día de holgura.
 */
export const DIAS_DISPONIBLES = 59;

export const periodoDisponible = (hoyISO = hoyCR()) => ({
  inicio: sumarDias(hoyISO, -DIAS_DISPONIBLES),
  fin: sumarDias(hoyISO, -1)
});

/** Un rango está cubierto por un historial ya descargado si cae dentro de él. */
export const rangoCubierto = (historial, rango) =>
  !!historial &&
  historial.rango.inicio <= rango.inicio &&
  historial.rango.fin >= rango.fin;

function construirUrl(zona, parametros) {
  const url = new URL(ENDPOINT);
  url.searchParams.set("latitude", zona.latitud);
  url.searchParams.set("longitude", zona.longitud);
  url.searchParams.set("temperature_unit", "celsius");
  url.searchParams.set("timeformat", "unixtime");
  url.searchParams.set("timezone", ZONA_CONSULTA);
  for (const [clave, valor] of Object.entries(parametros)) url.searchParams.set(clave, valor);
  return url.toString();
}

export const urlHistorial = (rango, zona) => construirUrl(zona, {
  hourly: "temperature_2m,relative_humidity_2m,dew_point_2m",
  start_date: rango.inicio,
  end_date: rango.fin
});

export const urlPronostico = (zona) => construirUrl(zona, {
  current: "temperature_2m,relative_humidity_2m,dew_point_2m,weather_code,is_day",
  hourly: "temperature_2m,relative_humidity_2m,dew_point_2m,weather_code,precipitation_probability",
  daily: "temperature_2m_min,temperature_2m_max,weather_code,precipitation_probability_max",
  forecast_days: DIAS_PRONOSTICO
});

/** Comprueba que la API devolvió las unidades que se le pidieron. */
function validarUnidades(unidades, exigeHumedad = true) {
  const correcto =
    unidades?.time === "unixtime" &&
    unidades?.temperature_2m === "°C" &&
    unidades?.dew_point_2m === "°C" &&
    (!exigeHumedad || unidades?.relative_humidity_2m === "%");
  if (!correcto) throw new Error("La API devolvió unidades distintas de las solicitadas.");
}

const numero = (valor) => (esNumeroValido(valor) ? valor : null);
const codigoWmo = (valor) =>
  esNumeroValido(valor) && valor >= 0 && valor <= 99 && Number.isInteger(valor) ? valor : null;
const probabilidad = (valor) => (esNumeroValido(valor) && valor >= 0 && valor <= 100 ? valor : null);

/**
 * Condición meteorológica a partir del código WMO.
 * Un código ausente o desconocido se informa como no disponible: presentarlo como cielo
 * despejado sería inventar un dato.
 */
export function condicionWmo(codigo) {
  if (codigo === null) return { texto: "Condición no disponible", icono: "desconocido" };
  if (codigo === 0) return { texto: "Despejado", icono: "despejado" };
  if (codigo <= 2) return { texto: "Parcialmente nublado", icono: "parcial" };
  if (codigo === 3) return { texto: "Nublado", icono: "nublado" };
  if (codigo === 45 || codigo === 48) return { texto: "Neblina", icono: "neblina" };
  if ([51, 53, 55, 56, 57].includes(codigo)) return { texto: "Llovizna", icono: "llovizna" };
  if ([61, 63, 65, 66, 67, 80, 81, 82].includes(codigo)) return { texto: "Lluvia", icono: "lluvia" };
  if ([71, 73, 75, 77, 85, 86].includes(codigo)) return { texto: "Nieve", icono: "nieve" };
  if ([95, 96, 99].includes(codigo)) return { texto: "Tormenta", icono: "tormenta" };
  return { texto: "Condición no disponible", icono: "desconocido" };
}

/**
 * Historial horario de diez días completos.
 *
 * Se exige que las 240 horas estén presentes y en su sitio: el tiempo Unix ya está en UTC,
 * así que cada posición debe ser exactamente la medianoche inicial más su número de horas.
 * No se le vuelve a sumar el desfase de Costa Rica.
 */
export function interpretarHistorial(datos, rango, zona, descargadoEn = Date.now()) {
  if (datos?.error) throw new Error("Open-Meteo rechazó la consulta del historial.");
  validarUnidades(datos?.hourly_units);

  // Cada día completo son 24 horas. Costa Rica no cambia de hora en todo el año, así que
  // no hay días de 23 ni de 25 que compensar.
  const horasEsperadas = (diasEntre(rango.inicio, rango.fin) + 1) * 24;

  const horas = datos.hourly;
  const tiempos = horas?.time;
  if (!Array.isArray(tiempos) || tiempos.length !== horasEsperadas) {
    throw new Error(`La respuesta no contiene las ${horasEsperadas} horas del periodo solicitado.`);
  }
  for (const campo of Object.values(CAMPOS)) {
    if (!Array.isArray(horas[campo]) || horas[campo].length !== tiempos.length) {
      throw new Error("Las series de la API no tienen la misma longitud.");
    }
  }

  const inicioSegundos = inicioDiaCR(rango.inicio) / 1000;
  const registros = tiempos.map((segundos, indice) => {
    if (segundos !== inicioSegundos + indice * 3600) {
      throw new Error("Las fechas horarias de la API no corresponden al periodo solicitado.");
    }
    const valores = {};
    for (const [clave, campo] of Object.entries(CAMPOS)) {
      const valor = numero(horas[campo][indice]);
      if (valor !== null) valores[clave] = valor;
    }
    return { ts: segundos * 1000, valores };
  });

  // La API responde 200 con todas las horas vacías para periodos que acepta pero no
  // cubre. Conviene detectarlo aquí y no dejar que llegue como un análisis en blanco.
  if (!registros.some((registro) => Object.keys(registro.valores).length > 0)) {
    throw new Error(
      `Open-Meteo no tiene datos de ${rango.inicio} a ${rango.fin} para esta zona. Prueba con un periodo más reciente.`
    );
  }

  return { rango, registros, descargadoEn, zonaId: zona.id, origen: origenDatos(zona) };
}

/** Procedencia que acompaña a los CSV y a la pantalla de información. */
export const origenDatos = (zona) => ({
  fuente: "Open-Meteo · datos modelados",
  ubicacion: `${zona.nombre}, ${zona.region}, Costa Rica`,
  latitud: zona.latitud,
  longitud: zona.longitud,
  proveedor: "https://open-meteo.com/",
  licencia: "https://creativecommons.org/licenses/by/4.0/"
});

/**
 * Condiciones actuales, próximas horas y diez días.
 *
 * Se conservan por separado la fecha del modelo y la de consulta: así se puede avisar de
 * que un dato es anterior sin presentarlo como recién recibido.
 */
export function interpretarPronostico(datos, zona, consultadoEn = Date.now()) {
  if (datos?.error) throw new Error("Open-Meteo rechazó la consulta.");
  validarUnidades(datos?.current_units);
  validarUnidades(datos?.hourly_units);

  const actual = datos.current;
  const segundos = actual?.time;
  if (!esNumeroValido(segundos) || !Number.isInteger(segundos) || segundos < 0) {
    throw new Error("La fecha del dato actual no es válida.");
  }
  const intervalo = actual?.interval;
  if (!esNumeroValido(intervalo) || !Number.isInteger(intervalo) || intervalo < 1 || intervalo > 3600) {
    throw new Error("El intervalo del dato actual no es válido.");
  }

  const valores = {};
  for (const [clave, campo] of Object.entries(CAMPOS)) {
    const valor = numero(actual[campo]);
    if (valor !== null) valores[clave] = valor;
  }
  if (Object.keys(valores).length === 0) {
    throw new Error("La API no devolvió valores actuales válidos.");
  }

  const horas = datos.hourly ?? {};
  const listaHoras = (horas.time ?? []).map((segundos, i) => ({
    ts: segundos * 1000,
    temperatura: numero(horas.temperature_2m?.[i]),
    codigo: codigoWmo(horas.weather_code?.[i]),
    lluvia: probabilidad(horas.precipitation_probability?.[i])
  }));

  const dias = datos.daily ?? {};
  const listaDias = (dias.time ?? []).map((segundos, i) => ({
    ts: segundos * 1000,
    fecha: fechaCR(segundos * 1000),
    minima: numero(dias.temperature_2m_min?.[i]),
    maxima: numero(dias.temperature_2m_max?.[i]),
    codigo: codigoWmo(dias.weather_code?.[i]),
    lluvia: probabilidad(dias.precipitation_probability_max?.[i])
  }));

  return {
    zonaId: zona.id,
    actual: { registro: { ts: segundos * 1000, valores }, consultadoEn, intervaloSegundos: intervalo },
    codigoActual: codigoWmo(actual.weather_code),
    esDia: actual.is_day === 1 || actual.is_day === true,
    horas: listaHoras,
    dias: listaDias
  };
}

/**
 * Un dato actual se considera anterior cuando pasa el mayor valor entre media hora y dos
 * intervalos del modelo. El intervalo de la API viene en segundos.
 */
export const datoActualAnterior = (actual, ahora = Date.now()) =>
  ahora - actual.registro.ts > Math.max(30 * 60 * 1000, actual.intervaloSegundos * 2000);

// --- Peticiones -------------------------------------------------------------------

const pedir = (url, señal) => pedirJson(url, {
  señal,
  servicio: "Open-Meteo",
  errores: { 429: "Open-Meteo recibió demasiadas consultas. Espera unos minutos e inténtalo de nuevo." }
});

/**
 * Descarga el historial de un periodo. Sin rango explícito usa los diez días completos
 * anteriores, que es lo que propone la aplicación al entrar.
 */
export async function descargarHistorial(zona, { señal, rango, hoyISO = hoyCR() } = {}) {
  const periodo = rango ?? ultimosDiezDias(hoyISO);
  if (diasEntre(periodo.inicio, periodo.fin) < 0) {
    throw new Error("El inicio del periodo es posterior al final.");
  }
  const datos = await pedir(urlHistorial(periodo, zona), señal);
  return interpretarHistorial(datos, periodo, zona);
}

export async function descargarPronostico(zona, { señal } = {}) {
  const datos = await pedir(urlPronostico(zona), señal);
  return interpretarPronostico(datos, zona);
}

// --- Copia local del historial ------------------------------------------------------

const VERSION_COPIA = 1;
const claveCopia = (zonaId) => `clima_${zonaId}`;

/**
 * Guarda el historial de una zona para poder consultarlo sin conexión.
 *
 * Cada zona tiene su propia copia, con la versión del formato y la procedencia, de modo
 * que cambiar de lugar nunca mezcle datos. Si el almacenamiento falla por cuota o por
 * estar deshabilitado, la aplicación sigue funcionando con los datos en memoria.
 */
export function guardarHistorial(historial) {
  try {
    localStorage.setItem(claveCopia(historial.zonaId), JSON.stringify({
      version: VERSION_COPIA,
      zonaId: historial.zonaId,
      rango: historial.rango,
      descargadoEn: historial.descargadoEn,
      origen: historial.origen,
      registros: historial.registros
    }));
    return true;
  } catch {
    return false;
  }
}

/** Recupera la copia de una zona, o null si no hay ninguna utilizable. */
export function leerHistorialGuardado(zonaId) {
  try {
    const texto = localStorage.getItem(claveCopia(zonaId));
    if (!texto) return null;
    const copia = JSON.parse(texto);
    // Una copia de otra versión o de otra zona se ignora en vez de usarse a medias.
    if (copia?.version !== VERSION_COPIA || copia.zonaId !== zonaId) return null;
    if (!Array.isArray(copia.registros) || copia.registros.length === 0) return null;
    return copia;
  } catch {
    return null;
  }
}
