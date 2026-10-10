package com.ic3101.consultaambiental

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromediosDiariosTest {
    private val dia = LocalDate.of(2026, 10, 10)
    private fun lectura(fecha: LocalDate, hora: Int, valores: Map<String, Double>) = RegistroAmbiental(
        fecha.atTime(hora, 0).atZone(zonaConsulta).toInstant().toEpochMilli(), valores
    )
    @Test fun promediaLecturasSinPromediarPromediosHorarios() {
        val datos = listOf(lectura(dia, 0, mapOf("temperatura" to 10.0)),
            lectura(dia, 0, mapOf("temperatura" to 20.0)), lectura(dia, 1, mapOf("temperatura" to 30.0)))
        val resultado = promediosDiarios(datos, RangoFechas(dia, dia), "temperatura").single()
        assertEquals(20.0, resultado.promedio!!, 0.0001)
        assertEquals(3, resultado.cantidad)
    }
    @Test fun incluyeAmbosExtremosRespetaZonaYConservaDiasVacios() {
        val inicio = dia.minusDays(2)
        val datos = listOf(lectura(inicio.minusDays(1), 23, mapOf("temperatura" to 99.0)),
            lectura(inicio, 0, mapOf("temperatura" to 20.0)),
            lectura(dia, 23, mapOf("temperatura" to 24.0)),
            lectura(dia.plusDays(1), 0, mapOf("temperatura" to 99.0)))
        val resultado = promediosDiarios(datos, RangoFechas(inicio, dia), "temperatura")
        assertEquals(3, resultado.size)
        assertEquals(20.0, resultado.first().promedio!!, 0.0001)
        assertNull(resultado[1].promedio)
        assertEquals(0, resultado[1].cantidad)
        assertEquals(24.0, resultado.last().promedio!!, 0.0001)
    }
    @Test fun calculaCadaVariableConSusLecturasValidas() {
        val datos = listOf(lectura(dia, 1, mapOf("temperatura" to Double.NaN, "humedad" to 40.0)),
            lectura(dia, 2, mapOf("temperatura" to 0.0, "humedad" to Double.POSITIVE_INFINITY)))
        val rango = RangoFechas(dia, dia)
        assertEquals(0.0, promediosDiarios(datos, rango, "temperatura").single().promedio!!, 0.0001)
        assertEquals(40.0, promediosDiarios(datos, rango, "humedad").single().promedio!!, 0.0001)
        assertNull(promediosDiarios(datos, rango, "puntoRocio").single().promedio)
    }
    @Test fun admiteUnDiaYRechazaRangosInvertidosYFuturos() {
        assertNull(errorRango(dia, dia, dia))
        assertNotNull(errorRango(dia, dia.minusDays(1), dia))
        assertNotNull(errorRango(dia, dia.plusDays(1), dia))
        assertNotNull(errorRango(dia.plusDays(1), dia.plusDays(1), dia))
    }
    @Test fun listaDiasSinDatosAlCruzarMes() {
        val inicio = LocalDate.of(2026, 9, 30)
        val fin = LocalDate.of(2026, 10, 2)
        val resultado = promediosDiarios(emptyList(), RangoFechas(inicio, fin), "humedad")
        assertEquals(listOf(inicio, inicio.plusDays(1), fin), resultado.map { it.fecha })
        assertTrue(resultado.all { it.promedio == null && it.cantidad == 0 })
    }
}
