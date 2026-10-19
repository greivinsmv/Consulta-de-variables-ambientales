/**
 * Íconos en SVG, sin librerías externas.
 *
 * La app Android los dibuja a mano en un Canvas (IconoAmbiental, en InterfazAmbiental.kt).
 * Aquí se replican las mismas figuras sobre un lienzo de 24×24, escalando las coordenadas
 * relativas del original, para que ambas aplicaciones muestren los mismos símbolos.
 */

const envoltura = (contenido, tamano) =>
  `<svg viewBox="0 0 24 24" width="${tamano}" height="${tamano}" fill="none" stroke="currentColor"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
        aria-hidden="true" focusable="false">${contenido}</svg>`;

const GOTA = "M12 1.92 C3.6 12 0 20.4 12 22.08 C24 20.4 20.4 12 12 1.92 Z";

const FIGURAS = {
  // Navegación
  actual: '<path d="M2.88 10.8 L12 2.88 L21.12 10.8 L21.12 21.12 L2.88 21.12 Z"/>' +
          '<path d="M9.6 21.12 V14.4 H14.4 V21.12"/>',
  hora: '<circle cx="12" cy="12" r="9.36"/><path d="M12 12 V6 M12 12 L16.8 14.4"/>',
  dia: '<path d="M5.52 20.4 V14.4 M12 20.4 V9.6 M18.48 20.4 V4.8 M2.4 21.6 H22.08"/>',
  historico: '<path d="M6 3.6 V20.4 M2.4 7.2 L6 3.6 L9.6 7.2"/>' +
             '<path d="M18 20.4 V3.6 M14.4 16.8 L18 20.4 L21.6 16.8"/>',
  info: '<circle cx="12" cy="12" r="10.08"/><path d="M12 10.8 V17.52"/>' +
        '<circle cx="12" cy="7.2" r="1.2" fill="currentColor" stroke="none"/>',

  // Variables
  temperatura: '<circle cx="12" cy="18" r="4.32"/>' +
               '<path d="M10.08 13.92 V4.32 H13.92 V13.92 M12 16.8 V7.68"/>',
  humedad: `<path d="${GOTA}"/>`,
  rocio: `<path d="${GOTA}"/><path d="M8.64 15.6 H15.36"/>`,

  // Acciones
  sol: '<circle cx="12" cy="12" r="4.2"/>' +
       '<path d="M12 1.5 V4 M12 20 V22.5 M1.5 12 H4 M20 12 H22.5' +
       ' M4.6 4.6 L6.4 6.4 M17.6 17.6 L19.4 19.4 M19.4 4.6 L17.6 6.4 M6.4 17.6 L4.6 19.4"/>',
  luna: '<path d="M20.5 14.3 A9 9 0 0 1 9.7 3.5 A9.2 9.2 0 1 0 20.5 14.3 Z"/>',
  descarga: '<path d="M12 3 V15 M7 10.5 L12 15.5 L17 10.5 M3.5 19.5 H20.5"/>',
  refrescar: '<path d="M20.5 11.5 A8.5 8.5 0 1 1 18 5.6 M18 1.5 V6.1 H13.4"/>',
  cerrar: '<path d="M5.5 5.5 L18.5 18.5 M18.5 5.5 L5.5 18.5"/>',
  lugar: '<path d="M12 22 C12 22 19 15.4 19 9.9 A7 7 0 0 0 5 9.9 C5 15.4 12 22 12 22 Z"/>' +
         '<circle cx="12" cy="9.9" r="2.6"/>',

  // Clima (códigos WMO)
  despejado: null, // se resuelve con sol o luna según sea de día
  parcial: '<circle cx="8.6" cy="8.6" r="3.4"/><path d="M8.6 1.9 V3.6 M1.9 8.6 H3.6 M3.9 3.9 L5.1 5.1"/>' +
           '<path d="M7.6 19.8 A3.6 3.6 0 0 1 8.2 12.7 A5.2 5.2 0 0 1 18.2 13.3 A3.3 3.3 0 0 1 17.8 19.8 Z"/>',
  nublado: '<path d="M7.2 19.2 A3.9 3.9 0 0 1 7.9 11.5 A5.6 5.6 0 0 1 18.6 12.2 A3.5 3.5 0 0 1 18.2 19.2 Z"/>',
  neblina: '<path d="M3.5 9 H20.5 M5.5 13 H18.5 M3.5 17 H20.5 M8 21 H16"/>',
  llovizna: '<path d="M7.2 14.5 A3.6 3.6 0 0 1 7.9 7.3 A5.3 5.3 0 0 1 18.2 8 A3.3 3.3 0 0 1 17.8 14.5 Z"/>' +
            '<path d="M9 18 V19.5 M14.5 18 V19.5"/>',
  lluvia: '<path d="M7.2 14.5 A3.6 3.6 0 0 1 7.9 7.3 A5.3 5.3 0 0 1 18.2 8 A3.3 3.3 0 0 1 17.8 14.5 Z"/>' +
          '<path d="M8.5 17.5 L7.3 21 M12.5 17.5 L11.3 21 M16.5 17.5 L15.3 21"/>',
  nieve: '<path d="M7.2 14.5 A3.6 3.6 0 0 1 7.9 7.3 A5.3 5.3 0 0 1 18.2 8 A3.3 3.3 0 0 1 17.8 14.5 Z"/>' +
         '<path d="M8.6 18.6 H10.4 M9.5 17.7 V19.5 M14.6 18.6 H16.4 M15.5 17.7 V19.5"/>',
  tormenta: '<path d="M7.2 13.8 A3.6 3.6 0 0 1 7.9 6.6 A5.3 5.3 0 0 1 18.2 7.3 A3.3 3.3 0 0 1 17.8 13.8 Z"/>' +
            '<path d="M13.2 15.5 L10.2 19.4 H13.2 L11.4 22.5"/>',
  desconocido: '<circle cx="12" cy="12" r="9.4"/>' +
               '<path d="M9.4 9.3 A2.7 2.7 0 1 1 12 13 V14.6"/>' +
               '<circle cx="12" cy="17.6" r="1.1" fill="currentColor" stroke="none"/>'
};

/** Devuelve el SVG de un ícono. Un nombre desconocido no rompe la interfaz. */
export function icono(nombre, tamano = 24) {
  const figura = FIGURAS[nombre];
  if (!figura) return envoltura(FIGURAS.desconocido, tamano);
  return envoltura(figura, tamano);
}

/**
 * Ícono de una condición meteorológica. El cielo despejado cambia entre sol y luna según
 * el indicador `is_day` de la API, igual que en la app Android.
 */
export function iconoClima(nombreCondicion, esDia = true, tamano = 24) {
  if (nombreCondicion === "despejado") return icono(esDia ? "sol" : "luna", tamano);
  return icono(nombreCondicion, tamano);
}

/** Ícono de la variable, para las tarjetas de medición. */
export const iconoVariable = (clave, tamano = 24) =>
  icono(clave === "temperatura" ? "temperatura" : clave === "humedad" ? "humedad" : "rocio", tamano);
