package com.phantomcode.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Cores customizáveis pelo usuário (Settings → Aparência → Custom). */
data class CustomThemeColors(
    val background: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF121212),
    val accentPrimary: Color = Color(0xFF9F4DFF),
    val accentSecondary: Color = Color(0xFF00FFFF),
    val textPrimary: Color = Color(0xFFE5E7EB),
    val success: Color = Color(0xFF00FF9F),
)

/**
 * Estado do tema (preset + cores custom), persistido em SharedPreferences.
 * Mudanças recompõem o app inteiro → preview ao vivo (D10).
 */
class ThemeController(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var preset by mutableStateOf(loadPreset())
        private set

    var custom by mutableStateOf(
        CustomThemeColors(
            background = loadColor(KEY_CUSTOM_BACKGROUND, CustomThemeColors().background),
            surface = loadColor(KEY_CUSTOM_SURFACE, CustomThemeColors().surface),
            accentPrimary = loadColor(KEY_CUSTOM_ACCENT, CustomThemeColors().accentPrimary),
            accentSecondary = loadColor(KEY_CUSTOM_SECONDARY, CustomThemeColors().accentSecondary),
            textPrimary = loadColor(KEY_CUSTOM_TEXT, CustomThemeColors().textPrimary),
            success = loadColor(KEY_CUSTOM_SUCCESS, CustomThemeColors().success),
        ),
    )
        private set

    fun selectPreset(p: PhantomPreset) {
        preset = p
        prefs.edit().putString(KEY_PRESET, p.name).apply()
    }

    fun setCustomColor(key: String, color: Color) {
        custom = when (key) {
            "background" -> custom.copy(background = color)
            "surface" -> custom.copy(surface = color)
            "accent" -> custom.copy(accentPrimary = color)
            "secondary" -> custom.copy(accentSecondary = color)
            "text" -> custom.copy(textPrimary = color)
            "success" -> custom.copy(success = color)
            else -> custom
        }
        prefs.edit().putInt("custom_$key", color.toArgb()).apply()
    }

    fun currentPalette(): PhantomPalette =
        if (preset == PhantomPreset.CUSTOM) customPalette(custom) else PhantomPalettes.getValue(preset)

    private fun loadPreset(): PhantomPreset {
        val name = prefs.getString(KEY_PRESET, PhantomPreset.PHANTOM.name) ?: PhantomPreset.PHANTOM.name
        return runCatching { PhantomPreset.valueOf(name) }.getOrDefault(PhantomPreset.PHANTOM)
    }

    private fun loadColor(key: String, fallback: Color): Color =
        Color(prefs.getInt(key, fallback.toArgb()))

    companion object {
        private const val PREFS_NAME = "phantom_theme"
        private const val KEY_PRESET = "preset"
        private const val KEY_CUSTOM_BACKGROUND = "custom_background"
        private const val KEY_CUSTOM_SURFACE = "custom_surface"
        private const val KEY_CUSTOM_ACCENT = "custom_accent"
        private const val KEY_CUSTOM_SECONDARY = "custom_secondary"
        private const val KEY_CUSTOM_TEXT = "custom_text"
        private const val KEY_CUSTOM_SUCCESS = "custom_success"

        fun customPalette(c: CustomThemeColors): PhantomPalette =
            PhantomPalettes.getValue(PhantomPreset.PHANTOM).copy(
                name = "Custom",
                background = c.background,
                surface = c.surface,
                accentPrimary = c.accentPrimary,
                accentSecondary = c.accentSecondary,
                textPrimary = c.textPrimary,
                success = c.success,
            )
    }
}

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("ThemeController não fornecido — envolva o conteúdo em PhantomRoot()")
}
