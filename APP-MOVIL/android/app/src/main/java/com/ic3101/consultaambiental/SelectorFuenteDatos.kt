package com.ic3101.consultaambiental

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
internal fun SelectorFuenteDatos(fuente: String, alElegir: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fuente de datos", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("esp32" to "ESP32", "clima" to "Santa Clara").forEach { (clave, nombre) ->
                if (fuente == clave) Button(onClick = { alElegir(clave) }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text(nombre) }
                else OutlinedButton(onClick = { alElegir(clave) }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text(nombre) }
            }
        }
    }
}

@Composable
internal fun PanelClima(historial: HistorialClima?, cargando: Boolean, mensaje: String?, alDescargar: () -> Unit) {
    val fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Historial de 10 días", style = MaterialTheme.typography.titleMedium)
            Button(enabled = !cargando, onClick = alDescargar) {
                Text(if (historial == null) "Cargar historial" else "Actualizar historial")
            }
            if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            historial?.let {
                Text("Periodo: ${it.rango.inicio.format(fecha)} al ${it.rango.fin.format(fecha)}")

            }
            mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
