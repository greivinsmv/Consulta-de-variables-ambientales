/**
 * Gráfica de comparación, compartida por las consultas por hora y por día.
 *
 * Reglas heredadas de GraficosAmbientales.kt:
 *  - Temperatura y punto de rocío comparten el eje en °C; la humedad usa el suyo en %.
 *    Con humedad sola, el eje izquierdo pasa a %.
 *  - Los periodos sin datos NO se unen: un hueco se ve como un hueco. Chart.js lo hace
 *    solo si el valor ausente es null y `spanGaps` queda desactivado.
 *  - El punto de rocío se dibuja con línea discontinua, igual que en el móvil.
 *  - Dos curvas que se cruzan con escalas distintas no representan valores iguales, por
 *    eso cada eje lleva su unidad rotulada.
 *
 * Chart.js se carga por CDN como global; este módulo es el único que lo conoce.
 */

import { escalasComparacion } from "./consultas.js";
import { colorVariable, colorVariableTexto } from "./filtro.js";
import { formatearNumero } from "./modelo.js";

let graficaActual = null;

/** Color legible para ejes y rejilla según el tema activo. */
const coloresTema = (oscuro) => ({
  texto: oscuro ? "#CCC0DD" : "#665C7D",
  rejilla: oscuro ? "#473D58" : "#E3DDF0"
});

export function destruirGrafica() {
  graficaActual?.destroy();
  graficaActual = null;
}

/**
 * Dibuja las series sobre el canvas indicado.
 *
 * `indices` son las posiciones posibles (las 24 horas, o los días del rango) y `etiquetas`
 * sus rótulos. Cada serie aporta solo los índices que tienen dato; el resto queda en null.
 */
export function dibujarComparacion(canvas, opciones) {
  const {
    series, indices, etiquetas, elegido = null,
    alElegir = null, oscuro = false, tipo = "linea"
  } = opciones;

  destruirGrafica();
  if (series.length === 0 || indices.length === 0) return null;

  const escalas = escalasComparacion(series);
  const unidades = Object.keys(escalas);
  if (unidades.length === 0) return null;

  // Si hay grados, mandan en el eje izquierdo; si solo hay humedad, ocupa ese eje.
  const unidadIzquierda = unidades.includes("°C") ? "°C" : "%";
  const unidadDerecha = unidades.find((u) => u !== unidadIzquierda) ?? null;
  const ejeDe = (unidad) => (unidad === unidadIzquierda ? "yIzq" : "yDer");

  const colores = coloresTema(oscuro);
  const posicionDe = new Map(indices.map((indice, i) => [indice, i]));

  const conjuntos = series.map((serie) => {
    const color = colorVariable(serie.variable.clave, oscuro);
    const datos = new Array(indices.length).fill(null);
    for (const punto of serie.puntos) {
      const posicion = posicionDe.get(punto.indice);
      if (posicion !== undefined) datos[posicion] = punto.valor;
    }
    const discontinua = serie.variable.clave === "puntoRocio";

    return {
      label: `${serie.variable.nombre} (${serie.variable.unidad})`,
      data: datos,
      yAxisID: ejeDe(serie.variable.unidad),
      unidad: serie.variable.unidad,
      borderColor: color,
      backgroundColor: tipo === "barra" ? `${color}C0` : color,
      borderWidth: tipo === "barra" ? 0 : 2.5,
      borderDash: tipo === "linea" && discontinua ? [6, 4] : undefined,
      borderRadius: tipo === "barra" ? 5 : undefined,
      pointRadius: tipo === "linea" ? 3.5 : undefined,
      pointHoverRadius: tipo === "linea" ? 6 : undefined,
      pointBackgroundColor: color,
      tension: 0,
      // Sin esto Chart.js uniría los extremos de cada hueco con una recta inventada.
      spanGaps: false
    };
  });

  const ejes = {};
  for (const unidad of unidades) {
    const id = ejeDe(unidad);
    ejes[id] = {
      type: "linear",
      position: id === "yIzq" ? "left" : "right",
      min: escalas[unidad].minimo,
      max: escalas[unidad].maximo,
      title: { display: true, text: unidad, color: colores.texto },
      ticks: { color: colores.texto, callback: (valor) => formatearNumero(valor, 1) },
      grid: { color: colores.rejilla, drawOnChartArea: id === "yIzq" }
    };
  }

  const config = {
    type: tipo === "barra" ? "bar" : "line",
    data: { labels: etiquetas, datasets: conjuntos },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      scales: {
        x: {
          ticks: { color: colores.texto, maxRotation: 0, autoSkipPadding: 18 },
          grid: { display: false }
        },
        ...ejes
      },
      plugins: {
        // La leyenda se dibuja en HTML para poder marcar el trazo discontinuo del rocío.
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (contexto) => {
              const unidad = contexto.dataset.unidad;
              if (contexto.parsed.y === null) return `${contexto.dataset.label}: Sin datos`;
              return `${contexto.dataset.label.replace(/ \(.*\)$/, "")}: ${formatearNumero(contexto.parsed.y, 1)} ${unidad}`;
            }
          }
        }
      },
      onClick: (evento, elementos, grafica) => {
        if (!alElegir) return;
        const posicion = elementos.length > 0
          ? elementos[0].index
          : Math.round(grafica.scales.x.getValueForPixel(evento.x));
        const indice = indices[Math.min(Math.max(posicion, 0), indices.length - 1)];
        if (indice !== undefined) alElegir(indice);
      }
    }
  };

  // Marca vertical del periodo seleccionado, como la línea punteada del móvil.
  const resaltado = {
    id: "resaltado",
    beforeDatasetsDraw(grafica) {
      const posicion = posicionDe.get(elegido);
      if (posicion === undefined) return;
      const x = grafica.scales.x.getPixelForValue(posicion);
      const { top, bottom } = grafica.chartArea;
      const ctx = grafica.ctx;
      ctx.save();
      ctx.strokeStyle = colores.texto;
      ctx.globalAlpha = 0.45;
      ctx.setLineDash([6, 4]);
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x, top);
      ctx.lineTo(x, bottom);
      ctx.stroke();
      ctx.restore();
    }
  };

  graficaActual = new Chart(canvas, { ...config, plugins: [resaltado] });

  canvas.setAttribute("role", "img");
  canvas.setAttribute("aria-label", descripcionAccesible(series, indices, etiquetas, unidadDerecha));
  return graficaActual;
}

/**
 * Texto equivalente de la gráfica para lectores de pantalla. Una gráfica sin descripción
 * deja fuera a quien no puede verla, y la app Android ya expone la suya.
 */
function descripcionAccesible(series, indices, etiquetas, unidadDerecha) {
  const posicionDe = new Map(indices.map((indice, i) => [indice, i]));
  const partes = series.map((serie) => {
    if (serie.puntos.length === 0) return `${serie.variable.nombre}: sin datos`;
    const valores = serie.puntos
      .map((p) => `${etiquetas[posicionDe.get(p.indice)]}: ${formatearNumero(p.valor, 1)} ${serie.variable.unidad}`)
      .join(", ");
    return `${serie.variable.nombre}, ${serie.puntos.length} periodos. ${valores}`;
  });
  const aviso = unidadDerecha ? ` La humedad usa el eje derecho en ${unidadDerecha}.` : "";
  return `Gráfica de comparación.${aviso} ${partes.join(". ")}`;
}

/**
 * Histograma de la distribución de una variable, para la consulta de extremos.
 *
 * Responde a algo que los paneles de mínimo y máximo no cuentan: si esos extremos son
 * casos aislados o si el grueso de las lecturas está cerca de ellos. Cada barra es un
 * tramo de valores y su altura, cuántas lecturas cayeron ahí.
 */
export function dibujarDistribucion(canvas, { tramos, variable, oscuro }) {
  destruirGrafica();
  if (!tramos || tramos.length === 0) return null;

  const colores = coloresTema(oscuro);
  const color = colorVariable(variable.clave, oscuro);
  const etiqueta = (tramo) => `${formatearNumero(tramo.desde, 1)} – ${formatearNumero(tramo.hasta, 1)}`;

  graficaActual = new Chart(canvas, {
    type: "bar",
    data: {
      labels: tramos.map(etiqueta),
      datasets: [{
        label: `Lecturas (${variable.unidad})`,
        data: tramos.map((tramo) => tramo.cantidad),
        backgroundColor: `${color}C0`,
        borderColor: color,
        borderWidth: 0,
        borderRadius: 5
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          title: { display: true, text: `Tramos de ${variable.unidad}`, color: colores.texto },
          ticks: { color: colores.texto, maxRotation: 60, minRotation: 0, autoSkipPadding: 8 },
          grid: { display: false }
        },
        y: {
          beginAtZero: true,
          title: { display: true, text: "Lecturas", color: colores.texto },
          // Las cuentas son enteras: no tiene sentido una marca en 2,5 lecturas.
          ticks: { color: colores.texto, precision: 0 },
          grid: { color: colores.rejilla }
        }
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (contexto) => `Entre ${contexto[0].label} ${variable.unidad}`,
            label: (contexto) => `${contexto.parsed.y} ${contexto.parsed.y === 1 ? "lectura" : "lecturas"}`
          }
        }
      }
    }
  });

  canvas.setAttribute("role", "img");
  canvas.setAttribute("aria-label",
    `Distribución de ${variable.nombre}. ` +
    tramos.map((t) => `entre ${etiqueta(t)} ${variable.unidad}: ${t.cantidad}`).join(", ") + ".");
  return graficaActual;
}

/** Leyenda en HTML, con el trazo discontinuo del punto de rocío. */
export function leyendaHtml(series, oscuro) {
  return series.map((serie) => {
    // La marca lleva el color del trazo; el texto, su variante legible sobre la tarjeta.
    const trazo = colorVariable(serie.variable.clave, oscuro);
    const texto = colorVariableTexto(serie.variable.clave, oscuro);
    const discontinuo = serie.variable.clave === "puntoRocio";
    const marca = discontinuo
      ? `<span class="leyenda__punto leyenda__punto--discontinuo" style="color:${trazo}"></span>`
      : `<span class="leyenda__punto" style="background:${trazo}"></span>`;
    return `<span class="leyenda__item" style="color:${texto}">${marca}${serie.variable.nombre} (${serie.variable.unidad})</span>`;
  }).join("");
}
