package com.phantomcode.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.phantomcode.app.data.LogoController
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.LocalUiStyleController
import com.phantomcode.app.ui.theme.PhantomButtonStyle
import com.phantomcode.app.ui.theme.fontFamily
import com.phantomcode.app.ui.theme.shape

/** Logo: imagem escolhida pelo usuário (pasta linux/) ou o escudo padrão. */
@Composable
fun PhantomLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalThemeController.current.currentPalette()
    val context = LocalContext.current
    val logos = remember { LogoController(context) }
    val custom = logos.selectedFile()
    val bitmap = remember(custom?.absolutePath) {
        custom?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
    val base = modifier
        .size(size)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    if (bitmap != null) {
        Box(modifier = base, contentAlignment = Alignment.Center) {
            Image(
                bitmap = bitmap,
                contentDescription = "Logo",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        return
    }

    Box(modifier = base) {
        // `canvasSize` é o DrawScope.Size (canvas) — não o parâmetro Dp `size`
        val w = this.size.width
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

/** Card com cantos/bordas do estilo do usuário e glow opcional. */
@Composable
fun PhantomCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    val ui = LocalUiStyleController.current.ui
    val corner = ui.cornerStyle.shape()
    val borderWidth = ui.borderStyle.width.dp // NONE (0) = sem borda de verdade
    Column(
        modifier = modifier
            .clip(corner)
            .background(palette.surface)
            .border(
                width = borderWidth,
                color = if (glow) palette.accentPrimary.copy(alpha = 0.6f) else palette.border.copy(alpha = 0.5f),
                shape = corner,
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Botão universal (Design System v2).
 *
 * Segue o estilo escolhido pelo usuário em Settings → UI & Botões, mas aceita
 * [style] e [color] para sobrepor pontualmente. Estilos: Neon (brilho),
 * Hacker (borda dupla de terminal), Gradient, Glass, Ghost, Pill e Sólido.
 * Inclui micro-interação: escala ao pressionar.
 */
@Composable
fun PhantomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    style: PhantomButtonStyle? = null,
    color: Color? = null,
) {
    val palette = LocalThemeController.current.currentPalette()
    val ui = LocalUiStyleController.current.ui
    val s = style ?: ui.buttonStyle
    val corner = ui.cornerStyle.shape()
    val shape = if (s == PhantomButtonStyle.PILL) RoundedCornerShape(50) else corner
    val borderWidth = ui.borderStyle.width.dp // NONE (0) = sem borda de verdade
    val fontFamily = ui.fontStyle.fontFamily()
    val accent = color ?: palette.accentPrimary

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed && enabled) 0.95f else 1f, label = "btnScale")

    val isFilled = s == PhantomButtonStyle.NEON || s == PhantomButtonStyle.GRADIENT ||
        s == PhantomButtonStyle.PILL || s == PhantomButtonStyle.SOLID
    val contentColor = when {
        isFilled -> Color.White
        s == PhantomButtonStyle.GLASS -> palette.textPrimary
        else -> accent
    }

    val buttonContent: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (s == PhantomButtonStyle.HACKER) "> $text" else text,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            fontFamily = fontFamily,
        )
    }

    Box(modifier = modifier.scale(scale), contentAlignment = Alignment.Center) {
        if (s == PhantomButtonStyle.HACKER) {
            // Borda dupla estilo terminal (linha externa + linha interna offset)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .border(borderWidth, accent.copy(alpha = 0.9f), shape)
                    .background(if (enabled) accent.copy(alpha = 0.05f) else Color.Transparent)
                    .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                    .padding(5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .border(1.dp, palette.accentSecondary.copy(alpha = 0.5f), shape)
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        content = buttonContent,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .then(if (s == PhantomButtonStyle.NEON && enabled) {
                        Modifier.shadow(16.dp, shape, spotColor = accent, ambientColor = accent)
                    } else {
                        Modifier
                    })
                    .clip(shape)
                    .then(
                        when {
                            s == PhantomButtonStyle.GRADIENT -> Modifier.background(
                                Brush.linearGradient(listOf(palette.accentPrimary, palette.accentSecondary)),
                            )
                            isFilled -> Modifier.background(if (enabled) accent else accent.copy(alpha = 0.35f))
                            s == PhantomButtonStyle.GLASS -> Modifier.background(palette.surfaceAlt.copy(alpha = 0.55f))
                            else -> Modifier
                        }
                    )
                    .then(
                        if (s == PhantomButtonStyle.GHOST || s == PhantomButtonStyle.GLASS) {
                            Modifier.border(
                                borderWidth,
                                if (enabled) accent.copy(alpha = if (s == PhantomButtonStyle.GHOST) 0.8f else 0.55f) else palette.border,
                                shape,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = buttonContent,
            )
        }
    }
}

/** Botão primário — segue o estilo do usuário (alias de [PhantomButton]). */
@Composable
fun PhantomPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    PhantomButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, icon = icon)
}

/** Botão secundário — variante contorno (GHOST). */
@Composable
fun PhantomOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    PhantomButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        style = PhantomButtonStyle.GHOST,
    )
}

/** Rótulo de seção em caps com espaçamento — fonte conforme estilo do usuário. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = LocalThemeController.current.currentPalette()
    val ui = LocalUiStyleController.current.ui
    Text(
        text = text.uppercase(),
        color = palette.textSecondary,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = ui.fontStyle.fontFamily(),
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
