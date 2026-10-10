package com.ic3101.consultaambiental

import org.junit.Assert.*
import org.junit.Test

class EscalaGraficoTest {
    @Test fun constanteTieneMargenYNoDividePorCero() {
        val escala = escalaGrafico(listOf(41.4, 41.4))
        assertTrue(escala.minimo < 41.4)
        assertTrue(escala.maximo > 41.4)
    }
    @Test fun admiteNegativosYContieneTodosLosValores() {
        val escala = escalaGrafico(listOf(-39.5, 20.3))
        assertTrue(escala.minimo < -39.5)
        assertTrue(escala.maximo > 20.3)
    }
    @Test fun barrasPositivasYNegativasIncluyenLineaCero() {
        val positivo = escalaGrafico(listOf(20.0, 30.0), true)
        val negativo = escalaGrafico(listOf(-30.0, -20.0), true)
        assertTrue(positivo.minimo < 0 && positivo.maximo > 30)
        assertTrue(negativo.minimo < -30 && negativo.maximo > 0)
    }
    @Test fun ceroTieneEscalaValida() {
        assertEquals(EscalaGrafico(-1.0, 1.0), escalaGrafico(listOf(0.0), true))
    }
}
