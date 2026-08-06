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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phantomcode.app.ui.theme.LocalThemeController

/** Diálogo estilizado com campo de texto (novo arquivo/pasta, renomear). */
@Composable
fun PhantomDialog(
    title: String,
    placeholder: String = "",
    initialValue: String = "",
    confirmText: String = "OK",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    var text by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(palette.surface, RoundedCornerShape(8.dp))
                .border(1.dp, palette.accentPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(20.dp),
        ) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(palette.accentSecondary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(placeholder, color = palette.textSecondary, fontSize = 14.sp)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                PhantomOutlinedButton(text = "Cancelar", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                PhantomPrimaryButton(
                    text = confirmText,
                    enabled = text.isNotBlank(),
                    onClick = { onConfirm(text.trim()) },
                )
            }
        }
    }
}

/** Diálogo de confirmação (ex.: excluir). */
@Composable
fun PhantomConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Excluir",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(palette.surface, RoundedCornerShape(8.dp))
                .border(1.dp, palette.error.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(20.dp),
        ) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                PhantomOutlinedButton(text = "Cancelar", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                PhantomPrimaryButton(text = confirmText, onClick = onConfirm)
            }
        }
    }
}

/** Folha de ações (menu de contexto ao segurar um item do Explorer). */
@Composable
fun PhantomActionSheet(
    title: String,
    actions: List<Pair<String, ImageVector>>,
    onAction: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(palette.surface, RoundedCornerShape(8.dp))
                .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp),
        ) {
            Text(
                title,
                color = palette.textSecondary,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            actions.forEachIndexed { index, (label, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(index) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.width(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label, color = palette.textPrimary, fontSize = 14.sp)
                }
            }
        }
    }
}
