package com.ic3101.consultaambiental

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

internal const val intervaloConsultaClima = 15 * 60 * 1000L
internal data class ClimaActual(val registro: RegistroAmbiental, val consultadoEn: Long, val intervaloSegundos: Int)
internal fun urlClimaActual(): String =
    "https://api.open-meteo.com/v1/forecast?latitude=$latitudSantaClara&longitude=$longitudSantaClara" +
        "&current=temperature_2m,relative_humidity_2m,dew_point_2m&temperature_unit=celsius" +
        "&timeformat=unixtime&timezone=America%2FCosta_Rica&forecast_days=1"

internal fun interpretarClimaActual(json: String, consultadoEn: Long): ClimaActual {
    val respuesta = JSONObject(json)
    require(!respuesta.optBoolean("error")) { "Open-Meteo rechazó la consulta actual." }
    val unidades = respuesta.getJSONObject("current_units")
    require(unidades.getString("time") == "unixtime" && unidades.getString("temperature_2m") == "°C" &&
        unidades.getString("relative_humidity_2m") == "%" && unidades.getString("dew_point_2m") == "°C") {
        "La API devolvió unidades distintas de las solicitadas."
    }
    val actual = respuesta.getJSONObject("current")
    val segundos = (actual.get("time") as? Number)?.toDouble()
    require(segundos != null && segundos.isFinite() && segundos >= 0 && segundos <= 253402300799.0 && segundos % 1.0 == 0.0) {
        "La fecha del dato actual no es válida."
    }
    val intervalo = (actual.get("interval") as? Number)?.toDouble()
    require(intervalo != null && intervalo.isFinite() && intervalo in 1.0..3600.0 && intervalo % 1.0 == 0.0) {
        "El intervalo del dato actual no es válido."
    }
    val campos = mapOf("temperatura" to "temperature_2m", "humedad" to "relative_humidity_2m", "puntoRocio" to "dew_point_2m")
    val valores = campos.mapNotNull { (clave, campo) ->
        val valor = (actual.opt(campo) as? Number)?.toDouble()
        if (valor != null && valor.isFinite()) clave to valor else null
    }.toMap()
    require(valores.isNotEmpty()) { "La API no devolvió valores actuales válidos." }
    return ClimaActual(RegistroAmbiental(segundos!!.toLong() * 1000, valores), consultadoEn, intervalo!!.toInt())
}

internal suspend fun descargarClimaActual(): ClimaActual = withContext(Dispatchers.IO) {
    val conexion = URL(urlClimaActual()).openConnection() as HttpsURLConnection
    conexion.connectTimeout = 15000
    conexion.readTimeout = 20000
    val json = try {
        if (conexion.responseCode != 200) throw IOException("Open-Meteo respondió HTTP ${conexion.responseCode}.")
        conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally { conexion.disconnect() }
    interpretarClimaActual(json, Instant.now().toEpochMilli())
}

internal fun datoActualAnterior(clima: ClimaActual, ahora: Long): Boolean =
    ahora - clima.registro.ts > maxOf(30 * 60 * 1000L, clima.intervaloSegundos * 2000L)

@Composable
internal fun VistaClimaActual(clima: ClimaActual?, cargando: Boolean, mensaje: String?, alActualizar: () -> Unit) {
    val enlaces = LocalUriHandler.current
    val ahora = System.currentTimeMillis()
    fun valor(clave: String, unidad: String) = clima?.registro?.valores?.get(clave)?.let {
        String.format(Locale.getDefault(), "%.1f %s", it, unidad)
    } ?: "Sin dato"
    fun fecha(ts: Long) = Instant.ofEpochMilli(ts).atZone(zonaConsulta)
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Clima actual · Santa Clara", style = MaterialTheme.typography.titleLarge)
        Text("Estimaciones de Open-Meteo para la zona. Consulta automática cada 15 minutos mientras Inicio esté visible.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(enabled = !cargando, onClick = alActualizar) { Text("Actualizar ahora") }
        if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (clima != null) {
            if (datoActualAnterior(clima, ahora)) {
                Text("La última estimación disponible es de ${fecha(clima.registro.ts)}. Puede no representar las condiciones actuales.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            TarjetaTemperatura(valor("temperatura", "°C"), "Dato meteorológico modelado")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaMedicion("Humedad", valor("humedad", "%"), "humedad", Modifier.weight(1f))
                TarjetaMedicion("Punto de rocío", valor("puntoRocio", "°C"), "rocio", Modifier.weight(1f))
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Fecha del dato", style = MaterialTheme.typography.titleSmall)
                    Text(fecha(clima.registro.ts))
                    Text("Última consulta: ${fecha(clima.consultadoEn)}", style = MaterialTheme.typography.bodySmall)
                    Text("Horario de Costa Rica", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (!cargando && mensaje == null) Text("Pulsa Actualizar ahora para consultar el clima.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { enlaces.openUri(urlProveedorClima) }) { Text("Open-Meteo") }
            TextButton(onClick = { enlaces.openUri(licenciaClima) }) { Text("CC BY 4.0") }
        }
    }
}
