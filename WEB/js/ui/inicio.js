/**
 * Pantalla de Inicio.
 *
 * Con la fuente ESP32 muestra la última lectura recibida en Firebase y se mantiene al día
 * sola. Con el clima muestra las condiciones actuales de la zona, las próximas 24 horas y
 * diez días de pronóstico.
 *
 * El pronóstico es una estimación futura y se mantiene separado del historial: nunca entra
 * en los promedios de las consultas académicas.
 */

import { formatearValor, formatearFechaHora, hoyCR, esNumeroValido } from "../modelo.js";
import { observarUltimaLectura } from "../firebase.js";
import { condicionWmo, datoActualAnterior } from "../openmeteo.js";
import { icono, iconoClima } from "./iconos.js";
import { esc, aviso, nota, tarjetaMedicion } from "./componentes.js";

const lecturaEsp32 = { lectura: null, error: "", cargando: true };
let cancelarEscucha = null;

/**
 * Enciende o apaga la escucha de Firebase según lo que esté a la vista.
 * Consultar la base mientras nadie mira Inicio sería gastar peticiones para nada.
 */
export function sincronizarEscucha(estado, alActualizar) {
  const debeEscuchar = estado.fuente === "esp32" && estado.pantalla === "actual" && !estado.info;

  if (!debeEscuchar) {
    cancelarEscucha?.();
    cancelarEscucha = null;
    return;
  }
  if (cancelarEscucha) return;

  cancelarEscucha = observarUltimaLectura(
    (lectura) => {
      lecturaEsp32.lectura = lectura;
      lecturaEsp32.error = "";
      lecturaEsp32.cargando = false;
      alActualizar();
    },
    (error) => {
      lecturaEsp32.error = error.message;
      lecturaEsp32.cargando = false;
      alActualizar();
    }
  );
}

// --- Vista del ESP32 -------------------------------------------------------------------

function vistaEsp32() {
  const { lectura, error, cargando } = lecturaEsp32;

  if (cargando && !lectura && !error) {
    return `<section class="tarjeta">${aviso("Cargando lecturas…")}</section>`;
  }
  if (error) {
    return `<section class="tarjeta pila">
      <h2 class="tarjeta__titulo">Lectura actual</h2>
      ${aviso(`No se pudo leer Firebase: ${error}`, "error")}
    </section>`;
  }
  if (!lectura) {
    return `<section class="tarjeta pila">
      <h2 class="tarjeta__titulo">Lectura actual</h2>
      ${aviso("Todavía no hay lecturas para equipo01.")}
      ${nota("El ESP32 envía una lectura por minuto mientras la simulación de Wokwi está corriendo.")}
    </section>`;
  }

  const { temperatura, humedad, puntoRocio } = lectura.valores;
  const incompleta = [temperatura, humedad, puntoRocio].some((v) => !esNumeroValido(v));

  return `
    <div class="pila">
      ${incompleta ? aviso("El último registro está incompleto.") : ""}
      <section class="tarjeta medicion-principal">
        <span class="medicion-principal__aro">${icono("temperatura", 36)}</span>
        <span class="medicion-principal__nombre">Temperatura</span>
        <span class="medicion-principal__valor">${esc(formatearValor(temperatura, "°C"))}</span>
      </section>
      <div class="rejilla-mediciones">
        ${tarjetaMedicion("Humedad", formatearValor(humedad, "%"), "humedad")}
        ${tarjetaMedicion("Punto de rocío", formatearValor(puntoRocio, "°C"), "rocio")}
      </div>
      <section class="tarjeta pila">
        <h2 class="tarjeta__titulo">Fecha de lectura</h2>
        <p style="margin:0;font-size:1.05rem">${esc(formatearFechaHora(lectura.ts))}</p>
        ${nota("Fecha del servidor de Firebase, en hora de Costa Rica.")}
      </section>
    </div>`;
}

// --- Vista del clima --------------------------------------------------------------------

const nombreDia = (fechaISO, ts) => {
  if (fechaISO === hoyCR()) return "Hoy";
  return new Intl.DateTimeFormat("es-CR", {
    weekday: "short", day: "numeric", timeZone: "America/Costa_Rica"
  }).format(new Date(ts));
};

const horaCorta = (ts) => new Intl.DateTimeFormat("es-CR", {
  hour: "2-digit", hourCycle: "h23", timeZone: "America/Costa_Rica"
}).format(new Date(ts)) + ":00";

function tiraHoras(pronostico) {
  const ahora = Date.now();
  const proximas = pronostico.horas.filter((h) => h.ts >= ahora - 3600000).slice(0, 24);
  if (proximas.length === 0) return "";

  const celdas = proximas.map((hora) => {
    const condicion = condicionWmo(hora.codigo);
    return `
      <div class="hora-pronostico">
        <span>${esc(horaCorta(hora.ts))}</span>
        <span style="color:var(--primario)">${iconoClima(condicion.icono, true, 22)}</span>
        <span class="hora-pronostico__valor">${esc(formatearValor(hora.temperatura, "°"))}</span>
        <span class="hora-pronostico__lluvia">${hora.lluvia === null ? "" : `${Math.round(hora.lluvia)}%`}</span>
      </div>`;
  }).join("");

  return `
    <section class="tarjeta pila">
      <h2 class="tarjeta__titulo">Próximas horas</h2>
      <div class="tira-horas">${celdas}</div>
      ${nota("El porcentaje es probabilidad de lluvia, no cantidad.")}
    </section>`;
}

function filasDiarias(pronostico) {
  const dias = pronostico.dias.filter((d) => esNumeroValido(d.minima) && esNumeroValido(d.maxima));
  if (dias.length === 0) return "";

  // Todas las barras comparten el rango térmico del periodo, como en la app Android.
  const minimoGlobal = Math.min(...dias.map((d) => d.minima));
  const maximoGlobal = Math.max(...dias.map((d) => d.maxima));
  const amplitud = Math.max(maximoGlobal - minimoGlobal, 1);

  const filas = dias.map((dia) => {
    const condicion = condicionWmo(dia.codigo);
    const desde = ((dia.minima - minimoGlobal) / amplitud) * 100;
    const ancho = Math.max(((dia.maxima - dia.minima) / amplitud) * 100, 6);
    return `
      <div class="dia-pronostico">
        <span class="dia-pronostico__nombre">
          <span style="color:var(--primario);vertical-align:-4px">${iconoClima(condicion.icono, true, 18)}</span>
          ${esc(nombreDia(dia.fecha, dia.ts))}
        </span>
        <span class="dia-pronostico__lluvia">${dia.lluvia === null ? "" : `${Math.round(dia.lluvia)}%`}</span>
        <span class="dia-pronostico__rango">
          <span class="dia-pronostico__minima">${esc(formatearValor(dia.minima, "°"))}</span>
          <span class="dia-pronostico__barra">
            <span style="left:${desde}%;width:${ancho}%"></span>
          </span>
          <span>${esc(formatearValor(dia.maxima, "°"))}</span>
        </span>
      </div>`;
  }).join("");

  return `
    <section class="tarjeta pila">
      <h2 class="tarjeta__titulo">Diez días</h2>
      <div>${filas}</div>
      ${nota("Mínimas y máximas previstas por el modelo; no son los extremos de las lecturas registradas.")}
    </section>`;
}

function vistaClima(estado, zona) {
  const { pronostico, cargandoPronostico, errorPronostico } = estado.clima;

  if (!pronostico) {
    return `
      <section class="tarjeta pila">
        <h2 class="tarjeta__titulo">Clima actual</h2>
        ${cargandoPronostico
          ? aviso("Consultando el clima…")
          : aviso(errorPronostico || "Todavía no hay datos del clima para esta zona.",
                  errorPronostico ? "error" : "")}
        <div class="grupo-botones">
          <button type="button" class="boton" data-refrescar-clima ${cargandoPronostico ? "disabled" : ""}>
            ${icono("refrescar", 18)} Actualizar clima
          </button>
        </div>
      </section>`;
  }

  const { registro } = pronostico.actual;
  const condicion = condicionWmo(pronostico.codigoActual);
  const anterior = datoActualAnterior(pronostico.actual);

  return `
    <div class="pila">
      ${errorPronostico ? aviso(`No se pudo actualizar: ${errorPronostico}`, "error") : ""}
      ${anterior ? aviso("Estos datos tienen más de treinta minutos.") : ""}
      <section class="tarjeta pila">
        <div class="clima-actual">
          <span style="color:var(--primario)">${iconoClima(condicion.icono, pronostico.esDia, 56)}</span>
          <div>
            <div class="clima-actual__temperatura">${esc(formatearValor(registro.valores.temperatura, "°"))}</div>
            <div class="clima-actual__condicion">${esc(condicion.texto)}</div>
            <div class="nota">${esc(`${zona.nombre}, ${zona.region}`)}</div>
          </div>
        </div>
        <div class="rejilla-mediciones">
          ${tarjetaMedicion("Humedad", formatearValor(registro.valores.humedad, "%"), "humedad")}
          ${tarjetaMedicion("Punto de rocío", formatearValor(registro.valores.puntoRocio, "°C"), "rocio")}
        </div>
        <p class="nota">${esc(`Dato del modelo: ${formatearFechaHora(registro.ts)}`)}</p>
        <div class="grupo-botones">
          <button type="button" class="boton boton--suave" data-refrescar-clima
                  ${cargandoPronostico ? "disabled" : ""}>
            ${icono("refrescar", 18)} ${cargandoPronostico ? "Actualizando…" : "Actualizar clima"}
          </button>
        </div>
      </section>
      ${tiraHoras(pronostico)}
      ${filasDiarias(pronostico)}
      ${nota("Se actualiza solo cada quince minutos mientras esta pantalla está a la vista.")}
    </div>`;
}

// --- Interfaz del módulo --------------------------------------------------------------------

export const render = (estado, zona) =>
  estado.fuente === "clima" ? vistaClima(estado, zona) : vistaEsp32();

export function conectar(raiz, estado, alRefrescarClima) {
  raiz.querySelectorAll("[data-refrescar-clima]").forEach((boton) => {
    boton.addEventListener("click", () => alRefrescarClima({ forzar: false }));
  });
}
