package com.phantomcode.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.data.vm.LocalVm
import kotlinx.coroutines.launch

/** Terminal (T17 v1): console ligado às streams do processo QEMU. */
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val vm = LocalVm.current
    val palette = LocalThemeController.current.currentPalette()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val terminal = vm.qemu.terminal
    var input by remember { mutableStateOf("") }

    // Auto-scroll para a última linha
    LaunchedEffect(terminal.lines.size) {
        if (terminal.lines.isNotEmpty()) {
            listState.animateScrollToItem(terminal.lines.size - 1)
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().background(palette.background),
    ) {
        // Barra do terminal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(palette.border.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = palette.textPrimary,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack).padding(10.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Terminal 1", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (terminal.active) palette.success else palette.border, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (terminal.active) "CONECTADO" else "VM PARADA",
                color = if (terminal.active) palette.success else palette.textSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.ClearAll,
                contentDescription = "Limpar",
                tint = palette.textSecondary,
                modifier = Modifier.size(36.dp).clickable { terminal.clear() }.padding(8.dp),
            )
        }

        // Aviso quando a VM está parada
        if (!terminal.active) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceAlt)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "VM parada — inicie o Linux para usar o terminal.",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                PhantomPrimaryButton(
                    text = "Iniciar",
                    onClick = { scope.launch { vm.qemu.start() } },
                )
            }
        }

        // Saída do terminal
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            if (terminal.lines.isEmpty()) {
                item {
                    Text(
                        "Phantom-Code · Terminal Linux (QEMU headless)\n" +
                            "root@phantom:~\$ — inicie a VM e rode apt/pip/npm/ollama.\n",
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
            itemsIndexed(terminal.lines) { _, line ->
                Text(
                    line,
                    color = palette.success,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }

        // Entrada de comando
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\$ ", color = palette.accentPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(palette.accentSecondary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        terminal.send(input)
                        input = ""
                    }),
                )
            }
            Spacer(Modifier.width(10.dp))
            PhantomPrimaryButton(
                text = "Enviar",
                enabled = terminal.active && input.isNotBlank(),
                onClick = {
                    terminal.send(input)
                    input = ""
                },
            )
        }
    }
}
