package com.phantomcode.app.data.vm

import jackpal.androidterm.emulatorview.TermSession

/**
 * Sessão de terminal (jackpal) ligada a um PTY real.
 *
 * Diferente do [ProcessTermSession] (pipes do QEMU), aqui o guest enxerga um
 * tty de verdade: prompt interativo, eco, cores, ^C e resize (TIOCSWINSZ).
 * Construir SEMPRE na main thread (o TermSession cria um Handler no construtor).
 */
class PtyTermSession(private val pty: PtyProcess, columns: Int = 80, rows: Int = 24) : TermSession() {

    init {
        runCatching {
            setTermIn(pty.input)
            setTermOut(pty.output)
            initializeEmulator(columns, rows)
        }
    }

    /**
     * Quando a view muda de tamanho: o jackpal atualiza o emulador e nós
     * avisamos o tty do guest (TIOCSWINSZ) — obrigatório para `stty size`,
     * vim/htop e quebra de linha corretas.
     */
    override fun updateSize(columns: Int, rows: Int) {
        super.updateSize(columns, rows)
        pty.resize(rows, columns)
    }

    override fun finish() {
        runCatching { pty.destroy() }
        runCatching { super.finish() }
    }
}
