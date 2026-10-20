/**
 * Filtro de valores atípicos, portado de FiltroAtipicos.kt y ComparacionVariables.kt.
 *
 * El filtro no modifica nada en Firebase ni decide si una lectura es "real": solo excluye
 * de los cálculos los valores fuera de unos límites que el usuario puede editar. Los
 * límites son inclusivos y se aplican a cada variable por separado, de modo que una
 * temperatura excluida no elimina la humedad del mismo registro.
 */

import { esNumeroValido, variables } from "./modelo.js";

/**
 * Límites iniciales. Son valores orientativos de demostración, no una afirmación de que
 * las lecturas fuera de ellos sean falsas: desde Wokwi se envían extremos a propósito
 * para probar la aplicación. El filtro arranca apagado.
 */
export const configuracionFiltroInicial = () => ({
  activo: false,
  limites: {
    temperatura: { minimo: "-10", maximo: "50" },
    humedad: { minimo: "0", maximo: "100" },
    puntoRocio: { minimo: "-30", maximo: "40" }
  }
});

/**
 * Convierte el texto de los dos campos en un rango numérico, o null si no es utilizable.
 *
 * Se acepta coma o punto decimal y números negativos. Cuidado con dos trampas de
 * JavaScript que no existen en Kotlin: `Number("")` devuelve 0 en vez de fallar, y
 * `parseFloat("50abc")` devuelve 50 en vez de null. Por eso se descarta el texto vacío
 * antes de convertir y se usa `Number`, que rechaza la cadena completa si sobra algo.
 */
export function interpretarLimites(texto) {
  const aNumero = (valor) => {
    if (typeof valor !== "string") return null;
    const limpio = valor.trim().replace(/,/g, ".");
    if (limpio === "") return null;
    const numero = Number(limpio);
    return Number.isFinite(numero) ? numero : null;
  };

  const minimo = aNumero(texto?.minimo);
  const maximo = aNumero(texto?.maximo);
  if (minimo === null || maximo === null || minimo > maximo) return null;
  return { minimo, maximo };
}

/**
 * Separa las lecturas utilizables de las excluidas por el filtro y de las que no traen
 * valor para esa variable. Distinguir esos tres casos es lo que permite mostrar
 * "N utilizadas · N excluidas · N sin valor" sin confundir un atípico con un dato ausente.
 */
export function filtrarLecturas(registros, variable, limites) {
  const utiles = [];
  let excluidos = 0;
  let sinValor = 0;

  for (const registro of registros) {
    const valor = registro.valores[variable];
    if (!esNumeroValido(valor)) {
      sinValor += 1;
    } else if (limites && (valor < limites.minimo || valor > limites.maximo)) {
      excluidos += 1;
    } else {
      utiles.push(registro);
    }
  }

  return { registros: utiles, utilizados: utiles.length, excluidos, sinValor };
}

/** Límites efectivos de una variable: null cuando el filtro está apagado. */
export const limitesComparacion = (filtro, clave) =>
  filtro.activo ? interpretarLimites(filtro.limites[clave]) : null;

/**
 * Una comparación es válida si hay al menos una variable elegida y, con el filtro
 * encendido, todas tienen límites utilizables. Unos límites mal escritos bloquean la
 * consulta en vez de producir un resultado silenciosamente incorrecto.
 */
export const comparacionValida = (elegidas, filtro) =>
  elegidas.length > 0 &&
  (!filtro.activo || elegidas.every((clave) => limitesComparacion(filtro, clave) !== null));

/** Aplica el filtro a cada variable elegida por separado. */
export function datosComparacion(registros, elegidas, filtro) {
  const resultado = {};
  for (const clave of elegidas) {
    resultado[clave] = filtrarLecturas(registros, clave, limitesComparacion(filtro, clave));
  }
  return resultado;
}

/** Resumen del filtro para mostrar bajo los resultados. */
export function resumenFiltro(datos) {
  const base = `${datos.utilizados} utilizadas · ${datos.excluidos} excluidas`;
  return datos.sinValor > 0 ? `${base} · ${datos.sinValor} sin valor` : base;
}

/** Color del trazo de cada serie en la gráfica, idéntico al de la app Android. */
export function colorVariable(clave, oscuro) {
  const paleta = {
    temperatura: { claro: "#7050C6", oscuro: "#C0A8FF" },
    humedad: { claro: "#007E78", oscuro: "#6ADBD3" },
    puntoRocio: { claro: "#BB397C", oscuro: "#FFA4D0" }
  };
  const colores = paleta[clave] ?? paleta.puntoRocio;
  return oscuro ? colores.oscuro : colores.claro;
}

/**
 * Variante para texto de color (leyenda, chips y valores del resumen).
 *
 * En tema claro, la humedad y el punto de rocío se quedan en 4,07:1 y 4,30:1 sobre las
 * superficies de la aplicación, por debajo del 4,5:1 que pide WCAG AA. Se oscurecen lo
 * justo para cruzar ese umbral; la diferencia es imperceptible y el trazo de la gráfica
 * conserva el color exacto del móvil. En tema oscuro los tres ya superan 6:1.
 */
export function colorVariableTexto(clave, oscuro) {
  if (oscuro) return colorVariable(clave, oscuro);
  const paleta = { temperatura: "#7050C6", humedad: "#007570", puntoRocio: "#B53778" };
  return paleta[clave] ?? paleta.puntoRocio;
}

export const clavesVariables = () => variables.map((v) => v.clave);
