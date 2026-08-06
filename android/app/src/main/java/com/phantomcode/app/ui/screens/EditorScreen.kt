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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phantomcode.app.data.SessionManager
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Editor (T12/T13): CodeMirror 6 no WebView + ponte JS↔Kotlin.
 * Auto-save com debounce de 800ms (JS) e salvar antes de fechar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EditorScreen(path: String, onClose: () -> Unit) {
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
    }
}

/** Converte o retorno de evaluateJavascript (string JSON) para texto puro. */
private fun unquoteJs(jsonString: String?): String {
    if (jsonString == null || jsonString == "null") return ""
    return runCatching { JSONObject("{\"v\":$jsonString}").getString("v") }
        .getOrDefault(jsonString.removeSurrounding("\""))
}
