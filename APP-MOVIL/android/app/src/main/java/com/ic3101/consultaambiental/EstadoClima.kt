package com.ic3101.consultaambiental

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

internal data class EstadoZona(
    val historial: HistorialClima? = null,
    val pronostico: PronosticoClima? = null,
    val leyendoCopia: Boolean = true,
    val descargandoHistorial: Boolean = false,
    val actualizando: Boolean = false,
    val errorHistorial: String? = null,
    val errorActual: String? = null,
    val ultimoIntento: Long = 0
)

/**
 * Mantiene las peticiones y sus resultados por zona durante la sesión.
 * Cambiar de ciudad no cancela una descarga útil ni permite que su respuesta
 * aparezca en otra ciudad. Solo el historial se guarda entre sesiones.
 */
internal class EstadoClima(private val directorio: File, private val alcance: CoroutineScope) {
    private val zonas = mutableStateMapOf<String, EstadoZona>()
    fun estado(zona: ZonaClimatica): EstadoZona = zonas[zona.id] ?: EstadoZona()
    private fun modificar(zona: ZonaClimatica, cambio: (EstadoZona) -> EstadoZona) {
        zonas[zona.id] = cambio(estado(zona))
    }

    suspend fun leerGuardado(zona: ZonaClimatica) {
        if (!estado(zona).leyendoCopia) return
        val historial = RepositorioClima(directorio, zona).leerGuardado()
        modificar(zona) { it.copy(historial = historial, leyendoCopia = false) }
    }

    fun actualizar(zona: ZonaClimatica) {
        if (estado(zona).actualizando) return
        modificar(zona) { it.copy(actualizando = true, errorActual = null, ultimoIntento = System.currentTimeMillis()) }
        alcance.launch {
            try {
                val pronostico = descargarPronostico(zona)
                modificar(zona) { it.copy(pronostico = pronostico) }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (error: Exception) {
                android.util.Log.w("Clima", "No se pudo consultar ${zona.id}", error)
                modificar(zona) { it.copy(errorActual = "No se pudo actualizar el clima. Comprueba la conexión." +
                    if (it.pronostico != null) " Se conserva el último pronóstico." else "") }
            } finally {
                modificar(zona) { it.copy(actualizando = false) }
            }
        }
    }

    fun descargarHistorial(zona: ZonaClimatica) {
        if (estado(zona).descargandoHistorial || estado(zona).leyendoCopia) return
        modificar(zona) { it.copy(descargandoHistorial = true, errorHistorial = null) }
        alcance.launch {
            try {
                val historial = RepositorioClima(directorio, zona).descargar()
                modificar(zona) { it.copy(historial = historial) }
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (error: Exception) {
                android.util.Log.w("Clima", "No se pudo descargar el historial de ${zona.id}", error)
                modificar(zona) { it.copy(errorHistorial = "No se pudo descargar o guardar el historial. Intenta nuevamente." +
                    if (it.historial != null) " Se conserva la copia anterior." else "") }
            } finally {
                modificar(zona) { it.copy(descargandoHistorial = false) }
            }
        }
    }
}
