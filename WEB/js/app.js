/**
 * Arranque, estado global y navegación.
 *
 * La aplicación tiene dos fuentes que no se mezclan nunca: las lecturas del ESP32 que
 * llegan a Firebase, y las estimaciones de Open-Meteo para una zona de Costa Rica. La
 * fuente elegida decide qué datos alimentan las tres consultas.
 *
 * Cada pantalla conserva sus fechas, variables, filtro y resultados mientras dura la
 * sesión: abrir Información o cambiar de sección y volver no borra lo consultado.
 */

import { zonaPorId, zonasClimaticas, formatearFecha } from "./modelo.js";
import {
  descargarPronostico, descargarHistorial, guardarHistorial,
  leerHistorialGuardado, origenDatos, rangoCubierto, periodoDisponible,
  INTERVALO_CONSULTA_CLIMA
} from "./openmeteo.js";
import { rangoDisponible } from "./firebase.js";
import { destruirGrafica } from "./graficos.js";
import { icono } from "./ui/iconos.js";
import { esc, aviso } from "./ui/componentes.js";
import * as inicio from "./ui/inicio.js";
import * as porPeriodo from "./ui/consultaPeriodos.js";
import * as extremos from "./ui/extremos.js";
import { panelInformacion } from "./ui/informacion.js";

const SECCIONES = [
  { clave: "actual", etiqueta: "Inicio", titulo: "Consulta ambiental" },
  { clave: "hora", etiqueta: "Por hora", titulo: "Promedios por hora" },
  { clave: "dia", etiqueta: "Por día", titulo: "Promedios diarios" },
  { clave: "historico", etiqueta: "Extremos", titulo: "Máximos y mínimos" }
];

const preferencia = (clave, porDefecto) => {
  try { return localStorage.getItem(clave) ?? porDefecto; } catch { return porDefecto; }
};
const guardarPreferencia = (clave, valor) => {
  try { localStorage.setItem(clave, valor); } catch { /* modo privado o sin permiso */ }
};

const prefiereOscuro = () =>
  window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;

export const estado = {
  pantalla: "actual",
  fuente: preferencia("fuente", "esp32"),
  zonaId: preferencia("zona_clima", "santa_clara"),
  oscuro: preferencia("tema", null) === null ? prefiereOscuro() : preferencia("tema") === "oscuro",
  info: false,
  esp32: { periodo: null, cargandoPeriodo: false },
  clima: {
    pronostico: null,
    historial: null,
    cargandoPronostico: false,
    cargandoHistorial: false,
    errorPronostico: "",
    errorHistorial: ""
  }
};

export const zonaActual = () => zonaPorId(estado.zonaId);

/**
 * Datos que alimentan las consultas según la fuente elegida.
 * Con el ESP32 cada pantalla consulta Firebase; con el clima se usa el historial
 * descargado, que ya está en memoria.
 */
export const fuenteDatos = () => estado.fuente === "clima"
  ? {
      tipo: "clima",
      historial: estado.clima.historial,
      origen: origenDatos(zonaActual()),
      periodo: periodoDisponible(),
      asegurar: asegurarHistorial
    }
  : {
      tipo: "esp32",
      historial: null,
      origen: null,
      // Null mientras se consulta; hasta entonces los selectores no se acotan.
      periodo: estado.esp32.periodo,
      asegurar: null
    };

/**
 * Averigua entre qué fechas hay lecturas del ESP32, una sola vez por sesión.
 * Si falla, los selectores se quedan sin acotar: es una ayuda, no un requisito.
 */
async function cargarPeriodoEsp32() {
  if (estado.esp32.periodo || estado.esp32.cargandoPeriodo) return;
  estado.esp32.cargandoPeriodo = true;
  try {
    estado.esp32.periodo = await rangoDisponible();
  } catch {
    estado.esp32.periodo = null;
  } finally {
    estado.esp32.cargandoPeriodo = false;
    render();
  }
}

/**
 * Devuelve un historial que cubra el periodo pedido, descargándolo si el que hay en
 * memoria no llega. Así las consultas pueden proponer cualquier fecha del periodo
 * disponible sin obligar a descargar antes desde Inicio.
 */
export async function asegurarHistorial(rango, { señal } = {}) {
  if (rangoCubierto(estado.clima.historial, rango)) return estado.clima.historial;

  const zona = zonaActual();
  const historial = await descargarHistorial(zona, { rango, señal });
  // Una respuesta lenta de otra zona no puede pisar la que el usuario mira ahora.
  if (historial.zonaId !== estado.zonaId) return estado.clima.historial;

  guardarHistorial(historial);
  estado.clima.historial = historial;
  return historial;
}

// --- Petición del pronóstico ----------------------------------------------------------

let temporizadorClima = null;
// Cada descarga se asocia a su zona: una respuesta lenta de Santa Clara no puede
// aparecer bajo el título de Liberia.
let peticionPronostico = null;
let peticionHistorial = null;

export async function actualizarPronostico({ forzar = false } = {}) {
  const zona = zonaActual();
  if (estado.clima.cargandoPronostico && !forzar) return;

  peticionPronostico?.abort();
  peticionPronostico = new AbortController();
  estado.clima.cargandoPronostico = true;
  estado.clima.errorPronostico = "";
  render();

  try {
    const pronostico = await descargarPronostico(zona, { señal: peticionPronostico.signal });
    if (pronostico.zonaId !== estado.zonaId) return;
    estado.clima.pronostico = pronostico;
  } catch (error) {
    if (error.name === "AbortError") return;
    // Se conserva el último resultado de la zona y se avisa de su antigüedad.
    estado.clima.errorPronostico = error.message;
  } finally {
    if (peticionPronostico?.signal.aborted !== true) estado.clima.cargandoPronostico = false;
    render();
  }
}

export async function cargarHistorial() {
  const zona = zonaActual();
  peticionHistorial?.abort();
  peticionHistorial = new AbortController();
  estado.clima.cargandoHistorial = true;
  estado.clima.errorHistorial = "";
  render();

  try {
    const historial = await descargarHistorial(zona, { señal: peticionHistorial.signal });
    if (historial.zonaId !== estado.zonaId) return;
    // Solo se reemplaza la copia guardada si la descarga nueva es válida.
    guardarHistorial(historial);
    estado.clima.historial = historial;
    porPeriodo.reiniciar();
    extremos.reiniciar();
  } catch (error) {
    if (error.name === "AbortError") return;
    estado.clima.errorHistorial = error.message;
  } finally {
    estado.clima.cargandoHistorial = false;
    render();
  }
}

/** Recupera la copia guardada de la zona activa, para poder consultar sin conexión. */
function recuperarHistorialGuardado() {
  const copia = leerHistorialGuardado(estado.zonaId);
  estado.clima.historial = copia
    ? { ...copia, origen: copia.origen ?? origenDatos(zonaActual()) }
    : null;
}

/**
 * El pronóstico se refresca cada quince minutos mientras Inicio está visible. Cambiar de
 * pantalla o de fuente, abrir Información o esconder la pestaña detiene la programación:
 * no tiene sentido consultar la API para una vista que nadie está mirando.
 */
function programarPronostico() {
  clearInterval(temporizadorClima);
  temporizadorClima = null;

  const activo = estado.fuente === "clima" && estado.pantalla === "actual" &&
    !estado.info && document.visibilityState === "visible";
  if (!activo) return;

  if (!estado.clima.pronostico && !estado.clima.cargandoPronostico) actualizarPronostico();
  temporizadorClima = setInterval(() => actualizarPronostico(), INTERVALO_CONSULTA_CLIMA);
}

// --- Navegación y preferencias ----------------------------------------------------------

export function irA(pantalla) {
  if (estado.pantalla === pantalla) return;
  estado.pantalla = pantalla;
  destruirGrafica();
  render();
  document.getElementById("contenido").focus({ preventScroll: true });
  window.scrollTo({ top: 0, behavior: "instant" });
}

export function cambiarFuente(fuente) {
  if (estado.fuente === fuente) return;
  estado.fuente = fuente;
  guardarPreferencia("fuente", fuente);
  destruirGrafica();
  porPeriodo.reiniciar();
  extremos.reiniciar();
  render();
}

export function cambiarZona(zonaId) {
  if (estado.zonaId === zonaId) return;
  estado.zonaId = zonaId;
  guardarPreferencia("zona_clima", zonaId);
  // Cambiar de zona limpia lo consultado y muestra únicamente el historial de la nueva.
  estado.clima.pronostico = null;
  estado.clima.errorPronostico = "";
  estado.clima.errorHistorial = "";
  recuperarHistorialGuardado();
  destruirGrafica();
  porPeriodo.reiniciar();
  extremos.reiniciar();
  render();
  if (estado.fuente === "clima" && estado.pantalla === "actual") actualizarPronostico();
}

function alternarTema() {
  estado.oscuro = !estado.oscuro;
  guardarPreferencia("tema", estado.oscuro ? "oscuro" : "claro");
  aplicarTema();
  destruirGrafica();
  render();
}

function aplicarTema() {
  document.documentElement.dataset.tema = estado.oscuro ? "oscuro" : "claro";
}

export function abrirInformacion() {
  estado.info = true;
  destruirGrafica();
  render();
}

export function cerrarInformacion() {
  estado.info = false;
  render();
}

// --- Pintado ------------------------------------------------------------------------------

function pintarCabecera() {
  const seccion = SECCIONES.find((s) => s.clave === estado.pantalla);
  const enClima = estado.fuente === "clima";
  const zona = zonaActual();

  document.getElementById("emblema").innerHTML = icono(seccion.clave, 22);
  document.getElementById("titulo").textContent =
    enClima && estado.pantalla === "actual" ? "Clima de Costa Rica" : seccion.titulo;

  const subtitulo = document.getElementById("subtitulo");
  if (enClima && estado.pantalla === "actual") {
    subtitulo.textContent = "";
    subtitulo.classList.add("oculto");
  } else {
    subtitulo.textContent = enClima
      ? `${zona.nombre}, ${zona.region}`
      : "Lecturas del ESP32";
    subtitulo.classList.remove("oculto");
  }

  const botonTema = document.getElementById("boton-tema");
  botonTema.setAttribute("aria-pressed", String(estado.oscuro));
  botonTema.setAttribute("aria-label", estado.oscuro ? "Cambiar a tema claro" : "Cambiar a tema oscuro");
  document.getElementById("boton-tema-contenido").innerHTML = icono(estado.oscuro ? "sol" : "luna", 20);
  document.getElementById("boton-info").innerHTML = icono("info", 22);
}

function pintarNavegacion() {
  document.getElementById("navegacion").innerHTML = SECCIONES.map((seccion) => `
    <button type="button" class="nav__destino" data-ir="${seccion.clave}"
            ${estado.pantalla === seccion.clave ? 'aria-current="page"' : ""}>
      <span class="nav__icono">${icono(seccion.clave, 22)}</span>
      <span>${esc(seccion.etiqueta)}</span>
    </button>`).join("");
}

/** Selector de fuente y, con el clima, de zona. */
function controlesFuente() {
  const opciones = [
    { clave: "esp32", etiqueta: "ESP32" },
    { clave: "clima", etiqueta: "Clima de Costa Rica" }
  ];
  const botones = opciones.map((opcion) => `
    <button type="button" class="variable variable--unica" data-fuente="${opcion.clave}"
            role="radio" aria-checked="${estado.fuente === opcion.clave}">
      <span>${esc(opcion.etiqueta)}</span>
    </button>`).join("");

  const zona = zonaActual();
  const selectorZona = estado.fuente !== "clima" ? "" : `
    <label class="campo">
      <span class="campo__etiqueta">Zona</span>
      <select data-zona>
        ${zonasClimaticas.map((z) => `
          <option value="${z.id}" ${z.id === estado.zonaId ? "selected" : ""}>
            ${esc(z.nombre)} · ${esc(z.region)}
          </option>`).join("")}
      </select>
    </label>`;

  return `
    <section class="tarjeta pila">
      <div class="campo">
        <span class="campo__etiqueta" id="et-fuente">Fuente de datos</span>
        <div class="selector-variables" role="radiogroup" aria-labelledby="et-fuente">${botones}</div>
      </div>
      ${selectorZona}
      ${estado.fuente === "clima"
        ? `<p class="nota">${esc(`${zona.latitud}, ${zona.longitud} · estimaciones de un modelo, no de una estación local.`)}</p>`
        : `<p class="nota">Lecturas del DHT22 simulado en Wokwi, enviadas a Firebase.</p>`}
    </section>`;
}

/** Panel para descargar o actualizar el historial de diez días de la zona. */
function panelHistorial() {
  const { historial, cargandoHistorial, errorHistorial } = estado.clima;
  const disponible = periodoDisponible();
  const descripcion = historial
    ? `Historial de ${formatearFecha(historial.rango.inicio)} a ${formatearFecha(historial.rango.fin)} · ${historial.registros.length} registros horarios.`
    : "Descarga los diez días completos anteriores, que es lo que analiza la consulta de extremos.";

  return `
    <section class="tarjeta pila">
      <h2 class="tarjeta__titulo">Historial meteorológico</h2>
      <p class="nota">${esc(descripcion)}</p>
      <p class="nota">${esc(
        `Por hora y Por día descargan por su cuenta el periodo que elijas, entre ` +
        `${formatearFecha(disponible.inicio)} y ${formatearFecha(disponible.fin)}.`
      )}</p>
      ${errorHistorial ? aviso(`No se pudo descargar: ${errorHistorial}`, "error") : ""}
      ${errorHistorial && historial ? aviso("Se mantiene la descarga anterior de esta zona.") : ""}
      <div class="grupo-botones">
        <button type="button" class="boton" data-historial ${cargandoHistorial ? "disabled" : ""}>
          ${icono("descarga", 18)}
          ${cargandoHistorial ? "Descargando…" : historial ? "Actualizar historial" : "Cargar historial"}
        </button>
      </div>
    </section>`;
}

function pintarContenido() {
  const contenido = document.getElementById("contenido");

  if (estado.info) {
    contenido.innerHTML = panelInformacion(estado, zonaActual());
    contenido.querySelector("[data-cerrar-info]")?.addEventListener("click", cerrarInformacion);
    return;
  }

  const datos = fuenteDatos();
  const enClima = estado.fuente === "clima";

  // Extremos analiza todo el historial de una vez, así que necesita una descarga previa.
  // Por hora y Por día no: descargan su propio periodo al consultar.
  const faltaHistorial = enClima && estado.pantalla === "historico" && !estado.clima.historial;

  // El panel del historial se muestra en Inicio y, en las consultas, solo si falta.
  const mostrarPanelHistorial = enClima && (estado.pantalla === "actual" || faltaHistorial);

  let pantalla = "";
  if (estado.pantalla === "actual") {
    pantalla = inicio.render(estado, zonaActual());
  } else if (faltaHistorial) {
    pantalla = aviso("Carga el historial de la zona para poder consultar sus extremos.");
  } else if (estado.pantalla === "hora" || estado.pantalla === "dia") {
    pantalla = porPeriodo.render(estado.pantalla, datos, estado.oscuro);
  } else {
    pantalla = extremos.render(datos, estado.oscuro);
  }

  contenido.innerHTML = `
    ${controlesFuente()}
    ${mostrarPanelHistorial ? panelHistorial() : ""}
    ${pantalla}`;

  // Eventos de los controles comunes.
  contenido.querySelectorAll("[data-fuente]").forEach((boton) => {
    boton.addEventListener("click", () => cambiarFuente(boton.dataset.fuente));
  });
  contenido.querySelector("[data-zona]")?.addEventListener("change", (evento) => {
    cambiarZona(evento.target.value);
  });
  contenido.querySelector("[data-historial]")?.addEventListener("click", cargarHistorial);

  // Eventos propios de la pantalla.
  if (estado.pantalla === "actual") inicio.conectar(contenido, estado, actualizarPronostico);
  else if (faltaHistorial) { /* sin controles */ }
  else if (estado.pantalla === "hora" || estado.pantalla === "dia") {
    porPeriodo.conectar(contenido, estado.pantalla, datos, estado.oscuro, render);
  } else {
    extremos.conectar(contenido, datos, estado.oscuro, render);
  }
}

export function render() {
  pintarCabecera();
  pintarNavegacion();
  pintarContenido();
  programarPronostico();
  // La escucha de Firebase solo se mantiene encendida mientras Inicio está a la vista.
  inicio.sincronizarEscucha(estado, render);
  if (estado.fuente === "esp32") cargarPeriodoEsp32();
}

// --- Inicio ---------------------------------------------------------------------------------

function iniciar() {
  aplicarTema();
  recuperarHistorialGuardado();

  document.getElementById("boton-tema").addEventListener("click", alternarTema);
  document.getElementById("boton-info").addEventListener("click", abrirInformacion);
  document.getElementById("navegacion").addEventListener("click", (evento) => {
    const destino = evento.target.closest("[data-ir]");
    if (destino) irA(destino.dataset.ir);
  });

  document.addEventListener("visibilitychange", programarPronostico);
  document.addEventListener("keydown", (evento) => {
    if (evento.key === "Escape" && estado.info) cerrarInformacion();
  });

  render();
}

// Chart.js se carga con `defer`, así que puede no estar listo al evaluar este módulo.
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", iniciar);
} else {
  iniciar();
}
