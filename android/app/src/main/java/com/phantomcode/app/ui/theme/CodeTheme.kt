package com.phantomcode.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Temas de editor de código + terminal — mesmo conjunto do opencode
 * (opencode.ai/docs/themes) e dos presets Phantom.
 *
 * Os `id`s são a fonte de verdade compartilhada:
 *  - editor: assets/editor/editor-themes.js (chrome + sintaxe do CodeMirror);
 *  - terminal: cores fg/bg/cursor abaixo (jackpal só permite esses 4).
 */
enum class CodeTheme(val id: String, val label: String) {
    PHANTOM("phantom", "Phantom"),
    DEEP_SLATE("deep_slate", "Deep Slate"),
    MATRIX("matrix", "Matrix"),
    DRACULA("dracula", "Dracula"),
    NORD("nord", "Nord"),
    SOLARIZED("solarized", "Solarized"),
    TOKYONIGHT("tokyonight", "Tokyonight"),
    EVERFOREST("everforest", "Everforest"),
    AYU("ayu", "Ayu"),
    CATPPUCCIN("catppuccin", "Catppuccin"),
    CATPPUCCIN_MACCHIATO("catppuccin-macchiato", "Catppuccin Macchiato"),
    GRUVBOX("gruvbox", "Gruvbox"),
    KANAGAWA("kanagawa", "Kanagawa"),
    ONE_DARK("one-dark", "One Dark");

    companion object {
        fun byId(id: String): CodeTheme = entries.firstOrNull { it.id == id } ?: PHANTOM
    }
}

/**
 * Cores aplicáveis ao terminal VT100 (jackpal emulatorview): o ColorScheme
 * só aceita foreground/background/cursor. O cursor inverte: fundo na cor de
 * destaque do tema (caret) e letra na cor de fundo do tema.
 */
data class TerminalThemeColors(
    val foreground: Color,
    val background: Color,
    val cursorForeground: Color,
    val cursorBackground: Color,
)

/** Cores do terminal para cada [CodeTheme] (fg/bg = chrome do editor). */
fun terminalColors(theme: CodeTheme): TerminalThemeColors = when (theme) {
    CodeTheme.PHANTOM -> t("#E5E7EB", "#000000", "#9F4DFF")
    CodeTheme.DEEP_SLATE -> t("#E2E2EA", "#0E0E14", "#8B7CFF")
    CodeTheme.MATRIX -> t("#00FF41", "#0A0A0A", "#00FF41")
    CodeTheme.DRACULA -> t("#F8F8F2", "#282A36", "#F8F8F2")
    CodeTheme.NORD -> t("#D8DEE9", "#2E3440", "#88C0D0")
    CodeTheme.SOLARIZED -> t("#839496", "#002B36", "#268BD2")
    CodeTheme.TOKYONIGHT -> t("#C0CAF5", "#1A1B26", "#C0CAF5")
    CodeTheme.EVERFOREST -> t("#D3C6AA", "#1E2326", "#DBBC7F")
    CodeTheme.AYU -> t("#B3B1AD", "#0B0E14", "#FF8F40")
    CodeTheme.CATPPUCCIN -> t("#CDD6F4", "#1E1E2E", "#F5E0DC")
    CodeTheme.CATPPUCCIN_MACCHIATO -> t("#CAD3F5", "#24273A", "#F4DBD6")
    CodeTheme.GRUVBOX -> t("#EBDBB2", "#1D2021", "#FABD2F")
    CodeTheme.KANAGAWA -> t("#DCD7BA", "#1F1F28", "#C0A36E")
    CodeTheme.ONE_DARK -> t("#ABB2BF", "#282C34", "#528BFF")
}

private fun t(fg: String, bg: String, caret: String): TerminalThemeColors =
    TerminalThemeColors(
        foreground = Color(android.graphics.Color.parseColor(fg)),
        background = Color(android.graphics.Color.parseColor(bg)),
        cursorForeground = Color(android.graphics.Color.parseColor(bg)),
        cursorBackground = Color(android.graphics.Color.parseColor(caret)),
    )
