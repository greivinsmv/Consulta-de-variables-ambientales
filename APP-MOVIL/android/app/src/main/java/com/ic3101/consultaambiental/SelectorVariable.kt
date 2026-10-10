package com.ic3101.consultaambiental

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun SelectorVariable(elegida: String, alElegir: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        variables.forEach { variable ->
            val activa = variable.clave == elegida
            Surface(onClick = { alElegir(variable.clave) },
                modifier = Modifier.weight(1f).semantics { selected = activa; role = Role.RadioButton },
                shape = MaterialTheme.shapes.small,
                color = if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (activa) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (activa) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(variable.nombre, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
