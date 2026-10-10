package com.ic3101.consultaambiental

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConsultaHistorica() {
    var seleccionadas by remember { mutableStateOf(variables.map { it.clave }.toSet()) }
    var consultar by remember { mutableStateOf(false) }
    var intento by remember { mutableIntStateOf(0) }
    var datos by remember { mutableStateOf<List<RegistroAmbiental>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("Selecciona las variables y consulta el historial.") }
    var recibido by remember { mutableStateOf(false) }
    var sinFecha by remember { mutableIntStateOf(0) }

    DisposableEffect(consultar, intento) {
        if (!consultar) {
            onDispose { }
        } else {
            var activo = true
            cargando = true
            recibido = false
            datos = emptyList()
            sinFecha = 0
            mensaje = "Cargando historial…"
            val referencia = FirebaseDatabase.getInstance("https://proyecto-iot-fa353-default-rtdb.firebaseio.com")
                .getReference("lecturas/equipo01")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!activo) return
                    var descartados = 0
                    datos = snapshot.children.mapNotNull { registro ->
                        val fecha = registro.child("ts").value as? Number
                        val numeroFecha = fecha?.toDouble()
                        if (numeroFecha == null || !numeroFecha.isFinite() || numeroFecha < 0 ||
                            numeroFecha > 253402300799999.0 || numeroFecha % 1.0 != 0.0) {
                            descartados++
                            return@mapNotNull null
                        }
                        val valores = variables.mapNotNull { variable ->
                            val valor = (registro.child(variable.clave).value as? Number)?.toDouble()
                            if (valor != null && valor.isFinite()) variable.clave to valor else null
                        }.toMap()
                        RegistroAmbiental(numeroFecha.toLong(), valores)
                    }
                    sinFecha = descartados
                    cargando = false
                    recibido = true
                    mensaje = if (!snapshot.exists()) "Todavía no hay registros históricos." else "Historial recibido."
                }
                override fun onCancelled(error: DatabaseError) {
                    if (!activo) return
                    cargando = false
                    recibido = false
                    datos = emptyList()
                    mensaje = "No se pudo leer el historial: ${error.message}"
                }
            }
            referencia.addValueEventListener(listener)
            onDispose { activo = false; referencia.removeEventListener(listener) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Máximos y mínimos históricos", style = MaterialTheme.typography.headlineSmall)
        Text("Se analizan las lecturas originales de equipo01, no los promedios. Fechas en horario de Costa Rica.")
        variables.forEach { variable ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = variable.clave in seleccionadas, onCheckedChange = { marcada ->
                    seleccionadas = if (marcada) seleccionadas + variable.clave else seleccionadas - variable.clave
                })
                Text("${variable.nombre} (${variable.unidad})")
            }
        }
        if (seleccionadas.isEmpty()) Text("Selecciona al menos una variable.", color = MaterialTheme.colorScheme.error)
        Button(enabled = seleccionadas.isNotEmpty() && !cargando, onClick = { consultar = true; intento++ }) {
            Text("Consultar máximos y mínimos")
        }
        if (cargando) CircularProgressIndicator()
        Text(mensaje)
        if (recibido && !cargando) {
            if (sinFecha > 0) Text("Se omitieron $sinFecha registros sin una fecha válida.")
            if (datos.isNotEmpty()) {
                Text("Periodo registrado: ${fechaHistorica(datos.minOf { it.ts })} al ${fechaHistorica(datos.maxOf { it.ts })}")
            }
            variables.filter { it.clave in seleccionadas }.forEach { variable ->
                val resumen = remember(datos, variable.clave) { extremosHistoricos(datos, variable.clave) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(variable.nombre, style = MaterialTheme.typography.titleMedium)
                        if (resumen == null) {
                            Text("Sin lecturas válidas para esta variable.")
                        } else {
                            Text("${resumen.cantidad} lecturas válidas analizadas")
                            Text("Mínimo: ${numeroHistorico(resumen.minimo.valor)} ${variable.unidad}")
                            Text("Registrado: ${fechaHistorica(resumen.minimo.ts)}")
                            Text("Máximo: ${numeroHistorico(resumen.maximo.valor)} ${variable.unidad}")
                            Text("Registrado: ${fechaHistorica(resumen.maximo.ts)}")
                            if (resumen.minimo.repeticiones > 1 || resumen.maximo.repeticiones > 1) {
                                Text("Repeticiones: mínimo ${resumen.minimo.repeticiones}, máximo ${resumen.maximo.repeticiones}. Se muestra la primera fecha de cada extremo.")
                            }
                            RangoHistorico(resumen)
                            Text("Amplitud: ${numeroHistorico(resumen.maximo.valor - resumen.minimo.valor)} ${variable.unidad}")
                        }
                    }
                }
            }
            Text("Los valores ausentes o no numéricos no se sustituyen por cero. Los resultados se actualizan al recibir nuevas lecturas.")
        }
    }
}

@Composable
private fun RangoHistorico(resumen: ResumenHistorico) {
    val color = MaterialTheme.colorScheme.primary
    val constante = resumen.minimo.valor == resumen.maximo.valor
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        val margen = 8.dp.toPx()
        if (constante) drawCircle(color, 5.dp.toPx(), Offset(size.width / 2, size.height / 2))
        else {
            val inicio = Offset(margen, size.height / 2)
            val fin = Offset(size.width - margen, size.height / 2)
            drawLine(color, inicio, fin, 3.dp.toPx())
            drawCircle(color, 5.dp.toPx(), inicio)
            drawCircle(color, 5.dp.toPx(), fin)
        }
    }
    Text(if (constante) "Todas las lecturas válidas tienen el mismo valor." else
        "Rango observado: ${numeroHistorico(resumen.minimo.valor)} → ${numeroHistorico(resumen.maximo.valor)}")
}
private fun numeroHistorico(valor: Double) = String.format(Locale.getDefault(), "%.1f", valor)
private fun fechaHistorica(ts: Long) = Instant.ofEpochMilli(ts).atZone(zonaConsulta)
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
