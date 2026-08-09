package com.phantomcode.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.phantomcode.app.data.LocalServer
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.vm.QemuManager
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.PhantomPalette
import java.io.File

/**
 * Preview Hub (P3.1): painel de pré-visualização do editor, lado a lado (split).
 *
 * Renderiza por tipo de arquivo no WebView do app:
 *  · HTML/CSS/JS → URL do servidor local (caminhos relativos + AJAX/fetch)
 *  · Markdown    → mini-engine de MD sem dependências
 *  · JSON        → formatado e colorido
 *  · CSV         → tabela (primeira linha como cabeçalho)
 *  · SQL         → destaque de sintaxe
 *  · PDF         → URL do servidor local (application/pdf) + "Abrir externamente"
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
    val context = LocalContext.current
    val webView = remember { arrayOfNulls<WebView>(1) }
    var notice by remember(relPath) { mutableStateOf<String?>(null) }

    val ext = relPath.substringAfterLast('.', "").lowercase()
    val mode = when (ext) {
        "html", "htm" -> "html"
        "md", "markdown" -> "md"
        "json" -> "json"
        "csv" -> "csv"
        "sql" -> "sql"
        "pdf" -> "pdf"
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
            "csv" -> {
                val text = runCatching { workspace.readText(relPath) }.getOrDefault("")
                wv.loadDataWithBaseURL(null, csvHtml(text, palette), "text/html", "UTF-8", null)
            }
            "sql" -> {
                val text = runCatching { workspace.readText(relPath) }.getOrDefault("")
                wv.loadDataWithBaseURL(null, sqlHtml(text, palette), "text/html", "UTF-8", null)
            }
            "pdf" -> {
                if (serverRunning) {
                    notice = "PDF — se não abrir no painel, use “Abrir externamente”"
                    wv.loadUrl("${LocalServer.BASE_URL}/$relPath")
                } else {
                    notice = "Inicie o servidor local (Ações) ou use “Abrir externamente”"
                    wv.loadUrl("about:blank")
                }
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
                    "csv" -> "Preview CSV"
                    "sql" -> "Preview SQL"
                    "pdf" -> "Preview PDF"
                    "image" -> "Preview de imagem"
                    "php" -> "Preview PHP"
                    "code" -> "Prévia do código"
                    else -> "Prévia do arquivo"
                },
                color = palette.textPrimary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            if (mode == "pdf" || mode == "csv" || mode == "sql" || mode == "image") {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = "Abrir externamente",
                    tint = palette.accentSecondary,
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { openExternally(context, workspace.resolve(relPath)) },
                )
            }
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
        val noticeText = notice
        if (noticeText != null) {
            Text(noticeText, color = palette.textSecondary, fontSize = 10.sp)
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

/** Tabela HTML a partir de um CSV (primeira linha como cabeçalho). */
private fun csvHtml(text: String, palette: PhantomPalette): String {
    val rows = csvRows(text).take(500)
    val body = rows.mapIndexed { idx, row ->
        val tag = if (idx == 0) "th" else "td"
        "<tr>" + row.joinToString("") { "<$tag>${htmlEscape(it)}</$tag>" } + "</tr>"
    }.joinToString("\n")
    val truncated = if (rows.size >= 500) "<p class=\"muted\">Mostrando as 500 primeiras linhas.</p>" else ""
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
body { font-family: Roboto, sans-serif; margin: 12px; background: ${hex(palette.background)}; color: ${hex(palette.textPrimary)}; }
table { border-collapse: collapse; width: 100%; font-size: 12px; }
th, td { border: 1px solid ${hex(palette.border)}; padding: 5px 8px; text-align: left; }
th { background: ${hex(palette.surfaceAlt)}; color: ${hex(palette.accentPrimary)}; font-weight: 600; position: sticky; top: 0; }
tr:nth-child(even) td { background: ${hex(palette.surface.copy(alpha = 0.4f))}; }
.muted { color: ${hex(palette.textSecondary)}; }
</style></head><body>$truncated<table>$body</table></body></html>"""
}

/** SQL com destaque simples de sintaxe (strings, comentários, números, keywords). */
private fun sqlHtml(text: String, palette: PhantomPalette): String {
    val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val pattern = Regex("'(?:[^']|'')*'|--[^\\n]*|/\\*.*?\\*/|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b")
    val out = pattern.replace(esc) { m ->
        val s = m.value
        when {
            s.startsWith("'") -> "<span class=\"st\">$s</span>"
            s.startsWith("--") || s.startsWith("/*") -> "<span class=\"cm\">$s</span>"
            s[0].isDigit() -> "<span class=\"num\">$s</span>"
            s.lowercase() in SQL_KEYWORDS -> "<span class=\"kw\">$s</span>"
            else -> s
        }
    }
    return """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
body { font-family: monospace; margin: 12px; background: ${hex(palette.background)}; color: ${hex(palette.textPrimary)}; white-space: pre-wrap; font-size: 12px; }
.kw { color: ${hex(palette.accentPrimary)}; font-weight: 600; }
.st { color: ${hex(palette.success)}; }
.num { color: ${hex(palette.info)}; }
.cm { color: ${hex(palette.textSecondary)}; font-style: italic; }
</style></head><body><pre>$out</pre></body></html>"""
}

/** Abre um arquivo do workspace num app externo via FileProvider (ex.: PDF). */
private fun openExternally(context: Context, file: File) {
    if (!file.isFile) return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    val mime = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "txt", "md", "log" -> "text/plain"
        else -> "application/octet-stream"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Nenhum app disponível para abrir este arquivo", Toast.LENGTH_SHORT).show() }
}

/** Escapa texto para HTML. */
private fun htmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Parse simples de CSV com suporte a aspas ("," ou ";" como separador). */
private fun csvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    for (line in text.lineSequence()) {
        val row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' || c == ';' -> {
                    row += sb.toString(); sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        row += sb.toString()
        rows += row
    }
    return rows
}

/** Palavras-chave SQL usadas no destaque do preview. */
private val SQL_KEYWORDS = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
    "create", "table", "database", "drop", "alter", "add", "join", "left", "right",
    "inner", "outer", "full", "on", "group", "by", "order", "having", "limit",
    "offset", "as", "and", "or", "not", "null", "is", "in", "exists", "between",
    "like", "union", "all", "distinct", "count", "sum", "avg", "min", "max",
    "primary", "key", "foreign", "references", "default", "index", "view",
    "begin", "commit", "rollback", "transaction", "case", "when", "then", "else",
    "end", "cast", "coalesce", "ifnull", "with", "recursive", "unique", "constraint",
    "desc", "asc", "using", "natural", "cross", "union", "except", "intersect",
    "boolean", "integer", "int", "bigint", "smallint", "text", "varchar", "char",
    "numeric", "decimal", "real", "double", "float", "timestamp", "date", "time",
)

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
