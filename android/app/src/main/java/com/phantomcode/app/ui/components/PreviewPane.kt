package com.phantomcode.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phantomcode.app.data.LocalServer
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.vm.QemuManager
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.PhantomPalette

/**
 * Preview Hub (P3.1): painel de pré-visualização do editor, lado a lado (split).
 *
 * Renderiza por tipo de arquivo no WebView do app:
 *  · HTML/CSS/JS → URL do servidor local (caminhos relativos + AJAX/fetch)
 *  · Markdown    → mini-engine de MD sem dependências
 *  · JSON        → formatado e colorido
 *  · Imagens     → <img> do servidor local
 *  · PHP         → servidor da VM (php -S) quando ativo; senão orientação
 *  · Python/JS/etc → orientação p/ Executar (VM)
 *  · Texto       → prévia mono
 *
 * O painel recarrega sozinho quando o arquivo é salvo (reloadTick do editor).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewPane(
    relPath: String,
    workspace: WorkspaceManager,
    reloadTick: Int,
    serverRunning: Boolean,
    vmServerRunning: Boolean = false,
    onReload: (() -> Unit)? = null,
) {
    val palette = LocalThemeController.current.currentPalette()
    val webView = remember { arrayOfNulls<WebView>(1) }
    var notice by remember(relPath) { mutableStateOf<String?>(null) }

    val ext = relPath.substringAfterLast('.', "").lowercase()
    val mode = when (ext) {
        "html", "htm" -> "html"
        "md", "markdown" -> "md"
        "json" -> "json"
        "png", "jpg", "jpeg", "gif", "svg", "webp", "ico" -> "image"
        "php" -> "php"
        "py", "rb", "js", "mjs", "ts", "sh", "bash", "java", "kt", "go", "rs" -> "code"
        else -> "text"
    }

    LaunchedEffect(relPath, reloadTick, vmServerRunning) {
        val wv = webView[0] ?: return@LaunchedEffect
        when (mode) {
            "html" -> {
                if (serverRunning) {
                    notice = "Servidor local ativo — AJAX e caminhos relativos OK"
                    wv.loadUrl("${LocalServer.BASE_URL}/$relPath")
                } else {
                    notice = "Sem servidor local — caminhos relativos/AJAX podem falhar"
                    val uri = runCatching { workspace.resolve(relPath).toURI().toString() }.getOrNull()
                    if (uri != null) wv.loadUrl(uri)
                }
            }
            "md" -> {
                val text = runCatching { workspace.readText(relPath) }.getOrDefault("")
                val base = "${LocalServer.BASE_URL}/${relPath.substringBeforeLast('/', "")}"
                wv.loadDataWithBaseURL(base, mdHtml(text, palette), "text/html", "UTF-8", null)
            }
            "json" -> {
                val text = runCatching { workspace.readText(relPath) }.getOrDefault("")
                wv.loadDataWithBaseURL(null, jsonHtml(text, palette), "text/html", "UTF-8", null)
            }
            "image" -> {
                wv.loadUrl("${LocalServer.BASE_URL}/$relPath")
            }
            "php" -> {
                if (vmServerRunning) {
                    notice = "Servidor da VM ativo (PHP)"
                    wv.loadUrl("${QemuManager.VM_SERVER_BASE_URL}/$relPath")
                } else {
                    notice = "PHP: inicie o servidor da VM em Ações → Servidor local (VM)"
                    wv.loadDataWithBaseURL(
                        null,
                        infoHtml("PHP", "Para pré-visualizar este arquivo, inicie o servidor da VM " +
                            "(Ações → Servidor local) e use “Ver no servidor”.", palette),
                        "text/html", "UTF-8", null,
                    )
                }
            }
            "code" -> {
                notice = "Código executável — use Executar (VM) no menu Ações"
                val text = runCatching { workspace.readText(relPath) }.getOrDefault("")
                wv.loadDataWithBaseURL(null, plainHtml(text, relPath, palette), "text/html", "UTF-8", null)
            }
            else -> {
                val text = runCatching { workspace.readText(relPath) }.getOrDefault("")
                wv.loadDataWithBaseURL(null, plainHtml(text, relPath, palette), "text/html", "UTF-8", null)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.surface)
            .padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.padding(end = 4.dp))
            Text(
                when (mode) {
                    "html" -> "Preview HTML/CSS/JS"
                    "md" -> "Preview Markdown"
                    "json" -> "Preview JSON"
                    "image" -> "Preview de imagem"
                    "php" -> "Preview PHP"
                    "code" -> "Prévia do código"
                    else -> "Prévia do arquivo"
                },
                color = palette.textPrimary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            if (onReload != null) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Recarregar preview",
                    tint = palette.accentPrimary,
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onReload),
                )
            }
        }
        if (notice != null) {
            Text(notice, color = palette.textSecondary, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.background),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                        }
                        webChromeClient = WebChromeClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        setBackgroundColor(palette.background.toArgb())
                        webView[0] = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Páginas HTML dos previews ─────────────────────────────────

private fun mdHtml(markdown: String, palette: PhantomPalette): String {
    val md = markdown
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("${'$'}{", "\\${'$'}{")
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { font-family: -apple-system, Roboto, sans-serif; margin: 16px; padding: 0;
  background: ${hex(palette.background)}; color: ${hex(palette.textPrimary)}; }
h1,h2,h3,h4 { color: ${hex(palette.accentPrimary)}; }
a { color: ${hex(palette.accentSecondary)}; }
code { font-family: monospace; background: ${hex(palette.surfaceAlt)}; padding: 2px 5px; border-radius: 4px; }
pre { background: ${hex(palette.surfaceAlt)}; padding: 12px; border-radius: 8px; overflow-x: auto; }
pre code { padding: 0; background: none; }
blockquote { border-left: 3px solid ${hex(palette.accentPrimary)}; margin: 0; padding-left: 12px; color: ${hex(palette.textSecondary)}; }
table { border-collapse: collapse; } th, td { border: 1px solid ${hex(palette.border)}; padding: 6px 10px; }
img { max-width: 100%; }
</style></head><body><div id="out"></div>
<script>
const md = `$md`;
const render = $MARKDOWN_RENDERER;
document.getElementById('out').innerHTML = render(md);
</script></body></html>"""
}

private fun jsonHtml(text: String, palette: PhantomPalette): String {
    val esc = text.replace("\\", "\\\\").replace("`", "\\`").replace("${'$'}{", "\\${'$'}{")
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
body { font-family: monospace; margin: 12px; background: ${hex(palette.background)}; color: ${hex(palette.textPrimary)}; white-space: pre-wrap; }
.k { color: ${hex(palette.accentPrimary)}; } .s { color: ${hex(palette.success)}; } .n { color: ${hex(palette.info)}; }
</style></head><body><pre id="out"></pre>
<script>
const raw = `$esc`;
try {
  const obj = JSON.parse(raw);
  document.getElementById('out').innerHTML =
    JSON.stringify(obj, null, 2)
      .replace(/"((?:[^"\\]|\\.)*)"(?=\s*:)/g, '<span class="k">"$1"</span>')
      .replace(/"((?:[^"\\]|\\.)*)"(?=\s*[,}\]])/g, '<span class="s">"$1"</span>')
      .replace(/\b(true|false|null)\b/g, '<span class="n">$1</span>');
} catch (e) {
  document.getElementById('out').textContent = raw;
}
</script></body></html>"""
}

private fun plainHtml(text: String, relPath: String, palette: PhantomPalette): String {
    val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
body { font-family: monospace; margin: 12px; background: ${hex(palette.background)}; color: ${hex(palette.textPrimary)}; white-space: pre-wrap; }
</style></head><body><pre>$esc</pre></body></html>"""
}

private fun infoHtml(title: String, msg: String, palette: PhantomPalette): String = """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
body { font-family: Roboto, sans-serif; margin: 20px; background: ${hex(palette.background)}; color: ${hex(palette.textPrimary)}; }
h3 { color: ${hex(palette.accentPrimary)}; } p { color: ${hex(palette.textSecondary)}; }
</style></head><body><h3>$title</h3><p>$msg</p></body></html>"""

private fun hex(c: androidx.compose.ui.graphics.Color): String {
    val argb = c.copy(alpha = 1f).toArgb()
    return String.format("#%06X", argb and 0xFFFFFF)
}

/** Mini-renderizador de Markdown em JS (sem dependências). */
private const val MARKDOWN_RENDERER = """
function mdInline(text) {
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_]+)__/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img alt="$1" src="$2" />')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
}
function render(md) {
  const lines = md.replace(/\\r\\n/g, '\\n').split('\\n');
  let html = '', i = 0, inCode = false, listType = '', listOpen = false, inTable = false;
  const escape = s => s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  const closeList = () => { if (listOpen) { html += '</' + listType + '>'; listOpen = false; } };
  while (i < lines.length) {
    const line = lines[i];
    const t = line.trim();
    if (t.startsWith('```')) {
      if (inCode) { html += '</code></pre>'; inCode = false; }
      else { closeList(); html += '<pre><code>'; inCode = true; }
      i++; continue;
    }
    if (inCode) { html += escape(line) + '\\n'; i++; continue; }
    if (t === '') { closeList(); if (inTable) { html += '</table>'; inTable = false; } i++; continue; }
    const h = t.match(/^(#{1,6})\\s+(.*)/);
    if (h) { closeList(); html += '<h' + h[1].length + '>' + mdInline(h[2]) + '</h' + h[1].length + '>'; i++; continue; }
    if (t === '---' || t === '***' || t === '___') { closeList(); html += '<hr>'; i++; continue; }
    if (/^>/.test(t)) { closeList(); html += '<blockquote>' + mdInline(t.replace(/^>\\s?/, '')) + '</blockquote>'; i++; continue; }
    const ul = t.match(/^[-*+]\\s+(.*)/);
    if (ul) { if (listType !== 'ul') { closeList(); html += '<ul>'; listType = 'ul'; listOpen = true; } html += '<li>' + mdInline(ul[1]) + '</li>'; i++; continue; }
    const ol = t.match(/^\\d+\\.\\s+(.*)/);
    if (ol) { if (listType !== 'ol') { closeList(); html += '<ol>'; listType = 'ol'; listOpen = true; } html += '<li>' + mdInline(ol[1]) + '</li>'; i++; continue; }
    if (t.startsWith('|')) {
      closeList();
      if (!inTable) { html += '<table>'; inTable = true; }
      const cells = t.split('|').slice(1, -1).map(c => mdInline(c.trim()));
      const next = (lines[i + 1] || '').trim();
      if (/^[-: ]+$/.test(next)) {
        html += '<tr>' + cells.map(c => '<th>' + c + '</th>').join('') + '</tr>';
        i++;
      } else {
        html += '<tr>' + cells.map(c => '<td>' + c + '</td>').join('') + '</tr>';
      }
      i++; continue;
    }
    if (inTable) { html += '</table>'; inTable = false; }
    closeList(); html += '<p>' + mdInline(t) + '</p>';
    i++;
  }
  if (inCode) html += '</code></pre>';
  closeList();
  if (inTable) html += '</table>';
  return html;
}
"""
