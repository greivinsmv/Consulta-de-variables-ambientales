package com.ic3101.consultaambiental

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val idiomaClima = Locale.forLanguageTag("es-CR")
private fun numeroClima(valor: Double?, unidad: String = "°"): String =
    valor?.let { String.format(Locale.getDefault(), "%.0f%s", it, unidad) } ?: "—"

@Composable
internal fun SelectorZonaClima(ubicacion: ZonaClimatica, alElegir: (ZonaClimatica) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text("${ubicacion.nombre} · ${ubicacion.region}", Modifier.weight(1f))
        Text("Cambiar ▾", style = MaterialTheme.typography.labelMedium)
    }
    if (abierto) Dialog(onDismissRequest = { abierto = false }) {
        var busqueda by remember { mutableStateOf("") }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Zonas de Costa Rica", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(busqueda, { busqueda = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Buscar zona o provincia") })
                val zonas = zonasClimaticas.filter { normalizarZona("${it.nombre} ${it.region}").contains(normalizarZona(busqueda)) }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    zonas.forEach { zona ->
                        Card(onClick = { alElegir(zona); abierto = false }, modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (zona.id == ubicacion.id)
                                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(zona.nombre, style = MaterialTheme.typography.titleMedium)
                                    Text(zona.region, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(if (zona.id == ubicacion.id) "✓" else "›", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    if (zonas.isEmpty()) Text("No hay zonas que coincidan.")
                }
                TextButton(onClick = { abierto = false }, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
            }
        }
    }
}

private fun normalizarZona(texto: String) = Normalizer.normalize(texto, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).trim()

@Composable
internal fun VistaPronostico(clima: PronosticoClima?, cargando: Boolean, mensaje: String?, alActualizar: () -> Unit) {
    var variable by remember { mutableStateOf("temperatura") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (clima != null) {
            CabeceraPronostico(clima)
            if (datoActualAnterior(clima.actual, System.currentTimeMillis())) Text(
                "Mostrando el último pronóstico recibido. Actualiza para consultar las condiciones recientes.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Próximas 24 horas", style = MaterialTheme.typography.titleMedium)
                    Text("La última fila indica la probabilidad de lluvia.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectorVariable(variable) { variable = it }
                    val seleccion = variables.first { it.clave == variable }
                    val horas = clima.horas.filter { it.registro.ts >= clima.actual.registro.ts }.take(24)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        horas.forEach { hora ->
                            Column(Modifier.width(64.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val fecha = Instant.ofEpochMilli(hora.registro.ts).atZone(zonaConsulta)
                                Text(fecha.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelMedium)
                                IconoClima(hora.codigo, fecha.hour in 6..17, Modifier.size(28.dp))
                                Text(numeroClima(hora.registro.valores[variable], seleccion.unidad), style = MaterialTheme.typography.titleSmall,
                                    color = colorVariable(variable))
                                Text(numeroClima(hora.lluvia, "%"), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { contentDescription = "Probabilidad de precipitación: ${numeroClima(hora.lluvia, "%")}" })
                            }
                        }
                    }
                }
            }
            PronosticoPorDia(clima.dias)
        } else if (!cargando && mensaje == null) Text("Consulta el pronóstico de la zona seleccionada.")
        TextButton(onClick = alActualizar, enabled = !cargando, modifier = Modifier.align(Alignment.End)) {
            Text(if (cargando) "Actualizando…" else "Actualizar clima")
        }
    }
}

@Composable
private fun CabeceraPronostico(clima: PronosticoClima) {
    val valores = clima.actual.registro.valores
    val hoy = clima.dias.firstOrNull()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))) {
        CieloClima(clima.codigoActual, clima.esDia, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(clima.ubicacion.nombre, style = MaterialTheme.typography.headlineSmall, color = Color.White,
                fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(numeroClima(valores["temperatura"]), fontSize = 72.sp, color = Color.White, fontWeight = FontWeight.Light)
                IconoClima(clima.codigoActual, clima.esDia, Modifier.size(56.dp))
            }
            Text(condicionClima(clima.codigoActual), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Máx. ${numeroClima(hoy?.maxima)} · Mín. ${numeroClima(hoy?.minima)}", color = Color.White.copy(alpha = .85f))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Humedad", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium)
                    Text(numeroClima(valores["humedad"], "%"), color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Punto de rocío", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium)
                    Text(numeroClima(valores["puntoRocio"], " °C"), color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
            val fecha = Instant.ofEpochMilli(clima.actual.registro.ts).atZone(zonaConsulta)
            Text(fecha.format(DateTimeFormatter.ofPattern("dd MMM · HH:mm", idiomaClima)),
                color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PronosticoPorDia(dias: List<DiaPronostico>) {
    val validos = dias.flatMap { listOfNotNull(it.minima, it.maxima) }
    val minimo = validos.minOrNull() ?: 0.0
    val amplitud = ((validos.maxOrNull() ?: minimo) - minimo).coerceAtLeast(1.0)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pronóstico de $diasPronostico días", style = MaterialTheme.typography.titleMedium)
            Text("Lluvia · Temperatura mín. / máx. (°C)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            dias.forEachIndexed { indice, dia ->
                if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.width(44.dp)) {
                        Text(if (dia.fecha == LocalDate.now(zonaConsulta)) "Hoy" else dia.fecha.format(DateTimeFormatter.ofPattern("EEE", idiomaClima)),
                            style = MaterialTheme.typography.labelLarge)
                        Text(dia.fecha.format(DateTimeFormatter.ofPattern("dd/MM")), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconoClima(dia.codigo, true, Modifier.size(24.dp))
                    Text(numeroClima(dia.lluvia, "%"), modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(numeroClima(dia.minima), modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium)
                    Canvas(Modifier.weight(1f).height(8.dp).semantics {
                        contentDescription = "${dia.fecha}: mínima ${numeroClima(dia.minima)}, máxima ${numeroClima(dia.maxima)}"
                    }) {
                        drawRoundRect(Color(0xFF8694B2).copy(alpha = .2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                        if (dia.minima != null && dia.maxima != null) {
                            val x = ((dia.minima - minimo) / amplitud).toFloat() * size.width
                            val ancho = (((dia.maxima - dia.minima) / amplitud).toFloat() * size.width).coerceAtLeast(2.dp.toPx())
                            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF62CBD6), Color(0xFFC9ACFF), Color(0xFFFFC86F))),
                                topLeft = Offset(x.coerceAtMost(size.width - ancho), 0f), size = Size(ancho, size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                        }
                    }
                    Text(numeroClima(dia.maxima), modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Fondo vectorial local; no descarga fotografías ni depende de servicios de imágenes. */
@Composable
private fun CieloClima(codigo: Int?, esDia: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val lluvia = codigo != null && codigo >= 51
        val fondo = when {
            !esDia -> listOf(Color(0xFF192849), Color(0xFF524382), Color(0xFF7360AE))
            lluvia -> listOf(Color(0xFF485879), Color(0xFF6577A7), Color(0xFF8B72B8))
            else -> listOf(Color(0xFF386CAE), Color(0xFF6C8BCE), Color(0xFF9874CA))
        }
        drawRect(Brush.verticalGradient(fondo))
        if (esDia && codigo in listOf(0, 1, 2)) drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE6AC).copy(alpha = .55f), Color.Transparent),
                center = Offset(size.width * .88f, size.height * .12f), radius = size.width * .55f),
            size.width * .55f, Offset(size.width * .88f, size.height * .12f))
        fun nube(x: Float, y: Float, escala: Float) {
            drawOval(Color.White.copy(alpha = .08f), Offset(x, y), Size(escala * 2.3f, escala * .6f))
            drawCircle(Color.White.copy(alpha = .1f), escala * .45f, Offset(x + escala * .7f, y + escala * .05f))
            drawCircle(Color.White.copy(alpha = .07f), escala * .6f, Offset(x + escala * 1.3f, y))
        }
        nube(-size.width * .25f, size.height * .15f, size.width * .45f)
        nube(size.width * .65f, size.height * .83f, size.width * .32f)
    }
}

/** Símbolos WMO simplificados. El texto accesible conserva la condición completa. */
@Composable
internal fun IconoClima(codigo: Int?, esDia: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = condicionClima(codigo) }) {
        val w = size.width
        val h = size.height
        val amarillo = Color(0xFFFFCE6D)
        val nube = Color(0xFFC1CDE3)
        if (codigo == null || condicionClima(codigo) == "Condición no disponible") {
            drawCircle(nube, w * .28f, Offset(w * .5f, h * .5f), style = Stroke(2.dp.toPx()))
            drawLine(nube, Offset(w * .36f, h * .5f), Offset(w * .64f, h * .5f), 2.dp.toPx())
            return@Canvas
        }
        if (codigo in 0..2) {
            val centro = Offset(w * .42f, h * .38f)
            if (esDia) {
                drawCircle(amarillo, w * .2f, centro)
                for (i in 0..7) {
                    val angulo = i * Math.PI / 4
                    fun punto(r: Float) = centro + Offset(cos(angulo).toFloat() * w * r, sin(angulo).toFloat() * h * r)
                    drawLine(amarillo, punto(.27f), punto(.34f), 1.5.dp.toPx())
                }
            } else {
                val luna = Path().apply {
                    moveTo(w * .55f, h * .1f)
                    cubicTo(w * .05f, h * .05f, w * .04f, h * .74f, w * .6f, h * .67f)
                    cubicTo(w * .25f, h * .52f, w * .33f, h * .18f, w * .55f, h * .1f)
                }
                drawPath(luna, Color(0xFFE6D8FF))
            }
        }
        if (codigo != 0) {
            drawCircle(nube, w * .18f, Offset(w * .35f, h * .55f))
            drawCircle(nube, w * .23f, Offset(w * .55f, h * .47f))
            drawCircle(nube, w * .17f, Offset(w * .77f, h * .57f))
            drawRoundRect(nube, Offset(w * .25f, h * .53f), Size(w * .57f, h * .19f), androidx.compose.ui.geometry.CornerRadius(w * .08f))
            if (codigo == 45 || codigo == 48) for (i in 0..1) {
                drawLine(nube.copy(alpha = .65f), Offset(w * .2f, h * (.83f + i * .13f)),
                    Offset(w * .85f, h * (.83f + i * .13f)), 1.5.dp.toPx())
            }
            if (codigo >= 51 && codigo !in listOf(71, 73, 75, 77, 85, 86)) for (i in 0..2) {
                val x = w * (.35f + i * .18f)
                drawLine(Color(0xFF67C7F0), Offset(x, h * .8f), Offset(x - w * .04f, h * .93f), 2.dp.toPx())
            }
            if (codigo in listOf(71, 73, 75, 77, 85, 86)) {
                val centro = Offset(w * .55f, h * .85f)
                for (i in 0..2) {
                    val angulo = i * Math.PI / 3
                    val radio = Offset(cos(angulo).toFloat() * w * .11f, sin(angulo).toFloat() * h * .11f)
                    drawLine(Color.White, centro - radio, centro + radio, 1.5.dp.toPx())
                }
            }
            if (codigo >= 95) {
                val rayo = Path().apply {
                    moveTo(w * .6f, h * .55f); lineTo(w * .43f, h * .78f)
                    lineTo(w * .57f, h * .78f); lineTo(w * .48f, h)
                    lineTo(w * .76f, h * .69f); lineTo(w * .6f, h * .69f); close()
                }
                drawPath(rayo, amarillo)
            }
        }
    }
}
