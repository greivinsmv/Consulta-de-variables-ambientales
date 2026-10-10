package com.ic3101.consultaambiental

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ExportacionCsvTest {
    private val variable = Variable("temperatura", "Temperatura", "°C")
    private val fecha = LocalDate.of(2026, 10, 10)

    @Test fun escapaComillasSeparadoresYSaltosDeLinea() {
        val csv = documentoCsv(listOf(listOf("texto;con;separador", "dice \"hola\"", "dos\nlineas")))
        assertEquals("\uFEFF\"texto;con;separador\";\"dice \"\"hola\"\"\";\"dos\nlineas\"\r\n", csv)
    }
    @Test fun horarioIncluye24HorasYNoConvierteHuecosEnCero() {
        val csv = csvPorHora(fecha, variable, listOf(PromedioHora(2, 0.0, 4)))
        val filas = csv.removePrefix("\uFEFF").trimEnd('\r','\n').split("\r\n")
        assertEquals(25, filas.size)
        assertTrue(filas[1].endsWith(";\"\";\"0\";\"Sin datos\""))
        assertTrue(filas[3].contains("\"02:00\";\"02:59\""))
        assertTrue(filas[3].endsWith(";\"0,000\";\"4\";\"Con datos\""))
    }
    @Test fun exportaVariableUnidadesYHorarioSinDependerDelIdiomaDelTelefono() {
        val csv = csvPorHora(fecha, variable, listOf(PromedioHora(23, -30.6, 1)))
        assertTrue(csv.contains("\"America/Costa_Rica\";\"Temperatura\";\"°C\";\"-30,600\""))
        assertTrue(csv.contains("\"23:00\";\"23:59\""))
        assertFalse(csv.contains("Humedad"))
    }
    @Test fun diarioConservaTodasLasFechasDelRangoYValoresNegativos() {
        val csv = csvPorDia(variable, listOf(PromedioDia(fecha, -5.25, 2), PromedioDia(fecha.plusDays(1), null, 0)))
        assertTrue(csv.contains("\"2026-10-10\""))
        assertTrue(csv.contains("\"-5,250\";\"2\";\"Con datos\""))
        assertTrue(csv.contains("\"2026-10-11\""))
        assertTrue(csv.contains("\"\";\"0\";\"Sin datos\""))
    }
    @Test fun extremosMantienenFechaLocalTimestampConteoYRepeticiones() {
        val ts = fecha.atTime(2, 30).atZone(zonaConsulta).toInstant().toEpochMilli()
        val csv = csvExtremos(variable, ResumenHistorico(ExtremoHistorico(-5.0, ts, 2), ExtremoHistorico(24.0, ts + 60000, 1), 4))
        assertTrue(csv.contains("\"Mínimo\";\"Temperatura\";\"°C\";\"-5,000\";\"2026-10-10 02:30:00\""))
        assertTrue(csv.contains("\"$ts\";\"America/Costa_Rica\";\"2\";\"4\""))
        assertTrue(csv.contains("\"Máximo\""))
    }
}
