package com.phantomcode.app.data.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jackpal.androidterm.emulatorview.TermSession
import java.io.File

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

    /**
     * Anexa uma sessão de terminal qualquer (ex.: console via socket da VM).
     * Fecha apenas a aba do console anterior — shells locais e logs sobrevivem
     * a um restart da VM.
     */
    fun attach(session: TermSession, title: String = "Phantom Code") {
        closeQemuTab()
        val tab = TerminalTab(TerminalTabKind.QEMU, title, session)
        tabs += tab
        activeIndex = tabs.lastIndex
        active = true
    }

    /**
     * Nova aba com shell local do Android (mksh) com ambiente mínimo (TERM/PATH).
     * Retorna false se nenhum shell puder ser iniciado (feedback para a UI).
     */
    fun addShellTab(cwd: String? = null): Boolean {
        var p: Process? = null
        for (sh in listOf("/system/bin/sh", "/system/bin/mksh")) {
            p = runCatching {
                val pb = ProcessBuilder(sh).redirectErrorStream(true)
                if (!cwd.isNullOrBlank()) runCatching { pb.directory(File(cwd)) }
                pb.environment().putAll(
                    mapOf(
                        "TERM" to "xterm-256color",
                        "PATH" to "/sbin:/system/bin:/vendor/bin:/product/bin",
                        "HOME" to "/data/data/com.phantomcode.app/files",
                        "LANG" to "C.UTF-8",
                    )
                )
                pb.start()
            }.getOrNull()
            if (p != null) break
        }
        val proc = p ?: return false
        shellCount++
        val tab = TerminalTab(TerminalTabKind.SHELL, "Shell $shellCount", ProcessTermSession(proc))
        tabs += tab
        activeIndex = tabs.lastIndex
        active = true
        return true
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
        if (index < activeIndex) activeIndex--
        if (activeIndex >= tabs.size) activeIndex = tabs.size - 1
        if (tabs.isEmpty()) activeIndex = -1
        active = tabs.isNotEmpty()
    }

    /** Fecha a aba do console da VM (mantém shells locais e logs abertos). */
    fun closeQemuTab() {
        val idx = tabs.indexOfFirst { it.kind == TerminalTabKind.QEMU }
        if (idx >= 0) closeTab(idx)
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
