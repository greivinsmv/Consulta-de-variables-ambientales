package com.ic3101.consultaambiental

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

internal data class EscalaGrafico(val minimo: Double, val maximo: Double)
internal fun escalaGrafico(valores: List<Double>, incluirCero: Boolean = false): EscalaGrafico {
    require(valores.isNotEmpty() && valores.all { it.isFinite() })
    val bajo = if (incluirCero) minOf(0.0, valores.min()) else valores.min()
    val alto = if (incluirCero) maxOf(0.0, valores.max()) else valores.max()
    val margen = maxOf((alto - bajo) * 0.15, 1.0)
    return EscalaGrafico(bajo - margen, alto + margen)
}
private fun numeroEje(valor: Double) = String.format(Locale.getDefault(), "%.1f", valor)


internal data class PuntoGrafico(val indice: Int, val valor: Double)
internal data class SerieGrafico(val variable: Variable, val puntos: List<PuntoGrafico>)

/**
 * Temperatura y rocío comparten escala en °C. Humedad usa otra escala en %.
 * Una serie sin datos no participa en el cálculo de su escala.
 */
internal fun escalasComparacion(series: List<SerieGrafico>): Map<String, EscalaGrafico> =
    series.groupBy { it.variable.unidad }.mapNotNull { (unidad, grupo) ->
        val valores = grupo.flatMap { it.puntos }.map { it.valor }
        if (valores.isEmpty()) null else unidad to escalaGrafico(valores)
    }.toMap()

/**
 * Gráfica compartida por las consultas horarias y diarias.
 * Los índices representan horas o días consecutivos: solo unimos vecinos,
 * de modo que una fecha sin lecturas deje un hueco visible en su serie.
 */
@Composable
internal fun GraficoComparacion(
    series: List<SerieGrafico>,
    indices: List<Int>,
    etiquetas: List<String>,
    elegido: Int,
    alElegir: (Int) -> Unit
) {
    val escalas = escalasComparacion(series)
    if (escalas.isEmpty() || indices.isEmpty()) return
    val colores = series.associate { it.variable.clave to colorVariable(it.variable.clave) }
    val texto = MaterialTheme.colorScheme.onSurfaceVariant
    val rejilla = MaterialTheme.colorScheme.outlineVariant
    val superficie = MaterialTheme.colorScheme.surface
    val dosEjes = escalas.size > 1
    val unidadIzquierda = if ("°C" in escalas) "°C" else "%"
    val primera = indices.first()
    val ultima = indices.last()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        series.forEach { serie ->
            Row(Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Canvas(Modifier.size(8.dp)) { drawCircle(colores.getValue(serie.variable.clave)) }
                Text(serie.variable.nombre, Modifier.padding(start = 5.dp),
                    style = MaterialTheme.typography.labelSmall, color = colores.getValue(serie.variable.clave))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(unidadIzquierda, style = MaterialTheme.typography.labelSmall, color = texto)
        if (dosEjes) Text("% · eje derecho", style = MaterialTheme.typography.labelSmall, color = colores.getValue("humedad"))
    }
    Canvas(Modifier.fillMaxWidth().height(240.dp).semantics {
        contentDescription = series.joinToString(". ") { serie ->
            serie.variable.nombre + ": " + serie.puntos.joinToString { punto ->
                "${etiquetas[indices.indexOf(punto.indice)]}: ${numeroEje(punto.valor)} ${serie.variable.unidad}"
            }
        }
    }.pointerInput(indices) {
        detectTapGestures { toque ->
            val izquierda = 44.dp.toPx()
            val derecha = size.width - (if (dosEjes) 42.dp else 14.dp).toPx()
            val proporcion = ((toque.x - izquierda) / (derecha - izquierda)).coerceIn(0f, 1f)
            val indice = primera + proporcion * (ultima - primera)
            alElegir(indices.minBy { abs(it - indice) })
        }
    }) {
        val izquierda = 44.dp.toPx()
        val derecha = size.width - (if (dosEjes) 42.dp else 14.dp).toPx()
        val arriba = 18.dp.toPx()
        val abajo = size.height - 32.dp.toPx()
        val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = texto.toArgb(); textSize = 10.sp.toPx() }
        fun x(indice: Int): Float = if (primera == ultima) (izquierda + derecha) / 2 else
            izquierda + (derecha - izquierda) * (indice - primera) / (ultima - primera)
        fun y(valor: Double, unidad: String): Float {
            val escala = escalas.getValue(unidad)
            return abajo - (abajo - arriba) * ((valor - escala.minimo) / (escala.maximo - escala.minimo)).toFloat()
        }
        for (i in 0..4) {
            val altura = abajo - (abajo - arriba) * i / 4
            drawLine(rejilla.copy(alpha = .65f), Offset(izquierda, altura), Offset(derecha, altura), 1.dp.toPx())
            escalas.forEach { (unidad, escala) ->
                val valor = escala.minimo + (escala.maximo - escala.minimo) * i / 4
                val aLaIzquierda = unidad == unidadIzquierda
                pintura.textAlign = if (aLaIzquierda) Paint.Align.RIGHT else Paint.Align.LEFT
                drawContext.canvas.nativeCanvas.drawText(numeroEje(valor),
                    if (aLaIzquierda) izquierda - 6.dp.toPx() else derecha + 6.dp.toPx(), altura + 3.dp.toPx(), pintura)
            }
        }
        if (elegido in indices) drawLine(texto.copy(alpha = .25f), Offset(x(elegido), arriba), Offset(x(elegido), abajo),
            1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        val marcas = if (indices.size <= 4) indices.indices.toList() else listOf(0, indices.size / 3, 2 * indices.size / 3, indices.lastIndex)
        marcas.distinct().forEach { i ->
            pintura.textAlign = when (i) { 0 -> Paint.Align.LEFT; indices.lastIndex -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            drawContext.canvas.nativeCanvas.drawText(etiquetas[i], x(indices[i]), abajo + 23.dp.toPx(), pintura)
        }
        series.forEach { serie ->
            val color = colores.getValue(serie.variable.clave)
            val punteada = serie.variable.clave == "puntoRocio"
            serie.puntos.zipWithNext().forEach { (a, b) ->
                if (b.indice == a.indice + 1) drawLine(color, Offset(x(a.indice), y(a.valor, serie.variable.unidad)),
                    Offset(x(b.indice), y(b.valor, serie.variable.unidad)), 2.5.dp.toPx(),
                    pathEffect = if (punteada) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null)
            }
            serie.puntos.forEach { punto ->
                val centro = Offset(x(punto.indice), y(punto.valor, serie.variable.unidad))
                if (punto.indice == elegido) drawCircle(color.copy(alpha = .2f), 9.dp.toPx(), centro)
                drawCircle(color, 3.5.dp.toPx(), centro)
                drawCircle(superficie, 1.3.dp.toPx(), centro)
            }
        }
    }
    Text(if (series.flatMap { it.puntos }.map { it.indice }.distinct().size == 1) "Un periodo con datos disponibles."
        else "Toca la gráfica para consultar un periodo.", style = MaterialTheme.typography.bodySmall, color = texto)
}
