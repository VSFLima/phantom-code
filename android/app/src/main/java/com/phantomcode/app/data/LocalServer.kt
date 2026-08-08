package com.phantomcode.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Servidor HTTP local do app (Preview Hub).
 *
 * Serve a pasta do workspace em `http://127.0.0.1:8383` para o preview do editor:
 *  · HTML/CSS/JS com caminhos relativos funcionando (imagens, fetch/XHR/AJAX);
 *  · Markdown, JSON e imagens exibidos pelo Preview Hub.
 *
 * Sem dependências externas: um ServerSocket simples em coroutine (GET/HEAD).
 * Arquivos .php/.py etc. não são executados aqui — respondem 501 com orientação
 * para o servidor da VM.
 */
object LocalServer {

    const val PORT = 8383
    const val BASE_URL = "http://127.0.0.1:$PORT"

    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var root: File? = null
    @Volatile private var running = false

    fun isRunning(): Boolean = running

    fun rootDir(): File? = root

    /** Inicia o servidor na pasta raiz do workspace. Retorna false se já ativo. */
    @Synchronized
    fun start(workspaceRoot: File): Boolean {
        if (running) {
            root = workspaceRoot
            return true
        }
        return runCatching {
            val srv = ServerSocket(PORT)
            serverSocket = srv
            root = workspaceRoot
            running = true
            val sc = CoroutineScope(Dispatchers.IO + Job())
            scope = sc
            acceptJob = sc.launch {
                while (isActive) {
                    val client = runCatching { srv.accept() }.getOrNull() ?: continue
                    handle(client)
                }
            }
            true
        }.getOrElse { false }
    }

    @Synchronized
    fun stop() {
        running = false
        acceptJob?.cancel()
        acceptJob = null
        scope = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handle(client: Socket) {
        try {
            client.soTimeout = 5000
            val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            if (method != "GET" && method != "HEAD") {
                respond(client, 405, "Method Not Allowed", null as ByteArray?, contentType("txt"))
                return
            }
            val rawPath = parts[1].substringBefore('?')
            val decoded = runCatching { URLDecoder.decode(rawPath, Charsets.UTF_8.name()) }
                .getOrDefault(rawPath)
            val base = root ?: return
            serveFile(client, base, decoded, method)
        } catch (_: Exception) {
        } finally {
            runCatching { client.close() }
        }
    }

    private fun serveFile(client: Socket, base: File, path: String, method: String) {
        var clean = path.trimStart('/')
        if (clean.isEmpty()) clean = "index.html"
        // Bloqueia navegação para fora da raiz (..)
        val target = File(base, clean).canonicalFile
        if (!target.path.startsWith(base.canonicalPath + File.separator) && target.canonicalFile != base.canonicalFile) {
            respond(client, 403, "Forbidden", null as ByteArray?, contentType("txt"))
            return
        }

        var file = target
        if (file.isDirectory) {
            file = File(file, "index.html")
        }

        val ext = file.extension.lowercase()
        if (ext == "php") {
            val body = "<!DOCTYPE html><html><body style='font-family:monospace;padding:20px'>" +
                "<h3>Precisa do servidor PHP (VM)</h3>" +
                "<p>O arquivo <b>${file.name}</b> é PHP e não roda no servidor estático do app.</p>" +
                "<p>Use <b>Ações → Servidor local (VM)</b> para rodar PHP/Python/Node no Linux da Phantom.</p></body></html>"
            respond(client, 501, "Not Implemented", body, contentType("html"))
            return
        }

        if (!file.isFile) {
            val body = "<!DOCTYPE html><html><body style='font-family:monospace;padding:20px'>" +
                "<h3>Não encontrado</h3><p>$clean</p></body></html>"
            respond(client, 404, "Not Found", body, contentType("html"))
            return
        }

        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null) {
            respond(client, 500, "Internal Server Error", null as ByteArray?, contentType("txt"))
            return
        }
        respond(client, 200, "OK", bytes, contentType(ext))
    }

    private fun respond(client: Socket, code: Int, reason: String, body: ByteArray?, mime: String) {
        val out: OutputStream = client.getOutputStream()
        val data = body ?: reason.toByteArray()
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $mime\r\n" +
            "Content-Length: ${data.size}\r\n" +
            "Connection: close\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Cache-Control: no-store\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(data)
        out.flush()
    }

    private fun respond(client: Socket, code: Int, reason: String, body: String?, mime: String) {
        val bytes = body?.toByteArray(Charsets.UTF_8)
        if (bytes != null) respond(client, code, reason, bytes, mime)
        else respond(client, code, reason, null as ByteArray?, mime)
    }

    private fun contentType(ext: String): String = when (ext) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "json", "map" -> "application/json; charset=utf-8"
        "md", "markdown", "txt", "log", "xml", "csv" -> "text/plain; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "pdf" -> "application/pdf"
        "wasm" -> "application/wasm"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }
}
