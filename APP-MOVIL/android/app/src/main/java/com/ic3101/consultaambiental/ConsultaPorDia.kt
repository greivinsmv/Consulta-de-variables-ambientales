package com.ic3101.consultaambiental

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun ConsultaPorDia(historial: HistorialClima? = null) {
    var inicio by remember { mutableStateOf(historial?.rango?.inicio ?: LocalDate.now(zonaConsulta)) }
    var fin by remember { mutableStateOf(historial?.rango?.fin ?: LocalDate.now(zonaConsulta)) }
    var elegidas by remember { mutableStateOf(listOf("temperatura")) }
    var indiceElegido by remember { mutableIntStateOf(0) }
    var menuFechas by remember { mutableStateOf(false) }
    var filtro by remember { mutableStateOf(ConfiguracionFiltro()) }
    var rangoConsulta by remember { mutableStateOf<RangoFechas?>(null) }
    var datos by remember { mutableStateOf<List<RegistroAmbiental>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var intento by remember { mutableIntStateOf(0) }
    val error = errorRango(inicio, fin, LocalDate.now(zonaConsulta))

    fun limpiarResultados() {
        rangoConsulta = null
        datos = emptyList()
        cargando = false
        mensaje = ""
    }

    val filtroValido = comparacionValida(elegidas, filtro)
    val filtrados = remember(datos, elegidas, filtro) { datosComparacion(datos, elegidas, filtro) }
    val resultados = remember(filtrados, rangoConsulta, filtroValido) {
        if (!filtroValido || rangoConsulta == null) emptyMap() else filtrados.mapValues { (clave, valor) ->
            promediosDiarios(valor.registros, rangoConsulta!!, clave)
        }
    }
    LaunchedEffect(rangoConsulta) { indiceElegido = 0 }

    DisposableEffect(rangoConsulta, intento, historial) {
        val rango = rangoConsulta
        if (rango == null) {
            onDispose { }
        } else if (historial != null) {
            datos = historial.registros.filter {
                val fecha = Instant.ofEpochMilli(it.ts).atZone(zonaConsulta).toLocalDate()
                !fecha.isBefore(rango.inicio) && !fecha.isAfter(rango.fin)
            }
            cargando = false
            mensaje = if (datos.isEmpty()) "Sin datos. Historial disponible: ${historial.rango.inicio} al ${historial.rango.fin}." else ""
            onDispose { }
        } else {
            var activo = true
            cargando = true
            datos = emptyList()
            mensaje = ""
            val desde = rango.inicio.atStartOfDay(zonaConsulta).toInstant().toEpochMilli()
            val hasta = rango.fin.plusDays(1).atStartOfDay(zonaConsulta).toInstant().toEpochMilli() - 1
            val consulta = FirebaseDatabase.getInstance("https://proyecto-iot-fa353-default-rtdb.firebaseio.com")
                .getReference("lecturas/equipo01").orderByChild("ts")
                .startAt(desde.toDouble()).endAt(hasta.toDouble())
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!activo) return
                    datos = snapshot.children.mapNotNull { registro ->
                        val ts = (registro.child("ts").value as? Number)?.toLong() ?: return@mapNotNull null
                        val valores = variables.mapNotNull { variable ->
                            val valor = (registro.child(variable.clave).value as? Number)?.toDouble()
                            if (valor != null && valor.isFinite()) variable.clave to valor else null
                        }.toMap()
                        RegistroAmbiental(ts, valores)
                    }
                    cargando = false
                    mensaje = if (datos.isEmpty()) "No hay lecturas en este rango." else ""
                }
                override fun onCancelled(error: DatabaseError) {
                    if (!activo) return
                    datos = emptyList()
                    cargando = false
                    mensaje = "No se pudo consultar Firebase: ${error.message}"
                }
            }
            consulta.addValueEventListener(listener)
            onDispose { activo = false; consulta.removeEventListener(listener) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Periodo", style = MaterialTheme.typography.labelLarge)
        SelectorFechaDiaria("Fecha inicial", inicio) { inicio = it; limpiarResultados() }
        SelectorFechaDiaria("Fecha final", fin) { fin = it; limpiarResultados() }
        SelectorVariables(elegidas) { elegidas = it }
        FiltrosComparacion(elegidas, filtro) { filtro = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = filtroValido && error == null && !cargando, onClick = {
            rangoConsulta = RangoFechas(inicio, fin)
            intento++
        }) { Text("Consultar") }
        val csv = if (!cargando && filtroValido && resultados.values.any { serie -> serie.any { it.promedio != null } })
            combinarCsvSeries(variables.filter { it.clave in elegidas }.map { v ->
                csvPorDia(v, resultados[v.clave].orEmpty(), limitesComparacion(filtro, v.clave), historial?.origen)
            }) else null
        BotonExportarCsv("promedios_dia_${rangoConsulta?.inicio}_${rangoConsulta?.fin}${historial?.let { "_${it.ubicacion.id}" }.orEmpty()}.csv", csv)
        if (filtro.activo && filtroValido && datos.isNotEmpty()) variables.filter { it.clave in elegidas }.forEach { v ->
            Text(v.nombre, style = MaterialTheme.typography.labelMedium)
            ResumenFiltro(filtrados.getValue(v.clave), true)
        }
        if (cargando) CircularProgressIndicator()
        if (mensaje.isNotBlank()) Text(mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!cargando && filtroValido) rangoConsulta?.let { rango ->
            Text("${fechaDiaria(rango.inicio)} — ${fechaDiaria(rango.fin)}", style = MaterialTheme.typography.titleMedium)
            val dias = resultados.values.firstOrNull().orEmpty().map { it.fecha }
            val series = variables.filter { it.clave in elegidas }.map { v ->
                SerieGrafico(v, resultados[v.clave].orEmpty().mapIndexedNotNull { i, r -> r.promedio?.let { PuntoGrafico(i, it) } })
            }
            if (series.any { it.puntos.isNotEmpty() }) GraficoComparacion(series, dias.indices.toList(),
                dias.map { it.format(DateTimeFormatter.ofPattern("dd/MM")) }, indiceElegido) { indiceElegido = it }
            if (dias.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { menuFechas = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${fechaDiaria(dias[indiceElegido.coerceIn(dias.indices)])} · Elegir día ▾")
                    }
                    DropdownMenu(expanded = menuFechas, onDismissRequest = { menuFechas = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        dias.forEachIndexed { indice, fecha ->
                            DropdownMenuItem(text = { Text(fechaDiaria(fecha)) }, onClick = { indiceElegido = indice; menuFechas = false })
                        }
                    }
                }
                DetalleComparacion(elegidas, resultados.mapValues { (_, serie) ->
                    val r = serie.getOrNull(indiceElegido)
                    r?.promedio to (r?.cantidad ?: 0)
                })
            }
        }
    }
}

@Composable
private fun SelectorFechaDiaria(etiqueta: String, fecha: LocalDate, alElegir: (LocalDate) -> Unit) {
    val contexto = LocalContext.current
    OutlinedButton(onClick = {
        val selector = DatePickerDialog(contexto, { _, anio, mes, dia -> alElegir(LocalDate.of(anio, mes + 1, dia)) },
            fecha.year, fecha.monthValue - 1, fecha.dayOfMonth)
        // El calendario usa la zona del dispositivo; también validamos el día de Costa Rica al consultar.
        selector.datePicker.maxDate = System.currentTimeMillis()
        selector.show()
    }) { Text("$etiqueta: ${fechaDiaria(fecha)}") }
}

private fun fechaDiaria(fecha: LocalDate) = fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
