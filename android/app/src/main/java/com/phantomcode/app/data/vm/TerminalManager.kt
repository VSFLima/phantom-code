package com.phantomcode.app.data.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val ANSI_PATTERN = Regex("\\u001B\\[[0-9;?]*[a-zA-Z]")
private val CONTROL_PATTERN = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

/** Remove códigos ANSI/controle para exibição segura no console. */
fun stripAnsi(s: String): String = ANSI_PATTERN.replace(CONTROL_PATTERN.replace(s, ""), "")

/**
 * Console do terminal: conecta as streams do processo QEMU à UI.
 * (v1 — console de linhas; upgrade para VT100/jackpal em T17 final.)
 */
class TerminalManager {

    private val scope = CoroutineScope(Dispatchers.IO)

    val lines = mutableStateListOf<String>()
    var active by mutableStateOf(false)
        private set

    private var writer: java.io.OutputStream? = null
    private var readerJob: Job? = null

    fun attach(process: Process) {
        stop()
        writer = process.outputStream
        active = true
        readerJob = scope.launch {
            process.inputStream.bufferedReader().forEachLine { raw ->
                lines += stripAnsi(raw)
                while (lines.size > MAX_LINES) lines.removeAt(0)
            }
            active = false
        }
    }

    /** Envia uma linha de comando para o console do guest. */
    fun send(text: String) {
        if (!active) return
        runCatching {
            writer?.write((text + "\n").toByteArray(Charsets.UTF_8))
            writer?.flush()
        }
    }

    fun stop() {
        readerJob?.cancel()
        readerJob = null
        writer = null
        active = false
    }

    fun clear() = lines.clear()

    companion object {
        private const val MAX_LINES = 2000
    }
}
