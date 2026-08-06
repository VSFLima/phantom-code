package com.phantomcode.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Design System v2 — estilo livre do usuário (W1).
 *
 * Cada usuário dá personalidade ao app: estilos base prontos, e o usuário
 * edita a partir deles. Tudo persiste em SharedPreferences e recompõe ao vivo.
 *
 * Quatro dimensões: botões · cantos · bordas · letras — aplicadas ao app
 * inteiro (botões, cards, diálogos) e ao terminal (cores).
 */

/** Estilos de botão prontos — o usuário escolhe o seu (ou usa o padrão). */
enum class PhantomButtonStyle(val id: String, val label: String, val desc: String) {
    NEON("neon", "Neon", "Preenchido com brilho"),
    HACKER("hacker", "Hacker", "Terminal com borda dupla"),
    GRADIENT("gradient", "Gradient", "Roxo → ciano em degradê"),
    GLASS("glass", "Glass", "Translúcido elegante"),
    GHOST("ghost", "Ghost", "Só contorno"),
    PILL("pill", "Pill", "Cápsula arredondada"),
    SOLID("solid", "Sólido", "Clássico reto"),
}

/** Arredondamento dos cantos (aplicado a botões, cards, diálogos). */
enum class PhantomCornerStyle(val id: String, val label: String, val dp: Float) {
    SHARP("sharp", "Sharp", 2f),
    ANGULAR("angular", "Angular", 6f),
    ROUNDED("rounded", "Rounded", 12f),
    SOFT("soft", "Soft", 20f),
}

/** Espessura das bordas/linhas. */
enum class PhantomBorderStyle(val id: String, val label: String, val width: Float) {
    NONE("none", "Nenhuma", 0f),
    HAIRLINE("hairline", "Fina", 1f),
    STANDARD("standard", "Padrão", 2f),
    BOLD("bold", "Grossa", 3f),
}

/** Estilo das letras (títulos, botões, labels de seção). */
enum class PhantomFontStyle(val id: String, val label: String) {
    HACKER("hacker", "Hacker Mono"),
    MODERN("modern", "Moderna"),
    CLASSIC("classic", "Clássica"),
}

/** Preferências de estilo de UI do usuário. */
data class UiStylePrefs(
    val buttonStyle: PhantomButtonStyle = PhantomButtonStyle.NEON,
    val cornerStyle: PhantomCornerStyle = PhantomCornerStyle.ANGULAR,
    val borderStyle: PhantomBorderStyle = PhantomBorderStyle.HAIRLINE,
    val fontStyle: PhantomFontStyle = PhantomFontStyle.HACKER,
)

/** Estado do estilo de UI — persistido e reativo (preview ao vivo). */
class UiStyleController(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var ui by mutableStateOf(load())
        private set

    /** Aplica uma transformação nas preferências e persiste. */
    fun update(transform: (UiStylePrefs) -> UiStylePrefs) {
        ui = transform(ui)
        prefs.edit()
            .putString(KEY_BUTTON, ui.buttonStyle.id)
            .putString(KEY_CORNER, ui.cornerStyle.id)
            .putString(KEY_BORDER, ui.borderStyle.id)
            .putString(KEY_FONT, ui.fontStyle.id)
            .apply()
    }

    private fun load(): UiStylePrefs {
        fun <T : Enum<T>> enumOf(values: Array<T>, id: String?, fallback: T): T =
            values.firstOrNull { it.id == id } ?: fallback
        return UiStylePrefs(
            buttonStyle = enumOf(PhantomButtonStyle.values(), prefs.getString(KEY_BUTTON, null), PhantomButtonStyle.NEON),
            cornerStyle = enumOf(PhantomCornerStyle.values(), prefs.getString(KEY_CORNER, null), PhantomCornerStyle.ANGULAR),
            borderStyle = enumOf(PhantomBorderStyle.values(), prefs.getString(KEY_BORDER, null), PhantomBorderStyle.HAIRLINE),
            fontStyle = enumOf(PhantomFontStyle.values(), prefs.getString(KEY_FONT, null), PhantomFontStyle.HACKER),
        )
    }

    companion object {
        private const val PREFS_NAME = "phantom_ui_style"
        private const val KEY_BUTTON = "button"
        private const val KEY_CORNER = "corner"
        private const val KEY_BORDER = "border"
        private const val KEY_FONT = "font"
    }
}

val LocalUiStyleController = staticCompositionLocalOf<UiStyleController> {
    error("UiStyleController não fornecido — envolva o conteúdo em PhantomRoot()")
}

/** Shape de cantos conforme a preferência do usuário. */
fun PhantomCornerStyle.shape(): RoundedCornerShape = RoundedCornerShape(dp.dp)

/** Família de fonte conforme o estilo de letras escolhido. */
fun PhantomFontStyle.fontFamily(): FontFamily = when (this) {
    PhantomFontStyle.HACKER -> FontFamily.Monospace
    PhantomFontStyle.MODERN -> FontFamily.SansSerif
    PhantomFontStyle.CLASSIC -> FontFamily.Serif
}
