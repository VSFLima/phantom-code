package com.phantomcode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phantomcode.app.ui.theme.LocalThemeController

/** Comando da palette (D14). */
data class PaletteCommand(
    val label: String,
    val icon: ImageVector? = null,
    val keywords: String = "",
    val action: () -> Unit,
)

/**
 * Command Palette estilo VS Code (D14): overlay escuro com busca por texto,
 * navegação por toque e ações rápidas do app.
 */
@Composable
fun CommandPalette(
    commands: List<PaletteCommand>,
    onDismiss: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val filtered = remember(query, commands) {
        if (query.isBlank()) commands
        else commands.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.keywords.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surface)
                .border(1.dp, palette.accentPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Campo de busca
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceAlt)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(palette.accentSecondary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Digite um comando…", color = palette.textSecondary, fontSize = 13.sp)
                            inner()
                        },
                    )
                    Text("⌘⇧P", color = palette.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                // Lista de comandos
                Box(Modifier.fillMaxWidth().height(if (filtered.size > 8) 320.dp else (filtered.size * 44).dp.coerceAtLeast(0.dp))) {
                    Column(Modifier.fillMaxSize()) {
                        filtered.take(12).forEach { cmd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cmd.action()
                                        onDismiss()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (cmd.icon != null) {
                                    Icon(cmd.icon, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    cmd.label,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("→", color = palette.textSecondary, fontSize = 11.sp)
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(palette.border.copy(alpha = 0.3f))
                            )
                        }
                        if (filtered.isEmpty()) {
                            Text(
                                "Nenhum comando encontrado",
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
