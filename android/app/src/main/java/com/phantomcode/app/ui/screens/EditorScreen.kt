package com.phantomcode.app.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phantomcode.app.data.SessionManager
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.components.PhantomDialog
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Editor (T12/T13): CodeMirror 6 no WebView + ponte JS↔Kotlin.
 * Auto-save com debounce de 800ms (JS), salvar como e salvar antes de fechar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EditorScreen(
    path: String,
    openTabs: List<String> = listOf(path),
    onSelectTab: (String) -> Unit = {},
    onClose: () -> Unit,
    onCloseTab: (String) -> Unit = {},
    onOpenFile: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val palette = LocalThemeController.current.currentPalette()
    val workspace = remember { WorkspaceManager(context) }
    val session = remember { SessionManager(context) }
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
                mainHandler.post { saved = true }
            }

            @JavascriptInterface
            fun dirty() {
                mainHandler.post { saved = false }
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
            // Barra do editor: voltar · nome · indicador de salvo · salvar
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
                        text = { Text("Selecionar tudo") },
                        onClick = {
                            actionsOpen = false
                            webView?.evaluateJavascript("window.PhantomEditor.selectAll()", null)
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

         // WebView com o CodeMirror 6 (preenche só o espaço abaixo da barra)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addJavascriptInterface(bridge, "AndroidBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            val payload = JSONObject()
                                .put("value", workspace.readText(path))
                                .put("name", fileName)
                            val js = "PhantomEditor.init(" +
                                "document.getElementById('editor')," +
                                "${payload.getString("value")}," +
                                "${payload.getString("name")});"
                            view.evaluateJavascript(js, null)
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

/** Converte o retorno de evaluateJavascript (string JSON) para texto puro. */
private fun unquoteJs(jsonString: String?): String {
    if (jsonString == null || jsonString == "null") return ""
    return runCatching { JSONObject("{\"v\":$jsonString}").getString("v") }
        .getOrDefault(jsonString.removeSurrounding("\""))
}
