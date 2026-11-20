package ar.edu.fguerrero.da1.robot_guia_universitario.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue40, onPrimary = Color.White,
    primaryContainer = BlueContainer40, onPrimaryContainer = Color(0xFF001B3E),
    secondary = Teal40, onSecondary = Color.White,
    secondaryContainer = Color(0xFFB4EBEF), onSecondaryContainer = Color(0xFF002022),
    tertiary = Amber40, tertiaryContainer = Color(0xFFFFDEA6),
    error = Danger40,
    background = Color(0xFFF8F9FF), surface = Color(0xFFF8F9FF),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
)

private val DarkColors = darkColorScheme(
    primary = Blue80, onPrimary = Color(0xFF002F65),
    primaryContainer = BlueContainer80, onPrimaryContainer = BlueContainer40,
    secondary = Teal80, secondaryContainer = Color(0xFF004F53),
    tertiary = Amber80, tertiaryContainer = Color(0xFF5C4200),
    error = Danger80,
    background = Color(0xFF111318), surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
)

/** Colores de estado que Material 3 no trae por defecto. */
@Immutable
data class StatusColors(val success: Color, val warning: Color, val danger: Color, val info: Color)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Success40, Warning40, Danger40, Blue40)
}

@Composable
fun RobotGuiaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val status = if (darkTheme) StatusColors(Success80, Warning80, Danger80, Blue80)
    else StatusColors(Success40, Warning40, Danger40, Blue40)
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
