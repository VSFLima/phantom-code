package com.phantomcode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Aplica a paleta Phantom escolhida como tema global (preview ao vivo). */
@Composable
fun PhantomTheme(
    palette: PhantomPalette,
    content: @Composable () -> Unit,
) {
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accentPrimary,
            onPrimary = Color.White,
            secondary = palette.accentSecondary,
            onSecondary = Color.Black,
            tertiary = palette.accentBright,
            onTertiary = Color.Black,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.textSecondary,
            error = palette.error,
            onError = Color.White,
            outline = palette.border,
            outlineVariant = palette.border,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = palette.accentPrimary,
            onPrimary = Color.White,
            secondary = palette.accentSecondary,
            onSecondary = Color.Black,
            tertiary = palette.accentBright,
            onTertiary = Color.Black,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceAlt,
            onSurfaceVariant = palette.textSecondary,
            error = palette.error,
            onError = Color.White,
            outline = palette.border,
            outlineVariant = palette.border,
            scrim = Color.White,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = PhantomTypography,
        content = content,
    )
}

val PhantomTypography = Typography()
