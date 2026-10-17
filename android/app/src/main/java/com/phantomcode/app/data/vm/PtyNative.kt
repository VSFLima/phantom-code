package com.phantomcode.app.data.vm

/**
 * Ponte JNI para o PTY nativo do motor PRoot (libpty.so, módulo :proot).
 *
 * Por que existe: o Bionic não tem forkpty(); sem um PTY real o comando do
 * guest roda em modo pipe (sem prompt, eco, cores ou job control). O JNI
 * (pty/jni_pty.c) faz open(/dev/ptmx) + fork + setsid + TIOCSCTTY + execve.
 */
object PtyNative {

    init {
        System.loadLibrary("pty")
    }

    /**
     * Executa [argv] dentro de um PTY recém-criado, com [envp] e [cwd].
     * Retorna [pid, masterFd] — ou null se o PTY/fork/exec falharem.
     */
    external fun spawn(
        argv: Array<String>,
        envp: Array<String>,
        cwd: String?,
        rows: Int,
        cols: Int,
    ): IntArray?

    /** Ajusta o tamanho da janela (TIOCSWINSZ) — chamado quando a view redimensiona. */
    external fun resize(masterFd: Int, rows: Int, cols: Int): Int

    /** Bloqueia até o processo [pid] sair; devolve o código (128+sinal se morto por sinal). */
    external fun waitFor(pid: Int): Int

    /** Mata o grupo de processos (o filho virou líder de sessão via setsid). */
    external fun killTree(pid: Int, sig: Int): Int

    /** Fecha um fd bruto (usado se o PTY for descartado sem sessão). */
    external fun closeFd(fd: Int): Int
}
