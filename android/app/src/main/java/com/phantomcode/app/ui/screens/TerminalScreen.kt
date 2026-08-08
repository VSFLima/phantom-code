package com.phantomcode.app.ui.screens

import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.data.vm.TerminalPrefs
import com.phantomcode.app.data.vm.TerminalTabKind
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomLogo
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.LocalTerminalStyleController
import jackpal.androidterm.emulatorview.ColorScheme
import jackpal.androidterm.emulatorview.EmulatorView
import jackpal.androidterm.emulatorview.TermSession
import kotlinx.coroutines.launch

/**
 * Terminal (T17): emulador VT100 real (jackpal emulatorview) com abas.
 *
 * - Aba "Linux (QEMU)": console do Linux da VM (anexada ao subir a VM);
 * - Abas "Shell N": shells locais do Android para comandos rápidos;
 * - Teclado físico e virtual com suporte a Ctrl/Alt/Tab do emulador.
 */
@Composable
fun TerminalScreen(onBack: () -> Unit, onOpenToolbox: () -> Unit = {}) {
    val context = LocalContext.current
    val vm = LocalVm.current
    val palette = LocalThemeController.current.currentPalette()
    val terminalStyle = LocalTerminalStyleController.current
    val terminalColors = terminalStyle.colors(palette)
    val keyboard = LocalSoftwareKeyboardController.current
    val terminal = vm.qemu.terminal
    val scope = rememberCoroutineScope()
    val tabScroll = rememberScrollState()
    val terminalPrefs = remember { TerminalPrefs(context) }

    var termView by remember { mutableStateOf<EmulatorView?>(null) }
    var attachedSession by remember { mutableStateOf<TermSession?>(null) }

    // Ciclo de vida da EmulatorView (IME + blink + redesenho)
    LaunchedEffect(termView, attachedSession) {
        termView?.let {
            runCatching {
                it.onResume()
                it.requestFocus()
                keyboard?.show()
            }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().background(palette.background),
    ) {
        // ── Barra superior: voltar · status da VM · abas · nova aba ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = palette.textPrimary,
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (vm.qemu.running) palette.success else palette.border, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (vm.qemu.running) "LINUX ATIVO" else "VM PARADA",
                color = if (vm.qemu.running) palette.success else palette.textSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(tabScroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                terminal.tabs.forEachIndexed { index, tab ->
                    TerminalTabChip(
                        title = tab.title,
                        selected = index == terminal.activeIndex,
                        accent = if (tab.kind == TerminalTabKind.QEMU) palette.accentPrimary else palette.accentSecondary,
                        onSelect = { terminal.selectTab(index) },
                        // Fechar o console da VM para a VM (parar QEMU): sem leitor no stdout,
                        // o pipe enche e o processo trava — parar é o comportamento seguro.
                        onClose = {
                            if (tab.kind == TerminalTabKind.QEMU) vm.qemu.stop() else terminal.closeTab(index)
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (terminal.tabs.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Nova aba (shell local)",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(32.dp).clickable { terminal.addShellTab() }.padding(7.dp),
                    )
                }
            }
        }

        // ── Aviso sem abas (VM parada) ──
        if (terminal.tabs.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(palette.background)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                PhantomLogo(size = 72.dp)
                Spacer(Modifier.size(12.dp))
                Text("PHANTOM-CODE TERMINAL", color = palette.textPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.size(6.dp))
                Text(
                    "Escolha uma ação para começar.",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.size(16.dp))
                PhantomPrimaryButton(
                    text = "Iniciar Linux",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { scope.launch { vm.qemu.start() } },
                )
                Spacer(Modifier.size(8.dp))
                PhantomOutlinedButton(
                    text = "Nova shell Android",
                    icon = Icons.Filled.Add,
                    onClick = {
                        terminal.addShellTab()
                    },
                )
                Spacer(Modifier.size(8.dp))
                PhantomOutlinedButton(
                    text = "Abrir Toolbox",
                    icon = Icons.Filled.Memory,
                    onClick = onOpenToolbox,
                )
            }
        }

        // ── Terminal VT100 real (jackpal emulatorview) ──
        if (terminal.activeTab != null) {
            AndroidView<View>(
                factory = { ctx ->
                // Construção em 2 passos: o construtor (ctx, session, metrics) chama
                // attachSession internamente e CRASHA com session null. Usamos o
                // construtor XML (sem session) + setDensity/attachSession manuais —
                // a sessão é anexada no `update` abaixo quando há aba ativa.
                runCatching { EmulatorView(ctx, null).apply {
                    isFocusableInTouchMode = true
                    setDensity(ctx.resources.displayMetrics)
                    // Tamanho da fonte (dp) define as colunas por linha — fonte
                    // menor = mais texto por linha (quebra de linha organizada).
                    runCatching { setTextSize(terminalPrefs.fontSizeSp) }
                    // Terminal segue a paleta do usuário (Design System v2):
                    // fundo e texto nas cores do app, com o verde de sucesso no prompt.
                    runCatching {
                        setColorScheme(
                            ColorScheme(
                                terminalColors.first.toArgb(),
                                terminalColors.second.toArgb(),
                            ),
                        )
                    }
                    termView = this
                } }.getOrElse {
                    TextView(ctx).apply {
                        text = "Terminal indisponível: ${it.message ?: "falha ao iniciar o emulador"}"
                        setTextColor(palette.textSecondary.toArgb())
                        setBackgroundColor(palette.background.toArgb())
                        setPadding(24, 24, 24, 24)
                    }
                }
                },
                update = { view ->
                    if (view is EmulatorView) {
                        val tab = terminal.activeTab
                        if (tab != null && attachedSession !== tab.session) {
                            runCatching {
                                view.setColorScheme(ColorScheme(terminalColors.first.toArgb(), terminalColors.second.toArgb()))
                                view.setTextSize(terminalPrefs.fontSizeSp)
                                view.attachSession(tab.session)
                                attachedSession = tab.session
                                view.requestFocus()
                                // Recalcula colunas/linhas após o attach — garante que a
                                // quebra de linha acompanhe a largura real da view.
                                view.post { runCatching { view.updateSize(true) } }
                            }
                        }
                    }
                },
             onRelease = { (it as? EmulatorView)?.onPause() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        // ── Rodapé: dica de uso ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (terminal.tabs.isEmpty()) {
                    "Toque em Iniciar para subir a VM."
                } else {
                    "Terminal VT100 · Ctrl/Alt no teclado físico · + para nova aba"
                },
                color = palette.textSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

/** Chip de aba: título + botão fechar. A aba QEMU usa a cor primária. */
@Composable
private fun TerminalTabChip(
    title: String,
    selected: Boolean,
    accent: Color,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) palette.surfaceAlt else palette.surface)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.9f) else palette.border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = if (selected) accent else palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = "Fechar aba",
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp).clickable(onClick = onClose).padding(3.dp),
        )
    }
}
