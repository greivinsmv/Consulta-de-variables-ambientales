package com.ic3101.consultaambiental

internal data class ExtremoHistorico(val valor: Double, val ts: Long, val repeticiones: Int)
internal data class ResumenHistorico(
    val minimo: ExtremoHistorico,
    val maximo: ExtremoHistorico,
    val cantidad: Int
)

internal fun extremosHistoricos(registros: List<RegistroAmbiental>, variable: String): ResumenHistorico? {
    val validos = registros.mapNotNull { registro ->
        val valor = registro.valores[variable]
        if (valor == null || !valor.isFinite()) null else registro.ts to valor
    }
    if (validos.isEmpty()) return null
    fun extremo(valor: Double): ExtremoHistorico {
        val coincidencias = validos.filter { it.second == valor }
        return ExtremoHistorico(valor, coincidencias.minOf { it.first }, coincidencias.size)
    }
    return ResumenHistorico(
        extremo(validos.minOf { it.second }),
        extremo(validos.maxOf { it.second }),
        validos.size
    )
}
