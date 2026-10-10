package com.ic3101.consultaambiental

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// UTF-8 con BOM, separador ; y coma decimal para hojas de cálculo en español.
internal fun documentoCsv(filas: List<List<String>>): String = "\uFEFF" + filas.joinToString("\r\n", postfix = "\r\n") { fila ->
    fila.joinToString(";") { celda -> "\"${celda.replace("\"", "\"\"")}\"" }
}
private fun numeroCsv(valor: Double): String = String.format(Locale.ROOT, "%.3f", valor).replace('.', ',')

internal fun csvPorHora(fecha: LocalDate, variable: Variable, resultados: List<PromedioHora>): String {
    val porHora = resultados.associateBy { it.hora }
    return documentoCsv(listOf(listOf("fecha", "hora_inicio", "hora_fin", "zona_horaria", "variable", "unidad", "promedio", "lecturas", "estado")) +
        (0..23).map { hora ->
            val resultado = porHora[hora]
            listOf(fecha.toString(), String.format(Locale.ROOT, "%02d:00", hora), String.format(Locale.ROOT, "%02d:59", hora),
                zonaConsulta.id, variable.nombre, variable.unidad, resultado?.let { numeroCsv(it.promedio) }.orEmpty(),
                (resultado?.cantidad ?: 0).toString(), if (resultado == null) "Sin datos" else "Con datos")
        })
}
internal fun csvPorDia(variable: Variable, resultados: List<PromedioDia>): String = documentoCsv(
    listOf(listOf("fecha", "zona_horaria", "variable", "unidad", "promedio", "lecturas", "estado")) + resultados.map { resultado ->
        listOf(resultado.fecha.toString(), zonaConsulta.id, variable.nombre, variable.unidad,
            resultado.promedio?.let(::numeroCsv).orEmpty(), resultado.cantidad.toString(),
            if (resultado.promedio == null) "Sin datos" else "Con datos")
    }
)
internal fun csvExtremos(variable: Variable, resumen: ResumenHistorico): String {
    fun fila(tipo: String, extremo: ExtremoHistorico) = listOf(tipo, variable.nombre, variable.unidad, numeroCsv(extremo.valor),
        Instant.ofEpochMilli(extremo.ts).atZone(zonaConsulta).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        extremo.ts.toString(), zonaConsulta.id, extremo.repeticiones.toString(), resumen.cantidad.toString())
    return documentoCsv(listOf(listOf("extremo", "variable", "unidad", "valor", "fecha_primera_ocurrencia", "ts", "zona_horaria", "repeticiones", "lecturas_validas"),
        fila("Mínimo", resumen.minimo), fila("Máximo", resumen.maximo)))
}
