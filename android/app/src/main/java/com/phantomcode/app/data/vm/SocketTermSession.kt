package com.phantomcode.app.data.vm

import android.net.LocalSocket
import jackpal.androidterm.emulatorview.TermSession

/**
 * Sessão de terminal VT100 via virtio-serial (T16).
 *
 * Ponte entre o chardev socket do QEMU (`virtconsole`) e o emulador jackpal:
 * - socket (console do guest) → tela do terminal (o TermSession lê termIn
 *   numa thread interna quando `setTermIn` é chamado — mesmo mecanismo do
 *   [ProcessTermSession]);
 * - teclado do usuário → socket (termOut), chegando como entrada do guest.
 */
class SocketTermSession(private val socket: LocalSocket) : TermSession() {

    init {
        runCatching {
            setTermIn(socket.inputStream)
            setTermOut(socket.outputStream)
            initializeEmulator(80, 24)
        }
    }

    override fun finish() {
        runCatching { socket.close() }
        runCatching { super.finish() }
    }
}
