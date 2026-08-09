package com.phantomcode.app.data

import android.content.Context
import com.phantomcode.app.ui.theme.CodeTheme

/**
 * Preferências do editor CodeMirror (T12/T13):
 * tema (mesmo conjunto de [CodeTheme]/editor-themes.js), tamanho de fonte em
 * px e quebra de linha — aplicadas via PhantomEditor.setTheme/... ao abrir.
 */
class EditorPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Tema do editor (default: phantom). */
    var theme: CodeTheme
        get() = CodeTheme.byId(prefs.getString(KEY_THEME, CodeTheme.PHANTOM.id) ?: CodeTheme.PHANTOM.id)
        set(value) = prefs.edit().putString(KEY_THEME, value.id).apply()

    /** Tamanho da fonte em px (padrão 14 px do CodeMirror). */
    var fontSizePx: Int
        get() = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE_PX)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE, value.coerceIn(MIN_FONT_SIZE_PX, MAX_FONT_SIZE_PX)).apply()

    /** Quebra de linha (wrap) ativada. */
    var wordWrap: Boolean
        get() = prefs.getBoolean(KEY_WRAP, false)
        set(value) = prefs.edit().putBoolean(KEY_WRAP, value).apply()

    /** Família da fonte do editor (P3.2): id no editor-actions.js. */
    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY) ?: DEFAULT_FONT_FAMILY
        set(value) = prefs.edit().putString(KEY_FONT_FAMILY, value).apply()

    /** Estilo do cursor do editor (P3.2): id no editor-actions.js. */
    var cursorStyle: String
        get() = prefs.getString(KEY_CURSOR_STYLE, DEFAULT_CURSOR_STYLE) ?: DEFAULT_CURSOR_STYLE
        set(value) = prefs.edit().putString(KEY_CURSOR_STYLE, value).apply()

    /** Cor de seleção do editor (P3.2): hex como #RRGGBB ou null para seguir o tema. */
    var selectionColor: String?
        get() = prefs.getString(KEY_SELECTION_COLOR, null)
        set(value) = prefs.edit().putString(KEY_SELECTION_COLOR, value).apply()

    companion object {
        private const val PREFS_NAME = "phantom_editor"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size_px"
        private const val KEY_WRAP = "word_wrap"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_CURSOR_STYLE = "cursor_style"
        private const val KEY_SELECTION_COLOR = "selection_color"
        const val MIN_FONT_SIZE_PX = 8
        const val MAX_FONT_SIZE_PX = 36
        const val DEFAULT_FONT_SIZE_PX = 14
        const val DEFAULT_FONT_FAMILY = "mono"
        const val DEFAULT_CURSOR_STYLE = "blink-block"

        /** Presets de fonte exibidos no Settings (id → rótulo). */
        val FONT_FAMILIES = listOf(
            "mono" to "Monospace (JetBrains Mono)",
            "droid" to "Droid Sans Mono",
            "sans" to "Sans-serif",
        )

        /** Presets de cursor exibidos no editor (id → rótulo). */
        val CURSOR_STYLES = listOf(
            "blink-block" to "Bloco (piscando)",
            "block" to "Bloco (fixo)",
            "bar" to "Barra (fixa)",
            "underline" to "Sublinhado",
        )
    }
}
