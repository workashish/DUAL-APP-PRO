package com.utility.toolbox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NebulaDarkColorScheme = darkColorScheme(
    primary = NebulaPrimary,
    secondary = NebulaSecondary,
    tertiary = NebulaTertiary,
    background = NebulaBackground,
    surface = NebulaSurface,
    onBackground = NebulaOnBackground,
    onSurface = NebulaOnSurface,
    onSurfaceVariant = NebulaOnSurfaceVariant,
    error = NebulaError
)

private val NebulaLightColorScheme = lightColorScheme(
    primary = NebulaPrimary,
    secondary = NebulaSecondary,
    tertiary = NebulaTertiary,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF636366),
    error = NebulaError
)

@Composable
fun DualSpaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> NebulaDarkColorScheme
        else -> NebulaLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DualSpaceTypography,
        content = content
    )
}
