package com.ic3101.consultaambiental

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConsultaPorDia() {
    var inicio by remember { mutableStateOf(LocalDate.now(zonaConsulta)) }
    var fin by remember { mutableStateOf(LocalDate.now(zonaConsulta)) }
    var seleccionadas by remember { mutableStateOf(variables.map { it.clave }.toSet()) }
    var rangoConsulta by remember { mutableStateOf<RangoFechas?>(null) }
    var datos by remember { mutableStateOf<List<RegistroAmbiental>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("Selecciona las fechas y las variables.") }
    var intento by remember { mutableIntStateOf(0) }
    val error = errorRango(inicio, fin, LocalDate.now(zonaConsulta))

    fun limpiarResultados() {
        rangoConsulta = null
        datos = emptyList()
        cargando = false
        mensaje = "Pulsa Consultar para cargar el rango elegido."
    }

    DisposableEffect(rangoConsulta, intento) {
        val rango = rangoConsulta
        if (rango == null) {
            onDispose { }
        } else {
            var activo = true
            cargando = true
            datos = emptyList()
            mensaje = "Cargando…"
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
                    mensaje = if (datos.isEmpty()) "No hay lecturas en este rango." else "${datos.size} registros recibidos."
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
        Text("Promedios diarios", style = MaterialTheme.typography.headlineSmall)
        Text("Ambas fechas están incluidas. Horario de Costa Rica.")
        SelectorFechaDiaria("Fecha inicial", inicio) { inicio = it; limpiarResultados() }
        SelectorFechaDiaria("Fecha final", fin) { fin = it; limpiarResultados() }
        variables.forEach { variable ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = variable.clave in seleccionadas, onCheckedChange = { seleccion ->
                    seleccionadas = if (seleccion) seleccionadas + variable.clave else seleccionadas - variable.clave
                })
                Text("${variable.nombre} (${variable.unidad})")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (seleccionadas.isEmpty()) Text("Selecciona al menos una variable.", color = MaterialTheme.colorScheme.error)
        Button(enabled = error == null && seleccionadas.isNotEmpty() && !cargando, onClick = {
            rangoConsulta = RangoFechas(inicio, fin)
            intento++
        }) { Text("Consultar promedios diarios") }
        if (cargando) CircularProgressIndicator()
        Text(mensaje)
        if (!cargando) rangoConsulta?.let { rango ->
            Text("Resultados: ${fechaDiaria(rango.inicio)} al ${fechaDiaria(rango.fin)}")
            Text("Cada promedio usa las lecturas válidas del día, sin rellenar los huecos.")
            variables.filter { it.clave in seleccionadas }.forEach { variable ->
                val resultados = remember(datos, rango, variable.clave) { promediosDiarios(datos, rango, variable.clave) }
                val conDatos = resultados.filter { it.promedio != null }
                Text(variable.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${conDatos.size} de ${resultados.size} días con datos válidos.")
                if (conDatos.isNotEmpty()) {
                    val valores = conDatos.map { it.promedio!! }
                    Text("Promedio diario mínimo: ${numeroDiario(valores.min())} ${variable.unidad}")
                    Text("Promedio diario máximo: ${numeroDiario(valores.max())} ${variable.unidad}")
                    GraficoDiario(resultados, variable.unidad)
                }
                resultados.forEach { resultado ->
                    Text("${fechaDiaria(resultado.fecha)} — " +
                        (resultado.promedio?.let { "${numeroDiario(it)} ${variable.unidad} · ${resultado.cantidad} lecturas" } ?: "Sin datos"))
                }
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

@Composable
private fun GraficoDiario(resultados: List<PromedioDia>, unidad: String) {
    val color = MaterialTheme.colorScheme.primary
    val eje = MaterialTheme.colorScheme.outline
    val valores = resultados.mapNotNull { it.promedio }
    val minimo = minOf(0.0, valores.min())
    val maximo = maxOf(0.0, valores.max())
    val amplitud = (maximo - minimo).takeIf { it > 0 } ?: 1.0
    val ancho = maxOf(280, resultados.size * 48).dp
    Text("Escala: ${numeroDiario(minimo)} a ${numeroDiario(maximo)} $unidad. Desliza para ver todos los días.")
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(ancho).height(160.dp)) {
            val margen = 8.dp.toPx()
            fun y(valor: Double) = margen + (size.height - margen * 2) * (1 - (valor - minimo) / amplitud).toFloat()
            val cero = y(0.0)
            drawLine(eje, Offset(0f, cero), Offset(size.width, cero), 1.dp.toPx())
            val espacio = size.width / resultados.size
            resultados.forEachIndexed { indice, resultado ->
                resultado.promedio?.let { valor ->
                    val altura = kotlin.math.abs(y(valor) - cero)
                    val x = espacio * indice + espacio * 0.2f
                    if (altura == 0f) drawLine(color, Offset(x, cero), Offset(x + espacio * 0.6f, cero), 3.dp.toPx())
                    else drawRect(color, Offset(x, minOf(y(valor), cero)), Size(espacio * 0.6f, altura))
                }
            }
        }
        Row(Modifier.width(ancho)) {
            resultados.forEach { Text(it.fecha.format(DateTimeFormatter.ofPattern("dd/MM")), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
private fun fechaDiaria(fecha: LocalDate) = fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
private fun numeroDiario(valor: Double) = String.format(Locale.getDefault(), "%.1f", valor)
