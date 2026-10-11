package com.ic3101.consultaambiental

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ClimaActualTest {
    private val fecha = LocalDate.of(2026, 10, 10).atTime(18, 45).atZone(zonaConsulta).toInstant().toEpochMilli()
    private fun respuesta() = JSONObject().put("current_units", JSONObject().put("time", "unixtime")
        .put("temperature_2m", "°C").put("relative_humidity_2m", "%").put("dew_point_2m", "°C"))
        .put("current", JSONObject().put("time", fecha / 1000).put("interval", 900)
            .put("temperature_2m", 24.1).put("relative_humidity_2m", 94).put("dew_point_2m", 23.2))

    @Test fun consultaLasTresVariablesActualesSinSolicitarElHistorial() {
        val url = urlClimaActual()
        assertTrue(url.contains("latitude=10.36&longitude=-84.51"))
        assertTrue(url.contains("current=temperature_2m,relative_humidity_2m,dew_point_2m"))
        assertTrue(url.contains("timeformat=unixtime&timezone=America%2FCosta_Rica"))
        assertFalse(url.contains("start_date"))
        assertFalse(url.contains("hourly="))
    }
    @Test fun conservaFechaDelModeloYFechaDeConsultaComoTiemposDistintos() {
        val consultado = fecha + 5 * 60 * 1000
        val dato = interpretarClimaActual(respuesta().toString(), consultado)
        assertEquals(fecha, dato.registro.ts)
        assertEquals(consultado, dato.consultadoEn)
        assertEquals(900, dato.intervaloSegundos)
        assertEquals(24.1, dato.registro.valores.getValue("temperatura"), 0.0)
        assertEquals(94.0, dato.registro.valores.getValue("humedad"), 0.0)
        assertEquals(23.2, dato.registro.valores.getValue("puntoRocio"), 0.0)
    }
    @Test fun datoRecienteYDatoAnteriorNoSePresentanIgual() {
        val dato = interpretarClimaActual(respuesta().toString(), fecha + 1000)
        assertFalse(datoActualAnterior(dato, fecha + 10 * 60 * 1000))
        assertTrue(datoActualAnterior(dato, fecha + 60 * 60 * 1000))
    }
    @Test fun noSustituyeDatosAusentesPorCeroYConservaHumedadCeroValida() {
        val json = respuesta()
        json.getJSONObject("current").put("temperature_2m", JSONObject.NULL)
            .put("relative_humidity_2m", 0).put("dew_point_2m", "23.2")
        val dato = interpretarClimaActual(json.toString(), fecha)
        assertFalse(dato.registro.valores.containsKey("temperatura"))
        assertFalse(dato.registro.valores.containsKey("puntoRocio"))
        assertEquals(0.0, dato.registro.valores.getValue("humedad"), 0.0)
    }
    @Test fun rechazaUnaRespuestaSinNingunValorValido() {
        val json = respuesta()
        json.getJSONObject("current").put("temperature_2m", JSONObject.NULL)
            .put("relative_humidity_2m", JSONObject.NULL).put("dew_point_2m", JSONObject.NULL)
        assertThrows(IllegalArgumentException::class.java) { interpretarClimaActual(json.toString(), fecha) }
    }
    @Test fun rechazaFechasOIntervalosInvalidos() {
        for (valor in listOf(-1, "1791680400", 1791680400.5, 253402300800L)) {
            val json = respuesta()
            json.getJSONObject("current").put("time", valor)
            assertThrows(IllegalArgumentException::class.java) { interpretarClimaActual(json.toString(), fecha) }
        }
        for (valor in listOf(0, "900", 900.5, 4000)) {
            val json = respuesta()
            json.getJSONObject("current").put("interval", valor)
            assertThrows(IllegalArgumentException::class.java) { interpretarClimaActual(json.toString(), fecha) }
        }
    }
    @Test fun rechazaUnidadesIncompatiblesYErroresDelServicio() {
        val json = respuesta()
        json.getJSONObject("current_units").put("temperature_2m", "°F")
        assertThrows(IllegalArgumentException::class.java) { interpretarClimaActual(json.toString(), fecha) }
        assertThrows(IllegalArgumentException::class.java) { interpretarClimaActual(respuesta().put("error", true).toString(), fecha) }
    }
}
