package com.phantomcode.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.theme.LocalThemeController

/** Logo: escudo com gradiente roxo→cyan + raio (marca Cyber-Phantom). */
@Composable
fun PhantomLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalThemeController.current.currentPalette()
    val base = modifier
        .size(size)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Box(modifier = base) {
        Canvas(Modifier.fillMaxSize()) {
            // `size` aqui é o DrawScope.Size (canvas) — não o parâmetro Dp
            val w = size.width
            val h = w * 1.15f
            val shield = Path().apply {
                moveTo(w / 2f, h * 0.06f)
                lineTo(w * 0.88f, h * 0.22f)
                lineTo(w * 0.84f, h * 0.62f)
                quadraticBezierTo(w * 0.78f, h * 0.85f, w / 2f, h * 0.94f)
                quadraticBezierTo(w * 0.22f, h * 0.85f, w * 0.16f, h * 0.62f)
                lineTo(w * 0.12f, h * 0.22f)
                close()
            }
            drawPath(
                path = shield,
                brush = Brush.linearGradient(
                    colors = listOf(palette.accentPrimary, palette.accentSecondary),
                ),
            )
            val bolt = Path().apply {
                moveTo(w * 0.55f, h * 0.30f)
                lineTo(w * 0.37f, h * 0.57f)
                lineTo(w * 0.50f, h * 0.57f)
                lineTo(w * 0.44f, h * 0.73f)
                lineTo(w * 0.65f, h * 0.43f)
                lineTo(w * 0.52f, h * 0.43f)
                close()
            }
            drawPath(bolt, color = Color.Black.copy(alpha = 0.85f))
        }
    }
}

/** Card Deep Slate com borda angular (6dp) e glow opcional. */
@Composable
fun PhantomCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surface)
            .border(
                width = 1.dp,
                color = if (glow) palette.accentPrimary.copy(alpha = 0.6f) else palette.border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Botão primário — filled roxo angular. */
@Composable
fun PhantomPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) palette.accentPrimary else palette.accentPrimary.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/** Botão secundário — outlined cyan/roxo angular. */
@Composable
fun PhantomOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = 1.dp,
                color = if (enabled) palette.accentSecondary.copy(alpha = 0.8f) else palette.border,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = palette.accentSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/** Rótulo de seção em caps com espaçamento. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = LocalThemeController.current.currentPalette()
    Text(
        text = text.uppercase(),
        color = palette.textSecondary,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/** Linha de item de configuração (label + valor). */
@Composable
fun SettingsRow(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = palette.textSecondary, fontSize = 12.sp)
        }
    }
}

/** Swatches para o tema custom (color pickers simplificados). */
val SwatchColors = listOf(
    Color(0xFF9F4DFF), Color(0xFFD34DFF), Color(0xFF00FFFF), Color(0xFF00FF9F),
    Color(0xFFFF3366), Color(0xFFFF9800), Color(0xFF2196F3), Color(0xFFFFFFFF),
    Color(0xFF121212), Color(0xFF000000),
)

@Composable
fun SwatchRow(selected: Color, onPick: (Color) -> Unit) {
    val palette = LocalThemeController.current.currentPalette()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SwatchColors.forEach { c ->
            val isSelected = c.toArgb() == selected.toArgb()
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) palette.accentSecondary else palette.border.copy(alpha = 0.5f),
                        shape = CircleShape,
                    )
                    .clickable { onPick(c) },
            )
        }
    }
}
