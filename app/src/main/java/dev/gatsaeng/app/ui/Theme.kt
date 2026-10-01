package dev.gatsaeng.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF146B53), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF6BD), onPrimaryContainer = Color(0xFF193C20),
    secondary = Color(0xFF6A745B), secondaryContainer = Color(0xFFE9EEDD),
    background = Color(0xFFF7F8F2), onBackground = Color(0xFF202C25),
    surface = Color(0xFFFCFDF8), onSurface = Color(0xFF202C25),
    surfaceVariant = Color(0xFFE8EDE4), onSurfaceVariant = Color(0xFF59655B),
    outline = Color(0xFF839084),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6DCBC), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF28583F), onPrimaryContainer = Color(0xFFDDF6BD),
    secondaryContainer = Color(0xFF3E4836),
    background = Color(0xFF141C17), onBackground = Color(0xFFE1EAE0),
    surface = Color(0xFF1C261F), onSurface = Color(0xFFE1EAE0),
    surfaceVariant = Color(0xFF303B32), onSurfaceVariant = Color(0xFFC0CDC0),
)

@Composable
fun GatsaengTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
