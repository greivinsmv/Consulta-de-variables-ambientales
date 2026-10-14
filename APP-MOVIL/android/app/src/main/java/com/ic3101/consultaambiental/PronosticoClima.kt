package com.ic3101.consultaambiental

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

internal const val diasPronostico = 10

internal data class HoraPronostico(
    val registro: RegistroAmbiental,
    val codigo: Int?,
    val lluvia: Double?
)
internal data class DiaPronostico(
    val fecha: LocalDate,
    val minima: Double?,
    val maxima: Double?,
    val codigo: Int?,
    val lluvia: Double?
)
internal data class PronosticoClima(
    val ubicacion: ZonaClimatica,
    val actual: ClimaActual,
    val codigoActual: Int?,
    val esDia: Boolean,
    val horas: List<HoraPronostico>,
    val dias: List<DiaPronostico>
)

/** El pronóstico se consulta aparte del historial: nunca entra en los promedios académicos. */
internal fun urlPronostico(ubicacion: ZonaClimatica): String =
    "https://api.open-meteo.com/v1/forecast?latitude=${ubicacion.latitud}&longitude=${ubicacion.longitud}" +
        "&current=temperature_2m,relative_humidity_2m,dew_point_2m,weather_code,is_day" +
        "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m,weather_code,precipitation_probability" +
        "&daily=temperature_2m_min,temperature_2m_max,weather_code,precipitation_probability_max" +
        "&temperature_unit=celsius&timeformat=unixtime&timezone=America%2FCosta_Rica&forecast_days=$diasPronostico"

private fun JSONObject.numero(campo: String): Double? =
    (opt(campo) as? Number)?.toDouble()?.takeIf { it.isFinite() }
private fun JSONArray.numero(indice: Int): Double? =
    (opt(indice) as? Number)?.toDouble()?.takeIf { it.isFinite() }
private fun Double.codigo(): Int? = takeIf { it >= 0 && it <= 99 && it % 1.0 == 0.0 }?.toInt()
private fun Double.probabilidad(): Double? = takeIf { it in 0.0..100.0 }

/**
 * Valida unidades, tamaños y fechas antes de aceptar una respuesta.
 * El tiempo Unix ya es UTC: no se le suma otra vez el desfase de Costa Rica.
 * Valores ausentes quedan en null, incluido el código de clima.
 */
internal fun interpretarPronostico(json: String, ubicacion: ZonaClimatica, consultadoEn: Long): PronosticoClima {
    val respuesta = JSONObject(json)
    val actual = interpretarClimaActual(json, consultadoEn)
    val unidadesHora = respuesta.getJSONObject("hourly_units")
    val unidadesDia = respuesta.getJSONObject("daily_units")
    require(unidadesHora.getString("time") == "unixtime" && unidadesHora.getString("temperature_2m") == "°C" &&
        unidadesHora.getString("relative_humidity_2m") == "%" && unidadesHora.getString("dew_point_2m") == "°C" &&
        unidadesHora.getString("precipitation_probability") == "%" && unidadesDia.getString("time") == "unixtime" &&
        unidadesDia.getString("temperature_2m_min") == "°C" && unidadesDia.getString("temperature_2m_max") == "°C" &&
        unidadesDia.getString("precipitation_probability_max") == "%") { "Unidades del pronóstico no válidas." }
    val hourly = respuesta.getJSONObject("hourly")
    val daily = respuesta.getJSONObject("daily")
    val tiemposHora = hourly.getJSONArray("time")
    val tiemposDia = daily.getJSONArray("time")
    val campos = mapOf("temperatura" to "temperature_2m", "humedad" to "relative_humidity_2m", "puntoRocio" to "dew_point_2m")
    val seriesHora = (campos.values + listOf("weather_code", "precipitation_probability")).associateWith { hourly.getJSONArray(it) }
    val seriesDia = listOf("temperature_2m_min", "temperature_2m_max", "weather_code", "precipitation_probability_max")
        .associateWith { daily.getJSONArray(it) }
    require(tiemposHora.length() == diasPronostico * 24 && seriesHora.values.all { it.length() == diasPronostico * 24 } &&
        tiemposDia.length() == diasPronostico && seriesDia.values.all { it.length() == diasPronostico }) { "El pronóstico debe contener diez días completos." }
    val hoy = Instant.ofEpochMilli(actual.registro.ts).atZone(zonaConsulta).toLocalDate()
    val inicio = hoy.atStartOfDay(zonaConsulta).toEpochSecond()
    val horas = (0 until diasPronostico * 24).map { i ->
        require(tiemposHora.numero(i) == (inicio + i * 3600L).toDouble()) { "Fechas horarias inesperadas." }
        val valores = campos.mapNotNull { (clave, campo) -> seriesHora.getValue(campo).numero(i)?.let { clave to it } }.toMap()
        HoraPronostico(RegistroAmbiental((inicio + i * 3600L) * 1000, valores),
            seriesHora.getValue("weather_code").numero(i)?.codigo(),
            seriesHora.getValue("precipitation_probability").numero(i)?.probabilidad())
    }
    val dias = (0 until diasPronostico).map { i ->
        val fecha = hoy.plusDays(i.toLong())
        require(tiemposDia.numero(i) == fecha.atStartOfDay(zonaConsulta).toEpochSecond().toDouble()) { "Fechas diarias inesperadas." }
        val minima = seriesDia.getValue("temperature_2m_min").numero(i)
        val maxima = seriesDia.getValue("temperature_2m_max").numero(i)
        require(minima == null || maxima == null || minima <= maxima) { "Extremos diarios inconsistentes." }
        DiaPronostico(fecha, minima, maxima, seriesDia.getValue("weather_code").numero(i)?.codigo(),
            seriesDia.getValue("precipitation_probability_max").numero(i)?.probabilidad())
    }
    val current = respuesta.getJSONObject("current")
    val esDia = current.numero("is_day")
    require(esDia == 0.0 || esDia == 1.0) { "Indicador de día o noche no válido." }
    return PronosticoClima(ubicacion, actual, current.numero("weather_code")?.codigo(), esDia == 1.0, horas, dias)
}

internal suspend fun descargarPronostico(ubicacion: ZonaClimatica): PronosticoClima = withContext(Dispatchers.IO) {
    val conexion = URL(urlPronostico(ubicacion)).openConnection() as HttpsURLConnection
    conexion.connectTimeout = 15000
    conexion.readTimeout = 20000
    val json = try {
        if (conexion.responseCode != 200) throw IOException("Open-Meteo respondió HTTP ${conexion.responseCode}.")
        conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        conexion.disconnect()
    }
    interpretarPronostico(json, ubicacion, Instant.now().toEpochMilli())
}

/** Grupos del código WMO que usa Open-Meteo; un código desconocido no significa cielo despejado. */
internal fun condicionClima(codigo: Int?): String = when (codigo) {
    0 -> "Despejado"
    1, 2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    51, 53, 55, 56, 57 -> "Llovizna"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Lluvia"
    71, 73, 75, 77, 85, 86 -> "Nieve"
    95, 96, 99 -> "Tormenta"
    else -> "Condición no disponible"
}
