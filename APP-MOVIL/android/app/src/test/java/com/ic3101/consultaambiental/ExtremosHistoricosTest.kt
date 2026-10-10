package com.ic3101.consultaambiental

import org.junit.Assert.*
import org.junit.Test

class ExtremosHistoricosTest {
    private fun registro(ts: Long, valor: Double) = RegistroAmbiental(ts, mapOf("temperatura" to valor))

    @Test fun obtieneValoresOriginalesConSusFechas() {
        val resultado = extremosHistoricos(listOf(registro(100, 30.0), registro(200, 10.0), registro(300, 20.0)), "temperatura")!!
        assertEquals(10.0, resultado.minimo.valor, 0.0001)
        assertEquals(200L, resultado.minimo.ts)
        assertEquals(30.0, resultado.maximo.valor, 0.0001)
        assertEquals(100L, resultado.maximo.ts)
        assertEquals(3, resultado.cantidad)
    }
    @Test fun empatesMuestranFechaMasAntiguaSinDependerDelOrden() {
        val resultado = extremosHistoricos(listOf(registro(400, 10.0), registro(300, 30.0), registro(100, 10.0), registro(200, 30.0)), "temperatura")!!
        assertEquals(100L, resultado.minimo.ts)
        assertEquals(200L, resultado.maximo.ts)
        assertEquals(2, resultado.minimo.repeticiones)
        assertEquals(2, resultado.maximo.repeticiones)
    }
    @Test fun aceptaCeroYNegativosEIgnoraNoFinitos() {
        val resultado = extremosHistoricos(listOf(registro(1, -5.0), registro(2, 0.0), registro(3, Double.NaN), registro(4, Double.POSITIVE_INFINITY)), "temperatura")!!
        assertEquals(-5.0, resultado.minimo.valor, 0.0001)
        assertEquals(0.0, resultado.maximo.valor, 0.0001)
        assertEquals(2, resultado.cantidad)
    }
    @Test fun devuelveSinDatosParaVariableAusenteOHistorialVacio() {
        assertNull(extremosHistoricos(emptyList(), "temperatura"))
        assertNull(extremosHistoricos(listOf(registro(1, 24.0)), "humedad"))
        assertNull(extremosHistoricos(listOf(registro(1, Double.NaN)), "temperatura"))
    }
    @Test fun lecturaUnicaEsMinimoYMaximo() {
        val resultado = extremosHistoricos(listOf(registro(123, 9.6)), "temperatura")!!
        assertEquals(resultado.minimo, resultado.maximo)
        assertEquals(1, resultado.cantidad)
    }
    @Test fun todasIgualesCuentanRepeticionesYConservanPrimeraFecha() {
        val resultado = extremosHistoricos(listOf(registro(20, 24.0), registro(10, 24.0)), "temperatura")!!
        assertEquals(resultado.minimo, resultado.maximo)
        assertEquals(2, resultado.minimo.repeticiones)
        assertEquals(10L, resultado.minimo.ts)
    }
}
