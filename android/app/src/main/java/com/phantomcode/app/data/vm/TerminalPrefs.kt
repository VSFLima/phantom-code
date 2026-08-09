package com.phantomcode.app.data.vm

import android.content.Context

/**
 * Preferências do terminal VT100 (T17/D11): tamanho da fonte em dp.
 *
 * O tamanho de fonte define quantas colunas cabem na tela — fonte maior
 * quebra linhas mais cedo, fonte menor cabe mais texto por linha
 * (organização da quebra de linha no emulador).
 */
class TerminalPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)

    /** Tamanho da fonte em dp (padrão: 10 dp — mesmo do jackpal). */
    var fontSizeSp: Int
        get() = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE_SP)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE, value.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)).apply()

    companion object {
        const val MIN_FONT_SIZE_SP = 6
        const val MAX_FONT_SIZE_SP = 32
        const val DEFAULT_FONT_SIZE_SP = 10
        private const val KEY_FONT_SIZE = "font_size_sp"
    }
}
