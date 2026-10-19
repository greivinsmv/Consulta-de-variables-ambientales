/**
 * Peticiones HTTP con mensajes de error comprensibles.
 *
 * `fetch` lanza un TypeError escueto ("Failed to fetch") cuando no hay red, el servidor no
 * responde o el navegador bloquea la petición. Mostrar ese texto en pantalla no le dice
 * nada a quien usa la aplicación, así que aquí se traduce a algo accionable y se conserva
 * el error original en `causa` por si hace falta depurarlo.
 *
 * Las cancelaciones (AbortError) se dejan pasar tal cual: no son fallos, son peticiones
 * que ya no interesan porque el usuario cambió de zona o de pantalla.
 */

export async function pedirJson(url, { señal, servicio = "el servidor", errores = {} } = {}) {
  let respuesta;
  try {
    respuesta = await fetch(url, { signal: señal });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    const fallo = new Error(
      `No se pudo conectar con ${servicio}. Revisa tu conexión a internet e inténtalo de nuevo.`
    );
    fallo.causa = error;
    throw fallo;
  }

  if (!respuesta.ok) {
    const mensaje = errores[respuesta.status]
      ?? (respuesta.status >= 500
        ? `${servicio} no está disponible en este momento (error ${respuesta.status}). Inténtalo más tarde.`
        : `${servicio} rechazó la consulta (error ${respuesta.status}).`);
    throw new Error(mensaje);
  }

  try {
    return await respuesta.json();
  } catch {
    throw new Error(`${servicio} devolvió una respuesta que no se pudo interpretar.`);
  }
}
