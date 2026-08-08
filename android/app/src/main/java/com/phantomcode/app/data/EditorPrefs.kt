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

    companion object {
        private const val PREFS_NAME = "phantom_editor"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size_px"
        private const val KEY_WRAP = "word_wrap"
        const val MIN_FONT_SIZE_PX = 8
        const val MAX_FONT_SIZE_PX = 36
        const val DEFAULT_FONT_SIZE_PX = 14
    }
}
