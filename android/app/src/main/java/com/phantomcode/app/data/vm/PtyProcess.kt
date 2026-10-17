package com.phantomcode.app.data.vm

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Field

/**
 * Processo rodando dentro de um PTY real (par pid + master fd vindo do JNI).
 *
 * O FileDescriptor é montado por reflection (campo `descriptor`) — mesmo
 * padrão do TerminalSession do Termux: em Android não há API pública para
 * embrulhar um fd bruto em streams de I/O.
 */
class PtyProcess(val pid: Int, val masterFd: Int) {

    val fd: FileDescriptor = wrap(masterFd)
    val input: InputStream = FileInputStream(fd)
    val output: OutputStream = FileOutputStream(fd)

    /** Redimensiona o tty (linhas × colunas) — o EmulatorView guia. */
    fun resize(rows: Int, cols: Int): Int = runCatching {
        PtyNative.resize(masterFd, rows, cols)
    }.getOrDefault(-1)

    /** Mata o grupo inteiro do processo (o filho é líder de sessão). */
    fun destroy() {
        runCatching { PtyNative.killTree(pid, SIGKILL) }
    }

    companion object {
        const val SIGKILL = 9

        private fun wrap(fd: Int): FileDescriptor {
            val descriptor = FileDescriptor()
            val field: Field = runCatching {
                FileDescriptor::class.java.getDeclaredField("descriptor")
            }.getOrElse {
                FileDescriptor::class.java.getDeclaredField("fd")
            }
            field.isAccessible = true
            field.setInt(descriptor, fd)
            return descriptor
        }
    }
}
