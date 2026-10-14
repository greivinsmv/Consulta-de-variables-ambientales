package com.ic3101.consultaambiental

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ComparacionVariablesTest {
    private val dia = LocalDate.of(2026, 10, 10)
    private fun registro(hora: Int, valores: Map<String, Double>) =
        RegistroAmbiental(dia.atTime(hora, 0).atZone(zonaConsulta).toInstant().toEpochMilli(), valores)

    @Test fun filtraCadaSerieSinEliminarDatosValidosDeOtraVariable() {
        val datos = listOf(
            registro(8, mapOf("temperatura" to 25.0, "humedad" to 45.0, "puntoRocio" to 12.0)),
            registro(9, mapOf("temperatura" to 80.0, "humedad" to 50.0)),
            registro(10, mapOf("temperatura" to 26.0, "humedad" to 140.0))
        )
        val seleccion = variables.map { it.clave }
        val filtro = ConfiguracionFiltro(activo = true)
        val resultado = datosComparacion(datos, seleccion, filtro)
        assertTrue(comparacionValida(seleccion, filtro))
        assertEquals(listOf(8, 10), promediosPorHora(resultado.getValue("temperatura").registros, dia, "temperatura").map { it.hora })
        assertEquals(listOf(8, 9), promediosPorHora(resultado.getValue("humedad").registros, dia, "humedad").map { it.hora })
        assertEquals(1, resultado.getValue("temperatura").excluidos)
        assertEquals(2, resultado.getValue("puntoRocio").sinValor)
        assertEquals(3, datos.size)
    }

    @Test fun temperaturaYRocioCompartenEscalaPeroLaHumedadConservaSuUnidad() {
        val series = listOf(
            SerieGrafico(variables[0], listOf(PuntoGrafico(0, 25.0))),
            SerieGrafico(variables[1], listOf(PuntoGrafico(0, 90.0))),
            SerieGrafico(variables[2], listOf(PuntoGrafico(0, -15.0)))
        )
        val escalas = escalasComparacion(series)
        assertEquals(setOf("°C", "%"), escalas.keys)
        assertTrue(escalas.getValue("°C").minimo < -15)
        assertTrue(escalas.getValue("°C").maximo > 25)
        assertTrue(escalas.getValue("°C").maximo < 90)
        assertTrue(escalas.getValue("%").minimo < 90)
        assertTrue(escalas.getValue("%").maximo > 90)
        assertFalse(escalasComparacion(listOf(SerieGrafico(variables[0], emptyList()))).containsKey("°C"))
    }

    @Test fun limitesInvalidosSoloBloqueanLasVariablesSeleccionadasConFiltroActivo() {
        val invalido = ConfiguracionFiltro(activo = true, limites = ConfiguracionFiltro().limites +
            ("humedad" to LimitesTexto("100", "0")))
        assertTrue(comparacionValida(listOf("temperatura"), invalido))
        assertFalse(comparacionValida(listOf("temperatura", "humedad"), invalido))
        assertTrue(comparacionValida(listOf("temperatura", "humedad"), invalido.copy(activo = false)))
        assertFalse(comparacionValida(emptyList(), invalido))
    }

    @Test fun exportaTodasLasSeriesConUnaCabeceraYLosLimitesDeCadaUna() {
        val documentos = variables.take(2).map { v ->
            csvPorHora(dia, v, listOf(PromedioHora(8, if (v.clave == "temperatura") 25.0 else 70.0, 1)),
                if (v.clave == "temperatura") RangoValores(-10.0, 50.0) else RangoValores(0.0, 100.0), santaClara.origen)
        }
        val csv = combinarCsvSeries(documentos)
        val filas = csv.removePrefix("\uFEFF").trim().split("\r\n")
        assertEquals(49, filas.size)
        assertEquals(1, csv.count { it == '\uFEFF' })
        assertTrue(filas[9].contains("\"Temperatura\";\"°C\";\"25,000\""))
        assertTrue(filas[33].contains("\"Humedad\";\"%\";\"70,000\""))
        assertTrue(filas[9].contains("\"-10,000\";\"50,000\""))
        assertTrue(filas[33].contains("\"0,000\";\"100,000\""))
        assertTrue(filas[1].contains("\"Sin datos\""))
    }
}
