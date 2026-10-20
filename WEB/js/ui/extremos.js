/**
 * Consulta de máximos y mínimos históricos.
 *
 * Analiza una variable sobre todo el historial de la fuente elegida. Trabaja con lecturas
 * individuales, no con promedios diarios: el máximo histórico es el valor más alto que se
 * registró, no el del día con la media más alta.
 *
 * Descarga el historial completo, que es lo razonable para el volumen de este proyecto.
 * Un conjunto mucho mayor pediría agregación o paginación, sin cambiar el significado del
 * resultado.
 */

import {
  variablePorClave, formatearValor, formatearFechaHora, formatearFecha
} from "../modelo.js";
import { extremosHistoricos, distribucion } from "../consultas.js";
import {
  filtrarLecturas, limitesComparacion, comparacionValida, configuracionFiltroInicial
} from "../filtro.js";
import { leerHistorialCompleto } from "../firebase.js";
import {
  csvExtremos, descargarCsv, nombreArchivo, numeroCsv, fechaHoraCsv, exportarPdf
} from "../exportar.js";
import { icono } from "./iconos.js";
import {
  esc, aviso, nota, controlFiltro, conectarControlFiltro, datoSuelto
} from "./componentes.js";
import { dibujarDistribucion, destruirGrafica } from "../graficos.js";
import { variables } from "../modelo.js";

const estadoInicial = () => ({
  variable: "temperatura",
  filtro: configuracionFiltroInicial(),
  consultada: null,
  registros: [],
  descartados: 0,
  cargando: false,
  error: ""
});

let estado = estadoInicial();

export function reiniciar() {
  estado = estadoInicial();
}

function panelExtremo(etiqueta, extremo, variable, total) {
  const porcentaje = total > 0 ? Math.round((extremo.repeticiones / total) * 100) : 0;
  return `
    <div class="panel-extremo">
      <span class="panel-extremo__etiqueta">${esc(etiqueta)}</span>
      <span class="panel-extremo__valor">${esc(formatearValor(extremo.valor, variable.unidad))}</span>
      <span class="panel-extremo__fecha">${esc(formatearFechaHora(extremo.ts))}</span>
      <span class="nota">
        ${extremo.repeticiones === 1
          ? "Un único registro con este valor."
          : `Se repite ${extremo.repeticiones} veces (${porcentaje} %). Se muestra la primera.`}
      </span>
    </div>`;
}

/**
 * Periodo que se va a analizar.
 *
 * Con el clima, lo que hay en memoria es el último periodo descargado, que puede venir de
 * una consulta por hora o por día. Decirlo evita que un "máximo histórico" calculado sobre
 * un solo día se lea como si cubriera todo el historial.
 */
function periodoAnalizado(datos) {
  if (datos.tipo !== "clima") {
    return "Se analizan todas las lecturas guardadas en Firebase.";
  }
  const rango = datos.historial?.rango;
  if (!rango) return "Carga el historial de la zona para poder analizarlo.";
  const dias = datos.historial.registros.length / 24;
  return `Se analiza el periodo descargado: del ${formatearFecha(rango.inicio)} al ${formatearFecha(rango.fin)} ` +
         `(${Math.round(dias)} días, ${datos.historial.registros.length} registros).`;
}

function controles(datos) {
  const filtroValido = comparacionValida([estado.variable], estado.filtro);

  return `
    <section class="tarjeta pila">
      <h2 class="tarjeta__titulo">Máximos y mínimos</h2>
      ${nota(periodoAnalizado(datos))}
      ${datos.tipo === "clima"
        ? nota("Para analizar otro periodo, descárgalo desde Inicio o haz una consulta por día de ese rango.")
        : ""}
      <label class="campo">
        <span class="campo__etiqueta">Variable</span>
        <select data-variable-select>
          ${variables.map((v) => `
            <option value="${v.clave}" ${v.clave === estado.variable ? "selected" : ""}>
              ${esc(v.nombre)} (${esc(v.unidad)})
            </option>`).join("")}
        </select>
      </label>
      ${controlFiltro(estado.filtro, [estado.variable], estado.variable)}
      <div class="grupo-botones">
        <button type="button" class="boton" data-consultar
                ${filtroValido && !estado.cargando ? "" : "disabled"}>
          ${icono("historico", 18)}
          ${estado.cargando ? "Consultando…" : "Consultar máximos y mínimos"}
        </button>
      </div>
    </section>`;
}

function resultados(datos) {
  if (estado.error) return `<section class="tarjeta">${aviso(estado.error, "error")}</section>`;
  if (!estado.consultada) {
    return `<section class="tarjeta">${aviso("Elige una variable y pulsa Consultar.")}</section>`;
  }
  if (!comparacionValida([estado.consultada], estado.filtro)) {
    return `<section class="tarjeta">${aviso("Completa los límites del filtro para ver o exportar los resultados.")}</section>`;
  }

  const variable = variablePorClave(estado.consultada);
  const limites = limitesComparacion(estado.filtro, estado.consultada);
  const filtrados = filtrarLecturas(estado.registros, estado.consultada, limites);
  const resumen = extremosHistoricos(filtrados.registros, estado.consultada);

  if (!resumen) {
    return `
      <section class="tarjeta pila">
        <h2 class="tarjeta__titulo">${esc(variable.nombre)}</h2>
        ${aviso("No hay lecturas válidas de esta variable en el historial.")}
        ${estado.filtro.activo
          ? nota(`${filtrados.excluidos} lecturas quedaron fuera de los límites del filtro.`)
          : ""}
      </section>`;
  }

  return `
    <section class="tarjeta pila">
      <h2 class="tarjeta__titulo">${esc(variable.nombre)}</h2>
      <p class="solo-lectores" role="status">
        ${esc(`Mínimo ${formatearValor(resumen.minimo.valor, variable.unidad)}, máximo ` +
              `${formatearValor(resumen.maximo.valor, variable.unidad)}, sobre ${resumen.cantidad} lecturas válidas.`)}
      </p>
      <div class="rejilla-mediciones">
        ${panelExtremo("Mínimo", resumen.minimo, variable, resumen.cantidad)}
        ${panelExtremo("Máximo", resumen.maximo, variable, resumen.cantidad)}
      </div>
      <div class="pila">
        <span class="campo__etiqueta">Distribución de las lecturas</span>
        <div class="grafica"><canvas data-distribucion></canvas></div>
        ${nota("Cada barra agrupa un tramo de valores. Sirve para ver si los extremos son casos aislados o si hay muchas lecturas cerca de ellos.")}
      </div>
      <div class="datos-sueltos">
        ${datoSuelto("Amplitud", formatearValor(resumen.amplitud, variable.unidad))}
        ${datoSuelto("Lecturas válidas", String(resumen.cantidad))}
        ${estado.filtro.activo ? datoSuelto("Excluidas por el filtro", String(filtrados.excluidos)) : ""}
        ${filtrados.sinValor > 0 ? datoSuelto("Sin valor", String(filtrados.sinValor)) : ""}
      </div>
      ${estado.descartados > 0
        ? aviso(`${estado.descartados} registros se omitieron por tener una fecha inválida.`)
        : ""}
      <div class="grupo-botones">
        <button type="button" class="boton boton--suave" data-exportar="csv">
          ${icono("descarga", 18)} Exportar CSV
        </button>
        <button type="button" class="boton boton--contorno" data-exportar="pdf">
          ${icono("descarga", 18)} Exportar PDF
        </button>
      </div>
      ${datos.tipo === "clima"
        ? nota("Datos de Open-Meteo · estimaciones de un modelo, bajo licencia CC BY 4.0.")
        : nota("Lecturas del DHT22 simulado en Wokwi; incluyen los valores extremos enviados a propósito para probar la aplicación.")}
    </section>`;
}

export function render(datos, oscuro) {
  estado.oscuro = oscuro;
  return `<div class="columnas">${controles(datos)}${resultados(datos)}</div>`;
}

async function consultar(datos, volverAPintar) {
  if (estado.cargando || !comparacionValida([estado.variable], estado.filtro)) return;
  const consulta = estado;
  const variable = consulta.variable;
  consulta.cargando = true;
  consulta.error = "";
  volverAPintar();

  try {
    const resultado = datos.tipo === "clima"
      ? { registros: datos.historial?.registros ?? [], descartados: 0 }
      : await leerHistorialCompleto();
    // reiniciar() reemplaza el estado al cambiar de fuente o zona.
    if (consulta !== estado) return;
    consulta.registros = resultado.registros;
    consulta.descartados = resultado.descartados;
    consulta.consultada = variable;
  } catch (error) {
    if (consulta !== estado) return;
    consulta.error = error.message;
    consulta.consultada = null;
    consulta.registros = [];
  } finally {
    if (consulta === estado) {
      consulta.cargando = false;
      volverAPintar();
    }
  }
}

/** Exporta el mínimo y el máximo de la variable consultada. */
function exportar(formato, datos) {
  if (!estado.consultada || !comparacionValida([estado.consultada], estado.filtro)) {
    throw new Error("Completa los límites del filtro antes de exportar.");
  }
  const variable = variablePorClave(estado.consultada);
  const limites = limitesComparacion(estado.filtro, estado.consultada);
  const filtrados = filtrarLecturas(estado.registros, estado.consultada, limites);
  const resumen = extremosHistoricos(filtrados.registros, estado.consultada);
  if (!resumen) throw new Error("No hay valores válidos que exportar.");

  const origen = datos.tipo === "clima" ? datos.origen : null;

  if (formato === "csv") {
    descargarCsv(
      nombreArchivo(["extremos", variable.nombre, datos.tipo], "csv"),
      csvExtremos(variable, resumen, limites, origen)
    );
    return;
  }

  const fila = (tipo, extremo) => [
    tipo, numeroCsv(extremo.valor), variable.unidad,
    fechaHoraCsv(extremo.ts), String(extremo.repeticiones)
  ];

  exportarPdf({
    titulo: `Máximos y mínimos · ${variable.nombre}`,
    subtitulos: [
      // Sin el periodo, el informe no dice sobre qué se calcularon esos extremos.
      datos.historial?.rango
        ? `Periodo analizado: del ${datos.historial.rango.inicio} al ${datos.historial.rango.fin}.`
        : "Periodo analizado: todas las lecturas guardadas en Firebase.",
      `Lecturas válidas analizadas: ${resumen.cantidad}. Amplitud: ${numeroCsv(resumen.amplitud)} ${variable.unidad}.`,
      "Se calculan sobre lecturas individuales, no sobre promedios diarios. En un empate se muestra la primera fecha.",
      "Fechas interpretadas en America/Costa_Rica.",
      estado.filtro.activo
        ? `Filtro activo entre ${numeroCsv(limites.minimo)} y ${numeroCsv(limites.maximo)} ${variable.unidad}. Excluidas: ${filtrados.excluidos}.`
        : "Sin filtro de valores atípicos.",
      origen
        ? `Fuente: ${origen.fuente}. ${origen.ubicacion} (${origen.latitud}, ${origen.longitud}). Licencia CC BY 4.0.`
        : "Fuente: lecturas del ESP32 con DHT22 simulado, almacenadas en Firebase."
    ],
    encabezados: ["Extremo", "Valor", "Unidad", "Primera ocurrencia", "Repeticiones"],
    filas: [fila("Mínimo", resumen.minimo), fila("Máximo", resumen.maximo)],
    lienzo: null,
    nombre: nombreArchivo(["extremos", variable.nombre, datos.tipo], "pdf")
  });
}

export function conectar(raiz, datos, oscuro, volverAPintar) {
  raiz.querySelector("[data-variable-select]")?.addEventListener("change", (evento) => {
    estado = { ...estado, variable: evento.target.value, consultada: null,
      registros: [], error: "", cargando: false };
    destruirGrafica();
    volverAPintar();
  });

  conectarControlFiltro(raiz, {
    filtro: estado.filtro,
    claveEditada: estado.variable,
    alCambiar: (filtro) => {
      estado.filtro = filtro;
      volverAPintar();
    },
    alEditarVariable: () => {}
  });

  raiz.querySelector("[data-consultar]")?.addEventListener("click", () => {
    consultar(datos, volverAPintar);
  });

  // El histograma se dibuja una vez que el HTML ya está en el documento.
  const lienzo = raiz.querySelector("[data-distribucion]");
  if (lienzo && estado.consultada) {
    const limites = limitesComparacion(estado.filtro, estado.consultada);
    const filtrados = filtrarLecturas(estado.registros, estado.consultada, limites);
    dibujarDistribucion(lienzo, {
      tramos: distribucion(filtrados.registros, estado.consultada),
      variable: variablePorClave(estado.consultada),
      oscuro
    });
  }

  raiz.querySelectorAll("[data-exportar]").forEach((boton) => {
    boton.addEventListener("click", () => {
      try {
        exportar(boton.dataset.exportar, datos);
      } catch (error) {
        estado.error = `No se pudo exportar: ${error.message}`;
        volverAPintar();
      }
    });
  });
}
