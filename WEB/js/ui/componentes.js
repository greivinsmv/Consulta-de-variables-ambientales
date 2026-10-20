/**
 * Piezas de interfaz compartidas por las pantallas de consulta.
 *
 * Cada componente devuelve HTML y expone una función que conecta sus eventos dentro de un
 * contenedor ya pintado. Se usa delegación en vez de crear nodos uno a uno: con cuatro
 * pantallas pequeñas resulta más corto y más fácil de seguir.
 */

import {
  variables, variablePorClave, formatearValor, formatearNumero, formatearFechaHora
} from "../modelo.js";
import { colorVariableTexto, interpretarLimites, resumenFiltro } from "../filtro.js";
import { icono } from "./iconos.js";

/** Escapa el texto que se inserta en HTML. */
export const esc = (texto) => String(texto)
  .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
  .replace(/"/g, "&quot;").replace(/'/g, "&#39;");

/** Avisos de la interfaz. Un error nunca se confunde con la ausencia de datos. */
export const aviso = (texto, tipo = "") =>
  `<p class="aviso ${tipo === "error" ? "aviso--error" : ""}">${esc(texto)}</p>`;

export const nota = (texto) => `<p class="nota">${esc(texto)}</p>`;

// --- Selector de variables ---------------------------------------------------------

/**
 * Selección múltiple de variables para comparar. No permite quedarse sin ninguna: una
 * consulta sin variables no tendría nada que calcular, así que el último chip activo no
 * se puede apagar.
 */
export function selectorVariables(elegidas, oscuro) {
  const chips = variables.map((variable) => {
    const activa = elegidas.includes(variable.clave);
    const color = colorVariableTexto(variable.clave, oscuro);
    return `
      <button type="button" class="variable" data-variable="${variable.clave}"
              role="checkbox" aria-checked="${activa}" style="--color-serie:${color}">
        <span class="variable__marca" aria-hidden="true">${activa ? "✓" : "+"}</span>
        <span>${esc(variable.nombre)}</span>
      </button>`;
  }).join("");

  return `
    <div class="campo">
      <span class="campo__etiqueta" id="et-variables">Variables a comparar</span>
      <div class="selector-variables" role="group" aria-labelledby="et-variables">${chips}</div>
    </div>`;
}

/** Conecta el selector de variables. */
export function conectarSelectorVariables(raiz, { elegidas, alCambiar }) {
  raiz.querySelectorAll("[data-variable]").forEach((boton) => {
    boton.addEventListener("click", () => {
      const clave = boton.dataset.variable;
      if (elegidas.includes(clave)) {
        if (elegidas.length > 1) alCambiar(elegidas.filter((c) => c !== clave));
      } else {
        // Se conserva el orden del catálogo para que la leyenda no baile.
        alCambiar(variables.map((v) => v.clave).filter((c) => elegidas.includes(c) || c === clave));
      }
    });
  });
}

// --- Estadísticas del periodo ---------------------------------------------------------

/** Estadísticas que el panel resumen puede mostrar. */
export const estadisticas = [
  { clave: "promedio", nombre: "Promedio" },
  { clave: "maximo", nombre: "Máximo" },
  { clave: "minimo", nombre: "Mínimo" }
];

/**
 * Casillas para elegir qué estadísticas aparecen en el resumen.
 * Se permite apagarlas todas: quien solo quiera la gráfica puede dejar el panel vacío.
 */
export function selectorEstadisticas(elegidas) {
  const casillas = estadisticas.map((estadistica) => {
    const activa = elegidas.includes(estadistica.clave);
    return `
      <button type="button" class="casilla" data-estadistica="${estadistica.clave}"
              role="checkbox" aria-checked="${activa}">
        <span class="casilla__marca" aria-hidden="true">${activa ? "✓" : ""}</span>
        <span>${esc(estadistica.nombre)}</span>
      </button>`;
  }).join("");

  return `
    <div class="campo">
      <span class="campo__etiqueta" id="et-estadisticas">Estadísticas a mostrar</span>
      <div class="grupo-casillas" role="group" aria-labelledby="et-estadisticas">${casillas}</div>
    </div>`;
}

export function conectarSelectorEstadisticas(raiz, { elegidas, alCambiar }) {
  raiz.querySelectorAll("[data-estadistica]").forEach((boton) => {
    boton.addEventListener("click", () => {
      const clave = boton.dataset.estadistica;
      alCambiar(elegidas.includes(clave)
        ? elegidas.filter((c) => c !== clave)
        : estadisticas.map((e) => e.clave).filter((c) => elegidas.includes(c) || c === clave));
    });
  });
}

/**
 * Panel resumen del periodo consultado: máximo, mínimo y promedio por variable.
 *
 * Los extremos son lecturas individuales con su fecha, no promedios, así que pueden no
 * coincidir con el punto más alto de la gráfica. La nota al pie lo aclara.
 */
export function panelResumenEstadistico(elegidas, estadisticasElegidas, resumenes, oscuro) {
  if (estadisticasElegidas.length === 0) return "";

  const filas = variables.filter((v) => elegidas.includes(v.clave)).map((variable) => {
    const resumen = resumenes[variable.clave];
    const color = colorVariableTexto(variable.clave, oscuro);

    if (!resumen) {
      return `
        <div class="resumen-serie">
          <div class="resumen-serie__nombre">${esc(variable.nombre)}</div>
          <div class="resumen-serie__detalle">Sin lecturas en el periodo</div>
        </div>`;
    }

    const celdas = estadisticasElegidas.map((clave) => {
      if (clave === "promedio") {
        return `<div class="estadistica">
                  <span class="estadistica__etiqueta">Promedio</span>
                  <span class="estadistica__valor" style="color:${color}">
                    ${esc(formatearValor(resumen.promedio, variable.unidad))}
                  </span>
                  <span class="estadistica__detalle">${resumen.cantidad} lecturas</span>
                </div>`;
      }
      const extremo = resumen[clave];
      return `<div class="estadistica">
                <span class="estadistica__etiqueta">${clave === "maximo" ? "Máximo" : "Mínimo"}</span>
                <span class="estadistica__valor" style="color:${color}">
                  ${esc(formatearValor(extremo.valor, variable.unidad))}
                </span>
                <span class="estadistica__detalle">${esc(formatearFechaHora(extremo.ts))}</span>
              </div>`;
    }).join("");

    return `
      <div class="resumen-variable">
        <div class="resumen-variable__nombre" style="color:${color}">${esc(variable.nombre)}</div>
        <div class="estadisticas">${celdas}</div>
      </div>`;
  }).join("");

  return `
    <div class="pila">
      <span class="campo__etiqueta">Resumen del periodo</span>
      <div class="resumen-series">${filas}</div>
      ${nota("El máximo y el mínimo son lecturas individuales con su fecha, no promedios: pueden no coincidir con el punto más alto de la gráfica.")}
    </div>`;
}

// --- Filtro de atípicos --------------------------------------------------------------

/**
 * Interruptor del filtro y, cuando está encendido, los límites de la variable en edición.
 * Con varias variables comparadas aparecen pestañas para editar cada una por separado.
 */
export function controlFiltro(filtro, elegidas, claveEditada) {
  const clave = elegidas.includes(claveEditada) ? claveEditada : elegidas[0];
  const variable = variablePorClave(clave);
  const limites = filtro.limites[clave];
  const invalidos = filtro.activo && interpretarLimites(limites) === null;

  const pestanas = filtro.activo && elegidas.length > 1
    ? `<div class="grupo-botones" role="tablist" aria-label="Variable a configurar">
         ${variables.filter((v) => elegidas.includes(v.clave)).map((v) => `
           <button type="button" class="boton boton--contorno" data-filtro-variable="${v.clave}"
                   role="tab" aria-selected="${v.clave === clave}">
             ${esc(v.nombre)}${v.clave === clave ? " •" : ""}
           </button>`).join("")}
       </div>`
    : "";

  const campos = filtro.activo ? `
    ${pestanas}
    <div class="fila-campos">
      <label class="campo">
        <span class="campo__etiqueta">Mínimo (${esc(variable.unidad)})</span>
        <input type="text" inputmode="text" data-limite="minimo" value="${esc(limites.minimo)}"
               aria-invalid="${invalidos}" autocomplete="off">
      </label>
      <label class="campo">
        <span class="campo__etiqueta">Máximo (${esc(variable.unidad)})</span>
        <input type="text" inputmode="text" data-limite="maximo" value="${esc(limites.maximo)}"
               aria-invalid="${invalidos}" autocomplete="off">
      </label>
    </div>
    ${invalidos ? aviso("Introduce dos números. El mínimo no puede superar al máximo.", "error") : ""}
    ${nota("Los límites son inclusivos y solo afectan a los cálculos: las lecturas guardadas no se modifican.")}
  ` : "";

  return `
    <div class="pila" data-filtro>
      <button type="button" class="interruptor" role="switch" aria-checked="${filtro.activo}"
              data-filtro-activo>
        <span>Filtrar valores atípicos</span>
        <span class="interruptor__pista" aria-hidden="true"></span>
      </button>
      ${campos}
    </div>`;
}

export function conectarControlFiltro(raiz, { filtro, claveEditada, alCambiar, alEditarVariable }) {
  raiz.querySelector("[data-filtro-activo]")?.addEventListener("click", () => {
    alCambiar({ ...filtro, activo: !filtro.activo });
  });

  raiz.querySelectorAll("[data-filtro-variable]").forEach((boton) => {
    boton.addEventListener("click", () => alEditarVariable(boton.dataset.filtroVariable));
  });

  raiz.querySelectorAll("[data-limite]").forEach((campo) => {
    const actualizar = (evento) => {
      if (evento.isComposing) return;
      const extremo = campo.dataset.limite;
      const teniaFoco = document.activeElement === campo;
      const { selectionStart, selectionEnd, selectionDirection } = campo;
      alCambiar({
        ...filtro,
        limites: {
          ...filtro.limites,
          [claveEditada]: { ...filtro.limites[claveEditada], [extremo]: campo.value }
        }
      });
      // alCambiar repinta los resultados y reemplaza los campos. Se retoma la edición
      // en el nuevo nodo para poder escribir varios caracteres o corregir uno en medio.
      if (teniaFoco) {
        const nuevo = raiz.querySelector(`[data-limite="${extremo}"]`);
        nuevo?.focus({ preventScroll: true });
        nuevo?.setSelectionRange(selectionStart, selectionEnd, selectionDirection);
      }
    };
    campo.addEventListener("input", actualizar);
    campo.addEventListener("compositionend", actualizar);
  });
}

/** Resumen de lecturas utilizadas y excluidas, visible solo con el filtro encendido. */
export function resumenFiltrado(filtro, datosPorVariable, elegidas) {
  if (!filtro.activo) return "";
  const lineas = elegidas.map((clave) => {
    const datos = datosPorVariable[clave];
    if (!datos) return "";
    return `<li>${esc(variablePorClave(clave).nombre)}: ${esc(resumenFiltro(datos))}</li>`;
  }).join("");
  return `<ul class="nota" style="margin:0;padding-left:18px">${lineas}</ul>`;
}

// --- Resultados -----------------------------------------------------------------------

/**
 * Detalle del periodo seleccionado: una fila por variable, con su promedio y la cantidad
 * de lecturas que lo respaldan. Sin lecturas se muestra "Sin datos", nunca un cero.
 */
export function resumenSeries(elegidas, valores, oscuro) {
  const filas = variables.filter((v) => elegidas.includes(v.clave)).map((variable) => {
    const dato = valores[variable.clave];
    const promedio = dato?.promedio ?? null;
    const cantidad = dato?.cantidad ?? 0;
    const color = colorVariableTexto(variable.clave, oscuro);
    return `
      <div class="resumen-serie">
        <div>
          <div class="resumen-serie__nombre">${esc(variable.nombre)}</div>
          <div class="resumen-serie__detalle">
            ${promedio === null ? "Sin lecturas" : `${cantidad} ${cantidad === 1 ? "lectura" : "lecturas"}`}
          </div>
        </div>
        <div class="resumen-serie__valor" style="color:${color}">
          ${esc(formatearValor(promedio, variable.unidad))}
        </div>
      </div>`;
  }).join("");

  return `<div class="resumen-series">${filas}</div>`;
}

/** Tarjeta de una medición suelta (Inicio). */
export const tarjetaMedicion = (nombre, valor, claveIcono) => `
  <div class="tarjeta medicion">
    <span class="medicion__icono">${icono(claveIcono, 26)}</span>
    <span class="medicion__nombre">${esc(nombre)}</span>
    <span class="medicion__valor">${esc(valor)}</span>
  </div>`;

/** Par etiqueta/valor para los paneles de extremos. */
export const datoSuelto = (etiqueta, valor) => `
  <div class="dato-suelto">
    <div class="dato-suelto__etiqueta">${esc(etiqueta)}</div>
    <div class="dato-suelto__valor">${esc(valor)}</div>
  </div>`;

export const numero = (valor, decimales = 1) => formatearNumero(valor, decimales);
