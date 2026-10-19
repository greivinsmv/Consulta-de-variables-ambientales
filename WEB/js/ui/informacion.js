/**
 * Panel de información.
 *
 * Reúne en un solo sitio las fuentes, la naturaleza de los datos, los criterios de cálculo
 * y los créditos. Concentrarlo aquí evita repetir explicaciones largas en cada tarjeta de
 * las pantallas de consulta.
 */

import { formatearFechaHora, formatearFecha } from "../modelo.js";
import { icono } from "./iconos.js";
import { esc } from "./componentes.js";

export function panelInformacion(estado, zona) {
  const historial = estado.clima.historial;

  const procedencia = estado.fuente === "clima"
    ? `<p>Estás viendo <strong>Clima de Costa Rica</strong>: estimaciones del modelo de
         Open-Meteo para ${esc(zona.nombre)}, ${esc(zona.region)}
         (${zona.latitud}, ${zona.longitud}). Son valores calculados para unas coordenadas
         aproximadas, no mediciones de una estación del campus: la celda del modelo puede
         estar cerca del lugar solicitado sin coincidir con él.</p>
       ${historial
          ? `<p>Historial descargado: ${esc(formatearFecha(historial.rango.inicio))} a ${esc(formatearFecha(historial.rango.fin))},
               ${historial.registros.length} registros horarios
               (${esc(formatearFechaHora(historial.descargadoEn))}).</p>`
          : "<p>Todavía no has descargado el historial de esta zona.</p>"}`
    : `<p>Estás viendo las lecturas del <strong>ESP32</strong>: un DHT22 simulado en Wokwi
         que envía una lectura por minuto a Firebase mientras la simulación está corriendo.
         Son datos de prueba, no mediciones de un sensor físico, e incluyen valores extremos
         enviados a propósito para comprobar la aplicación.</p>`;

  return `
    <div class="info pila">
      <section class="tarjeta pila">
        <div style="display:flex;align-items:center;justify-content:space-between;gap:12px">
          <h2 style="font-size:1.15rem">Información</h2>
          <button type="button" class="boton boton--suave" data-cerrar-info>
            ${icono("cerrar", 18)} Volver
          </button>
        </div>

        <div class="info__seccion">
          <h3>De dónde salen los datos</h3>
          ${procedencia}
        </div>

        <div class="info__seccion">
          <h3>Fechas y zona horaria</h3>
          <p>Todas las fechas se interpretan en <strong>América/Costa_Rica</strong> (UTC−6),
             sin importar la zona horaria de tu navegador. Un día va de medianoche a
             medianoche de Costa Rica.</p>
        </div>

        <div class="info__seccion">
          <h3>Cómo se calculan los resultados</h3>
          <p>Cada promedio usa las lecturas originales válidas de su variable. El promedio
             diario se calcula sobre las lecturas del día, no promediando los promedios de
             cada hora: con distinta cantidad de lecturas por hora daría otro número.</p>
          <p>Cada variable se calcula por separado. Un registro con la temperatura dañada y
             la humedad correcta cuenta para la humedad y no para la temperatura.</p>
          <p>Un periodo sin lecturas se muestra como <strong>Sin datos</strong> y nunca se
             sustituye por cero. En las gráficas, los periodos sin datos no se unen.</p>
          <p>Los máximos y mínimos se obtienen de las lecturas individuales, no de los
             promedios diarios. En un empate se muestra la fecha más antigua.</p>
        </div>

        <div class="info__seccion">
          <h3>Filtro de valores atípicos</h3>
          <p>Los límites son inclusivos y los puedes editar. El filtro solo cambia los
             resultados que se calculan: <strong>no modifica ni borra</strong> las lecturas
             guardadas. Tampoco decide si una lectura es real; los límites iniciales son
             orientativos.</p>
        </div>

        <div class="info__seccion">
          <h3>Gráficas</h3>
          <p>La temperatura y el punto de rocío comparten la escala en °C; la humedad usa
             otra escala en %. Que dos curvas se crucen no significa que sus valores sean
             iguales: cada eje lleva su unidad rotulada.</p>
        </div>

        <div class="info__seccion">
          <h3>Esta aplicación no escribe en la base</h3>
          <p>La web solo lee la ruta <code>lecturas/equipo01</code>. Las lecturas las crea
             únicamente el ESP32 con su propia cuenta; la página no usa ni pide credenciales
             de escritura.</p>
        </div>

        <div class="info__seccion">
          <h3>Créditos</h3>
          <p>Datos meteorológicos de
             <a href="https://open-meteo.com/" target="_blank" rel="noopener noreferrer">Open-Meteo</a>,
             bajo licencia
             <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noopener noreferrer">CC BY 4.0</a>.
             Los promedios, extremos y filtros los calcula Consulta Ambiental.</p>
          <p>Proyecto IoT · IC-3101 Arquitectura de Computadoras · II Semestre 2026.</p>
        </div>
      </section>
    </div>`;
}
