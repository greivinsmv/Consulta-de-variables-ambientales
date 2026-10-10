package com.ic3101.consultaambiental

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConsultaHistorica() {
    var variableElegida by remember { mutableStateOf("temperatura") }
    var filtro by remember { mutableStateOf(ConfiguracionFiltro()) }
    var consultar by remember { mutableStateOf(false) }
    var intento by remember { mutableIntStateOf(0) }
    var datos by remember { mutableStateOf<List<RegistroAmbiental>>(emptyList()) }
    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("Selecciona las variables y consulta el historial.") }
    var recibido by remember { mutableStateOf(false) }
    var sinFecha by remember { mutableIntStateOf(0) }

    val variableFiltro = variables.first { it.clave == variableElegida }
    val limitesFiltro = if (filtro.activo) interpretarLimites(filtro.limites.getValue(variableElegida)) else null
    val filtroValido = !filtro.activo || limitesFiltro != null
    val filtrados = remember(datos, variableElegida, limitesFiltro) { filtrarLecturas(datos, variableElegida, limitesFiltro) }
    val datosConsulta = if (filtroValido) filtrados.registros else emptyList()

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
        Text("Valores de todas las lecturas guardadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectorVariable(variableElegida) { variableElegida = it }
        ControlFiltro(variableFiltro, filtro) { filtro = it }
        Button(enabled = filtroValido && !cargando, onClick = { consultar = true; intento++ }) {
            Text("Consultar historial")
        }
        val variableCsv = variables.first { it.clave == variableElegida }
        val resumenCsv = extremosHistoricos(datosConsulta, variableElegida)
        BotonExportarCsv("extremos_${variableElegida}.csv",
            if (recibido && !cargando && resumenCsv != null) csvExtremos(variableCsv, resumenCsv, limitesFiltro) else null)
        if (datos.isNotEmpty() && filtroValido) ResumenFiltro(filtrados, filtro.activo)
        if (cargando) CircularProgressIndicator()
        Text(mensaje, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (recibido && !cargando) {
            if (sinFecha > 0) Text("Se omitieron $sinFecha registros sin una fecha válida.")
            if (datos.isNotEmpty()) {
                Text("Periodo registrado: ${fechaHistorica(datos.minOf { it.ts })} al ${fechaHistorica(datos.maxOf { it.ts })}")
            }
            variables.filter { it.clave == variableElegida }.forEach { variable ->
                val resumen = remember(datosConsulta, variable.clave) { extremosHistoricos(datosConsulta, variable.clave) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(variable.nombre, style = MaterialTheme.typography.titleMedium)
                        if (resumen == null) {
                            Text("Sin lecturas válidas para esta variable.")
                        } else {
                            Text("${resumen.cantidad} lecturas válidas analizadas")
                            Text("Mínimo", style = MaterialTheme.typography.labelLarge)
                            Text("${numeroHistorico(resumen.minimo.valor)} ${variable.unidad}",
                                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text(fechaHistorica(resumen.minimo.ts), style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Máximo", style = MaterialTheme.typography.labelLarge)
                            Text("${numeroHistorico(resumen.maximo.valor)} ${variable.unidad}",
                                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text(fechaHistorica(resumen.maximo.ts), style = MaterialTheme.typography.bodySmall)
                            if (resumen.minimo.repeticiones > 1 || resumen.maximo.repeticiones > 1) {
                                Text("Repeticiones: mínimo ${resumen.minimo.repeticiones}, máximo ${resumen.maximo.repeticiones}. Se muestra la primera fecha de cada extremo.")
                            }
                            Text("Amplitud: ${numeroHistorico(resumen.maximo.valor - resumen.minimo.valor)} ${variable.unidad}")
                        }
                    }
                }
            }
            Text("Horario de Costa Rica. Los valores se actualizan al recibir nuevas lecturas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun numeroHistorico(valor: Double) = String.format(Locale.getDefault(), "%.1f", valor)
private fun fechaHistorica(ts: Long) = Instant.ofEpochMilli(ts).atZone(zonaConsulta)
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
