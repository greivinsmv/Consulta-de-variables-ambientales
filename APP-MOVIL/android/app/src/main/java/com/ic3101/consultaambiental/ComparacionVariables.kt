package com.ic3101.consultaambiental

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

internal fun limitesComparacion(filtro: ConfiguracionFiltro, clave: String): RangoValores? =
    if (filtro.activo) interpretarLimites(filtro.limites.getValue(clave)) else null
internal fun comparacionValida(elegidas: List<String>, filtro: ConfiguracionFiltro): Boolean =
    elegidas.isNotEmpty() && (!filtro.activo || elegidas.all { limitesComparacion(filtro, it) != null })
internal fun datosComparacion(datos: List<RegistroAmbiental>, elegidas: List<String>, filtro: ConfiguracionFiltro): Map<String, DatosFiltrados> =
    elegidas.associateWith { filtrarLecturas(datos, it, limitesComparacion(filtro, it)) }

@Composable
internal fun colorVariable(clave: String): Color {
    val oscuro = MaterialTheme.colorScheme.background.red < .3f
    return when (clave) {
        "temperatura" -> if (oscuro) Color(0xFFC0A8FF) else Color(0xFF7050C6)
        "humedad" -> if (oscuro) Color(0xFF6ADBD3) else Color(0xFF007E78)
        else -> if (oscuro) Color(0xFFFFA4D0) else Color(0xFFBB397C)
    }
}

@Composable
internal fun SelectorVariables(elegidas: List<String>, alCambiar: (List<String>) -> Unit) {
    Text("Variables a comparar", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        variables.forEach { variable ->
            val activa = variable.clave in elegidas
            val color = colorVariable(variable.clave)
            Surface(onClick = {
                if (!activa) alCambiar(elegidas + variable.clave)
                else if (elegidas.size > 1) alCambiar(elegidas - variable.clave)
            }, modifier = Modifier.weight(1f).semantics { selected = activa; role = Role.Checkbox },
                shape = MaterialTheme.shapes.small,
                color = if (activa) color.copy(alpha = .15f) else MaterialTheme.colorScheme.surface,
                contentColor = if (activa) color else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(if (activa) 2.dp else 1.dp, if (activa) color else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.heightIn(min = 64.dp).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(if (activa) "✓" else "+", style = MaterialTheme.typography.labelLarge)
                    Text(variable.nombre, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun FiltrosComparacion(elegidas: List<String>, filtro: ConfiguracionFiltro, alCambiar: (ConfiguracionFiltro) -> Unit) {
    var editar by remember { mutableStateOf("temperatura") }
    val clave = editar.takeIf { it in elegidas } ?: elegidas.first()
    if (filtro.activo && elegidas.size > 1) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            variables.filter { it.clave in elegidas }.forEach { v ->
                TextButton(onClick = { editar = v.clave }, modifier = Modifier.weight(1f)) {
                    Text(if (v.clave == clave) "${v.nombre} •" else v.nombre, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    ControlFiltro(variables.first { it.clave == clave }, filtro, alCambiar)
    if (!comparacionValida(elegidas, filtro)) Text("Revisa los límites de las variables seleccionadas.",
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun DetalleComparacion(elegidas: List<String>, valores: Map<String, Pair<Double?, Int>>) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            variables.filter { it.clave in elegidas }.forEach { variable ->
                val (valor, cantidad) = valores[variable.clave] ?: (null to 0)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(variable.nombre, style = MaterialTheme.typography.labelLarge)
                        Text(if (valor == null) "Sin lecturas" else "$cantidad lecturas", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(valor?.let { String.format(Locale.getDefault(), "%.1f %s", it, variable.unidad) } ?: "Sin datos",
                        style = MaterialTheme.typography.titleLarge, color = colorVariable(variable.clave))
                }
            }
        }
    }
}

/** Conserva unidades, filtros y procedencia en cada fila al exportar varias series. */
internal fun combinarCsvSeries(documentos: List<String>): String {
    require(documentos.isNotEmpty())
    val filas = documentos.map { it.removePrefix("\uFEFF").trimEnd('\r', '\n').split("\r\n") }
    require(filas.all { it.first() == filas.first().first() })
    return "\uFEFF" + (listOf(filas.first().first()) + filas.flatMap { it.drop(1) }).joinToString("\r\n", postfix = "\r\n")
}
