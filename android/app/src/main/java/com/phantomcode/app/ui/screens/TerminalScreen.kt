package com.phantomcode.app.ui.screens

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.data.vm.LogTermSession
import com.phantomcode.app.data.vm.TerminalPrefs
import com.phantomcode.app.data.vm.TerminalTabKind
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomLogo
import com.phantomcode.app.ui.components.PhantomTerminalView
import com.phantomcode.app.ui.components.StylePickerDialog
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.LocalTerminalStyleController
import com.phantomcode.app.ui.theme.TerminalPreset
import com.phantomcode.app.ui.theme.TerminalThemeColors
import com.phantomcode.app.ui.theme.terminalColorsFor
import jackpal.androidterm.emulatorview.ColorScheme
import jackpal.androidterm.emulatorview.EmulatorView
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
    val tc = terminalStyle.colors(palette)
    val terminal = vm.qemu.terminal
    val scope = rememberCoroutineScope()
    val tabScroll = rememberScrollState()
    val terminalPrefs = remember { TerminalPrefs(context) }

    var termView by remember { mutableStateOf<EmulatorView?>(null) }
    var themePickerOpen by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }

    /** Copia o texto selecionado no terminal para a área de transferência. */
    fun copySelection() {
        val txt = (termView as? PhantomTerminalView)?.getSelectedText()?.trim()
        if (!txt.isNullOrEmpty()) {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("terminal", txt))
            }
            Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
        }
        (termView as? PhantomTerminalView)?.toggleSelectingText()
    }

    /** Cola o texto da área de transferência no terminal ativo (Termux-like). */
    fun pasteClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        val session = terminal.activeTab?.session ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        runCatching { session.write(bytes, 0, bytes.size) }
    }

    /** Abre um shell local; falha silenciosa vira aviso visível (antes o toque em
     *  "+" não fazia nada quando o shell não podia ser iniciado). */
    fun newShell(cwd: String? = null) {
        if (!terminal.addShellTab(cwd)) {
            Toast.makeText(
                context,
                "Não foi possível abrir um shell local neste dispositivo",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Escreve bytes crus na sessão ativa (ESC/Tab/setas do teclado virtual). */
    fun sendKeys(text: String) {
        val session = terminal.activeTab?.session ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        runCatching { session.write(bytes, 0, bytes.size) }
    }

    fun sendCommand() {
        val command = commandInput
        val session = terminal.activeTab?.session ?: return
        if (command.isBlank()) return
        val bytes = (command + "\n").toByteArray(Charsets.UTF_8)
        runCatching { session.write(bytes, 0, bytes.size) }
        commandInput = ""
    }

    // Abre o teclado do Android via InputMethodManager (o keyboard?.show() do
    // Compose falha com a EmulatorView nativa — o IME precisa ser acionado
    // diretamente depois que a view tem foco).
    fun forceKeyboard(view: View) {
        view.post {
            runCatching {
                view.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /** Constrói o ColorScheme do jackpal com as 4 cores do tema atual. */
    fun schemeOf(colors: TerminalThemeColors): ColorScheme = ColorScheme(
        colors.foreground.toArgb(),
        colors.background.toArgb(),
        colors.cursorForeground.toArgb(),
        colors.cursorBackground.toArgb(),
    )

    // Ciclo de vida da EmulatorView (IME + blink + redesenho). O requestFocus
    // é assíncrono — postamos para garantir que o foco seja aplicado depois do
    // layout e o teclado virtual (IME) abra de fato ao entrar no terminal.
    LaunchedEffect(termView) {
        termView?.let { view ->
            runCatching {
                view.onResume()
                forceKeyboard(view)
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
                        modifier = Modifier.size(32.dp).clickable { newShell() }.padding(7.dp),
                    )
                }
            }
            // Divisor entre o grupo "abas" e o grupo "ações" (organização da barra).
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(1.dp).height(22.dp).background(palette.border))
            Spacer(Modifier.width(4.dp))
            if (selecting) {
                TermActionChip(
                    text = "Copiar",
                    icon = Icons.Filled.ContentCopy,
                    onClick = { copySelection() },
                )
                Spacer(Modifier.width(6.dp))
                TermActionChip(
                    text = "Sair",
                    icon = Icons.Filled.Close,
                    onClick = { (termView as? PhantomTerminalView)?.toggleSelectingText() },
                )
                Spacer(Modifier.width(4.dp))
            } else {
                TermActionChip(
                    text = "Ctrl",
                    icon = Icons.Filled.Keyboard,
                    onClick = { termView?.sendControlKey() },
                )
                Spacer(Modifier.width(4.dp))
            }
            // Menu de ações do terminal: tudo que é menos frequente fica aqui.
            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Ações do terminal",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(34.dp).clickable { menuOpen = true }.padding(7.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Nova aba (shell local)") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = {
                            menuOpen = false
                            newShell()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (selecting) "Sair da seleção" else "Selecionar texto") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = {
                            menuOpen = false
                            (termView as? PhantomTerminalView)?.toggleSelectingText()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Colar") },
                        leadingIcon = { Icon(Icons.Filled.ContentPaste, null) },
                        onClick = {
                            menuOpen = false
                            pasteClipboard()
                        },
                    )
                    HorizontalDivider()
                    val quickKeys = listOf(
                        "ESC" to "\u001b",
                        "Tab" to "\t",
                        "↑" to "\u001b[A",
                        "↓" to "\u001b[B",
                        "←" to "\u001b[D",
                        "→" to "\u001b[C",
                        "⌫ Backspace" to "\u007f",
                    )
                    quickKeys.forEach { (label, seq) ->
                        DropdownMenuItem(
                            text = { Text("Tecla $label") },
                            onClick = {
                                menuOpen = false
                                sendKeys(seq)
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Tema do terminal") },
                        leadingIcon = { Icon(Icons.Filled.Palette, null) },
                        onClick = {
                            menuOpen = false
                            themePickerOpen = true
                        },
                    )
                }
            }
        }

        // ── Erro da VM (start() falhou): exposto em banner até o próximo start ──
        val vmError = vm.qemu.lastError
        if (vmError != null && !vm.qemu.running) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.error.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "✕ ",
                    color = palette.error,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    vmError,
                    color = palette.error,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
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
                    onClick = { newShell() },
                )
                Spacer(Modifier.size(8.dp))
                PhantomOutlinedButton(
                    text = "Abrir Toolbox",
                    icon = Icons.Filled.Memory,
                    onClick = onOpenToolbox,
                )
            }
        }

        // ── Barra de progresso da instalação (T30): quando a aba ativa é um log
        //    de instalação, mostra a fase ao vivo + barra real (Baixando % / SHA /
        //    Extraindo). O usuário acompanha tudo dentro do próprio terminal. ──
        val activeLog = (terminal.activeTab?.session as? LogTermSession)
            ?.takeIf { it.progress != null || it.phase != null }
        if (activeLog != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        activeLog.phase ?: "Instalando…",
                        color = palette.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    val prog = activeLog.progress
                    if (prog != null) {
                        Text(
                            "${(prog * 100).toInt()}%",
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val barProg = activeLog.progress
                if (barProg != null) {
                    LinearProgressIndicator(
                        progress = { barProg },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = palette.accentPrimary,
                        trackColor = palette.border,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = palette.accentPrimary,
                        trackColor = palette.border,
                    )
                }
            }
        }

        // ── Banner de boot da VM: informa o usuário na aba do console enquanto
        //    o kernel sobe (primeiro boot costuma demorar) ──
        var bootBannerDismissed by remember { mutableStateOf(false) }
        LaunchedEffect(vm.qemu.running) { if (vm.qemu.running) bootBannerDismissed = false }
        if (terminal.activeTab?.kind == TerminalTabKind.QEMU && vm.qemu.running && !bootBannerDismissed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.accentPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = palette.accentPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Iniciando Linux… o primeiro boot pode levar alguns minutos",
                    color = palette.textPrimary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Fechar aviso",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { bootBannerDismissed = true }
                        .padding(3.dp),
                )
            }
        }

        // ── Terminal VT100 real (jackpal emulatorview) ──
        if (terminal.activeTab != null) {
            // key = sessão: cada aba tem SUA própria EmulatorView. Sem isso, o
            // `update` reanexa sessões na MESMA view e o jackpal não re-inicializa
            // (`initialize()` só roda no primeiro attachSession) — a tela fica com
            // o emulador da aba anterior e parece "congelada" ao trocar de aba.
            key(terminal.activeTab?.session) {
            AndroidView<View>(
                factory = { ctx ->
                // Construção em 2 passos: o construtor (ctx, session, metrics) chama
                // attachSession internamente e CRASHA com session null. Usamos o
                // construtor XML (sem session) + setDensity/attachSession manuais —
                // a sessão é anexada no `update` abaixo quando há aba ativa.
                runCatching { PhantomTerminalView(ctx).apply {
                    isFocusableInTouchMode = true
                    setDensity(ctx.resources.displayMetrics)
                    // Tamanho da fonte (dp) define as colunas por linha — fonte
                    // menor = mais texto por linha (quebra de linha organizada).
                    // A pinça do usuário atualiza este valor e salva nas prefs.
                    applyFontSize(terminalPrefs.fontSizeSp)
                    // Pinça → salva o tamanho escolhido; seleção → mostra a barra
                    // Copiar/Colar/Sair no Compose.
                    setOnFontSizeChanged { size -> terminalPrefs.fontSizeSp = size }
                    setOnSelectionChanged { selecting = it }
                    // Terminal segue o tema escolhido (Design System v2 + CodeTheme):
                    // fundo/texto/cursor nas cores do tema, com as 4 cores do ColorScheme.
                    runCatching {
                        setColorScheme(schemeOf(tc))
                    }
                     post { termView = this }
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
                        // Sempre reaplica tema/fonte (mudança de preset recompõe e repinta).
                        runCatching {
                            view.setColorScheme(schemeOf(tc))
                            (view as? PhantomTerminalView)?.applyFontSize(terminalPrefs.fontSizeSp)
                                ?: view.setTextSize(terminalPrefs.fontSizeSp)
                        }
                         if (tab != null && view.getTermSession() !== tab.session) {
                             runCatching {
                                 view.attachSession(tab.session)
                                 // Foco + teclado também após anexar a sessão (novas abas):
                                forceKeyboard(view)
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
        }

        // Entrada explícita: funciona mesmo quando o teclado IME do emulatorview
        // não entrega commitText em determinados teclados Android.
        if (terminal.activeTab?.kind != TerminalTabKind.LOG && terminal.activeTab != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .background(palette.surfaceAlt, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    textStyle = TextStyle(color = palette.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    cursorBrush = SolidColor(palette.accentSecondary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (commandInput.isEmpty()) Text("Digite um comando e toque em Enviar", color = palette.textSecondary, fontSize = 12.sp)
                        inner()
                    },
                )
                Spacer(Modifier.width(8.dp))
                PhantomPrimaryButton(text = "Enviar", onClick = ::sendCommand, enabled = commandInput.isNotBlank())
            }
        }

    } // fim do Column da tela

    // ── Seletor de tema do terminal (mesmo conjunto do editor) ──
    if (themePickerOpen) {
        StylePickerDialog(
            title = "Tema do terminal",
            options = TerminalPreset.entries,
            selected = terminalStyle.style,
            render = { p ->
                val preview = terminalColorsFor(p, palette)
                Row {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(preview.background)
                            .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(preview.cursorBackground),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "A",
                        color = preview.foreground,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            onPick = {
                terminalStyle.select(it)
                themePickerOpen = false
            },
            onDismiss = { themePickerOpen = false },
        )
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

/** Botão compacto da barra de ações do terminal (Termux-like). */
@Composable
private fun TermActionChip(text: String, icon: ImageVector, onClick: () -> Unit) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = text, tint = palette.accentPrimary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
