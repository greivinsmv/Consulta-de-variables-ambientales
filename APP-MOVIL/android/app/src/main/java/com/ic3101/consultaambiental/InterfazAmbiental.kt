package com.ic3101.consultaambiental

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ic3101.consultaambiental.ui.theme.ConsultaAmbientalTheme

private data class Seccion(val clave: String, val etiqueta: String, val titulo: String, val subtitulo: String)
private val secciones = listOf(
    Seccion("actual", "Inicio", "Consulta ambiental", "Última lectura guardada"),
    Seccion("hora", "Por hora", "Promedios por hora", "Consulta un día y selecciona una hora"),
    Seccion("dia", "Por día", "Promedios diarios", "Consulta un periodo de fechas"),
    Seccion("historico", "Extremos", "Máximos y mínimos", "Valores registrados en el historial")
)

@Composable
fun InterfazAmbiental() {
    val contexto = LocalContext.current
    val preferencias = remember { contexto.getSharedPreferences("apariencia", Context.MODE_PRIVATE) }
    val oscuroSistema = isSystemInDarkTheme()
    var oscuro by rememberSaveable { mutableStateOf(preferencias.getBoolean("oscuro", oscuroSistema)) }
    var pantalla by rememberSaveable { mutableStateOf("actual") }
    val seccion = secciones.first { it.clave == pantalla }
    ConsultaAmbientalTheme(darkTheme = oscuro) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    secciones.forEach { destino ->
                        NavigationBarItem(selected = pantalla == destino.clave, onClick = { pantalla = destino.clave },
                            icon = { IconoAmbiental(destino.clave, Modifier.size(23.dp)) },
                            label = { Text(destino.etiqueta, maxLines = 1) })
                    }
                }
            }
        ) { padding ->
            val scroll = rememberScrollState()
            LaunchedEffect(pantalla) { scroll.scrollTo(0) }
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll)) {
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Color(0xFF5262C7), Color(0xFF9250CE))),
                        RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                    ).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Surface(color = Color.White.copy(alpha = 0.16f), shape = CircleShape) {
                            Box(Modifier.padding(8.dp)) { IconoAmbiental(seccion.clave, Modifier.size(22.dp), Color.White) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Modo oscuro", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Switch(checked = oscuro, onCheckedChange = {
                                oscuro = it
                                preferencias.edit().putBoolean("oscuro", it).apply()
                            }, modifier = Modifier.semantics { contentDescription = "Modo oscuro" })
                        }
                    }
                    Text(seccion.titulo, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(seccion.subtitulo, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (pantalla == "actual") UltimaLectura(Modifier)
                    else Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            when (pantalla) {
                                "hora" -> ConsultaPorHora()
                                "dia" -> ConsultaPorDia()
                                else -> ConsultaHistorica()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TarjetaTemperatura(valor: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.padding(18.dp)) { IconoAmbiental("temperatura", Modifier.size(36.dp)) }
            }
            Text("Temperatura", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Text(valor, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text("Última lectura registrada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TarjetaMedicion(nombre: String, valor: String, icono: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoAmbiental(icono, Modifier.size(26.dp))
            Text(nombre, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IconoAmbiental(tipo: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    // Iconos vectoriales locales: no se descargan imágenes ni se añaden dependencias.
    Canvas(modifier) {
        fun punto(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun linea(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, punto(x1,y1), punto(x2,y2), 2.dp.toPx())
        when (tipo) {
            "actual" -> {
                val casa = Path().apply {
                    moveTo(size.width * .12f, size.height * .45f); lineTo(size.width * .5f, size.height * .12f)
                    lineTo(size.width * .88f, size.height * .45f); lineTo(size.width * .88f, size.height * .88f)
                    lineTo(size.width * .12f, size.height * .88f); close()
                }
                drawPath(casa, color, style = Stroke(2.dp.toPx()))
                linea(.4f,.88f,.4f,.6f); linea(.4f,.6f,.6f,.6f); linea(.6f,.6f,.6f,.88f)
            }
            "hora" -> {
                drawCircle(color, size.minDimension * .39f, punto(.5f,.5f), style = Stroke(2.dp.toPx()))
                linea(.5f,.5f,.5f,.25f); linea(.5f,.5f,.7f,.6f)
            }
            "dia" -> {
                for (i in 0..2) {
                    val x = .23f + i * .27f
                    linea(x,.85f,x,.6f - i * .2f)
                }
                linea(.1f,.9f,.92f,.9f)
            }
            "historico" -> {
                linea(.25f,.15f,.25f,.85f); linea(.1f,.3f,.25f,.15f); linea(.25f,.15f,.4f,.3f)
                linea(.75f,.15f,.75f,.85f); linea(.6f,.7f,.75f,.85f); linea(.75f,.85f,.9f,.7f)
            }
            "temperatura" -> {
                drawCircle(color,size.minDimension*.18f,punto(.5f,.75f),style=Stroke(2.dp.toPx()))
                linea(.42f,.58f,.42f,.18f); linea(.58f,.58f,.58f,.18f); linea(.42f,.18f,.58f,.18f)
                linea(.5f,.7f,.5f,.32f)
            }
            else -> {
                val gota = Path().apply {
                    moveTo(size.width*.5f,size.height*.08f)
                    cubicTo(size.width*.15f,size.height*.5f,0f,size.height*.85f,size.width*.5f,size.height*.92f)
                    cubicTo(size.width,size.height*.85f,size.width*.85f,size.height*.5f,size.width*.5f,size.height*.08f)
                    close()
                }
                drawPath(gota,color,style=Stroke(2.dp.toPx()))
                if(tipo=="rocio") linea(.36f,.65f,.64f,.65f)
            }
        }
    }
}
