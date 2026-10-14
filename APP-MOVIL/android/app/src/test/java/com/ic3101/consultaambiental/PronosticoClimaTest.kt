package com.ic3101.consultaambiental

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PronosticoClimaTest {
    private val hoy = LocalDate.of(2026, 10, 13)
    private val inicio = hoy.atStartOfDay(zonaConsulta).toEpochSecond()
    private val sanJose = zonasClimaticas.first { it.id == "san_jose" }

    private fun respuesta(): JSONObject {
        val horas = JSONObject()
            .put("time", JSONArray((0 until 240).map { inicio + it * 3600L }))
            .put("temperature_2m", JSONArray(List(240) { 25.0 }))
            .put("relative_humidity_2m", JSONArray(List(240) { 80.0 }))
            .put("dew_point_2m", JSONArray(List(240) { 20.0 }))
            .put("weather_code", JSONArray(List(240) { 61 }))
            .put("precipitation_probability", JSONArray(List(240) { 70.0 }))
        val dias = JSONObject()
            .put("time", JSONArray((0 until 10).map { inicio + it * 86400L }))
            .put("temperature_2m_min", JSONArray(List(10) { 18.0 }))
            .put("temperature_2m_max", JSONArray(List(10) { 29.0 }))
            .put("weather_code", JSONArray(List(10) { 61 }))
            .put("precipitation_probability_max", JSONArray(List(10) { 80.0 }))
        return JSONObject()
            .put("current_units", JSONObject().put("time", "unixtime").put("temperature_2m", "°C")
                .put("relative_humidity_2m", "%").put("dew_point_2m", "°C"))
            .put("current", JSONObject().put("time", inicio + 8 * 3600).put("interval", 900)
                .put("temperature_2m", 25).put("relative_humidity_2m", 80).put("dew_point_2m", 20)
                .put("weather_code", 61).put("is_day", 1))
            .put("hourly_units", JSONObject().put("time", "unixtime").put("temperature_2m", "°C")
                .put("relative_humidity_2m", "%").put("dew_point_2m", "°C").put("precipitation_probability", "%"))
            .put("daily_units", JSONObject().put("time", "unixtime").put("temperature_2m_min", "°C")
                .put("temperature_2m_max", "°C").put("precipitation_probability_max", "%"))
            .put("hourly", horas).put("daily", dias)
    }

    @Test fun consultaLasCoordenadasDeLaZonaYPideDiezDiasConCondiciones() {
        val url = urlPronostico(sanJose)
        assertTrue(url.contains("latitude=9.93&longitude=-84.08"))
        assertTrue(url.contains("weather_code,is_day"))
        assertTrue(url.contains("forecast_days=10"))
        assertFalse(url.contains("start_date"))
        assertTrue(urlClima(ultimosDiezDias(hoy), sanJose).contains("latitude=9.93&longitude=-84.08"))
    }

    @Test fun conservaZonaUnidadesYFechasLocalesSinDuplicarElDesfase() {
        val pronostico = interpretarPronostico(respuesta().toString(), sanJose, inicio * 1000 + 10000)
        assertEquals(sanJose, pronostico.ubicacion)
        assertEquals(240, pronostico.horas.size)
        assertEquals(10, pronostico.dias.size)
        assertEquals(inicio * 1000, pronostico.horas.first().registro.ts)
        assertEquals(hoy, pronostico.dias.first().fecha)
        assertEquals(hoy.plusDays(9), pronostico.dias.last().fecha)
        assertTrue(pronostico.esDia)
        assertEquals("Lluvia", condicionClima(pronostico.codigoActual))
        assertEquals(70.0, pronostico.horas.first().lluvia!!, 0.0)
    }

    @Test fun noInventaSolNiValoresCuandoLaApiDevuelveNulos() {
        val json = respuesta()
        json.getJSONObject("current").put("weather_code", JSONObject.NULL)
        json.getJSONObject("hourly").getJSONArray("temperature_2m").put(0, JSONObject.NULL)
        json.getJSONObject("hourly").getJSONArray("precipitation_probability").put(0, 101)
        json.getJSONObject("daily").getJSONArray("temperature_2m_min").put(0, JSONObject.NULL)
        val pronostico = interpretarPronostico(json.toString(), sanJose, inicio * 1000)
        assertNull(pronostico.codigoActual)
        assertEquals("Condición no disponible", condicionClima(pronostico.codigoActual))
        assertFalse(pronostico.horas.first().registro.valores.containsKey("temperatura"))
        assertNull(pronostico.horas.first().lluvia)
        assertNull(pronostico.dias.first().minima)
    }

    @Test fun rechazaArraysIncompletosFechasDesordenadasYUnidadesDistintas() {
        val corta = respuesta()
        corta.getJSONObject("hourly").getJSONArray("time").remove(239)
        assertThrows(IllegalArgumentException::class.java) { interpretarPronostico(corta.toString(), sanJose, 0) }
        val fecha = respuesta()
        fecha.getJSONObject("hourly").getJSONArray("time").put(1, inicio)
        assertThrows(IllegalArgumentException::class.java) { interpretarPronostico(fecha.toString(), sanJose, 0) }
        val unidades = respuesta()
        unidades.getJSONObject("daily_units").put("temperature_2m_max", "°F")
        assertThrows(IllegalArgumentException::class.java) { interpretarPronostico(unidades.toString(), sanJose, 0) }
    }

    @Test fun rechazaMinimosMayoresQueMaximosEIndicadoresInvalidos() {
        val extremos = respuesta()
        extremos.getJSONObject("daily").getJSONArray("temperature_2m_min").put(0, 40)
        assertThrows(IllegalArgumentException::class.java) { interpretarPronostico(extremos.toString(), sanJose, 0) }
        val indicador = respuesta()
        indicador.getJSONObject("current").put("is_day", "1")
        assertThrows(IllegalArgumentException::class.java) { interpretarPronostico(indicador.toString(), sanJose, 0) }
    }

    @Test fun interpretaUnaRespuestaRealDelServicioSinConexionDuranteLaPrueba() {
        // Respuesta pública de Santa Clara guardada como fixture para detectar cambios de esquema.
        val json = javaClass.getResourceAsStream("/pronostico_open_meteo.json")!!.bufferedReader().use { it.readText() }
        val fuente = JSONObject(json)
        val consultado = fuente.getJSONObject("current").getLong("time") * 1000
        val clima = interpretarPronostico(json, santaClara, consultado)
        assertEquals(10, clima.dias.size)
        assertEquals(240, clima.horas.size)
        assertEquals(fuente.getJSONObject("current").getDouble("temperature_2m"),
            clima.actual.registro.valores.getValue("temperatura"), 0.0)
        assertEquals(fuente.getJSONObject("current").getInt("weather_code"), clima.codigoActual)
        assertEquals(fuente.getJSONObject("current").getInt("is_day") == 1, clima.esDia)
        assertEquals(santaClara, clima.ubicacion)
    }

    @Test fun todasLasZonasTienenIdentificadoresUnicosYProcedenciaPropia() {
        assertEquals(zonasClimaticas.size, zonasClimaticas.map { it.id }.distinct().size)
        assertTrue(zonasClimaticas.all { it.id.matches(Regex("[a-z_]+")) })
        val historial = HistorialClima(ultimosDiezDias(hoy), emptyList(), 0, sanJose)
        assertEquals(sanJose.origen, historial.origen)
        assertNotEquals(santaClara.origen, historial.origen)
    }
}
