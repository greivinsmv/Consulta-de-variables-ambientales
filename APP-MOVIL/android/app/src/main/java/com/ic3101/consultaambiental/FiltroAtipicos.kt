package com.ic3101.consultaambiental

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class RangoValores(val minimo: Double, val maximo: Double)
internal data class LimitesTexto(val minimo: String, val maximo: String)
internal data class ConfiguracionFiltro(
    val activo: Boolean = false,
    val limites: Map<String, LimitesTexto> = mapOf(
        "temperatura" to LimitesTexto("-10", "50"),
        "humedad" to LimitesTexto("0", "100"),
        "puntoRocio" to LimitesTexto("-30", "40")
    )
)
internal fun interpretarLimites(texto: LimitesTexto): RangoValores? {
    val minimo = texto.minimo.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val maximo = texto.maximo.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!minimo.isFinite() || !maximo.isFinite() || minimo > maximo) return null
    return RangoValores(minimo, maximo)
}
internal data class DatosFiltrados(val registros: List<RegistroAmbiental>, val utilizados: Int, val excluidos: Int, val sinValor: Int)
internal fun filtrarLecturas(registros: List<RegistroAmbiental>, variable: String, limites: RangoValores?): DatosFiltrados {
    var excluidos = 0
    var sinValor = 0
    val validos = registros.filter { registro ->
        val valor = registro.valores[variable]
        when {
            valor == null || !valor.isFinite() -> { sinValor++; false }
            limites != null && (valor < limites.minimo || valor > limites.maximo) -> { excluidos++; false }
            else -> true
        }
    }
    return DatosFiltrados(validos, validos.size, excluidos, sinValor)
}

@Composable
internal fun ControlFiltro(variable: Variable, configuracion: ConfiguracionFiltro, alCambiar: (ConfiguracionFiltro) -> Unit) {
    val texto = configuracion.limites.getValue(variable.clave)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Filtrar valores atípicos", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Switch(checked = configuracion.activo, onCheckedChange = { alCambiar(configuracion.copy(activo = it)) })
        }
        if (configuracion.activo) {
            Text("Se omiten valores fuera de estos límites (${variable.unidad}). Puedes ajustarlos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = texto.minimo, onValueChange = {
                    alCambiar(configuracion.copy(limites = configuracion.limites + (variable.clave to texto.copy(minimo = it))))
                }, label = { Text("Mínimo") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                OutlinedTextField(value = texto.maximo, onValueChange = {
                    alCambiar(configuracion.copy(limites = configuracion.limites + (variable.clave to texto.copy(maximo = it))))
                }, label = { Text("Máximo") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            }
            if (interpretarLimites(texto) == null) Text("Introduce dos números. El mínimo no puede superar al máximo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ResumenFiltro(datos: DatosFiltrados, activo: Boolean) {
    if (activo) Text("${datos.utilizados} lecturas utilizadas · ${datos.excluidos} fuera de límites · ${datos.sinValor} sin valor válido",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
