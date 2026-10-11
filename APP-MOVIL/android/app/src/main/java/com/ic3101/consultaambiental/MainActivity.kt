package com.ic3101.consultaambiental

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { InterfazAmbiental() }
    }
}

@Composable
internal fun UltimaLectura(modifier: Modifier = Modifier, historial: HistorialClima? = null) {
    var mensaje by remember { mutableStateOf("Cargando lecturas…") }
    var temperatura by remember { mutableStateOf<Double?>(null) }
    var humedad by remember { mutableStateOf<Double?>(null) }
    var puntoRocio by remember { mutableStateOf<Double?>(null) }
    var fecha by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(historial) {
        if (historial != null) {
            val ultimo = historial.registros.maxByOrNull { it.ts }
            temperatura = ultimo?.valores?.get("temperatura")
            humedad = ultimo?.valores?.get("humedad")
            puntoRocio = ultimo?.valores?.get("puntoRocio")
            fecha = ultimo?.ts
            mensaje = "Último dato horario del periodo descargado"
            onDispose { }
        } else {
            val consulta = FirebaseDatabase.getInstance(
                "https://proyecto-iot-fa353-default-rtdb.firebaseio.com"
            ).getReference("lecturas/equipo01").orderByKey().limitToLast(1)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lectura = snapshot.children.firstOrNull()
                    temperatura = (lectura?.child("temperatura")?.value as? Number)?.toDouble()
                    humedad = (lectura?.child("humedad")?.value as? Number)?.toDouble()
                    puntoRocio = (lectura?.child("puntoRocio")?.value as? Number)?.toDouble()
                    fecha = (lectura?.child("ts")?.value as? Number)?.toLong()
                    mensaje = when {
                        lectura == null -> "Todavía no hay lecturas para equipo01."
                        temperatura == null || humedad == null || puntoRocio == null || fecha == null ->
                            "El último registro está incompleto."
                        else -> "Último registro recibido"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    temperatura = null
                    humedad = null
                    puntoRocio = null
                    fecha = null
                    mensaje = "No se pudo leer Firebase: ${error.message}"
                }
            }
            consulta.addValueEventListener(listener)
            onDispose { consulta.removeEventListener(listener) }
        }
    }

    fun valor(numero: Double?, unidad: String): String =
        if (numero != null && numero.isFinite())
            String.format(Locale.getDefault(), "%.1f %s", numero, unidad)
        else "Sin dato"

    val fechaTexto = fecha?.let {
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        formato.timeZone = TimeZone.getTimeZone("America/Costa_Rica")
        formato.format(Date(it))
    } ?: "Sin fecha"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(if (historial == null) "Lectura actual" else "Dato horario más reciente", style = MaterialTheme.typography.titleLarge)
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(mensaje, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
        TarjetaTemperatura(valor(temperatura, "°C"), if (historial == null) "Última lectura registrada" else "Dato de Open-Meteo")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TarjetaMedicion("Humedad", valor(humedad, "%"), "humedad", Modifier.weight(1f))
            TarjetaMedicion("Punto de rocío", valor(puntoRocio, "°C"), "rocio", Modifier.weight(1f))
        }
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (historial == null) "Fecha de lectura" else "Fecha del dato horario", style = MaterialTheme.typography.titleSmall)
                Text(fechaTexto, style = MaterialTheme.typography.bodyLarge)
                Text("Horario de Costa Rica", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(if (historial == null) "El punto de rocío se calcula a partir de temperatura y humedad." else "Punto de rocío proporcionado por Open-Meteo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
