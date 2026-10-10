package com.ic3101.consultaambiental

import java.time.Instant
import java.time.LocalDate

internal data class RangoFechas(val inicio: LocalDate, val fin: LocalDate)
internal data class PromedioDia(val fecha: LocalDate, val promedio: Double?, val cantidad: Int)

internal fun errorRango(inicio: LocalDate, fin: LocalDate, hoy: LocalDate): String? = when {
    inicio.isAfter(fin) -> "La fecha inicial debe ser anterior o igual a la final."
    inicio.isAfter(hoy) || fin.isAfter(hoy) -> "No puedes consultar fechas futuras."
    else -> null
}

internal fun promediosDiarios(registros: List<RegistroAmbiental>, rango: RangoFechas, variable: String): List<PromedioDia> {
    require(!rango.inicio.isAfter(rango.fin))
    val grupos = registros.mapNotNull { registro ->
        val fecha = Instant.ofEpochMilli(registro.ts).atZone(zonaConsulta).toLocalDate()
        val valor = registro.valores[variable]
        if (fecha.isBefore(rango.inicio) || fecha.isAfter(rango.fin) || valor == null || !valor.isFinite()) null
        else fecha to valor
    }.groupBy({ it.first }, { it.second })
    return generateSequence(rango.inicio) { it.plusDays(1) }.takeWhile { !it.isAfter(rango.fin) }.map { fecha ->
        val valores = grupos[fecha].orEmpty()
        PromedioDia(fecha, if (valores.isEmpty()) null else valores.average(), valores.size)
    }.toList()
}
