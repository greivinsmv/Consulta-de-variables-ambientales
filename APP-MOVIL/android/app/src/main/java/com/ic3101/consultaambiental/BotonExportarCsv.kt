package com.ic3101.consultaambiental

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BotonExportarCsv(nombre: String, contenido: String?) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var mensaje by rememberSaveable { mutableStateOf<String?>(null) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val copia = pendiente
        pendiente = null
        if (uri == null) mensaje = "Exportación cancelada."
        else if (copia == null) mensaje = "No se pudo recuperar el contenido. Vuelve a exportar."
        else {
            guardando = true
            scope.launch {
                mensaje = try {
                    withContext(Dispatchers.IO) {
                        val salida = contexto.contentResolver.openOutputStream(uri, "wt")
                            ?: error("No se pudo abrir el archivo.")
                        salida.bufferedWriter(Charsets.UTF_8).use { it.write(copia) }
                    }
                    "CSV guardado."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "No se pudo guardar el CSV. Comprueba el destino e inténtalo de nuevo."
                } finally { guardando = false }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(enabled = contenido != null && !guardando && pendiente == null,
            modifier = Modifier.fillMaxWidth(), onClick = {
                pendiente = contenido
                mensaje = null
                try { selector.launch(nombre) }
                catch (e: Exception) { pendiente = null; mensaje = "No se pudo abrir el selector de archivos." }
            }) { Text(if (guardando) "Guardando…" else "Exportar CSV") }
        mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
