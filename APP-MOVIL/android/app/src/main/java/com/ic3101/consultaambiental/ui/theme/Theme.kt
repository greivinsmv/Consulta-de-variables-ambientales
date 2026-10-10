package com.ic3101.consultaambiental.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF7055C5), onPrimary = Color.White,
    secondary = Color(0xFF5465B0), tertiary = Color(0xFFAD347B),
    background = Color(0xFFF6F5FC), onBackground = Color(0xFF302547),
    surface = Color.White, onSurface = Color(0xFF302547),
    surfaceVariant = Color(0xFFEEE9FA), onSurfaceVariant = Color(0xFF665C7D),
    outline = Color(0xFF837794), outlineVariant = Color(0xFFE3DDF0)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7ADFF), onPrimary = Color(0xFF32205D),
    secondary = Color(0xFFBCC5FF), tertiary = Color(0xFFFFABDC),
    background = Color(0xFF151221), onBackground = Color(0xFFECE6F7),
    surface = Color(0xFF211C30), onSurface = Color(0xFFECE6F7),
    surfaceVariant = Color(0xFF322A45), onSurfaceVariant = Color(0xFFCCC0DD),
    outline = Color(0xFF9D8EAF), outlineVariant = Color(0xFF473D58)
)

@Composable
fun ConsultaAmbientalTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}
