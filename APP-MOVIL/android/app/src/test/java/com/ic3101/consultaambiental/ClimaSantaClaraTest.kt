package com.ic3101.consultaambiental

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ClimaSantaClaraTest {
    private val hoy = LocalDate.of(2026, 10, 11)
    private val rango = ultimosDiezDias(hoy)
    private val descargadoEn = hoy.atStartOfDay(zonaConsulta).toInstant().toEpochMilli()
    private fun respuesta(): JSONObject {
        val inicio = rango.inicio.atStartOfDay(zonaConsulta).toEpochSecond()
        return JSONObject().put("hourly_units", JSONObject().put("time", "unixtime")
            .put("temperature_2m", "°C").put("relative_humidity_2m", "%").put("dew_point_2m", "°C"))
            .put("hourly", JSONObject().put("time", JSONArray((0 until 240).map { inicio + it * 3600L }))
                .put("temperature_2m", JSONArray((0 until 240).map { 20.0 + it % 24 }))
                .put("relative_humidity_2m", JSONArray((0 until 240).map { 70.0 }))
                .put("dew_point_2m", JSONArray((0 until 240).map { 15.0 })))
    }
    private fun rechaza(respuesta: JSONObject) {
        try { interpretarClima(respuesta.toString(), rango, descargadoEn); fail("Debía rechazar una respuesta inválida") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun periodoExcluyeHoyYFuncionaEntreMesesYAnioBisiesto() {
        assertEquals(RangoFechas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10)), rango)
        assertEquals(RangoFechas(LocalDate.of(2024, 2, 20), LocalDate.of(2024, 2, 29)), ultimosDiezDias(LocalDate.of(2024, 3, 1)))
        val url = urlClima(rango)
        assertTrue(url.contains("latitude=10.36&longitude=-84.51"))
        assertTrue(url.contains("start_date=2026-10-01&end_date=2026-10-10"))
        assertTrue(url.contains("timeformat=unixtime&timezone=America%2FCosta_Rica"))
        assertFalse(url.contains("past_days"))
    }
    @Test fun convierte240HorasSinDesplazarFechasLocales() {
        val datos = interpretarClima(respuesta().toString(), rango, descargadoEn)
        assertEquals(240, datos.registros.size)
        assertEquals(rango.inicio.atStartOfDay(zonaConsulta).toInstant().toEpochMilli(), datos.registros.first().ts)
        assertEquals(rango.fin.atTime(23, 0).atZone(zonaConsulta).toInstant().toEpochMilli(), datos.registros.last().ts)
        assertTrue(datos.registros.all { Instant.ofEpochMilli(it.ts).atZone(zonaConsulta).toLocalDate().isBefore(hoy) })
        assertEquals(15.0, datos.registros.first().valores.getValue("puntoRocio"), 0.0)
    }
    @Test fun valoresAusentesSeOmitenPorVariableSinInventarCeros() {
        val json = respuesta()
        json.getJSONObject("hourly").getJSONArray("temperature_2m").put(0, JSONObject.NULL).put(1, "22")
        val datos = interpretarClima(json.toString(), rango, descargadoEn).registros
        assertFalse(datos[0].valores.containsKey("temperatura"))
        assertFalse(datos[1].valores.containsKey("temperatura"))
        assertEquals(70.0, datos[0].valores.getValue("humedad"), 0.0)
        assertEquals(22, promediosPorHora(datos, rango.inicio, "temperatura").size)
        assertEquals(24, promediosPorHora(datos, rango.inicio, "humedad").size)
    }
    @Test fun rechazaHorasDuplicadasFueraDelPeriodoYSeriesIncompletas() {
        val duplicado = respuesta()
        val horas = duplicado.getJSONObject("hourly").getJSONArray("time")
        horas.put(1, horas.getLong(0))
        rechaza(duplicado)
        val fueraDeRango = respuesta()
        fueraDeRango.getJSONObject("hourly").getJSONArray("time").put(0, descargadoEn / 1000)
        rechaza(fueraDeRango)
        val incompleto = respuesta()
        incompleto.getJSONObject("hourly").put("temperature_2m", JSONArray(listOf(20.0)))
        rechaza(incompleto)
    }
    @Test fun rechazaUnidadesIncorrectasYErroresDelServicio() {
        val json = respuesta()
        json.getJSONObject("hourly_units").put("temperature_2m", "°F")
        rechaza(json)
        rechaza(respuesta().put("error", true))
    }
    @Test fun datosHorariosAlimentanPromediosExtremosYFiltro() {
        val registros = interpretarClima(respuesta().toString(), rango, descargadoEn).registros
        val dias = promediosDiarios(registros, rango, "temperatura")
        assertEquals(10, dias.size)
        assertTrue(dias.all { it.cantidad == 24 && it.promedio == 31.5 })
        val extremos = extremosHistoricos(registros, "temperatura")!!
        assertEquals(20.0, extremos.minimo.valor, 0.0)
        assertEquals(43.0, extremos.maximo.valor, 0.0)
        assertEquals(10, extremos.minimo.repeticiones)
        val filtrados = filtrarLecturas(registros, "temperatura", RangoValores(20.0, 30.0))
        assertEquals(110, filtrados.utilizados)
        assertEquals(130, filtrados.excluidos)
        assertTrue(promediosDiarios(filtrados.registros, rango, "temperatura").all { it.promedio == 25.0 })
    }
    @Test fun lasTresExportacionesIdentificanFuenteUbicacionLicenciaYFiltro() {
        val datos = interpretarClima(respuesta().toString(), rango, descargadoEn)
        val variable = variables.first()
        val filtro = RangoValores(20.0, 30.0)
        val registros = filtrarLecturas(datos.registros, variable.clave, filtro).registros
        val archivos = listOf(
            csvPorHora(rango.inicio, variable, promediosPorHora(registros, rango.inicio, variable.clave), filtro, datos.origen),
            csvPorDia(variable, promediosDiarios(registros, rango, variable.clave), filtro, datos.origen),
            csvExtremos(variable, extremosHistoricos(registros, variable.clave)!!, filtro, datos.origen))
        archivos.forEach { csv ->
            assertTrue(csv.contains("Santa Clara, San Carlos, Costa Rica"))
            assertTrue(csv.contains("Open-Meteo · datos modelados"))
            assertTrue(csv.contains(licenciaClima))
            assertTrue(csv.contains("\"latitud_solicitada\";\"longitud_solicitada\""))
            assertTrue(csv.contains("\"20,000\";\"30,000\""))
        }
    }
}
