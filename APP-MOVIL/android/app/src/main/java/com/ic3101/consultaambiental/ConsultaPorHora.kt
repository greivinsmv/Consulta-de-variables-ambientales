package com.ic3101.consultaambiental

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val zonaConsulta: ZoneId = ZoneId.of("America/Costa_Rica")
internal data class RegistroAmbiental(val ts: Long, val valores: Map<String, Double>)
internal data class PromedioHora(val hora: Int, val promedio: Double, val cantidad: Int)
internal fun promediosPorHora(registros: List<RegistroAmbiental>, dia: LocalDate, variable: String): List<PromedioHora> =
    registros.filter { Instant.ofEpochMilli(it.ts).atZone(zonaConsulta).toLocalDate() == dia }
        .filter { it.valores[variable]?.isFinite() == true }
        .groupBy { Instant.ofEpochMilli(it.ts).atZone(zonaConsulta).hour }
        .toSortedMap().map { (hora, grupo) ->
            PromedioHora(hora, grupo.map { it.valores.getValue(variable) }.average(), grupo.size)
        }

internal data class Variable(val clave: String, val nombre: String, val unidad: String)
internal val variables = listOf(
    Variable("temperatura", "Temperatura", "°C"),
    Variable("humedad", "Humedad", "%"),
    Variable("puntoRocio", "Punto de rocío", "°C")
)

@Composable
internal fun ConsultaPorHora(historial: HistorialClima? = null) {
    val contexto = LocalContext.current
    var dia by remember { mutableStateOf(historial?.rango?.fin ?: LocalDate.now(zonaConsulta)) }
    var variableElegida by remember { mutableStateOf("temperatura") }
    var filtro by remember { mutableStateOf(ConfiguracionFiltro()) }
    var fechaConsulta by remember { mutableStateOf<LocalDate?>(null) }
    var datos by remember { mutableStateOf<List<RegistroAmbiental>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var intento by remember { mutableIntStateOf(0) }
    var horaElegida by remember { mutableIntStateOf(0) }
    var menuHoras by remember { mutableStateOf(false) }

    val variableFiltro = variables.first { it.clave == variableElegida }
    val limitesFiltro = if (filtro.activo) interpretarLimites(filtro.limites.getValue(variableElegida)) else null
    val filtroValido = !filtro.activo || limitesFiltro != null
    val filtrados = remember(datos, variableElegida, limitesFiltro) { filtrarLecturas(datos, variableElegida, limitesFiltro) }
    val datosConsulta = if (filtroValido) filtrados.registros else emptyList()

    DisposableEffect(fechaConsulta, intento, historial) {
        val fecha = fechaConsulta
        if (fecha == null) {
            onDispose { }
        } else if (historial != null) {
            datos = historial.registros.filter { Instant.ofEpochMilli(it.ts).atZone(zonaConsulta).toLocalDate() == fecha }
            cargando = false
            mensaje = if (datos.isEmpty()) "Sin datos. El historial descargado cubre ${historial.rango.inicio} al ${historial.rango.fin}."
                else ""
            onDispose { }
        } else {
            cargando = true
            datos = emptyList()
            mensaje = ""
            val inicio = fecha.atStartOfDay(zonaConsulta).toInstant().toEpochMilli()
            val fin = fecha.plusDays(1).atStartOfDay(zonaConsulta).toInstant().toEpochMilli()
            val consulta = FirebaseDatabase.getInstance("https://proyecto-iot-fa353-default-rtdb.firebaseio.com")
                .getReference("lecturas/equipo01").orderByChild("ts")
                .startAt(inicio.toDouble()).endAt((fin - 1).toDouble())
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    datos = snapshot.children.mapNotNull { registro ->
                        val ts = (registro.child("ts").value as? Number)?.toLong() ?: return@mapNotNull null
                        val valores = variables.mapNotNull { variable ->
                            val valor = (registro.child(variable.clave).value as? Number)?.toDouble()
                            if (valor != null && valor.isFinite()) variable.clave to valor else null
                        }.toMap()
                        RegistroAmbiental(ts, valores)
                    }
                    cargando = false
                    mensaje = if (datos.isEmpty()) "No hay lecturas para este día." else ""
                }
                override fun onCancelled(error: DatabaseError) {
                    datos = emptyList()
                    cargando = false
                    mensaje = "No se pudo consultar: ${error.message}"
                }
            }
            consulta.addValueEventListener(listener)
            onDispose { consulta.removeEventListener(listener) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fecha", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = {
            val selector = DatePickerDialog(contexto, { _, anio, mes, numero ->
                dia = LocalDate.of(anio, mes + 1, numero)
                fechaConsulta = null
                datos = emptyList()
                cargando = false
                mensaje = ""
            }, dia.year, dia.monthValue - 1, dia.dayOfMonth)
            selector.datePicker.maxDate = System.currentTimeMillis()
            selector.show()
        }) { Text(dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
        SelectorVariable(variableElegida) { variableElegida = it }
        ControlFiltro(variableFiltro, filtro) { filtro = it }
        Button(enabled = filtroValido && !cargando && !dia.isAfter(LocalDate.now(zonaConsulta)),
            onClick = { fechaConsulta = dia; intento++ }) { Text("Consultar promedios") }
        val variableCsv = variables.first { it.clave == variableElegida }
        val fechaCsv = fechaConsulta
        val resultadosCsv = fechaCsv?.let { promediosPorHora(datosConsulta, it, variableElegida) }.orEmpty()
        BotonExportarCsv("promedios_hora_${fechaCsv}_${variableElegida}${if (historial != null) "_santa_clara" else ""}.csv",
            if (!cargando && fechaCsv != null && resultadosCsv.isNotEmpty()) csvPorHora(fechaCsv, variableCsv, resultadosCsv, limitesFiltro, historial?.origen) else null)
        if (datos.isNotEmpty() && filtroValido) ResumenFiltro(filtrados, filtro.activo)
        if (cargando) CircularProgressIndicator()
        if (mensaje.isNotBlank()) Text(mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        fechaConsulta?.let { fecha ->
            Text("Resultados del ${fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
            Text("Hora del promedio", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { menuHoras = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(String.format(Locale.getDefault(), "%02d:00–%02d:59 · Elegir hora ▾", horaElegida, horaElegida))
                }
                DropdownMenu(expanded = menuHoras, onDismissRequest = { menuHoras = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    (0..23).forEach { hora ->
                        DropdownMenuItem(
                            text = { Text(String.format(Locale.getDefault(), "%02d:00–%02d:59", hora, hora)) },
                            onClick = { horaElegida = hora; menuHoras = false }
                        )
                    }
                }
            }
            variables.filter { it.clave == variableElegida }.forEach { variable ->
                val resultados = promediosPorHora(datosConsulta, fecha, variable.clave)
                Text(variable.nombre, style = MaterialTheme.typography.titleMedium)
                if (resultados.isEmpty()) {
                    Text("Sin datos válidos para esta variable.")
                } else {
                    val resultado = resultados.find { it.hora == horaElegida }
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(String.format(Locale.getDefault(), "%02d:00–%02d:59", horaElegida, horaElegida),
                                style = MaterialTheme.typography.labelLarge)
                            Text(resultado?.let { "${formato(it.promedio)} ${variable.unidad}" } ?: "Sin datos",
                                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text(resultado?.let { "Promedio de ${it.cantidad} lecturas" } ?: "No hay lecturas en esta hora.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    var mostrarGrafico by remember(fecha, variable.clave) { mutableStateOf(false) }
                    OutlinedButton(onClick = { mostrarGrafico = !mostrarGrafico }) {
                        Text(if (mostrarGrafico) "Ocultar gráfico" else "Ver gráfico del día")
                    }
                    if (mostrarGrafico) GraficoHorario(resultados, variable.unidad, horaElegida) { horaElegida = it }
                }
            }
        }
    }
}
private fun formato(valor: Double): String = String.format(Locale.getDefault(), "%.1f", valor)
