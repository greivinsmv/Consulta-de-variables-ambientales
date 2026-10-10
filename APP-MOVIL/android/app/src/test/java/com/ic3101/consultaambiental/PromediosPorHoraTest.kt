package com.ic3101.consultaambiental

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromediosPorHoraTest {
    private val dia = LocalDate.of(2026, 10, 10)
    private fun registro(hora: Int, minuto: Int, valor: Double) = RegistroAmbiental(
        dia.atTime(hora, minuto).atZone(zonaConsulta).toInstant().toEpochMilli(),
        mapOf("temperatura" to valor)
    )
    @Test fun agrupaPorHoraYCuentaLecturas() {
        val resultado = promediosPorHora(listOf(registro(8, 0, 20.0), registro(8, 30, 24.0), registro(9, 0, 30.0)), dia, "temperatura")
        assertEquals(listOf(8, 9), resultado.map { it.hora })
        assertEquals(22.0, resultado[0].promedio, 0.0001)
        assertEquals(2, resultado[0].cantidad)
    }
    @Test fun respetaMedianocheLocalYExcluyeOtroDia() {
        val siguienteDia = RegistroAmbiental(dia.plusDays(1).atStartOfDay(zonaConsulta).toInstant().toEpochMilli(), mapOf("temperatura" to 99.0))
        val resultado = promediosPorHora(listOf(registro(0, 0, 20.0), registro(23, 59, 24.0), siguienteDia), dia, "temperatura")
        assertEquals(listOf(0, 23), resultado.map { it.hora })
    }
    @Test fun ignoraValoresAusentesYNoFinitosSinInventarHoras() {
        val datos = listOf(registro(8, 0, Double.NaN), registro(9, 0, 0.0))
        assertTrue(promediosPorHora(datos, dia, "humedad").isEmpty())
        val resultado = promediosPorHora(datos, dia, "temperatura")
        assertEquals(1, resultado.size)
        assertEquals(9, resultado.first().hora)
        assertEquals(0.0, resultado.first().promedio, 0.0001)
    }
}
