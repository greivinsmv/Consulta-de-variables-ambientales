/**
 * Cálculos de las tres consultas del proyecto.
 *
 * Portado de PromediosPorHora (ConsultaPorHora.kt), PromediosDiarios.kt,
 * ExtremosHistoricos.kt y escalaGrafico (GraficosAmbientales.kt). Las funciones son puras:
 * reciben registros ya normalizados y no consultan la red ni tocan la interfaz.
 *
 * Reglas que deben coincidir con la app Android:
 *  - Cada variable se calcula con sus propias lecturas válidas. Un registro con temperatura
 *    inválida y humedad correcta cuenta para humedad y no para temperatura.
 *  - Los promedios usan las lecturas originales. El promedio diario NO promedia los
 *    promedios horarios: con distinta cantidad de lecturas por hora daría otro número.
 *  - Un periodo sin lecturas queda sin promedio. Nunca se sustituye por cero.
 */

import { esNumeroValido, fechaCR, horaCR, fechasDelRango, diasEntre } from "./modelo.js";

/**
 * Promedios de cada hora de un día. Devuelve solo las horas que tienen lecturas,
 * ordenadas de menor a mayor: las horas vacías se representan por su ausencia.
 */
export function promediosPorHora(registros, diaISO, variable) {
  const sumas = new Map();

  for (const registro of registros) {
    const valor = registro.valores[variable];
    if (!esNumeroValido(valor)) continue;
    if (fechaCR(registro.ts) !== diaISO) continue;

    const hora = horaCR(registro.ts);
    const acumulado = sumas.get(hora);
    if (acumulado === undefined) sumas.set(hora, { suma: valor, cantidad: 1 });
    else {
      acumulado.suma += valor;
      acumulado.cantidad += 1;
    }
  }

  return [...sumas.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([hora, { suma, cantidad }]) => ({ hora, promedio: suma / cantidad, cantidad }));
}

/**
 * Promedios de cada día del rango, ambos extremos incluidos. A diferencia de los
 * promedios horarios, aquí sí aparecen las fechas sin lecturas, con promedio nulo y
 * cantidad cero, porque la consulta por día debe mostrar los huecos del periodo.
 */
export function promediosDiarios(registros, rango, variable) {
  if (diasEntre(rango.inicio, rango.fin) < 0) {
    throw new Error("El inicio del rango es posterior al final.");
  }

  const sumas = new Map();
  for (const registro of registros) {
    const valor = registro.valores[variable];
    if (!esNumeroValido(valor)) continue;

    const fecha = fechaCR(registro.ts);
    const acumulado = sumas.get(fecha);
    if (acumulado === undefined) sumas.set(fecha, { suma: valor, cantidad: 1 });
    else {
      acumulado.suma += valor;
      acumulado.cantidad += 1;
    }
  }

  return fechasDelRango(rango.inicio, rango.fin).map((fecha) => {
    const acumulado = sumas.get(fecha);
    if (acumulado === undefined) return { fecha, promedio: null, cantidad: 0 };
    return { fecha, promedio: acumulado.suma / acumulado.cantidad, cantidad: acumulado.cantidad };
  });
}

/** Mensaje de error de un rango de fechas, o null si es válido. */
export function errorRango(inicioISO, finISO, hoyISO) {
  if (diasEntre(inicioISO, finISO) < 0) {
    return "La fecha inicial debe ser anterior o igual a la final.";
  }
  if (diasEntre(inicioISO, hoyISO) < 0 || diasEntre(finISO, hoyISO) < 0) {
    return "No puedes consultar fechas futuras.";
  }
  return null;
}

/**
 * Mínimo y máximo de una variable en todo el historial consultado.
 *
 * Trabaja sobre las lecturas individuales, no sobre promedios diarios. En un empate se
 * conserva la fecha más antigua, sin depender del orden en que vengan los registros. Una
 * sola lectura puede ser mínimo y máximo a la vez. Devuelve null si no hay nada válido.
 */
export function extremosHistoricos(registros, variable) {
  const validos = [];
  for (const registro of registros) {
    const valor = registro.valores[variable];
    if (esNumeroValido(valor)) validos.push({ ts: registro.ts, valor });
  }
  if (validos.length === 0) return null;

  let menor = validos[0].valor;
  let mayor = validos[0].valor;
  for (const { valor } of validos) {
    if (valor < menor) menor = valor;
    if (valor > mayor) mayor = valor;
  }

  const extremo = (objetivo) => {
    let repeticiones = 0;
    let primeraFecha = Infinity;
    for (const { ts, valor } of validos) {
      if (valor !== objetivo) continue;
      repeticiones += 1;
      if (ts < primeraFecha) primeraFecha = ts;
    }
    return { valor: objetivo, ts: primeraFecha, repeticiones };
  };

  return {
    minimo: extremo(menor),
    maximo: extremo(mayor),
    cantidad: validos.length,
    amplitud: mayor - menor
  };
}

/**
 * Promedio, máximo y mínimo de una variable sobre las lecturas del periodo consultado.
 *
 * Es el "panel resumen" del enunciado. Se calcula sobre las lecturas individuales, igual
 * que la consulta de extremos: el máximo es el valor más alto que se registró, no el del
 * periodo con la media más alta. Por eso puede no coincidir con la barra más alta de la
 * gráfica, que muestra promedios; la interfaz lo indica.
 */
export function resumenEstadistico(registros, variable) {
  const extremos = extremosHistoricos(registros, variable);
  if (!extremos) return null;

  let suma = 0;
  for (const registro of registros) {
    const valor = registro.valores[variable];
    if (esNumeroValido(valor)) suma += valor;
  }

  return {
    promedio: suma / extremos.cantidad,
    maximo: extremos.maximo,
    minimo: extremos.minimo,
    amplitud: extremos.amplitud,
    cantidad: extremos.cantidad
  };
}

/**
 * Reparte las lecturas en tramos de igual ancho entre el mínimo y el máximo.
 * Sirve para dibujar la distribución de una variable: cuántas veces se registró cada
 * franja de valores, que es lo que el histograma de la consulta de extremos muestra.
 */
export function distribucion(registros, variable, tramos = 12) {
  const valores = [];
  for (const registro of registros) {
    const valor = registro.valores[variable];
    if (esNumeroValido(valor)) valores.push(valor);
  }
  if (valores.length === 0) return [];

  const menor = Math.min(...valores);
  const mayor = Math.max(...valores);
  // Con todos los valores iguales no hay ancho que repartir: un solo tramo lo dice mejor.
  if (menor === mayor) return [{ desde: menor, hasta: mayor, cantidad: valores.length }];

  const ancho = (mayor - menor) / tramos;
  const cubos = Array.from({ length: tramos }, (_, i) => ({
    desde: menor + i * ancho,
    hasta: menor + (i + 1) * ancho,
    cantidad: 0
  }));
  for (const valor of valores) {
    // El último tramo incluye el máximo, que si no caería en un cubo inexistente.
    const indice = Math.min(Math.floor((valor - menor) / ancho), tramos - 1);
    cubos[indice].cantidad += 1;
  }
  return cubos;
}

/**
 * Escala de una gráfica con margen, para que ningún punto quede pegado al borde.
 *
 * El margen mínimo de 1 evita dividir entre cero cuando todos los valores son iguales:
 * una serie constante o un único punto siguen teniendo una escala utilizable.
 * `incluirCero` se usa en las barras, donde la línea del cero debe ser visible.
 */
export function escalaGrafico(valores, incluirCero = false) {
  if (valores.length === 0 || !valores.every(esNumeroValido)) {
    throw new Error("La escala necesita al menos un valor finito.");
  }
  let bajo = Math.min(...valores);
  let alto = Math.max(...valores);
  if (incluirCero) {
    bajo = Math.min(0, bajo);
    alto = Math.max(0, alto);
  }
  const margen = Math.max((alto - bajo) * 0.15, 1);
  return { minimo: bajo - margen, maximo: alto + margen };
}

/**
 * Escalas de una comparación, agrupadas por unidad: temperatura y punto de rocío comparten
 * el eje en °C, mientras que la humedad usa el suyo en %. Una serie sin puntos no participa
 * en el cálculo de su escala.
 */
export function escalasComparacion(series) {
  const porUnidad = new Map();
  for (const serie of series) {
    const valores = serie.puntos.map((p) => p.valor);
    if (valores.length === 0) continue;
    const acumulado = porUnidad.get(serie.variable.unidad);
    if (acumulado === undefined) porUnidad.set(serie.variable.unidad, [...valores]);
    else acumulado.push(...valores);
  }
  const escalas = {};
  for (const [unidad, valores] of porUnidad) escalas[unidad] = escalaGrafico(valores);
  return escalas;
}
