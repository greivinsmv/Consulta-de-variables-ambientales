package com.ic3101.consultaambiental

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

private data class Variable(val clave: String, val nombre: String, val unidad: String)
private val variables = listOf(
    Variable("temperatura", "Temperatura", "°C"),
    Variable("humedad", "Humedad", "%"),
    Variable("puntoRocio", "Punto de rocío", "°C")
)

@Composable
fun ConsultaPorHora() {
    val contexto = LocalContext.current
    var dia by remember { mutableStateOf(LocalDate.now(zonaConsulta)) }
    var seleccionadas by remember { mutableStateOf(setOf("temperatura", "humedad", "puntoRocio")) }
    var fechaConsulta by remember { mutableStateOf<LocalDate?>(null) }
    var datos by remember { mutableStateOf<List<RegistroAmbiental>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("Selecciona una fecha y pulsa Consultar.") }
    var intento by remember { mutableIntStateOf(0) }

    DisposableEffect(fechaConsulta, intento) {
        val fecha = fechaConsulta
        if (fecha == null) {
            onDispose { }
        } else {
            cargando = true
            datos = emptyList()
            mensaje = "Cargando…"
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
                    mensaje = if (datos.isEmpty()) "No hay lecturas para este día." else "${datos.size} registros recibidos."
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
        Text("Promedios por hora", style = MaterialTheme.typography.headlineSmall)
        Text("Horario de Costa Rica. Cada promedio usa las lecturas disponibles de esa hora.")
        OutlinedButton(onClick = {
            val selector = DatePickerDialog(contexto, { _, anio, mes, numero ->
                dia = LocalDate.of(anio, mes + 1, numero)
                fechaConsulta = null
                datos = emptyList()
                cargando = false
                mensaje = "Pulsa Consultar para cargar la fecha elegida."
            }, dia.year, dia.monthValue - 1, dia.dayOfMonth)
            selector.datePicker.maxDate = System.currentTimeMillis()
            selector.show()
        }) { Text(dia.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
        variables.forEach { variable ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = variable.clave in seleccionadas, onCheckedChange = { activar ->
                    seleccionadas = if (activar) seleccionadas + variable.clave else seleccionadas - variable.clave
                })
                Text("${variable.nombre} (${variable.unidad})")
            }
        }
        if (seleccionadas.isEmpty()) Text("Selecciona al menos una variable.")
        Button(enabled = seleccionadas.isNotEmpty() && !cargando && !dia.isAfter(LocalDate.now(zonaConsulta)),
            onClick = { fechaConsulta = dia; intento++ }) { Text("Consultar promedios") }
        if (cargando) CircularProgressIndicator()
        Text(mensaje)
        fechaConsulta?.let { fecha ->
            Text("Resultados del ${fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
            variables.filter { it.clave in seleccionadas }.forEach { variable ->
                val resultados = promediosPorHora(datos, fecha, variable.clave)
                Text(variable.nombre, style = MaterialTheme.typography.titleMedium)
                if (resultados.isEmpty()) {
                    Text("Sin datos válidos para esta variable.")
                } else {
                    val color = MaterialTheme.colorScheme.primary
                    val minimo = resultados.minOf { it.promedio }
                    val maximo = resultados.maxOf { it.promedio }
                    Text("Escala del gráfico: ${formato(minimo)} a ${formato(maximo)} ${variable.unidad}")
                    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                        val margen = 8.dp.toPx()
                        val ancho = size.width - 2 * margen
                        val alto = size.height - 2 * margen
                        fun punto(r: PromedioHora): Offset = Offset(
                            margen + ancho * r.hora / 23f,
                            if (maximo == minimo) size.height / 2 else
                                margen + alto * (1 - (r.promedio - minimo) / (maximo - minimo)).toFloat()
                        )
                        resultados.zipWithNext().forEach { (a, b) ->
                            if (b.hora == a.hora + 1) drawLine(color, punto(a), punto(b), 3.dp.toPx())
                        }
                        resultados.forEach { drawCircle(color, 4.dp.toPx(), punto(it)) }
                    }
                    Text("Eje horizontal: 00:00 → 23:00. Los huecos no se interpolan.")
                    (0..23).forEach { hora ->
                        val resultado = resultados.find { it.hora == hora }
                        Text(String.format(Locale.getDefault(), "%02d:00 — %s", hora,
                            resultado?.let { "${formato(it.promedio)} ${variable.unidad} · ${it.cantidad} lecturas" } ?: "Sin datos"))
                    }
                }
            }
        }
    }
}
private fun formato(valor: Double): String = String.format(Locale.getDefault(), "%.1f", valor)
