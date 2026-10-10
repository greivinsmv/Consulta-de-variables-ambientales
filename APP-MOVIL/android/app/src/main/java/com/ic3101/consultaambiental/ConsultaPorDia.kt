package com.ic3101.consultaambiental

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var variableElegida by remember { mutableStateOf("temperatura") }
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
        Text("Periodo", style = MaterialTheme.typography.labelLarge)
        Text("Se incluyen la fecha inicial y la final.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectorFechaDiaria("Fecha inicial", inicio) { inicio = it; limpiarResultados() }
        SelectorFechaDiaria("Fecha final", fin) { fin = it; limpiarResultados() }
        SelectorVariable(variableElegida) { variableElegida = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = error == null && !cargando, onClick = {
            rangoConsulta = RangoFechas(inicio, fin)
            intento++
        }) { Text("Consultar promedios diarios") }
        if (cargando) CircularProgressIndicator()
        Text(mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!cargando) rangoConsulta?.let { rango ->
            Text("Resultados: ${fechaDiaria(rango.inicio)} al ${fechaDiaria(rango.fin)}")
            Text("Promedio de las lecturas disponibles en cada día.", style = MaterialTheme.typography.bodySmall)
            variables.filter { it.clave == variableElegida }.forEach { variable ->
                val resultados = remember(datos, rango, variable.clave) { promediosDiarios(datos, rango, variable.clave) }
                val conDatos = resultados.filter { it.promedio != null }
                Text(variable.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${conDatos.size} de ${resultados.size} días con datos válidos.")
                if (conDatos.isNotEmpty()) {
                    val valores = conDatos.map { it.promedio!! }
                    Text("Promedio diario mínimo: ${numeroDiario(valores.min())} ${variable.unidad}")
                    Text("Promedio diario máximo: ${numeroDiario(valores.max())} ${variable.unidad}")
                    var mostrarGrafico by remember(rango, variable.clave) { mutableStateOf(false) }
                    OutlinedButton(onClick = { mostrarGrafico = !mostrarGrafico }) {
                        Text(if (mostrarGrafico) "Ocultar gráfico" else "Ver gráfico diario")
                    }
                    if (mostrarGrafico) GraficoDiarioClaro(resultados, variable.unidad)
                }
                resultados.forEach { resultado ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(fechaDiaria(resultado.fecha), style = MaterialTheme.typography.labelLarge)
                            Text(resultado.promedio?.let { "${numeroDiario(it)} ${variable.unidad}" } ?: "Sin datos",
                                style = MaterialTheme.typography.titleLarge)
                            Text("${resultado.cantidad} lecturas", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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

private fun fechaDiaria(fecha: LocalDate) = fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
private fun numeroDiario(valor: Double) = String.format(Locale.getDefault(), "%.1f", valor)
