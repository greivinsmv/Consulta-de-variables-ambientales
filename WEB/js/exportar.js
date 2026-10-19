/**
 * Exportación de resultados a CSV y PDF.
 *
 * El CSV reproduce byte a byte el formato de ExportacionCsv.kt para que los archivos de
 * la web y los del móvil sean intercambiables: UTF-8 con BOM, separador `;`, coma decimal
 * con tres decimales, todas las celdas entrecomilladas y saltos CRLF.
 *
 * El BOM y el punto y coma son lo que hace que Excel en español abra el archivo en
 * columnas sin pedir nada. Los periodos sin lecturas se exportan igual, con el promedio
 * vacío, cantidad cero y estado "Sin datos": omitirlos escondería los huecos del periodo.
 */

import { ZONA_CONSULTA, variablePorClave } from "./modelo.js";

/** Tres decimales con coma, como `String.format(Locale.ROOT, "%.3f")` seguido de replace. */
export const numeroCsv = (valor) => valor.toFixed(3).replace(".", ",");

const celda = (texto) => `"${String(texto).replace(/"/g, '""')}"`;

/**
 * Arma el documento a partir de las filas, añadiendo las columnas del filtro y de la
 * procedencia cuando corresponde. La primera fila son los encabezados.
 */
export function documentoCsv(filas, filtro = null, origen = null) {
  let completas = filas;

  if (filtro) {
    completas = completas.map((fila, indice) => fila.concat(
      indice === 0
        ? ["filtro", "limite_inferior", "limite_superior"]
        : ["Límites configurados", numeroCsv(filtro.minimo), numeroCsv(filtro.maximo)]
    ));
  }

  if (origen) {
    completas = completas.map((fila, indice) => fila.concat(
      indice === 0
        ? ["fuente", "ubicacion", "latitud_solicitada", "longitud_solicitada", "proveedor", "licencia", "tratamiento"]
        : [origen.fuente, origen.ubicacion, numeroCsv(origen.latitud), numeroCsv(origen.longitud),
           origen.proveedor, origen.licencia, "Resumen calculado por Consulta Ambiental"]
    ));
  }

  return "﻿" + completas.map((fila) => fila.map(celda).join(";")).join("\r\n") + "\r\n";
}

const dosDigitos = (n) => String(n).padStart(2, "0");

/** Las 24 franjas del día, incluidas las que no tienen lecturas. */
export function csvPorHora(fechaISO, variable, resultados, filtro = null, origen = null) {
  const porHora = new Map(resultados.map((r) => [r.hora, r]));
  const filas = [
    ["fecha", "hora_inicio", "hora_fin", "zona_horaria", "variable", "unidad", "promedio", "lecturas", "estado"]
  ];
  for (let hora = 0; hora < 24; hora++) {
    const resultado = porHora.get(hora);
    filas.push([
      fechaISO, `${dosDigitos(hora)}:00`, `${dosDigitos(hora)}:59`, ZONA_CONSULTA,
      variable.nombre, variable.unidad,
      resultado ? numeroCsv(resultado.promedio) : "",
      String(resultado?.cantidad ?? 0),
      resultado ? "Con datos" : "Sin datos"
    ]);
  }
  return documentoCsv(filas, filtro, origen);
}

/** Una fila por día del rango, conservando las fechas sin lecturas. */
export function csvPorDia(variable, resultados, filtro = null, origen = null) {
  const filas = [["fecha", "zona_horaria", "variable", "unidad", "promedio", "lecturas", "estado"]];
  for (const resultado of resultados) {
    filas.push([
      resultado.fecha, ZONA_CONSULTA, variable.nombre, variable.unidad,
      resultado.promedio === null ? "" : numeroCsv(resultado.promedio),
      String(resultado.cantidad),
      resultado.promedio === null ? "Sin datos" : "Con datos"
    ]);
  }
  return documentoCsv(filas, filtro, origen);
}

/** Fecha completa en hora de Costa Rica, con el formato que usa el CSV del móvil. */
export function fechaHoraCsv(ts) {
  const partes = {};
  const formateador = new Intl.DateTimeFormat("en-CA", {
    timeZone: ZONA_CONSULTA,
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23"
  });
  for (const { type, value } of formateador.formatToParts(new Date(ts))) partes[type] = value;
  const hora = String(Number(partes.hour) % 24).padStart(2, "0");
  return `${partes.year}-${partes.month}-${partes.day} ${hora}:${partes.minute}:${partes.second}`;
}

/** Mínimo y máximo de la variable elegida. */
export function csvExtremos(variable, resumen, filtro = null, origen = null) {
  const fila = (tipo, extremo) => [
    tipo, variable.nombre, variable.unidad, numeroCsv(extremo.valor),
    fechaHoraCsv(extremo.ts), String(extremo.ts), ZONA_CONSULTA,
    String(extremo.repeticiones), String(resumen.cantidad)
  ];
  return documentoCsv([
    ["extremo", "variable", "unidad", "valor", "fecha_primera_ocurrencia", "ts", "zona_horaria", "repeticiones", "lecturas_validas"],
    fila("Mínimo", resumen.minimo),
    fila("Máximo", resumen.maximo)
  ], filtro, origen);
}

/**
 * Une varios documentos en uno solo, conservando un único encabezado.
 * Cada fila sigue identificando su variable y unidad, así que las series no se confunden.
 */
export function combinarCsvSeries(documentos) {
  if (documentos.length === 0) throw new Error("No hay documentos que combinar.");
  const porDocumento = documentos.map((doc) =>
    doc.replace(/^﻿/, "").replace(/[\r\n]+$/, "").split("\r\n"));

  const encabezado = porDocumento[0][0];
  if (!porDocumento.every((filas) => filas[0] === encabezado)) {
    throw new Error("Los documentos no comparten el mismo encabezado.");
  }
  const cuerpo = porDocumento.flatMap((filas) => filas.slice(1));
  return "﻿" + [encabezado, ...cuerpo].join("\r\n") + "\r\n";
}

// --- Descarga ----------------------------------------------------------------------

/**
 * Entrega el archivo al navegador. La URL temporal se libera después: dejarla viva
 * retiene el contenido en memoria hasta recargar la página.
 */
export function descargarArchivo(nombre, contenido, tipo) {
  const blob = contenido instanceof Blob ? contenido : new Blob([contenido], { type: tipo });
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombre;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export const descargarCsv = (nombre, contenido) =>
  descargarArchivo(nombre, contenido, "text/csv;charset=utf-8");

/** Nombre de archivo sin acentos ni caracteres que molesten en otros sistemas. */
export const nombreArchivo = (partes, extension) =>
  partes.filter(Boolean).join("-")
    .normalize("NFD").replace(/[̀-ͯ]/g, "")
    .replace(/[^a-zA-Z0-9_-]+/g, "-").replace(/-+/g, "-").toLowerCase() + "." + extension;

// --- PDF ---------------------------------------------------------------------------

/**
 * Informe en PDF con los mismos datos del CSV, más la gráfica tal como se ve en pantalla.
 *
 * jsPDF y su complemento de tablas se cargan por CDN; si no están disponibles se avisa en
 * vez de fallar en silencio. Las fuentes estándar del PDF cubren los acentos y el símbolo
 * de grado, así que no hace falta incrustar una fuente propia.
 */
export function exportarPdf({
  titulo, subtitulos, encabezados, filas, lienzo, nombre, tablaResumen = null
}) {
  const constructor = window.jspdf?.jsPDF;
  if (!constructor) throw new Error("No se pudo cargar el generador de PDF. Revisa la conexión.");

  const doc = new constructor({ orientation: "portrait", unit: "mm", format: "a4" });
  const margen = 14;
  const ancho = doc.internal.pageSize.getWidth();
  let y = margen + 4;

  doc.setFont("helvetica", "bold");
  doc.setFontSize(16);
  doc.setTextColor(60, 42, 110);
  doc.text(titulo, margen, y);
  y += 7;

  doc.setFont("helvetica", "normal");
  doc.setFontSize(9.5);
  doc.setTextColor(90, 85, 105);
  for (const linea of subtitulos) {
    for (const trozo of doc.splitTextToSize(linea, ancho - margen * 2)) {
      doc.text(trozo, margen, y);
      y += 4.6;
    }
  }
  y += 3;

  if (lienzo) {
    try {
      // Se dibuja sobre blanco: el PNG del canvas es transparente y en el PDF se vería
      // un gráfico sin fondo, con los ejes claros del tema oscuro casi invisibles.
      const plano = document.createElement("canvas");
      plano.width = lienzo.width;
      plano.height = lienzo.height;
      const ctx = plano.getContext("2d");
      ctx.fillStyle = "#FFFFFF";
      ctx.fillRect(0, 0, plano.width, plano.height);
      ctx.drawImage(lienzo, 0, 0);

      const anchoImagen = ancho - margen * 2;
      const altoImagen = (plano.height / plano.width) * anchoImagen;
      // JPEG en vez de PNG: sobre fondo blanco se ve igual y deja el informe en una
      // décima parte del tamaño, que importa al adjuntarlo a la entrega.
      doc.addImage(plano.toDataURL("image/jpeg", 0.92), "JPEG", margen, y, anchoImagen, altoImagen);
      y += altoImagen + 6;
    } catch {
      // Un fallo al rasterizar la gráfica no debe impedir que salga la tabla.
    }
  }

  const estiloTabla = {
    margin: { left: margen, right: margen },
    styles: { fontSize: 8, cellPadding: 1.8, textColor: [48, 37, 71] },
    headStyles: { fillColor: [112, 85, 197], textColor: 255, fontStyle: "bold" },
    alternateRowStyles: { fillColor: [243, 240, 252] }
  };

  /** Encabezado de sección dentro del informe. */
  const seccion = (texto, desde) => {
    doc.setFont("helvetica", "bold");
    doc.setFontSize(10.5);
    doc.setTextColor(60, 42, 110);
    doc.text(texto, margen, desde);
    return desde + 4;
  };

  if (typeof doc.autoTable === "function") {
    // El resumen va antes del detalle: es la lectura rápida del informe, y quien solo
    // mire la primera página debe encontrar ahí el máximo, el mínimo y el promedio.
    if (tablaResumen && tablaResumen.filas.length > 0) {
      y = seccion(tablaResumen.titulo, y);
      doc.autoTable({ head: [tablaResumen.encabezados], body: tablaResumen.filas, startY: y, ...estiloTabla });
      y = doc.lastAutoTable.finalY + 9;
      if (tablaResumen.nota) {
        doc.setFont("helvetica", "normal");
        doc.setFontSize(8);
        doc.setTextColor(90, 85, 105);
        for (const trozo of doc.splitTextToSize(tablaResumen.nota, ancho - margen * 2)) {
          doc.text(trozo, margen, y);
          y += 4;
        }
        y += 5;
      }
      y = seccion("Detalle por periodo", y);
    }

    doc.autoTable({ head: [encabezados], body: filas, startY: y, ...estiloTabla });
  } else {
    doc.setFontSize(9);
    doc.text("No se pudo generar la tabla: falta el complemento de tablas.", margen, y);
  }

  const paginas = doc.internal.getNumberOfPages();
  for (let pagina = 1; pagina <= paginas; pagina++) {
    doc.setPage(pagina);
    doc.setFontSize(8);
    doc.setTextColor(130, 125, 145);
    doc.text(
      `Consulta Ambiental · IC-3101 · página ${pagina} de ${paginas}`,
      margen, doc.internal.pageSize.getHeight() - 8
    );
  }

  doc.save(nombre);
}

/** Filas de la tabla del PDF para una consulta por periodos. */
export function filasPdfPeriodos(modo, elegidas, resultadosPorVariable, etiquetaDe) {
  const filas = [];
  for (const clave of elegidas) {
    const variable = variablePorClave(clave);
    for (const resultado of resultadosPorVariable[clave]) {
      const valor = modo === "hora" ? resultado.promedio : resultado.promedio;
      filas.push([
        etiquetaDe(resultado),
        variable.nombre,
        variable.unidad,
        valor === null || valor === undefined ? "" : numeroCsv(valor),
        String(resultado.cantidad),
        valor === null || valor === undefined ? "Sin datos" : "Con datos"
      ]);
    }
  }
  return filas;
}
