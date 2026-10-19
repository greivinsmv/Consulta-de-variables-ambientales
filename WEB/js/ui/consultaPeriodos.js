/**
 * Consultas por hora y por día.
 *
 * Comparten casi todo —selección de variables, filtro, gráfica y resumen del periodo
 * elegido— y se diferencian en el periodo que agrupan: las 24 horas de un día, o cada día
 * de un rango de fechas. Por eso viven en el mismo módulo, con una tabla de diferencias.
 *
 * La evolución horaria se dibuja con líneas y la comparación entre días con barras, que es
 * lo que pide el enunciado para cada caso.
 */

import {
  variablePorClave, hoyCR, sumarDias, franjaHoraria, fechasDelRango, diasEntre,
  formatearFecha, formatearFechaCorta
} from "../modelo.js";
import {
  promediosPorHora, promediosDiarios, errorRango, resumenEstadistico
} from "../consultas.js";
import { datosComparacion, comparacionValida, configuracionFiltroInicial } from "../filtro.js";
import { leerDia, leerRangoFechas } from "../firebase.js";
import { ultimosDiezDias } from "../openmeteo.js";
import { dibujarComparacion, leyendaHtml, destruirGrafica } from "../graficos.js";
import { limitesComparacion } from "../filtro.js";
import {
  csvPorHora, csvPorDia, combinarCsvSeries, descargarCsv,
  nombreArchivo, numeroCsv, fechaHoraCsv, exportarPdf
} from "../exportar.js";
import { icono } from "./iconos.js";
import {
  esc, aviso, nota, selectorVariables, conectarSelectorVariables,
  controlFiltro, conectarControlFiltro, resumenSeries, resumenFiltrado,
  selectorEstadisticas, conectarSelectorEstadisticas, panelResumenEstadistico,
  estadisticas
} from "./componentes.js";

/** Lo que distingue una consulta de la otra. */
const MODOS = {
  hora: {
    titulo: "Promedios por hora",
    descripcion: "Elige un día y las variables que quieras comparar entre sus 24 horas.",
    tipoGrafica: "linea",
    etiquetaPeriodo: "Hora",
    nombrePeriodo: (hora) => franjaHoraria(hora)
  },
  dia: {
    titulo: "Promedios diarios",
    descripcion: "Elige un periodo y las variables que quieras comparar entre sus días.",
    tipoGrafica: "barra",
    etiquetaPeriodo: "Fecha",
    nombrePeriodo: (fecha) => fecha
  }
};

const estadoInicial = (modo) => ({
  dia: hoyCR(),
  inicio: sumarDias(hoyCR(), -9),
  fin: hoyCR(),
  elegidas: ["temperatura"],
  filtro: configuracionFiltroInicial(),
  claveFiltro: "temperatura",
  consulta: null,
  registros: [],
  cargando: false,
  error: "",
  fechasPropuestas: false,
  estadisticas: ["promedio", "maximo", "minimo"],
  seleccion: 0
});

const estados = { hora: estadoInicial("hora"), dia: estadoInicial("dia") };

/** Al cambiar de fuente, de zona o al recargar el historial, se descarta lo consultado. */
export function reiniciar() {
  estados.hora = estadoInicial("hora");
  estados.dia = estadoInicial("dia");
}

/**
 * Propone unas fechas de partida la primera vez que se entra con el clima.
 *
 * Se hace una sola vez, con la bandera `fechasPropuestas`: antes se repetía en cada
 * repintado, de modo que al cambiar una fecha el valor volvía solo al del historial y
 * parecía que el selector estuviera bloqueado.
 */
function ajustarADatos(modo, datos) {
  const estado = estados[modo];
  // Sin periodo conocido todavía no hay nada que proponer; al llegar se repinta y se
  // vuelve a entrar aquí.
  if (!datos.periodo || estado.fechasPropuestas) return;

  // Con el clima se propone el historial descargado, o los diez días anteriores. Con el
  // ESP32, las fechas en las que realmente hay lecturas: las de por omisión llegan hasta
  // hoy y caerían fuera, dejando la pantalla en un error nada más entrar.
  const rango = datos.tipo === "clima"
    ? (datos.historial?.rango ?? ultimosDiezDias())
    : datos.periodo;

  if (modo === "hora") estado.dia = rango.fin;
  else { estado.inicio = rango.inicio; estado.fin = rango.fin; }
  estado.fechasPropuestas = true;
}

/**
 * Límites de los selectores de fecha, según lo que cada fuente puede dar.
 *
 * Con el clima, el periodo lo marca Open-Meteo: unos dos meses hacia atrás. No se limita
 * al historial ya descargado, porque el que falte se descarga al consultar.
 *
 * Con el ESP32, el periodo son la primera y la última lectura que hay en Firebase. Deja
 * fuera las fechas en las que es imposible que haya datos, pero no las de en medio: el
 * dispositivo solo envía mientras la simulación corre, así que dentro del rango puede
 * haber días vacíos. Para esos está el mensaje de resultados.
 */
const fechaMaxima = (datos) => datos.periodo?.fin ?? hoyCR();
const fechaMinima = (datos) => datos.periodo?.inicio ?? null;

/** Texto de ayuda bajo los selectores, para no tener que adivinar dónde hay datos. */
function pistaPeriodo(datos) {
  if (!datos.periodo) return "";
  const { inicio, fin } = datos.periodo;
  return datos.tipo === "clima"
    ? `Open-Meteo cubre del ${formatearFecha(inicio)} al ${formatearFecha(fin)}.`
    : `El ESP32 tiene lecturas del ${formatearFecha(inicio)} al ${formatearFecha(fin)}, ` +
      "con días sueltos sin envíos.";
}

/** Validación en tiempo real: un rango inválido no llega a consultarse. */
function errorDeFechas(modo, estado, datos) {
  const fechas = modo === "hora" ? [estado.dia] : [estado.inicio, estado.fin];
  if (fechas.some((fecha) => !fecha)) {
    return modo === "hora" ? "Elige una fecha." : "Elige las dos fechas del periodo.";
  }

  if (modo === "dia") {
    const error = errorRango(estado.inicio, estado.fin, hoyCR());
    if (error) return error;
  } else if (diasEntre(estado.dia, hoyCR()) < 0) {
    return "No puedes consultar fechas futuras.";
  }

  // Cada fuente tiene además su propio periodo con datos.
  if (datos.periodo) {
    const { inicio, fin } = datos.periodo;
    if (fechas.some((fecha) => fecha < inicio || fecha > fin)) {
      const desde = formatearFecha(inicio);
      const hasta = formatearFecha(fin);
      return datos.tipo === "clima"
        ? `Open-Meteo cubre del ${desde} al ${hasta}. Elige fechas dentro de ese periodo.`
        : `El ESP32 solo tiene lecturas del ${desde} al ${hasta}. Elige fechas dentro de ese periodo.`;
    }
  }
  return null;
}

// --- Cálculo de resultados ------------------------------------------------------------

/**
 * Convierte los promedios de cada variable en series para la gráfica.
 * Los índices son posiciones consecutivas —la hora del día, o el número de día dentro del
 * rango—, de modo que un periodo sin lecturas deje un hueco en lugar de una línea recta.
 */
function construirSeries(modo, estado, filtrados) {
  if (modo === "hora") {
    const indices = Array.from({ length: 24 }, (_, hora) => hora);
    const etiquetas = indices.map((hora) => `${String(hora).padStart(2, "0")}:00`);
    const series = estado.elegidas.map((clave) => ({
      variable: variablePorClave(clave),
      puntos: promediosPorHora(filtrados[clave].registros, estado.consulta.dia, clave)
        .map((r) => ({ indice: r.hora, valor: r.promedio, cantidad: r.cantidad }))
    }));
    return { indices, etiquetas, series };
  }

  const fechas = fechasDelRango(estado.consulta.inicio, estado.consulta.fin);
  const indices = fechas.map((_, i) => i);
  const etiquetas = fechas.map(formatearFechaCorta);
  const series = estado.elegidas.map((clave) => ({
    variable: variablePorClave(clave),
    puntos: promediosDiarios(
      filtrados[clave].registros,
      { inicio: estado.consulta.inicio, fin: estado.consulta.fin },
      clave
    )
      .map((r, i) => ({ indice: i, valor: r.promedio, cantidad: r.cantidad }))
      .filter((p) => p.valor !== null)
  }));
  return { indices, etiquetas, series, fechas };
}

/**
 * Resultados completos por variable, tal como van al CSV y al PDF.
 *
 * A diferencia de las series de la gráfica, aquí se conservan los periodos vacíos: el
 * archivo exportado debe mostrar los huecos del periodo, no esconderlos.
 */
function resultadosPorVariable(modo, estado, filtrados) {
  const resultados = {};
  for (const clave of estado.elegidas) {
    resultados[clave] = modo === "hora"
      ? promediosPorHora(filtrados[clave].registros, estado.consulta.dia, clave)
      : promediosDiarios(
          filtrados[clave].registros,
          { inicio: estado.consulta.inicio, fin: estado.consulta.fin },
          clave
        );
  }
  return resultados;
}

/** Valores del periodo seleccionado, una entrada por variable. */
function valoresDelPeriodo(series, indice) {
  const valores = {};
  for (const serie of series) {
    const punto = serie.puntos.find((p) => p.indice === indice);
    valores[serie.variable.clave] = punto
      ? { promedio: punto.valor, cantidad: punto.cantidad }
      : { promedio: null, cantidad: 0 };
  }
  return valores;
}

// --- Pintado ----------------------------------------------------------------------------

function controles(modo, estado, datos) {
  const config = MODOS[modo];
  const maxima = fechaMaxima(datos);
  const minima = fechaMinima(datos);
  const limites = `max="${maxima}" ${minima ? `min="${minima}"` : ""}`;

  const campos = modo === "hora"
    ? `<label class="campo">
         <span class="campo__etiqueta">Fecha</span>
         <input type="date" data-fecha="dia" value="${esc(estado.dia)}" ${limites} required>
       </label>`
    : `<label class="campo">
         <span class="campo__etiqueta">Desde</span>
         <input type="date" data-fecha="inicio" value="${esc(estado.inicio)}" ${limites} required>
       </label>
       <label class="campo">
         <span class="campo__etiqueta">Hasta</span>
         <input type="date" data-fecha="fin" value="${esc(estado.fin)}" ${limites} required>
       </label>`;

  const errorFechas = errorDeFechas(modo, estado, datos);
  const filtroValido = comparacionValida(estado.elegidas, estado.filtro);
  const puedeConsultar = !errorFechas && filtroValido && !estado.cargando;

  return `
    <section class="tarjeta pila">
      <h2 class="tarjeta__titulo">${esc(config.titulo)}</h2>
      ${nota(config.descripcion)}
      <div class="fila-campos">${campos}</div>
      ${errorFechas ? aviso(errorFechas, "error") : nota(pistaPeriodo(datos))}
      ${selectorVariables(estado.elegidas, estado.oscuro)}
      ${selectorEstadisticas(estado.estadisticas)}
      ${controlFiltro(estado.filtro, estado.elegidas, estado.claveFiltro)}
      <div class="grupo-botones">
        <button type="button" class="boton" data-consultar ${puedeConsultar ? "" : "disabled"}>
          ${icono("hora", 18)} ${estado.cargando ? "Consultando…" : "Consultar"}
        </button>
      </div>
    </section>`;
}

function resultados(modo, estado, datos, oscuro) {
  if (estado.error) return `<section class="tarjeta">${aviso(estado.error, "error")}</section>`;
  if (!estado.consulta) {
    return `<section class="tarjeta">${aviso("Elige las fechas y las variables, y pulsa Consultar.")}</section>`;
  }

  const config = MODOS[modo];
  const filtrados = datosComparacion(estado.registros, estado.elegidas, estado.filtro);
  const { indices, etiquetas, series, fechas } = construirSeries(modo, estado, filtrados);
  const hayDatos = series.some((s) => s.puntos.length > 0);

  if (!hayDatos) {
    // Un resultado vacío tiene dos causas distintas y conviene separarlas: puede que no
    // haya lecturas en ese periodo, o que el filtro las haya excluido todas.
    const excluidas = estado.elegidas.reduce((total, clave) => total + filtrados[clave].excluidos, 0);
    const motivo = excluidas > 0
      ? `El filtro excluyó las ${excluidas} lecturas del periodo. Revisa sus límites o desactívalo.`
      : datos.tipo === "esp32"
        ? "No hay lecturas del ESP32 en ese periodo. El dispositivo solo envía mientras la simulación de Wokwi está corriendo, así que hay días sin datos."
        : "No hay lecturas para el periodo y las variables seleccionadas.";

    return `
      <section class="tarjeta pila">
        <h2 class="tarjeta__titulo">Resultados</h2>
        ${aviso(motivo)}
        ${datos.periodo && excluidas === 0 ? nota(pistaPeriodo(datos)) : ""}
        ${resumenFiltrado(estado.filtro, filtrados, estado.elegidas)}
      </section>`;
  }

  // Si el periodo elegido se quedó sin datos, se mueve al primero que sí tenga.
  const disponibles = series.flatMap((s) => s.puntos.map((p) => p.indice));
  const seleccion = disponibles.includes(estado.seleccion)
    ? estado.seleccion
    : Math.min(...disponibles);
  estado.seleccion = seleccion;

  const opciones = indices.map((indice) => {
    const nombre = modo === "hora" ? config.nombrePeriodo(indice) : formatearFecha(fechas[indice]);
    const vacio = disponibles.includes(indice) ? "" : " · sin datos";
    return `<option value="${indice}" ${indice === seleccion ? "selected" : ""}>${esc(nombre + vacio)}</option>`;
  }).join("");

  const unPeriodo = new Set(disponibles).size === 1;

  // Resumen hablado para lectores de pantalla: al repintar, la gráfica y las tarjetas
  // cambian sin que nada lo anuncie.
  const anuncio = `Consulta lista. ${series.map((s) =>
    `${s.variable.nombre}: ${s.puntos.length} periodos con datos`).join(". ")}.`;

  return `
    <section class="tarjeta pila">
      <h2 class="tarjeta__titulo">Resultados</h2>
      <p class="solo-lectores" role="status">${esc(anuncio)}</p>
      <div class="leyenda">${leyendaHtml(series, oscuro)}</div>
      <div class="grafica"><canvas data-grafica></canvas></div>
      ${nota(unPeriodo ? "Un periodo con datos disponibles." : "Toca la gráfica o usa el menú para consultar un periodo.")}
      <label class="campo">
        <span class="campo__etiqueta">${esc(config.etiquetaPeriodo)}</span>
        <select data-periodo>${opciones}</select>
      </label>
      ${resumenSeries(estado.elegidas, valoresDelPeriodo(series, seleccion), oscuro)}
      ${panelResumenEstadistico(
          estado.elegidas,
          estado.estadisticas,
          Object.fromEntries(estado.elegidas.map((clave) =>
            [clave, resumenEstadistico(filtrados[clave].registros, clave)])),
          oscuro
        )}
      ${resumenFiltrado(estado.filtro, filtrados, estado.elegidas)}
      <div class="grupo-botones">
        <button type="button" class="boton boton--suave" data-exportar="csv">
          ${icono("descarga", 18)} Exportar CSV
        </button>
        <button type="button" class="boton boton--contorno" data-exportar="pdf">
          ${icono("descarga", 18)} Exportar PDF
        </button>
      </div>
      ${nota("El CSV conserva el periodo completo, con las franjas sin lecturas marcadas como «Sin datos».")}
      ${datos.tipo === "clima" ? nota("Datos de Open-Meteo · estimaciones de un modelo, bajo licencia CC BY 4.0.") : ""}
    </section>`;
}

export function render(modo, datos, oscuro) {
  const estado = estados[modo];
  estado.oscuro = oscuro;
  ajustarADatos(modo, datos);
  return `<div class="columnas">
    ${controles(modo, estado, datos)}
    ${resultados(modo, estado, datos, oscuro)}
  </div>`;
}

// --- Eventos -------------------------------------------------------------------------------

async function consultar(modo, datos, volverAPintar) {
  const estado = estados[modo];
  if (errorDeFechas(modo, estado, datos)) return;

  estado.cargando = true;
  estado.error = "";
  volverAPintar();

  try {
    if (datos.tipo === "clima") {
      // Si el periodo elegido no está en el historial que hay en memoria, se descarga.
      const rango = modo === "hora"
        ? { inicio: estado.dia, fin: estado.dia }
        : { inicio: estado.inicio, fin: estado.fin };
      const historial = await datos.asegurar(rango);
      estado.registros = historial?.registros ?? [];
    } else if (modo === "hora") {
      estado.registros = (await leerDia(estado.dia)).registros;
    } else {
      estado.registros = (await leerRangoFechas(estado.inicio, estado.fin)).registros;
    }
    estado.consulta = modo === "hora"
      ? { dia: estado.dia }
      : { inicio: estado.inicio, fin: estado.fin };
    estado.seleccion = modo === "hora" ? 0 : 0;
  } catch (error) {
    estado.error = error.message;
    estado.consulta = null;
    estado.registros = [];
  } finally {
    estado.cargando = false;
    volverAPintar();
  }
}

/**
 * Genera el archivo con los resultados que hay en pantalla en el momento de pulsar.
 * El contenido queda fijado aquí: si entraran lecturas nuevas mientras se guarda, el
 * archivo seguiría correspondiendo a lo que el usuario vio.
 */
function exportar(formato, modo, estado, datos, lienzo) {
  const config = MODOS[modo];
  const filtrados = datosComparacion(estado.registros, estado.elegidas, estado.filtro);
  const resultados = resultadosPorVariable(modo, estado, filtrados);
  const origen = datos.tipo === "clima" ? datos.origen : null;

  const periodo = modo === "hora"
    ? estado.consulta.dia
    : `${estado.consulta.inicio}_${estado.consulta.fin}`;

  if (formato === "csv") {
    const documentos = estado.elegidas.map((clave) => {
      const variable = variablePorClave(clave);
      const limites = limitesComparacion(estado.filtro, clave);
      return modo === "hora"
        ? csvPorHora(estado.consulta.dia, variable, resultados[clave], limites, origen)
        : csvPorDia(variable, resultados[clave], limites, origen);
    });
    descargarCsv(
      nombreArchivo([config.titulo, datos.tipo, periodo], "csv"),
      combinarCsvSeries(documentos)
    );
    return;
  }

  // Tabla del PDF: las mismas filas del CSV, sin las columnas de procedencia, que van
  // como texto en la cabecera del informe para no repetirlas en cada línea.
  const filas = [];
  for (const clave of estado.elegidas) {
    const variable = variablePorClave(clave);
    if (modo === "hora") {
      const porHora = new Map(resultados[clave].map((r) => [r.hora, r]));
      for (let hora = 0; hora < 24; hora++) {
        const r = porHora.get(hora);
        filas.push([
          franjaHoraria(hora), variable.nombre, variable.unidad,
          r ? numeroCsv(r.promedio) : "", String(r?.cantidad ?? 0), r ? "Con datos" : "Sin datos"
        ]);
      }
    } else {
      for (const r of resultados[clave]) {
        filas.push([
          r.fecha, variable.nombre, variable.unidad,
          r.promedio === null ? "" : numeroCsv(r.promedio),
          String(r.cantidad), r.promedio === null ? "Sin datos" : "Con datos"
        ]);
      }
    }
  }

  // Resumen del periodo: lo mismo que el panel de la pantalla, respetando las
  // estadísticas que el usuario dejó marcadas.
  const columnasResumen = estadisticas.filter((e) => estado.estadisticas.includes(e.clave));
  const filasResumen = estado.elegidas.map((clave) => {
    const variable = variablePorClave(clave);
    const resumen = resumenEstadistico(filtrados[clave].registros, clave);
    if (!resumen) {
      return [variable.nombre, variable.unidad, ...columnasResumen.map(() => "Sin lecturas"), "0"];
    }
    const celdas = columnasResumen.map((columna) => {
      if (columna.clave === "promedio") return numeroCsv(resumen.promedio);
      const extremo = resumen[columna.clave];
      // Valor y fecha en la misma celda: con una columna por cada uno, la tabla no
      // cabría a lo ancho de la página.
      return `${numeroCsv(extremo.valor)}\n${fechaHoraCsv(extremo.ts)}`;
    });
    return [variable.nombre, variable.unidad, ...celdas, String(resumen.cantidad)];
  });

  const tablaResumen = columnasResumen.length === 0 ? null : {
    titulo: "Resumen del periodo",
    encabezados: ["Variable", "Unidad", ...columnasResumen.map((c) => c.nombre), "Lecturas"],
    filas: filasResumen,
    nota: "El máximo y el mínimo son lecturas individuales con su fecha, no promedios: " +
          "pueden no coincidir con el valor más alto de la tabla de detalle, que recoge medias."
  };

  const subtitulos = [
    modo === "hora"
      ? `Día consultado: ${estado.consulta.dia}`
      : `Periodo: ${estado.consulta.inicio} a ${estado.consulta.fin} (ambos incluidos)`,
    `Variables: ${estado.elegidas.map((c) => variablePorClave(c).nombre).join(", ")}`,
    "Fechas interpretadas en America/Costa_Rica. Los periodos sin lecturas se indican como «Sin datos».",
    estado.filtro.activo
      ? `Filtro activo. ${estado.elegidas.map((clave) => {
          const l = limitesComparacion(estado.filtro, clave);
          return `${variablePorClave(clave).nombre}: ${numeroCsv(l.minimo)} a ${numeroCsv(l.maximo)}`;
        }).join(" · ")}`
      : "Sin filtro de valores atípicos.",
    origen
      ? `Fuente: ${origen.fuente}. ${origen.ubicacion} (${origen.latitud}, ${origen.longitud}). Licencia CC BY 4.0.`
      : "Fuente: lecturas del ESP32 con DHT22 simulado, almacenadas en Firebase."
  ];

  exportarPdf({
    titulo: config.titulo,
    subtitulos,
    tablaResumen,
    encabezados: [config.etiquetaPeriodo, "Variable", "Unidad", "Promedio", "Lecturas", "Estado"],
    filas,
    lienzo,
    nombre: nombreArchivo([config.titulo, datos.tipo, periodo], "pdf")
  });
}

export function conectar(raiz, modo, datos, oscuro, volverAPintar) {
  const estado = estados[modo];

  raiz.querySelectorAll("[data-fecha]").forEach((campo) => {
    campo.addEventListener("change", () => {
      estado[campo.dataset.fecha] = campo.value;
      // Cambiar las fechas descarta la consulta anterior: sus resultados ya no
      // corresponden a lo que muestran los controles.
      estado.consulta = null;
      estado.registros = [];
      estado.error = "";
      destruirGrafica();
      volverAPintar();
    });
  });

  conectarSelectorVariables(raiz, {
    elegidas: estado.elegidas,
    alCambiar: (elegidas) => {
      estado.elegidas = elegidas;
      if (!elegidas.includes(estado.claveFiltro)) estado.claveFiltro = elegidas[0];
      destruirGrafica();
      volverAPintar();
    }
  });

  conectarSelectorEstadisticas(raiz, {
    elegidas: estado.estadisticas,
    alCambiar: (elegidas) => {
      estado.estadisticas = elegidas;
      destruirGrafica();
      volverAPintar();
    }
  });

  conectarControlFiltro(raiz, {
    filtro: estado.filtro,
    claveEditada: estados[modo].elegidas.includes(estado.claveFiltro)
      ? estado.claveFiltro : estado.elegidas[0],
    alCambiar: (filtro) => {
      estado.filtro = filtro;
      destruirGrafica();
      volverAPintar();
    },
    alEditarVariable: (clave) => {
      estado.claveFiltro = clave;
      volverAPintar();
    }
  });

  raiz.querySelector("[data-consultar]")?.addEventListener("click", () => {
    consultar(modo, datos, volverAPintar);
  });

  raiz.querySelector("[data-periodo]")?.addEventListener("change", (evento) => {
    estado.seleccion = Number(evento.target.value);
    volverAPintar();
  });

  raiz.querySelectorAll("[data-exportar]").forEach((boton) => {
    boton.addEventListener("click", () => {
      try {
        exportar(boton.dataset.exportar, modo, estado, datos, raiz.querySelector("[data-grafica]"));
      } catch (error) {
        estado.error = `No se pudo exportar: ${error.message}`;
        volverAPintar();
      }
    });
  });

  // La gráfica se dibuja después de que el HTML esté en el documento.
  const lienzo = raiz.querySelector("[data-grafica]");
  if (!lienzo || !estado.consulta) return;

  const filtrados = datosComparacion(estado.registros, estado.elegidas, estado.filtro);
  const { indices, etiquetas, series } = construirSeries(modo, estado, filtrados);
  dibujarComparacion(lienzo, {
    series, indices, etiquetas,
    elegido: estado.seleccion,
    oscuro,
    tipo: MODOS[modo].tipoGrafica,
    alElegir: (indice) => {
      estado.seleccion = indice;
      volverAPintar();
    }
  });
}
