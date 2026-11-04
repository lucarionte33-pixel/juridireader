package com.juridireader.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.juridireader.app.ui.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    background = SepiaBackground,
    surface = SepiaSurface,
    onPrimary = Color.White,
    onBackground = SepiaOnSurface,
    onSurface = SepiaOnSurface
)

@Composable
fun JuridireaderTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.SEPIA -> SepiaColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
