package com.phantomcode.app.data.vm

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jackpal.androidterm.emulatorview.TermSession

/**
 * Sessão de terminal VT100 sem processo (T17/D11) — usada para o app escrever
 * saída diretamente na tela (ex.: log de instalação de distro acompanhado ao
 * vivo pelo usuário no terminal).
 */
class LogTermSession : TermSession() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var emulatorReady = false

    /** Progresso da instalação (0..1) para a barra de progresso no terminal;
     *  null = sem barra (ou barra indeterminada). */
    var progress by mutableStateOf<Float?>(null)

    /** Fase ao vivo da instalação ("Baixando…", "Verificando SHA-256…", "Extraindo…"). */
    var phase by mutableStateOf<String?>(null)

    init {
        // ⚠️ FIX CRASH: o initializeEmulator() do jackpal inicia mReaderThread e
        // mWriterThread. Sem setTermIn/setTermOut ANTES, a reader thread faz
        // mTermIn.read(...) com null → NPE não capturado → o APP FECHA ao abrir
        // a aba de log (ex.: instalar distro). Streams dummy resolvem:
        //  - termIn: EOF (-1) → a reader thread encerra limpa (exitOnEOF=false);
        //  - termOut: descarta a saída (o app escreve via appendToEmulator).
        setTermIn(
            object : java.io.InputStream() {
                override fun read(): Int = -1
                override fun read(b: ByteArray, off: Int, len: Int): Int = -1
            },
        )
        setTermOut(
            object : java.io.OutputStream() {
                override fun write(b: Int) {
                    // descarta
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    // descarta
                }
            },
        )
        emulatorReady = runCatching {
            initializeEmulator(80, 24)
            true
        }.getOrDefault(false)
    }

    /** Anexa texto à tela do terminal (como se viesse de um processo).
     *  Postado na MAIN thread: o download da distro roda em Dispatchers.IO e o
     *  appendToEmulator não é thread-safe com o EmulatorView (evita corromper
     *  a tela durante a instalação). */
    fun append(text: String) {
        if (!emulatorReady) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        mainHandler.post { runCatching { appendToEmulator(bytes, 0, bytes.size) } }
    }

    /** Atualiza a barra de progresso e a fase da instalação (postado na main thread). */
    fun setProgress(p: Float?, phaseText: String?) {
        mainHandler.post {
            progress = p
            phase = phaseText
        }
    }

    override fun finish() {
        runCatching { super.finish() }
    }
}
