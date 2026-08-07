package com.phantomcode.app.data.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jackpal.androidterm.emulatorview.TermSession

/** Tipo de aba do terminal: console da VM, shell local ou log do app. */
enum class TerminalTabKind { QEMU, SHELL, LOG }

/** Uma aba do terminal: título + sessão VT100 real ligada a um processo. */
class TerminalTab(
    val kind: TerminalTabKind,
    var title: String,
    val session: TermSession,
)

/**
 * Gerenciador de terminais VT100 reais (T17 — D11).
 *
 * Múltiplas abas sobre o emulador jackpal (emulatorview):
 * - aba 0: console do QEMU (anexada quando a VM sobe);
 * - abas extras: shells locais (`/system/bin/sh`) para comandos rápidos
 *   sem ocupar o console da VM.
 *
 * Compat com o v1: [attach] + [stop] + [active] mantidos para o QemuManager.
 */
class TerminalManager {

    val tabs = mutableStateListOf<TerminalTab>()
    var activeIndex by mutableStateOf(-1)
        private set

    /** Compat v1: true quando há ao menos uma aba viva. */
    var active by mutableStateOf(false)
        private set

    val activeTab: TerminalTab?
        get() = tabs.getOrNull(activeIndex)

    /** Anexa o console do processo QEMU (substitui abas antigas). */
    fun attach(process: Process) {
        attach(ProcessTermSession(process))
    }

    /** Anexa uma sessão de terminal qualquer (ex.: console via socket da VM). */
    fun attach(session: TermSession, title: String = "Linux (QEMU)") {
        stop()
        val tab = TerminalTab(TerminalTabKind.QEMU, title, session)
        tabs += tab
        activeIndex = tabs.lastIndex
        active = true
    }

    /** Nova aba com shell local do Android (mksh) com ambiente mínimo (TERM/PATH). */
    fun addShellTab() {
        val pb = ProcessBuilder("/system/bin/sh").redirectErrorStream(true)
        pb.environment().putAll(
            mapOf(
                "TERM" to "xterm-256color",
                "PATH" to "/sbin:/system/bin:/vendor/bin:/product/bin",
                "HOME" to "/data/data/com.phantomcode.app/files",
                "LANG" to "C.UTF-8",
            )
        )
        val p = runCatching { pb.start() }.getOrNull() ?: return
        shellCount++
        val tab = TerminalTab(TerminalTabKind.SHELL, "Shell $shellCount", ProcessTermSession(p))
        tabs += tab
        activeIndex = tabs.lastIndex
        active = true
    }

    /** Troca para a aba [index] (mantém as demais vivas). */
    fun selectTab(index: Int) {
        if (index in tabs.indices) activeIndex = index
    }

    /**
     * Nova aba de log: o app escreve na tela em tempo real (ex.: instalação
     * de distro). Retorna a sessão para o app fazer `append(...)`.
     */
    fun addLogTab(title: String): LogTermSession {
        val session = LogTermSession()
        val tab = TerminalTab(TerminalTabKind.LOG, title, session)
        tabs += tab
        activeIndex = tabs.lastIndex
        active = true
        return session
    }

    /** Fecha a aba [index]: encerra a sessão e o processo do shell local. */
    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        tabs.removeAt(index)
        runCatching { tab.session.finish() }
        if (activeIndex >= tabs.size) activeIndex = tabs.size - 1
        if (tabs.isEmpty()) activeIndex = -1
        active = tabs.isNotEmpty()
    }

    /** Encerra todas as abas (chamado quando a VM para). */
    fun stop() {
        tabs.toList().forEach { runCatching { it.session.finish() } }
        tabs.clear()
        activeIndex = -1
        active = false
    }

    private var shellCount = 0
}
