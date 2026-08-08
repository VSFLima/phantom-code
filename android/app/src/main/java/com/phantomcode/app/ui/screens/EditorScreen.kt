package com.phantomcode.app.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phantomcode.app.data.FileEntry
import com.phantomcode.app.data.SessionManager
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.git.GitManager
import com.phantomcode.app.data.git.GitStatus
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.components.PhantomConfirmDialog
import com.phantomcode.app.ui.components.PhantomDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Editor (T12/T13): CodeMirror 6 no WebView + ponte JS↔Kotlin.
 *
 * Funcionalidades estilo SPCK (docs/TAREFA-EDITOR-IDE.md):
 *  - Auto-save com debounce de 800ms (JS);
 *  - Ir para linha, indicador linha/coluna;
 *  - Atalhos de teclado (Ctrl+S/F/G/H/D, Alt+setas, Ctrl+/);
 *  - Snippets com Tab;
 *  - Watcher de disco (P0.2): recarrega quando o arquivo muda fora do editor;
 *  - Git integrado (P1.1): status + commit/push/pull do projeto atual;
 *  - Preview no navegador interno (P1.4).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    path: String,
    openTabs: List<String> = listOf(path),
    onSelectTab: (String) -> Unit = {},
    onClose: () -> Unit,
    onCloseTab: (String) -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    onPreviewUrl: (String) -> Unit = {},
    onOpenTerminalIn: (String) -> Unit = {},
    onOpenExplorer: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalThemeController.current.currentPalette()
    val workspace = remember { WorkspaceManager(context) }
    val session = remember { SessionManager(context) }
    val git = remember { GitManager(context) }
    val fileName = path.substringAfterLast('/')
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var saved by remember { mutableStateOf(true) }
    var actionsOpen by remember { mutableStateOf(false) }
    var saveAsOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var saveAsInitial by remember { mutableStateOf(path) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var gotoOpen by remember { mutableStateOf(false) }
    var cursorLabel by remember { mutableStateOf("") }
    var lastSeenModified by remember { mutableStateOf(0L) }
    var diskChanged by remember { mutableStateOf(false) }
    var progKeyboardOpen by remember { mutableStateOf(false) }
    var explorerOpen by remember { mutableStateOf(false) }
    val explorerExpanded = remember { mutableStateMapOf<String, Boolean>() }
    var explorerTick by remember { mutableStateOf(0) }

    // ── Git integrado (P1.1) ──
    var gitRoot by remember { mutableStateOf<File?>(null) }
    var gitStatus by remember { mutableStateOf<GitStatus?>(null) }
    var commitOpen by remember { mutableStateOf(false) }
    var gitBusy by remember { mutableStateOf(false) }

    val language = when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "Kotlin"
        "js", "mjs", "ts", "tsx" -> "JavaScript / TypeScript"
        "py" -> "Python"
        "html", "htm" -> "HTML"
        "css" -> "CSS"
        "json" -> "JSON"
        "md" -> "Markdown"
        "sh", "bash" -> "Shell"
        else -> "Texto"
    }

    fun reloadFromDisk() {
        val wv = webView ?: return
        val content = runCatching { workspace.readText(path) }.getOrDefault("")
        val js = "window.PhantomEditor.setValue(${JSONObject.quote(content)});window.PhantomEditor.focus();"
        wv.evaluateJavascript(js, null)
        lastSeenModified = runCatching { workspace.resolve(path).lastModified() }.getOrDefault(0L)
    }

    fun replaceAll() {
        val wv = webView ?: return
        val query = searchQuery
        if (query.isBlank()) return
        wv.evaluateJavascript("window.PhantomEditor.getValue()") { value ->
            val current = unquoteJs(value)
            val count = current.windowed(query.length, 1, partialWindows = false)
                .count { it == query }
            val updated = current.replace(query, replacement)
            val script = "window.PhantomEditor.setValue(${JSONObject.quote(updated)});window.PhantomEditor.focus();"
            wv.evaluateJavascript(script, null)
            scope.launch { snackbar.showSnackbar("$count ocorrência(s) substituída(s)") }
        }
    }

    // Ponte exposta ao JS como window.AndroidBridge
    val bridge = remember(path, workspace) {
        object {
            @JavascriptInterface
            fun save(text: String) {
                runCatching { workspace.writeText(path, text) }
                session.lastOpenPath = path
                val lm = runCatching { workspace.resolve(path).lastModified() }.getOrDefault(0L)
                mainHandler.post {
                    saved = true
                    lastSeenModified = lm
                }
            }

            @JavascriptInterface
            fun dirty() {
                mainHandler.post { saved = false }
            }

            /** Ctrl+S no JS → aviso de salvo. */
            @JavascriptInterface
            fun saved() {
                scope.launch { snackbar.showSnackbar("Salvo") }
            }

            /** Ctrl+F / Ctrl+H no JS → abre o painel de busca. */
            @JavascriptInterface
            fun openSearch() {
                mainHandler.post { searchOpen = true }
            }

            /** Ctrl+G no JS → abre o diálogo "ir para linha". */
            @JavascriptInterface
            fun openGoto() {
                mainHandler.post { gotoOpen = true }
            }
        }
    }

    fun currentTextAndClose() {
        val wv = webView
        if (wv == null) {
            onClose()
            return
        }
        wv.evaluateJavascript("window.PhantomEditor.getValue()") { value ->
            runCatching { workspace.writeText(path, unquoteJs(value)) }
            session.lastOpenPath = path
            onClose()
        }
    }

    // Watcher de disco (P0.2): a cada 3s compara o lastModified do arquivo.
    LaunchedEffect(path) {
        while (true) {
            delay(3000)
            val f = runCatching { workspace.resolve(path) }.getOrNull() ?: continue
            val lm = f.lastModified()
            if (lm != 0L && lastSeenModified != 0L && lm != lastSeenModified) {
                if (saved) {
                    reloadFromDisk()
                    scope.launch { snackbar.showSnackbar("Arquivo atualizado no disco — recarregado") }
                } else {
                    diskChanged = true
                }
            }
        }
    }

    // Indicador linha/coluna (P1.3) — polling leve a cada 1s.
    LaunchedEffect(path) {
        while (true) {
            delay(1000)
            webView?.evaluateJavascript("window.PhantomEditor.getCursor()") { value ->
                runCatching {
                    val obj = JSONObject("{\"c\":$value}").getJSONObject("c")
                    val line = obj.getInt("line")
                    val col = obj.getInt("col")
                    cursorLabel = "L:$line C:$col"
                }
            }
        }
    }

    // Descobre a raiz do projeto Git a partir do arquivo aberto (P1.1).
    LaunchedEffect(path) {
        gitRoot = runCatching {
            var dir = workspace.resolve(path).parentFile ?: workspace.root
            while (dir != null && dir != workspace.root.parentFile) {
                if (File(dir, ".git").isDirectory) return@runCatching dir
                dir = dir.parentFile
            }
            null
        }.getOrNull()
    }

    // Polling do status Git (P1.1) — a cada 5s.
    LaunchedEffect(gitRoot) {
        while (true) {
            val root = gitRoot
            if (root != null) {
                gitStatus = git.status(root)
            }
            delay(5000)
        }
    }

    // Destrói o WebView ao sair (evita vazamento de memória)
    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }

    BackHandler(onBack = { currentTextAndClose() })

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
            // ── Barra do editor: voltar · nome · salvo · linha/coluna · salvar ──
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
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { currentTextAndClose() }
                        .padding(10.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        fileName,
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(if (saved) palette.success else palette.accentBright, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (saved) "Salvo" else "Editando…",
                            color = if (saved) palette.success else palette.accentBright,
                            fontSize = 10.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(language, color = palette.textSecondary, fontSize = 10.sp)
                        if (cursorLabel.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(cursorLabel, color = palette.textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Save,
                    contentDescription = "Salvar agora",
                    tint = palette.accentPrimary,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            webView?.evaluateJavascript("window.PhantomEditor.getValue()") { value ->
                                runCatching { workspace.writeText(path, unquoteJs(value)) }
                                saved = true
                                lastSeenModified = runCatching { workspace.resolve(path).lastModified() }.getOrDefault(0L)
                                scope.launch { snackbar.showSnackbar("Salvo") }
                            }
                        }
                        .padding(10.dp),
                )
                Box {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Ações do arquivo",
                        tint = palette.textSecondary,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { actionsOpen = true }
                            .padding(10.dp),
                    )
                    DropdownMenu(
                        expanded = actionsOpen,
                        onDismissRequest = { actionsOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Buscar e substituir") },
                            onClick = {
                                actionsOpen = false
                                searchOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Ir para linha…") },
                            onClick = {
                                actionsOpen = false
                                gotoOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Desfazer") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.undo()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Refazer") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.redo()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicar linha") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.duplicateLine()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Comentar / descomentar") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.toggleComment()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Autocompletar") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.complete()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Dobrar bloco") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.fold()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Desdobrar bloco") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.unfold()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Dobrar tudo") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.foldAll()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Desdobrar tudo") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.unfoldAll()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Teclado de programação") },
                            onClick = {
                                actionsOpen = false
                                progKeyboardOpen = !progKeyboardOpen
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Terminal aqui (projeto)") },
                            onClick = {
                                actionsOpen = false
                                val dir = runCatching {
                                    workspace.resolve(path).parentFile?.absolutePath ?: workspace.root.absolutePath
                                }.getOrDefault(workspace.root.absolutePath)
                                onOpenTerminalIn(dir)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Explorer do projeto") },
                            onClick = {
                                actionsOpen = false
                                explorerOpen = !explorerOpen
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Selecionar tudo") },
                            onClick = {
                                actionsOpen = false
                                webView?.evaluateJavascript("window.PhantomEditor.selectAll()", null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Preview no navegador") },
                            onClick = {
                                actionsOpen = false
                                val url = runCatching { workspace.resolve(path).toURI().toString() }.getOrNull()
                                if (url != null) onPreviewUrl(url) else scope.launch { snackbar.showSnackbar("Não foi possível abrir o preview") }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Salvar como…") },
                            onClick = {
                                actionsOpen = false
                                saveAsInitial = path
                                saveAsOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicar arquivo") },
                            onClick = {
                                actionsOpen = false
                                val extension = fileName.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
                                val base = fileName.removeSuffix(extension)
                                val parent = path.substringBeforeLast('/', "")
                                saveAsInitial = if (parent.isBlank()) "$base-copy$extension" else "$parent/$base-copy$extension"
                                saveAsOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Renomear arquivo") },
                            onClick = {
                                actionsOpen = false
                                renameOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Fechar editor") },
                            onClick = {
                                actionsOpen = false
                                currentTextAndClose()
                            },
                        )
                    }
                }
            }

            // ── Explorer lateral (P2.1) ──
            if (explorerOpen) {
                EditorExplorer(
                    workspace = workspace,
                    expanded = explorerExpanded,
                    tick = explorerTick,
                    onToggle = { rel ->
                        explorerExpanded[rel] = explorerExpanded[rel] != true
                        explorerTick++
                    },
                    onRefresh = { explorerTick++ },
                    onOpenFile = onOpenFile,
                )
            }

            // ── Abas ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                openTabs.forEach { tab ->
                    val selected = tab == path
                    Row(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(
                                if (selected) palette.surfaceAlt else palette.surface,
                                RoundedCornerShape(4.dp),
                            )
                            .clickable { onSelectTab(tab) }
                            .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tab.substringAfterLast('/'),
                            color = if (selected) palette.accentPrimary else palette.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Fechar ${tab.substringAfterLast('/')}",
                            tint = palette.textSecondary,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(16.dp)
                                .clickable { onCloseTab(tab) }
                                .padding(3.dp),
                        )
                    }
                }
            }

            // ── Git integrado (P1.1): status + commit/push/pull ──
            val root = gitRoot
            if (root != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surface)
                        .border(width = 1.dp, color = palette.border.copy(alpha = 0.4f), shape = RoundedCornerShape(0.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val st = gitStatus
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (st?.clean == true) palette.success else palette.accentBright, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${st?.branch ?: "git"} · ${if (st?.clean == true) "limpo" else "${st?.changes?.size ?: 0} mudança(s)"}",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    GitChip(text = "Commit", enabled = !gitBusy && st?.clean == false, onClick = { commitOpen = true })
                    Spacer(Modifier.width(6.dp))
                    GitChip(text = "Push", enabled = !gitBusy && st?.clean == true, onClick = {
                        scope.launch {
                            gitBusy = true
                            val result = withContext(Dispatchers.IO) { git.push(root) }
                            gitBusy = false
                            snackbar.showSnackbar(result?.takeIf { it != "push ok" } ?: "Push OK — enviado")
                        }
                    })
                    Spacer(Modifier.width(6.dp))
                    GitChip(text = "Pull", enabled = !gitBusy, onClick = {
                        scope.launch {
                            gitBusy = true
                            val result = withContext(Dispatchers.IO) { git.pull(root) }
                            gitBusy = false
                            snackbar.showSnackbar(result ?: "Pull OK")
                        }
                    })
                }
            }

            // ── Buscar e substituir ──
            if (searchOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceAlt)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(palette.accentSecondary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Buscar…", color = palette.textSecondary, fontSize = 12.sp)
                            inner()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(palette.accentSecondary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (replacement.isEmpty()) Text("Substituir por…", color = palette.textSecondary, fontSize = 12.sp)
                            inner()
                        },
                    )
                    Text(
                        "Trocar tudo",
                        color = if (searchQuery.isBlank()) palette.border else palette.accentPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable(enabled = searchQuery.isNotBlank()) { replaceAll() },
                    )
                    IconButton(onClick = { searchOpen = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar busca", tint = palette.textSecondary)
                    }
                }
            }

            // ── Teclado extra de programação (barra de símbolos) ──
            if (progKeyboardOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceAlt)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val symbols = listOf("{", "}", "(", ")", "[", "]", "<", ">", ";", ":", ",", ".", "=", "+", "-", "*", "/", "!", "?", "&", "|", "\"", "'", "#", "@", "_", "~", "\\", "%", "^")
                    symbols.forEach { sym ->
                        Text(
                            text = sym,
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .border(1.dp, palette.border.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable {
                                    val escaped = JSONObject.quote(sym)
                                    webView?.evaluateJavascript("window.PhantomEditor.insertText($escaped)", null)
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // WebView com o CodeMirror 6 (preenche só o espaço abaixo das barras)
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        addJavascriptInterface(bridge, "AndroidBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                scope.launch(Dispatchers.IO) {
                                    val payload = JSONObject()
                                        .put("value", workspace.readText(path))
                                        .put("name", fileName)
                                    val js = "PhantomEditor.init(" +
                                        "document.getElementById('editor')," +
                                        "${payload.getString("value")}," +
                                        "${payload.getString("name")});"
                                    mainHandler.post {
                                        view.evaluateJavascript(js, null)
                                        lastSeenModified = runCatching { workspace.resolve(path).lastModified() }.getOrDefault(0L)
                                    }
                                }
                            }
                        }
                        loadUrl("file:///android_asset/editor/index.html")
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                update = { webView = it },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        if (gotoOpen) {
            PhantomDialog(
                title = "Ir para linha",
                placeholder = "Número da linha",
                initialValue = "",
                confirmText = "Ir",
                onConfirm = { line ->
                    gotoOpen = false
                    webView?.evaluateJavascript("window.PhantomEditor.gotoLine(${line.trim().toIntOrNull() ?: 1})", null)
                },
                onDismiss = { gotoOpen = false },
            )
        }

        if (commitOpen) {
            PhantomDialog(
                title = "Commit Git",
                placeholder = "Mensagem do commit…",
                initialValue = "",
                confirmText = "Commit",
                onConfirm = { message ->
                    commitOpen = false
                    val root = gitRoot ?: return@PhantomDialog
                    scope.launch {
                        gitBusy = true
                        val result = withContext(Dispatchers.IO) { git.commit(root, message) }
                        gitBusy = false
                        snackbar.showSnackbar(result ?: "Commit OK")
                    }
                },
                onDismiss = { commitOpen = false },
            )
        }

        if (diskChanged) {
            PhantomConfirmDialog(
                title = "Arquivo mudou no disco",
                message = "O arquivo foi alterado fora do editor (ex.: Git Pull). Recarregar agora?",
                confirmText = "Recarregar",
                onConfirm = {
                    diskChanged = false
                    reloadFromDisk()
                    scope.launch { snackbar.showSnackbar("Arquivo recarregado") }
                },
                onDismiss = {
                    diskChanged = false
                    // Mantém as edições locais; evita insistir no mesmo arquivo.
                    lastSeenModified = runCatching { workspace.resolve(path).lastModified() }.getOrDefault(0L)
                },
            )
        }

        if (saveAsOpen) {
            PhantomDialog(
                title = "Salvar arquivo como",
                placeholder = "projeto/src/arquivo.ext",
                initialValue = saveAsInitial,
                confirmText = "Salvar cópia",
                onConfirm = { newPath ->
                    saveAsOpen = false
                    webView?.evaluateJavascript("window.PhantomEditor.getValue()") { value ->
                        runCatching {
                            val target = workspace.resolve(newPath.trim())
                            target.parentFile?.mkdirs()
                            target.writeText(unquoteJs(value))
                            session.lastOpenPath = newPath.trim()
                            onOpenFile(newPath.trim())
                        }.onFailure {
                            scope.launch { snackbar.showSnackbar("Não foi possível salvar: ${it.message}") }
                        }
                    }
                },
                onDismiss = { saveAsOpen = false },
            )
        }

        if (renameOpen) {
            PhantomDialog(
                title = "Renomear arquivo",
                initialValue = fileName,
                placeholder = "novo-nome.ext",
                confirmText = "Renomear",
                onConfirm = { newName ->
                    renameOpen = false
                    val cleanName = newName.trim().substringAfterLast('/').substringAfterLast('\\')
                    runCatching {
                        check(cleanName.isNotBlank()) { "Nome vazio" }
                        check(workspace.rename(path, cleanName)) { "Arquivo já existe ou não pode ser renomeado" }
                        val parent = path.substringBeforeLast('/', "")
                        val newPath = if (parent.isBlank()) cleanName else "$parent/$cleanName"
                        session.lastOpenPath = newPath
                        onClose()
                        onOpenFile(newPath)
                    }.onFailure {
                        scope.launch { snackbar.showSnackbar("Não foi possível renomear: ${it.message}") }
                    }
                },
                onDismiss = { renameOpen = false },
            )
        }
    }
}

/** Botão compacto da barra Git do editor. */
@Composable
private fun GitChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalThemeController.current.currentPalette()
    Text(
        text = text,
        color = if (enabled) palette.accentPrimary else palette.border,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .border(1.dp, if (enabled) palette.accentPrimary.copy(alpha = 0.7f) else palette.border.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Converte o retorno de evaluateJavascript (string JSON) para texto puro. */
private fun unquoteJs(jsonString: String?): String {
    if (jsonString == null || jsonString == "null") return ""
    return runCatching { JSONObject("{\"v\":$jsonString}").getString("v") }
        .getOrDefault(jsonString.removeSurrounding("\""))
}

/** Nó da árvore do explorer lateral do editor. */
private data class EditorTreeNode(val entry: FileEntry, val depth: Int)

/** Constrói a árvore visível do workspace (pastas expandidas conforme [expanded]). */
private fun editorVisibleTree(ws: WorkspaceManager, expanded: Map<String, Boolean>): List<EditorTreeNode> {
    val out = mutableListOf<EditorTreeNode>()
    fun walk(rel: String, depth: Int) {
        ws.list(rel).forEach { e ->
            out += EditorTreeNode(e, depth)
            if (e.isDir && expanded[e.relPath] == true) walk(e.relPath, depth + 1)
        }
    }
    walk("", 0)
    return out
}

/** Explorer lateral (P2.1): árvore de arquivos ao lado do editor. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorExplorer(
    workspace: WorkspaceManager,
    expanded: Map<String, Boolean>,
    tick: Int,
    onToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    val tree = remember(tick, expanded) { editorVisibleTree(workspace, expanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .border(width = 1.dp, color = palette.border.copy(alpha = 0.5f), shape = RoundedCornerShape(0.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Projeto", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Atualizar",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp).clickable(onClick = onRefresh),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            items(tree, key = { it.entry.relPath }) { node ->
                val e = node.entry
                val isOpen = e.isDir && expanded[e.relPath] == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (e.isDir) onToggle(e.relPath) else onOpenFile(e.relPath)
                            },
                            onLongClick = {},
                        )
                        .padding(start = 6.dp + (node.depth * 14).dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (e.isDir) {
                        Icon(
                            if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Icon(
                            if (isOpen) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                            contentDescription = null,
                            tint = palette.accentPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = palette.accentSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        e.name,
                        color = if (e.isDir) palette.textPrimary else palette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
