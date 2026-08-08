package com.phantomcode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phantomcode.app.ui.theme.CodeTheme
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.PhantomBorderStyle
import com.phantomcode.app.ui.theme.PhantomButtonStyle
import com.phantomcode.app.ui.theme.PhantomCornerStyle
import com.phantomcode.app.ui.theme.PhantomFontStyle
import com.phantomcode.app.ui.theme.TerminalPreset
import com.phantomcode.app.ui.theme.shape

/**
 * Diálogo genérico de escolha de estilo com preview ao vivo (Design System v2).
 * Cada opção renderiza uma prévia real ([render]) — o usuário vê antes de escolher.
 */
@Composable
fun <T> StylePickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    render: @Composable (T) -> Unit,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(palette.surface, RoundedCornerShape(12.dp))
                .border(1.dp, palette.accentPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            options.forEach { option ->
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) palette.accentPrimary.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) palette.accentPrimary else palette.border.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onPick(option) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    render(option)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        labelOf(option),
                        color = if (isSelected) palette.accentPrimary else palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun <T> labelOf(option: T): String = when (option) {
    is PhantomButtonStyle -> option.label
    is PhantomCornerStyle -> option.label
    is PhantomBorderStyle -> option.label
    is PhantomFontStyle -> option.label
    is CodeTheme -> option.label
    is TerminalPreset -> option.label
    is Int -> "$option px"
    else -> option.toString()
}

/** Previews reais de cada estilo — usados dentro do [StylePickerDialog]. */

@Composable
fun ButtonStylePreview(style: PhantomButtonStyle) {
    PhantomButton(text = "Teste", onClick = {}, style = style, modifier = Modifier.width(110.dp))
}

@Composable
fun CornerStylePreview(corner: PhantomCornerStyle) {
    val palette = LocalThemeController.current.currentPalette()
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(44.dp)
            .clip(corner.shape())
            .background(palette.surfaceAlt)
            .border(1.dp, palette.accentSecondary.copy(alpha = 0.7f), corner.shape()),
    )
}

@Composable
fun BorderStylePreview(border: PhantomBorderStyle) {
    val palette = LocalThemeController.current.currentPalette()
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surfaceAlt)
            .border(
                width = if (border.width > 0f) border.width.dp else 1.dp,
                color = if (border.width == 0f) palette.surfaceAlt else palette.accentPrimary.copy(alpha = 0.8f),
                shape = RoundedCornerShape(6.dp),
            ),
    )
}

@Composable
fun FontStylePreview(font: PhantomFontStyle) {
    val palette = LocalThemeController.current.currentPalette()
    Text(
        text = "Phantom",
        color = palette.accentPrimary,
        fontFamily = when (font) {
            PhantomFontStyle.HACKER -> FontFamily.Monospace
            PhantomFontStyle.MODERN -> FontFamily.SansSerif
            PhantomFontStyle.CLASSIC -> FontFamily.Serif
        },
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    )
}
