package com.ic3101.consultaambiental

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.time.Instant
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
    val enlaces = LocalUriHandler.current
    val fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    var detalles by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Historial de 10 días", style = MaterialTheme.typography.titleMedium)
            Text("Datos horarios modelados · Open-Meteo", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !cargando, onClick = alDescargar) {
                Text(if (historial == null) "Cargar últimos 10 días" else "Actualizar últimos 10 días")
            }
            if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            historial?.let {
                Text("Periodo: ${it.rango.inicio.format(fecha)} al ${it.rango.fin.format(fecha)}")
                val descarga = Instant.ofEpochMilli(it.descargadoEn).atZone(zonaConsulta)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                TextButton(onClick = { detalles = !detalles }) { Text(if (detalles) "Ocultar detalles" else "Detalles de la descarga") }
                if (detalles) {
                    Text("${it.registros.size} datos horarios · Descargado: $descarga", style = MaterialTheme.typography.bodySmall)
                    Text("Ubicación aproximada: 10.36, −84.51. Copia guardada en el teléfono. Cada actualización reemplaza el periodo descargado.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { enlaces.openUri(urlProveedorClima) }) { Text("Open-Meteo") }
                TextButton(onClick = { enlaces.openUri(licenciaClima) }) { Text("CC BY 4.0") }
            }
        }
    }
}
