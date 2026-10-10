package com.ic3101.consultaambiental

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ic3101.consultaambiental.ui.theme.ConsultaAmbientalTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConsultaAmbientalTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    UltimaLectura(Modifier.padding(padding).padding(24.dp))
                }
            }
        }
    }
}

@Composable
fun UltimaLectura(modifier: Modifier = Modifier) {
    var mensaje by remember { mutableStateOf("Cargando lecturas…") }
    var temperatura by remember { mutableStateOf<Double?>(null) }
    var humedad by remember { mutableStateOf<Double?>(null) }
    var puntoRocio by remember { mutableStateOf<Double?>(null) }
    var fecha by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
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

    fun valor(numero: Double?, unidad: String): String =
        if (numero != null && numero.isFinite())
            String.format(Locale.getDefault(), "%.1f %s", numero, unidad)
        else "Sin dato"

    val fechaTexto = fecha?.let {
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        formato.timeZone = TimeZone.getTimeZone("America/Costa_Rica")
        formato.format(Date(it))
    } ?: "Sin fecha"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Consulta ambiental", style = MaterialTheme.typography.headlineMedium)
        Text("Equipo 01 · Firebase Realtime Database")
        Text(mensaje)
        Text("Temperatura: ${valor(temperatura, "°C")}")
        Text("Humedad: ${valor(humedad, "%")}")
        Text("Punto de rocío: ${valor(puntoRocio, "°C")}")
        Text("Fecha del registro: $fechaTexto (Costa Rica)")
        Text("El punto de rocío se calcula a partir de temperatura y humedad.")
    }
}