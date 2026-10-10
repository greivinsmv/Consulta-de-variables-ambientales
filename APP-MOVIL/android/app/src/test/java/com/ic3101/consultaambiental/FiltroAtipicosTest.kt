package com.ic3101.consultaambiental

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FiltroAtipicosTest {
    private val fecha = LocalDate.of(2026, 10, 10)
    private fun registro(valor: Double) = RegistroAmbiental(fecha.atTime(2,0).atZone(zonaConsulta).toInstant().toEpochMilli(),
        mapOf("temperatura" to valor, "humedad" to 40.0))
    @Test fun apagadoConservaValoresDePrueba() {
        val resultado = filtrarLecturas(listOf(registro(-30.6), registro(62.8)), "temperatura", null)
        assertEquals(2, resultado.utilizados)
        assertEquals(0, resultado.excluidos)
        assertFalse(ConfiguracionFiltro().activo)
    }
    @Test fun limitesInclusivosSeparanValoresAusentesDeAtipicos() {
        val registros = listOf(registro(-10.0), registro(50.0), registro(50.01), registro(-10.01), registro(Double.NaN), RegistroAmbiental(1, emptyMap()))
        val resultado = filtrarLecturas(registros, "temperatura", RangoValores(-10.0,50.0))
        assertEquals(2, resultado.utilizados)
        assertEquals(2, resultado.excluidos)
        assertEquals(2, resultado.sinValor)
    }
    @Test fun unAtipicoDeTemperaturaNoExcluyeHumedad() {
        val registros = listOf(registro(62.8))
        assertEquals(0, filtrarLecturas(registros,"temperatura",RangoValores(-10.0,50.0)).utilizados)
        assertEquals(1, filtrarLecturas(registros,"humedad",RangoValores(0.0,100.0)).utilizados)
    }
    @Test fun aceptaComaDecimalNegativosYLimitesIguales() {
        assertEquals(RangoValores(-10.5,50.5), interpretarLimites(LimitesTexto(" -10,5 ","50.5")))
        assertEquals(RangoValores(0.0,0.0), interpretarLimites(LimitesTexto("0","0")))
    }
    @Test fun rechazaLimitesVaciosInvertidosNoNumericosYNoFinitos() {
        listOf(LimitesTexto("","10"),LimitesTexto("50","10"),LimitesTexto("abc","10"),LimitesTexto("NaN","10"),LimitesTexto("0","Infinity")).forEach {
            assertNull(interpretarLimites(it))
        }
    }
    @Test fun recalculaHoraDiaYExtremosSobreMismoConjunto() {
        val originales = listOf(registro(20.0),registro(30.0),registro(99.0))
        val resultado = filtrarLecturas(originales,"temperatura",RangoValores(-10.0,50.0))
        assertEquals(25.0,promediosPorHora(resultado.registros,fecha,"temperatura").single().promedio,0.0001)
        assertEquals(25.0,promediosDiarios(resultado.registros,RangoFechas(fecha,fecha),"temperatura").single().promedio!!,0.0001)
        assertEquals(30.0,extremosHistoricos(resultado.registros,"temperatura")!!.maximo.valor,0.0001)
        assertEquals(99.0,originales.last().valores.getValue("temperatura"),0.0001)
    }
    @Test fun excluirTodoDaSinDatosYNoPromedioCero() {
        val resultado = filtrarLecturas(listOf(registro(99.0)),"temperatura",RangoValores(-10.0,50.0))
        assertTrue(promediosPorHora(resultado.registros,fecha,"temperatura").isEmpty())
        assertNull(promediosDiarios(resultado.registros,RangoFechas(fecha,fecha),"temperatura").single().promedio)
        assertNull(extremosHistoricos(resultado.registros,"temperatura"))
    }
    @Test fun csvIndicaLosLimitesYExportaElPromedioFiltrado() {
        val limites = RangoValores(-10.0,50.0)
        val datos = filtrarLecturas(listOf(registro(20.0),registro(99.0)),"temperatura",limites).registros
        val csv = csvPorHora(fecha,Variable("temperatura","Temperatura","°C"),promediosPorHora(datos,fecha,"temperatura"),limites)
        assertTrue(csv.contains("\"filtro\";\"limite_inferior\";\"limite_superior\""))
        assertTrue(csv.contains("\"20,000\";\"1\";\"Con datos\";\"Límites configurados\";\"-10,000\";\"50,000\""))
        assertFalse(csv.contains("99,000"))
    }
}
