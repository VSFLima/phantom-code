package com.phantomcode.app.data.vm

import com.termux.terminal.TermSession

/**
 * Sessão de terminal VT100 real (T17/D11).
 *
 * Ponte entre um [Process] (console QEMU ou shell local) e o emulador Termux:
 * - stdout/stderr do processo → tela do terminal (termIn)
 * - teclado do usuário → stdin do processo (termOut)
 *
 * O redimensionamento é feito pela própria TerminalView (terminal-view), que
 * chama [updateSize] com as colunas/linhas calculadas do layout.
 */
class ProcessTermSession(private val process: Process) : TermSession() {

    init {
        setTermIn(process.inputStream)
        setTermOut(process.outputStream)
        initializeEmulator(80, 24)
    }

    override fun finish() {
        runCatching { super.finish() }
        runCatching { process.destroy() }
    }
}
