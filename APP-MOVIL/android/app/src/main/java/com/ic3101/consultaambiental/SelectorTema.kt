package com.ic3101.consultaambiental

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun SelectorTema(oscuro: Boolean, alCambiar: (Boolean) -> Unit) {
    val posicion by animateDpAsState(if (oscuro) 94.dp else 4.dp, tween(220), label = "Posición del selector")
    val fondo by animateColorAsState(if (oscuro) Color(0xFF2E2448) else Color(0xFFECE7FA), tween(220), label = "Fondo del selector")
    val borde by animateColorAsState(if (oscuro) Color(0xFF65567F) else Color(0xFFD4C9F0), label = "Borde del selector")
    val texto = if (oscuro) Color(0xFFE1D5FA) else Color(0xFF584378)
    val superficie = if (oscuro) listOf(Color(0xFF9C7ADD), Color(0xFF7251B3)) else
        listOf(Color.White, Color(0xFFF0EBFF))
    val icono = if (oscuro) Color.White else Color(0xFF7055C5)
    val forma = RoundedCornerShape(28.dp)

    Box(Modifier.size(width = 144.dp, height = 54.dp)
        .shadow(3.dp, forma)
        .background(fondo, forma).border(1.dp, borde, forma)
        .toggleable(value = oscuro, role = Role.Switch, onValueChange = alCambiar)
        .semantics { contentDescription = "Tema oscuro" }) {
        Text(if (oscuro) "OSCURO" else "CLARO",
            modifier = Modifier.align(if (oscuro) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(start = if (oscuro) 14.dp else 0.dp, end = if (oscuro) 0.dp else 14.dp),
            color = texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Box(Modifier.align(Alignment.CenterStart).offset(x = posicion).size(46.dp)
            .shadow(4.dp, CircleShape).background(Brush.linearGradient(superficie), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(27.dp)) {
                if (oscuro) {
                    val luna = Path().apply {
                        moveTo(size.width * .58f, size.height * .08f)
                        cubicTo(size.width * .03f, 0f, -size.width * .06f, size.height * .85f, size.width * .52f, size.height * .94f)
                        cubicTo(size.width * .8f, size.height, size.width * .98f, size.height * .76f, size.width * .96f, size.height * .57f)
                        cubicTo(size.width * .55f, size.height * .77f, size.width * .3f, size.height * .36f, size.width * .58f, size.height * .08f)
                        close()
                    }
                    drawPath(luna, icono, style = Stroke(1.8.dp.toPx()))
                    drawLine(icono, Offset(size.width * .83f, size.height * .08f), Offset(size.width * .83f, size.height * .28f), 1.5.dp.toPx())
                    drawLine(icono, Offset(size.width * .73f, size.height * .18f), Offset(size.width * .93f, size.height * .18f), 1.5.dp.toPx())
                } else {
                    val centro = Offset(size.width / 2, size.height / 2)
                    drawCircle(icono, size.minDimension * .22f, centro, style = Stroke(1.8.dp.toPx()))
                    for (i in 0..7) {
                        val angulo = i * Math.PI / 4
                        fun extremo(radio: Float) = Offset(centro.x + cos(angulo).toFloat() * size.width * radio,
                            centro.y + sin(angulo).toFloat() * size.height * radio)
                        drawLine(icono, extremo(.34f), extremo(.47f), 1.8.dp.toPx())
                    }
                }
            }
        }
    }
}
