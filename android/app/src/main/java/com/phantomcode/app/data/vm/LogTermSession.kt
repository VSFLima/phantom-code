package com.phantomcode.app.data.vm

import jackpal.androidterm.emulatorview.TermSession

/**
 * Sessão de terminal VT100 sem processo (T17/D11) — usada para o app escrever
 * saída diretamente na tela (ex.: log de instalação de distro acompanhado ao
 * vivo pelo usuário no terminal).
 */
class LogTermSession : TermSession() {

    init {
        initializeEmulator(80, 24)
    }

    /** Anexa texto à tela do terminal (como se viesse de um processo). */
    fun append(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        runCatching { appendToEmulator(bytes, 0, bytes.size) }
    }

    override fun finish() {
        runCatching { super.finish() }
    }
}
