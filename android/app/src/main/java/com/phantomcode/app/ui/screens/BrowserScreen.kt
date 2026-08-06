package com.phantomcode.app.ui.screens

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.LocalUiStyleController
import com.phantomcode.app.ui.theme.PhantomFontStyle
import com.phantomcode.app.ui.theme.shape

/** Cor → #RRGGBB para o HTML da página inicial. */
private fun Int.asHexColor(): String = "#%06X".format(this and 0xFFFFFF)

/**
 * Navegador interno (W2): WebView com a cara do app.
 * Barra de URL, voltar/avançar/recarregar, página inicial temática e
 * progresso do carregamento — tudo nas cores do usuário.
 */
@Composable
fun BrowserScreen(onBack: () -> Unit) {
    val palette = LocalThemeController.current.currentPalette()
    val ui = LocalUiStyleController.current.ui
    val corner = ui.cornerStyle.shape()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun navigate(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val url = if (trimmed.contains(".") && !trimmed.startsWith("http")) "https://$trimmed" else trimmed
        webView?.loadUrl(url)
    }

    val startPage = buildString {
        append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>")
        append("<style>")
        append("body{background:${palette.background.toArgb().asHexColor()};color:${palette.textPrimary.toArgb().asHexColor()};")
        append("font-family:monospace;display:flex;flex-direction:column;align-items:center;padding-top:40px;}")
        append("h1{color:${palette.accentPrimary.toArgb().asHexColor()};font-size:22px;letter-spacing:2px;}")
        append(".tag{color:${palette.textSecondary.toArgb().asHexColor()};font-size:12px;margin-bottom:26px;}")
        append(".grid{display:flex;flex-wrap:wrap;gap:12px;justify-content:center;max-width:560px;}")
        append("a{display:block;text-decoration:none;color:${palette.accentSecondary.toArgb().asHexColor()};")
        append("border:1px solid ${palette.border.toArgb().asHexColor()};border-radius:8px;padding:14px 22px;")
        append("font-size:14px;background:${palette.surface.toArgb().asHexColor()};}")
        append("a:active{background:${palette.accentPrimary.toArgb().asHexColor()};color:#fff;}")
        append("</style></head><body>")
        append("<h1>◤ PHANTOM</h1><div class='tag'>navegador interno</div><div class='grid'>")
        val links = listOf(
            "https://github.com" to "GitHub",
            "https://www.google.com" to "Google",
            "https://stackoverflow.com" to "Stack Overflow",
            "https://developer.mozilla.org" to "MDN",
            "https://www.npmjs.com" to "npm",
            "https://www.wikipedia.org" to "Wikipedia",
        )
        links.forEach { (url, label) -> append("<a href='$url'>$label</a>") }
        append("</div></body></html>")
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().background(palette.background),
    ) {
        // ── Barra de URL ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Fechar navegador",
                tint = palette.textPrimary,
                modifier = Modifier.size(34.dp).clickable(onClick = onBack).padding(7.dp),
            )
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(corner)
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border.copy(alpha = 0.6f), corner)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("◉", color = palette.accentPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = if (ui.fontStyle == PhantomFontStyle.HACKER) FontFamily.Monospace else FontFamily.SansSerif,
                    ),
                    cursorBrush = SolidColor(palette.accentSecondary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigate(urlInput) }),
                    decorationBox = { inner ->
                        if (urlInput.isEmpty()) {
                            Text("URL ou pesquisa", color = palette.textSecondary, fontSize = 12.sp)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Search,
                contentDescription = "Ir",
                tint = palette.accentSecondary,
                modifier = Modifier.size(34.dp).clickable { navigate(urlInput) }.padding(7.dp),
            )
        }

        // ── Controles: voltar · avançar · recarregar · início ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = if (canGoBack) palette.textPrimary else palette.border.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp).clickable(enabled = canGoBack) { webView?.goBack() }.padding(7.dp),
            )
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = "Avançar",
                tint = if (canGoForward) palette.textPrimary else palette.border.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp).clickable(enabled = canGoForward) { webView?.goForward() }.padding(7.dp),
            )
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Recarregar",
                tint = palette.textPrimary,
                modifier = Modifier.size(32.dp).clickable { webView?.reload() }.padding(7.dp),
            )
            Icon(
                Icons.Filled.Home,
                contentDescription = "Início",
                tint = palette.accentPrimary,
                modifier = Modifier.size(32.dp).clickable {
                    webView?.loadDataWithBaseURL(null, startPage, "text/html", "UTF-8", null)
                    urlInput = ""
                }.padding(7.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (progress >= 1f) "pronto" else "${(progress * 100).toInt()}%",
                color = palette.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        // ── Progresso do carregamento ──
        if (progress in 0f..0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = palette.accentPrimary,
                trackColor = palette.surfaceAlt,
            )
        }

        // ── WebView ──
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    setBackgroundColor(palette.background.toArgb())
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            urlInput = url ?: ""
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                            progress = 1f
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress / 100f
                        }
                    }
                    loadDataWithBaseURL(null, startPage, "text/html", "UTF-8", null)
                    webView = this
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
