package com.ic3101.consultaambiental

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
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

@Composable
internal fun GraficoHorario(resultados: List<PromedioHora>, unidad: String, elegida: Int, alElegir: (Int) -> Unit) {
    val escala = escalaGrafico(resultados.map { it.promedio })
    val color = MaterialTheme.colorScheme.primary
    val texto = MaterialTheme.colorScheme.onSurfaceVariant
    val rejilla = MaterialTheme.colorScheme.outlineVariant
    val superficie = MaterialTheme.colorScheme.surface
    val primera = resultados.first().hora
    val ultima = resultados.last().hora
    Text("Promedio por hora · $unidad", style = MaterialTheme.typography.titleSmall)
    Canvas(Modifier.fillMaxWidth().height(240.dp)
        .semantics { contentDescription = resultados.joinToString { "${it.hora}:00, ${numeroEje(it.promedio)} $unidad" } }
        .pointerInput(resultados) {
            detectTapGestures { toque ->
                val izquierda = 58.dp.toPx()
                val ancho = size.width - izquierda - 18.dp.toPx()
                val hora = primera + ((toque.x - izquierda) / ancho) * (ultima - primera)
                alElegir(resultados.minBy { abs(it.hora - hora) }.hora)
            }
        }) {
        val izquierda = 58.dp.toPx(); val derecha = size.width - 18.dp.toPx()
        val arriba = 24.dp.toPx(); val abajo = size.height - 38.dp.toPx()
        val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = texto.toArgb(); textSize = 11.sp.toPx() }
        fun y(valor: Double) = abajo - (abajo - arriba) * ((valor - escala.minimo) / (escala.maximo - escala.minimo)).toFloat()
        fun x(hora: Int) = if (primera == ultima) (izquierda + derecha) / 2 else
            izquierda + (derecha - izquierda) * (hora - primera) / (ultima - primera)
        for (i in 0..3) {
            val valor = escala.minimo + (escala.maximo - escala.minimo) * i / 3
            val altura = y(valor)
            drawLine(rejilla, Offset(izquierda, altura), Offset(derecha, altura), 1.dp.toPx())
            pintura.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(numeroEje(valor), izquierda - 8.dp.toPx(), altura + 4.dp.toPx(), pintura)
        }
        val marcas = if (ultima - primera <= 3) (primera..ultima).toList() else listOf(primera, (primera + ultima) / 2, ultima)
        pintura.textAlign = Paint.Align.CENTER
        marcas.distinct().forEach { hora ->
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.getDefault(), "%02d:00", hora), x(hora), abajo + 24.dp.toPx(), pintura)
        }
        resultados.zipWithNext().forEach { (a,b) ->
            if (b.hora == a.hora + 1) drawLine(color, Offset(x(a.hora),y(a.promedio)),Offset(x(b.hora),y(b.promedio)),2.dp.toPx())
        }
        resultados.forEach { r ->
            val punto = Offset(x(r.hora),y(r.promedio))
            if (r.hora == elegida) drawCircle(color.copy(alpha=.16f),10.dp.toPx(),punto)
            drawCircle(color,4.dp.toPx(),punto)
            drawCircle(superficie,1.5.dp.toPx(),punto)
            if (resultados.size <= 6) {
                pintura.textAlign = when(r.hora) { primera -> Paint.Align.LEFT; ultima -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                drawContext.canvas.nativeCanvas.drawText(numeroEje(r.promedio),punto.x,punto.y-12.dp.toPx(),pintura)
            }
        }
    }
    Text(if (resultados.size == 1) "Una sola hora con datos."
        else "Toca un punto para elegir su hora.",
        style = MaterialTheme.typography.bodySmall, color = texto)
}

@Composable
internal fun GraficoDiarioClaro(resultados: List<PromedioDia>, unidad: String) {
    val validos = resultados.mapNotNull { it.promedio }
    val escala = escalaGrafico(validos, incluirCero = true)
    val color = MaterialTheme.colorScheme.primary
    val texto = MaterialTheme.colorScheme.onSurfaceVariant
    val rejilla = MaterialTheme.colorScheme.outlineVariant
    Text("Promedio diario · $unidad", style = MaterialTheme.typography.titleSmall)
    Text("Desliza para ver las fechas.", style = MaterialTheme.typography.bodySmall, color = texto)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ancho = maxOf(maxWidth, (80 + resultados.size * 64).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(ancho).height(240.dp).semantics {
                contentDescription = resultados.joinToString { "${it.fecha}: ${it.promedio?.let(::numeroEje) ?: "Sin datos"} $unidad" }
            }) {
                val izquierda = 58.dp.toPx(); val derecha = size.width - 8.dp.toPx()
                val arriba = 24.dp.toPx(); val abajo = size.height - 38.dp.toPx()
                val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = texto.toArgb(); textSize = 11.sp.toPx() }
                fun y(valor: Double) = abajo - (abajo-arriba)*((valor-escala.minimo)/(escala.maximo-escala.minimo)).toFloat()
                for (i in 0..3) {
                    val valor = escala.minimo + (escala.maximo-escala.minimo)*i/3
                    drawLine(rejilla,Offset(izquierda,y(valor)),Offset(derecha,y(valor)),1.dp.toPx())
                    pintura.textAlign = Paint.Align.RIGHT
                    drawContext.canvas.nativeCanvas.drawText(numeroEje(valor),izquierda-8.dp.toPx(),y(valor)+4.dp.toPx(),pintura)
                }
                drawLine(texto.copy(alpha=.6f),Offset(izquierda,y(0.0)),Offset(derecha,y(0.0)),1.dp.toPx())
                val paso = (derecha-izquierda)/resultados.size
                resultados.forEachIndexed { i,r ->
                    val x = izquierda + paso*(i+.5f)
                    pintura.textAlign = Paint.Align.CENTER
                    drawContext.canvas.nativeCanvas.drawText(r.fecha.format(DateTimeFormatter.ofPattern("dd/MM")),x,abajo+24.dp.toPx(),pintura)
                    r.promedio?.let { valor ->
                        val alto = abs(y(valor)-y(0.0))
                        if (alto == 0f) drawLine(color,Offset(x-paso*.22f,y(0.0)),Offset(x+paso*.22f,y(0.0)),3.dp.toPx())
                        else drawRoundRect(color,Offset(x-paso*.22f,minOf(y(valor),y(0.0))),Size(paso*.44f,alto),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                        drawContext.canvas.nativeCanvas.drawText(numeroEje(valor),x,if(valor>=0) y(valor)-8.dp.toPx() else y(valor)+16.dp.toPx(),pintura)
                    } ?: drawContext.canvas.nativeCanvas.drawText("—",x,y(0.0)-8.dp.toPx(),pintura)
                }
            }
        }
    }
}
