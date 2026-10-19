/**
 * Modelo común de la aplicación: variables, zonas, manejo de fechas y validación.
 *
 * Todo el proyecto interpreta las fechas en América/Costa_Rica, sin importar la zona
 * horaria del navegador. Portado de ConsultaPorHora.kt y ZonasClimaticas.kt para que la
 * web y la app Android produzcan exactamente los mismos resultados.
 */

export const ZONA_CONSULTA = "America/Costa_Rica";

/** Las tres variables ambientales del proyecto, en el orden en que se presentan. */
export const variables = [
  { clave: "temperatura", nombre: "Temperatura", unidad: "°C" },
  { clave: "humedad", nombre: "Humedad", unidad: "%" },
  { clave: "puntoRocio", nombre: "Punto de rocío", unidad: "°C" }
];

export const variablePorClave = (clave) => variables.find((v) => v.clave === clave);

/**
 * Coordenadas aproximadas del centro de cada zona; no requieren permiso de ubicación.
 * Los identificadores se comparten con la app Android: forman parte de las preferencias
 * guardadas y de los nombres de los CSV. No cambiar los existentes.
 */
export const zonasClimaticas = [
  { id: "santa_clara", nombre: "Santa Clara", region: "San Carlos", latitud: 10.36, longitud: -84.51 },
  { id: "san_jose", nombre: "San José", region: "San José", latitud: 9.93, longitud: -84.08 },
  { id: "alajuela", nombre: "Alajuela", region: "Alajuela", latitud: 10.02, longitud: -84.21 },
  { id: "cartago", nombre: "Cartago", region: "Cartago", latitud: 9.86, longitud: -83.92 },
  { id: "heredia", nombre: "Heredia", region: "Heredia", latitud: 10.0, longitud: -84.12 },
  { id: "liberia", nombre: "Liberia", region: "Guanacaste", latitud: 10.63, longitud: -85.44 },
  { id: "puntarenas", nombre: "Puntarenas", region: "Puntarenas", latitud: 9.98, longitud: -84.84 },
  { id: "limon", nombre: "Limón", region: "Limón", latitud: 9.99, longitud: -83.04 },
  { id: "la_fortuna", nombre: "La Fortuna", region: "San Carlos", latitud: 10.47, longitud: -84.65 },
  { id: "perez_zeledon", nombre: "San Isidro", region: "Pérez Zeledón", latitud: 9.37, longitud: -83.7 }
];

export const santaClara = zonasClimaticas[0];
export const zonaPorId = (id) => zonasClimaticas.find((z) => z.id === id) ?? santaClara;

export const urlProveedorClima = "https://open-meteo.com/";
export const licenciaClima = "https://creativecommons.org/licenses/by/4.0/";

// --- Validación numérica -----------------------------------------------------------

/**
 * Un valor solo cuenta si es un número finito. El cero es un dato válido, así que no se
 * puede usar `valor || 0` ni `Number(null)` para rellenar ausencias.
 */
export const esNumeroValido = (valor) => typeof valor === "number" && Number.isFinite(valor);

/** Rango admitido para una fecha Unix en milisegundos: del año 0 al 9999. */
const TS_MAXIMO = 253402300799999;
export const esTimestampValido = (ts) =>
  esNumeroValido(ts) && Number.isInteger(ts) && ts >= 0 && ts <= TS_MAXIMO;

// --- Fechas en Costa Rica ----------------------------------------------------------

const formateadorCR = new Intl.DateTimeFormat("en-CA", {
  timeZone: ZONA_CONSULTA,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  hourCycle: "h23"
});

// Convertir con Intl cuesta unos microsegundos; un historial de 14 400 lecturas lo haría
// miles de veces por consulta. Se memoriza el resultado por timestamp.
const memoriaPartes = new Map();

/** Devuelve la fecha ISO y la hora (0-23) de un timestamp, en hora de Costa Rica. */
export function partesCR(ts) {
  const guardado = memoriaPartes.get(ts);
  if (guardado !== undefined) return guardado;

  const partes = {};
  for (const { type, value } of formateadorCR.formatToParts(new Date(ts))) {
    partes[type] = value;
  }
  // `hourCycle: "h23"` puede devolver "24" para la medianoche en algunos motores.
  const hora = Number(partes.hour) % 24;
  const resultado = { fecha: `${partes.year}-${partes.month}-${partes.day}`, hora };

  if (memoriaPartes.size > 50000) memoriaPartes.clear();
  memoriaPartes.set(ts, resultado);
  return resultado;
}

export const fechaCR = (ts) => partesCR(ts).fecha;
export const horaCR = (ts) => partesCR(ts).hora;

/**
 * Milisegundos de la medianoche de una fecha en Costa Rica.
 *
 * Costa Rica mantiene UTC−6 todo el año: abolió el horario de verano en 1992, así que
 * todos sus días duran 24 horas exactas y el desfase puede escribirse como literal.
 * `new Date("2026-10-08")` NO sirve aquí: ese formato se interpreta como UTC.
 */
export function inicioDiaCR(fechaISO) {
  const ms = new Date(`${fechaISO}T00:00:00-06:00`).getTime();
  if (!Number.isFinite(ms)) throw new Error(`Fecha no válida: ${fechaISO}`);
  return ms;
}

const UN_DIA_MS = 86400000;

/** Fin exclusivo de una fecha: la medianoche siguiente. */
export const finDiaExclusivoCR = (fechaISO) => inicioDiaCR(fechaISO) + UN_DIA_MS;

/** Suma (o resta, con días negativos) días a una fecha ISO. */
export const sumarDias = (fechaISO, dias) =>
  fechaCR(inicioDiaCR(fechaISO) + dias * UN_DIA_MS + UN_DIA_MS / 2);

/** Días completos entre dos fechas ISO; negativo si la segunda es anterior. */
export const diasEntre = (desdeISO, hastaISO) =>
  Math.round((inicioDiaCR(hastaISO) - inicioDiaCR(desdeISO)) / UN_DIA_MS);

/** Fecha de hoy en Costa Rica, que puede no coincidir con la del navegador. */
export const hoyCR = () => fechaCR(Date.now());

/**
 * Todas las fechas del rango, ambos extremos incluidos y en formato canónico.
 *
 * Se recorre por cantidad de días en vez de comparar cadenas: si llegara una fecha sin
 * ceros a la izquierda ("2026-10-5"), una comparación textual no encontraría nunca el
 * final del rango.
 */
export function fechasDelRango(inicioISO, finISO) {
  const total = diasEntre(inicioISO, finISO);
  if (total < 0) throw new Error("El inicio del rango es posterior al final.");
  const fechas = [];
  for (let i = 0; i <= total; i++) fechas.push(sumarDias(inicioISO, i));
  return fechas;
}

// --- Presentación ------------------------------------------------------------------

/**
 * Fecha para mostrar en pantalla, como "16/09/2026".
 *
 * Los selectores `<input type="date">` los pinta el navegador según el idioma del
 * sistema, y en español salen con el día delante. Las fechas que escribe la aplicación
 * siguen ese mismo orden para que la pantalla no mezcle dos convenciones.
 *
 * El formato ISO (2026-09-16) se reserva para los datos: los CSV y los PDF lo conservan,
 * porque ahí sí importa que sea inequívoco y que coincida con lo que exporta la app
 * Android.
 */
export function formatearFecha(fechaISO) {
  const [anio, mes, dia] = String(fechaISO).split("-");
  if (!anio || !mes || !dia) return String(fechaISO);
  return `${dia}/${mes}/${anio}`;
}

/** Versión corta, "16/09", para los ejes de las gráficas, donde el espacio escasea. */
export function formatearFechaCorta(fechaISO) {
  const [, mes, dia] = String(fechaISO).split("-");
  return mes && dia ? `${dia}/${mes}` : String(fechaISO);
}

/** Texto de una hora completa, como "08:00 - 08:59". */
export const franjaHoraria = (hora) =>
  `${String(hora).padStart(2, "0")}:00 - ${String(hora).padStart(2, "0")}:59`;

/** Número con coma decimal, al estilo del resto del proyecto. */
export function formatearNumero(valor, decimales = 1) {
  if (!esNumeroValido(valor)) return "Sin dato";
  return valor.toFixed(decimales).replace(".", ",");
}

/** Valor con su unidad, o "Sin datos" si no hay nada que mostrar. */
export function formatearValor(valor, unidad, decimales = 1) {
  if (!esNumeroValido(valor)) return "Sin datos";
  return `${formatearNumero(valor, decimales)} ${unidad}`;
}

/** Fecha y hora completas en Costa Rica, como "10/10/2026 14:35:02". */
export function formatearFechaHora(ts) {
  if (!esTimestampValido(ts)) return "Sin fecha";
  const partes = {};
  const formateador = new Intl.DateTimeFormat("es-CR", {
    timeZone: ZONA_CONSULTA,
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  });
  for (const { type, value } of formateador.formatToParts(new Date(ts))) partes[type] = value;
  return `${partes.day}/${partes.month}/${partes.year} ${partes.hour}:${partes.minute}:${partes.second}`;
}

/**
 * Punto de rocío por la aproximación de Magnus, con las mismas constantes que el firmware
 * del ESP32. Se usa solo para generar fixtures: los valores que llegan de Firebase o de
 * Open-Meteo se muestran tal cual, nunca recalculados.
 */
export function puntoRocioMagnus(temperatura, humedad) {
  if (!esNumeroValido(temperatura) || !esNumeroValido(humedad)) return NaN;
  if (humedad <= 0 || humedad > 100 || temperatura <= -243.12) return NaN;
  const a = 17.62;
  const b = 243.12;
  const gamma = Math.log(humedad / 100) + (a * temperatura) / (b + temperatura);
  return (b * gamma) / (a - gamma);
}
