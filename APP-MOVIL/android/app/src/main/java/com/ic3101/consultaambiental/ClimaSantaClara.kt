package com.ic3101.consultaambiental

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

internal const val latitudSantaClara = 10.36
internal const val longitudSantaClara = -84.51
internal const val urlProveedorClima = "https://open-meteo.com/"
internal const val licenciaClima = "https://creativecommons.org/licenses/by/4.0/"
internal data class OrigenDatos(
    val fuente: String, val ubicacion: String, val latitud: Double, val longitud: Double,
    val proveedor: String, val licencia: String
)
internal val origenSantaClara = OrigenDatos("Open-Meteo · datos modelados", "Santa Clara, San Carlos, Costa Rica",
    latitudSantaClara, longitudSantaClara, urlProveedorClima, licenciaClima)
internal data class HistorialClima(val rango: RangoFechas, val registros: List<RegistroAmbiental>, val descargadoEn: Long) {
    val origen: OrigenDatos get() = origenSantaClara
}
internal fun ultimosDiezDias(hoy: LocalDate) = RangoFechas(hoy.minusDays(10), hoy.minusDays(1))
internal fun urlClima(rango: RangoFechas): String =
    "https://api.open-meteo.com/v1/forecast?latitude=$latitudSantaClara&longitude=$longitudSantaClara" +
        "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m&temperature_unit=celsius" +
        "&timeformat=unixtime&timezone=America%2FCosta_Rica&start_date=${rango.inicio}&end_date=${rango.fin}"

internal fun interpretarClima(json: String, rango: RangoFechas, descargadoEn: Long): HistorialClima {
    require(rango == ultimosDiezDias(rango.fin.plusDays(1))) { "El periodo debe abarcar diez días completos." }
    val respuesta = JSONObject(json)
    require(!respuesta.optBoolean("error")) { "Open-Meteo rechazó la consulta." }
    val unidades = respuesta.getJSONObject("hourly_units")
    require(unidades.getString("time") == "unixtime" && unidades.getString("temperature_2m") == "°C" &&
        unidades.getString("relative_humidity_2m") == "%" && unidades.getString("dew_point_2m") == "°C") {
        "La API devolvió unidades distintas de las solicitadas."
    }
    val horas = respuesta.getJSONObject("hourly")
    val fechas = horas.getJSONArray("time")
    val campos = mapOf("temperatura" to "temperature_2m", "humedad" to "relative_humidity_2m", "puntoRocio" to "dew_point_2m")
    val series = campos.mapValues { (_, campo) -> horas.getJSONArray(campo) }
    require(fechas.length() == 240 && series.values.all { it.length() == fechas.length() }) {
        "La respuesta no contiene las 240 horas del periodo solicitado."
    }
    val inicio = rango.inicio.atStartOfDay(zonaConsulta).toEpochSecond()
    val registros = (0 until fechas.length()).map { indice ->
        val segundos = (fechas.get(indice) as? Number)?.toDouble()
        // El tiempo Unix ya representa UTC; no se vuelve a sumar el desplazamiento horario.
        require(segundos == (inicio + indice * 3600L).toDouble()) { "Las fechas horarias de la API no corresponden al periodo solicitado." }
        val valores = series.mapNotNull { (clave, serie) ->
            val valor = (serie.opt(indice) as? Number)?.toDouble()
            if (valor != null && valor.isFinite()) clave to valor else null
        }.toMap()
        RegistroAmbiental(segundos!!.toLong() * 1000, valores)
    }
    require(registros.any { it.valores.isNotEmpty() }) { "La API no devolvió valores válidos." }
    return HistorialClima(rango, registros, descargadoEn)
}

/** Archivo privado y atómico: conserva la última descarga válida sin escribir en Firebase. */
internal class RepositorioClima(directorio: File) {
    private val archivo = AtomicFile(File(directorio, "clima_santa_clara.json"))
    suspend fun leerGuardado(): HistorialClima? = withContext(Dispatchers.IO) {
        try {
            val copia = JSONObject(archivo.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            require(copia.getDouble("latitud") == latitudSantaClara && copia.getDouble("longitud") == longitudSantaClara)
            interpretarClima(copia.getString("respuesta"),
                RangoFechas(LocalDate.parse(copia.getString("inicio")), LocalDate.parse(copia.getString("fin"))),
                copia.getLong("descargado_en"))
        } catch (_: Exception) { null }
    }
    suspend fun descargar(): HistorialClima = withContext(Dispatchers.IO) {
        val rango = ultimosDiezDias(LocalDate.now(zonaConsulta))
        val conexion = URL(urlClima(rango)).openConnection() as HttpsURLConnection
        conexion.connectTimeout = 15000
        conexion.readTimeout = 20000
        val json = try {
            if (conexion.responseCode != 200) throw IOException("Open-Meteo respondió HTTP ${conexion.responseCode}. Intenta nuevamente más tarde.")
            conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { conexion.disconnect() }
        val historial = interpretarClima(json, rango, Instant.now().toEpochMilli())
        val copia = JSONObject().put("latitud", latitudSantaClara).put("longitud", longitudSantaClara)
            .put("inicio", rango.inicio.toString()).put("fin", rango.fin.toString())
            .put("descargado_en", historial.descargadoEn).put("respuesta", json).toString()
        val salida = archivo.startWrite()
        try {
            salida.write(copia.toByteArray(Charsets.UTF_8))
            archivo.finishWrite(salida)
        } catch (error: Exception) {
            archivo.failWrite(salida)
            throw IOException("No se pudo guardar el historial del clima en el teléfono.", error)
        }
        historial
    }
}
