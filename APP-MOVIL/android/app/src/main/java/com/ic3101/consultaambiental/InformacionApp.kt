package com.ic3101.consultaambiental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
internal fun InformacionApp(historial: HistorialClima?, actual: ClimaActual?, alCerrar: () -> Unit) {
    val enlaces = LocalUriHandler.current
    fun fecha(ts: Long) = Instant.ofEpochMilli(ts).atZone(zonaConsulta)
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    Dialog(onDismissRequest = alCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF5262C7), Color(0xFF9250CE))),
                    RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                ).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = alCerrar, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Volver") }
                    Text("Información", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TarjetaInformacion("Fuentes de datos") {
                        Text("ESP32", style = MaterialTheme.typography.titleSmall)
                        Text("Lecturas de un DHT22 simulado en Wokwi, almacenadas en Firebase XD. La app recibe las lecturas nuevas automáticamente.")
                        Text("Santa Clara, San Carlos", style = MaterialTheme.typography.titleSmall)
                        Text("Estimaciones meteorológicas de Open-Meteo para la zona. Ubicación aproximada: 10.36, −84.51.")
                        Text("Los resultados utilizan la fuente seleccionada en la app.")
                    }
                    TarjetaInformacion("Actualización e historial") {
                        Text("Inicio consulta el clima actual cada 15 minutos mientras esa pantalla está visible. Actualizar ahora permite consultarlo manualmente.")
                        Text("El historial cubre los 10 días completos anteriores: hoy queda excluido. Incluye 24 datos horarios por día y se conserva en el teléfono para consultar sin conexión.")
                        Text("Actualizar historial reemplaza la copia guardada con el nuevo periodo. Si falla, se conserva la última descarga válida.")
                        Text("Horario de todas las fechas: Costa Rica (UTC−6).")
                        actual?.let { Text("Última consulta del clima: ${fecha(it.consultadoEn)}", style = MaterialTheme.typography.bodySmall) }
                        historial?.let {
                            Text("Periodo guardado: ${it.rango.inicio} al ${it.rango.fin}", style = MaterialTheme.typography.bodySmall)
                            Text("${it.registros.size} datos horarios · Descarga: ${fecha(it.descargadoEn)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TarjetaInformacion("Consultas y gráficos") {
                        Text("Cada promedio usa los valores numéricos válidos de la variable seleccionada. Por día incluye ambas fechas y usa las lecturas originales, sin promediar promedios horarios.")
                        Text("Las horas y días vacíos se muestran como Sin datos. Los gráficos no unen horas sin lecturas.")
                        Text("Extremos muestra los valores individuales mínimo y máximo. Si hay empates, se indica su cantidad y la primera fecha registrada.")
                        Text("En la fuente meteorológica hay un dato por hora; el promedio horario coincide con ese dato.")
                    }
                    TarjetaInformacion("Filtro y punto de rocío") {
                        Text("El filtro utiliza límites mínimo y máximo inclusivos por variable. Sus valores iniciales son orientativos y puedes ajustarlos. No modifica los registros originales.")
                        Text("El punto de rocío indica la temperatura a la que el aire alcanzaría la saturación. El ESP32 lo calcula a partir de temperatura y humedad con la aproximación de Magnus; la fuente meteorológica lo proporciona Open-Meteo.")
                    }
                    TarjetaInformacion("Exportación CSV") {
                        Text("Exportar CSV guarda una copia de los resultados de la consulta. Incluye fechas, unidades, cantidad de lecturas y límites del filtro cuando está activo.")
                        Text("Los archivos meteorológicos identifican la fuente, ubicación y licencia. Para abrirlos en una hoja de cálculo, utiliza UTF-8 y separador punto y coma.")
                    }
                    TarjetaInformacion("Créditos y licencia") {
                        Text("Datos meteorológicos proporcionados por Open-Meteo bajo licencia CC BY 4.0. Consulta Ambiental calcula promedios, extremos y filtros sobre esos datos.")
                        TextButton(onClick = { enlaces.openUri(urlProveedorClima) }) { Text("Open-Meteo · Sitio web") }
                        TextButton(onClick = { enlaces.openUri(licenciaClima) }) { Text("Licencia de datos · CC BY 4.0") }
                        Text("Proyecto académico IC-3101. Aplicación Android desarrollada con Kotlin y Jetpack Compose.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaInformacion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            contenido()
        }
    }
}
