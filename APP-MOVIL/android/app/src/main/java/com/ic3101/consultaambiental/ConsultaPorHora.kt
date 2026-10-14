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
    var elegidas by remember { mutableStateOf(listOf("temperatura")) }
    var filtro by remember { mutableStateOf(ConfiguracionFiltro()) }
    var fechaConsulta by remember { mutableStateOf<LocalDate?>(null) }
    var datos by remember { mutableStateOf<List<RegistroAmbiental>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var intento by remember { mutableIntStateOf(0) }
    var horaElegida by remember { mutableIntStateOf(0) }
    var menuHoras by remember { mutableStateOf(false) }

    val filtroValido = comparacionValida(elegidas, filtro)
    val filtrados = remember(datos, elegidas, filtro) { datosComparacion(datos, elegidas, filtro) }
    // Cada serie conserva sus registros: excluir una temperatura no elimina su humedad.
    val resultados = remember(filtrados, fechaConsulta, filtroValido) {
        if (!filtroValido || fechaConsulta == null) emptyMap() else filtrados.mapValues { (clave, valor) ->
            promediosPorHora(valor.registros, fechaConsulta!!, clave)
        }
    }
    LaunchedEffect(fechaConsulta, resultados) {
        if (resultados.values.none { serie -> serie.any { it.hora == horaElegida } })
            resultados.values.flatten().minOfOrNull { it.hora }?.let { horaElegida = it }
    }

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
        SelectorVariables(elegidas) { elegidas = it }
        FiltrosComparacion(elegidas, filtro) { filtro = it }
        Button(enabled = filtroValido && !cargando && !dia.isAfter(LocalDate.now(zonaConsulta)),
            onClick = { fechaConsulta = dia; intento++ }) { Text("Consultar") }
        val csv = if (!cargando && filtroValido && fechaConsulta != null && resultados.values.any { it.isNotEmpty() })
            combinarCsvSeries(variables.filter { it.clave in elegidas }.map { v ->
                csvPorHora(fechaConsulta!!, v, resultados[v.clave].orEmpty(), limitesComparacion(filtro, v.clave), historial?.origen)
            }) else null
        BotonExportarCsv("promedios_hora_${fechaConsulta}_${elegidas.joinToString("_")}${historial?.let { "_${it.ubicacion.id}" }.orEmpty()}.csv", csv)
        if (filtro.activo && filtroValido && datos.isNotEmpty()) variables.filter { it.clave in elegidas }.forEach { v ->
            Text(v.nombre, style = MaterialTheme.typography.labelMedium)
            ResumenFiltro(filtrados.getValue(v.clave), true)
        }
        if (cargando) CircularProgressIndicator()
        if (mensaje.isNotBlank()) Text(mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!cargando && filtroValido) fechaConsulta?.let { fecha ->
            Text(fecha.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("es-CR"))),
                style = MaterialTheme.typography.titleMedium)
            val series = variables.filter { it.clave in elegidas }.map { v ->
                SerieGrafico(v, resultados[v.clave].orEmpty().map { PuntoGrafico(it.hora, it.promedio) })
            }
            val horas = series.flatMap { it.puntos }.map { it.indice }.distinct().sorted()
            if (horas.isNotEmpty()) GraficoComparacion(series, horas, horas.map { String.format(Locale.ROOT, "%02d:00", it) }, horaElegida) { horaElegida = it }
            Box {
                OutlinedButton(onClick = { menuHoras = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(String.format(Locale.ROOT, "%02d:00–%02d:59 ▾", horaElegida, horaElegida))
                }
                DropdownMenu(expanded = menuHoras, onDismissRequest = { menuHoras = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    (0..23).forEach { hora ->
                        DropdownMenuItem(text = { Text(String.format(Locale.ROOT, "%02d:00–%02d:59", hora, hora)) },
                            onClick = { horaElegida = hora; menuHoras = false })
                    }
                }
            }
            DetalleComparacion(elegidas, resultados.mapValues { (_, serie) ->
                val r = serie.find { it.hora == horaElegida }
                r?.promedio to (r?.cantidad ?: 0)
            })
        }
    }
}
