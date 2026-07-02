package com.utility.toolbox.ui.theme

import androidx.compose.ui.graphics.Color

// Primary - Nebula Palette
val NebulaPrimary = Color(0xFF7C4DFF) // Electric Violet
val NebulaSecondary = Color(0xFF00E5FF) // Cyan Neon
val NebulaTertiary = Color(0xFFFF4081) // Rose Glow

// Deep Backgrounds
val NebulaBackground = Color(0xFF0A0A0B)
val NebulaSurface = Color(0xFF161618)
val NebulaSurfaceGlass = Color(0xCC161618)

// Text
val NebulaOnBackground = Color(0xFFF5F5F7)
val NebulaOnSurface = Color(0xFFF5F5F7)
val NebulaOnSurfaceVariant = Color(0xFFA1A1AA)

// State colors
val NebulaSuccess = Color(0xFF00C853)
val NebulaError = Color(0xFFFF5252)
val NebulaWarning = Color(0xFFFFD600)

val NebulaGradientStart = Color(0xFF7C4DFF)
val NebulaGradientEnd = Color(0xFF00E5FF)

// Workspace icon colors (Nebula variants)
val WorkspaceColors = listOf(
    Color(0xFF7C4DFF), // Violet
    Color(0xFF00E5FF), // Cyan
    Color(0xFF00C853), // Emerald
    Color(0xFFFFD600), // Gold
    Color(0xFFFF5252), // Coral
    Color(0xFFFF4081), // Rose
    Color(0xFF7C4DFF).copy(alpha = 0.7f),
    Color(0xFF00E5FF).copy(alpha = 0.7f)
)

// Workspace icon colors as Int ARGB values (for domain model)
val WorkspaceColorInts = listOf(
    0xFF7C4DFF.toInt(),
    0xFF448AFF.toInt(),
    0xFF4CAF50.toInt(),
    0xFFFFA726.toInt(),
    0xFFE53935.toInt(),
    0xFFE91E63.toInt(),
    0xFF00BCD4.toInt(),
    0xFF9C27B0.toInt()
)
