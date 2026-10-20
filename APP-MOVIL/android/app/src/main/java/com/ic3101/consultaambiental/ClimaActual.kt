package com.ic3101.consultaambiental

import org.json.JSONObject

internal const val intervaloConsultaClima = 15 * 60 * 1000L
internal data class ClimaActual(val registro: RegistroAmbiental, val consultadoEn: Long, val intervaloSegundos: Int)
/** Lee los valores actuales y conserva por separado la fecha del modelo y la de consulta. */
internal fun interpretarClimaActual(json: String, consultadoEn: Long): ClimaActual {
    val respuesta = JSONObject(json)
    require(!respuesta.optBoolean("error")) { "Open-Meteo rechazó la consulta actual." }
    val unidades = respuesta.getJSONObject("current_units")
    require(unidades.getString("time") == "unixtime" && unidades.getString("temperature_2m") == "°C" &&
        unidades.getString("relative_humidity_2m") == "%" && unidades.getString("dew_point_2m") == "°C") {
        "La API devolvió unidades distintas de las solicitadas."
    }
    val actual = respuesta.getJSONObject("current")
    val segundos = (actual.get("time") as? Number)?.toDouble()
    require(segundos != null && segundos.isFinite() && segundos >= 0 && segundos <= 253402300799.0 && segundos % 1.0 == 0.0) {
        "La fecha del dato actual no es válida."
    }
    val intervalo = (actual.get("interval") as? Number)?.toDouble()
    require(intervalo != null && intervalo.isFinite() && intervalo in 1.0..3600.0 && intervalo % 1.0 == 0.0) {
        "El intervalo del dato actual no es válido."
    }
    val campos = mapOf("temperatura" to "temperature_2m", "humedad" to "relative_humidity_2m", "puntoRocio" to "dew_point_2m")
    val valores = campos.mapNotNull { (clave, campo) ->
        val valor = (actual.opt(campo) as? Number)?.toDouble()
        if (valor != null && valor.isFinite()) clave to valor else null
    }.toMap()
    require(valores.isNotEmpty()) { "La API no devolvió valores actuales válidos." }
    return ClimaActual(RegistroAmbiental(segundos!!.toLong() * 1000, valores), consultadoEn, intervalo!!.toInt())
}

internal fun datoActualAnterior(clima: ClimaActual, ahora: Long): Boolean =
    ahora - clima.registro.ts > maxOf(30 * 60 * 1000L, clima.intervaloSegundos * 2000L)
