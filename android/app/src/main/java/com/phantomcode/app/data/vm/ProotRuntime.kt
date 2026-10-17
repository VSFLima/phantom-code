package com.phantomcode.app.data.vm

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Motor PRoot — guest Linux (Wolfi) dentro do app, sem root.
 *
 * Substitui o QEMU (TCG ~15–20% do nativo) pelo PRoot em espaço de usuário:
 * proot (fork do Termux, GPL-2.0 — ver NOTICE) faz ptrace + emulação de
 * syscalls e apresenta o rootfs como "/" para os processos do guest.
 *
 * Fluxo:
 *  1. o APK embute o rootfs (assets/linux/wolfi.tar.gz) — na primeira sessão
 *     o script host copia/extrai para files/linux/wolfi;
 *  2. [start] monta o env (PROOT/PROOT_LOADER/PROOT_TMP_DIR), e executa
 *     `sh init-wolfi-host` dentro de um PTY real (libpty.so) — o guest ganha
 *     tty de verdade (prompt, eco, cores, resize);
 *  3. o terminal do app anexa a sessão VT100 como qualquer outra aba.
 *
 * Binários executáveis vêm de applicationInfo.nativeLibraryDir (libproot.so,
 * libloader.so, libloader32.so) — o único lugar onde o Android ainda permite
 * execve para apps com targetSdk 35.
 */
class ProotRuntime(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    val terminal = TerminalManager()

    var running by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("STOPPED")
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /** True quando o rootfs está extraído e pronto para boot. */
    var rootfsReady by mutableStateOf(false)
        private set

    /** True quando o APK veio com o rootfs embutido (assets/linux/wolfi.tar.gz). */
    var bundlePresent by mutableStateOf(false)
        private set

    private val files: File = appContext.filesDir
    private val linuxDir: File = File(files, "linux").apply { mkdirs() }
    val rootfsDir: File = File(linuxDir, ROOTFS_ID)
    private val binDir: File = File(linuxDir, "bin").apply { mkdirs() }
    private val tmpDir: File = File(linuxDir, "tmp").apply { mkdirs() }
    private val prootTmp: File = File(tmpDir, "proot").apply { mkdirs() }
    private val bundleTar: File = File(linuxDir, BUNDLE_FILE)

    private var pty: PtyProcess? = null
    private var fallback: Process? = null
    private var watcher: Job? = null

    private val nativeDir: String get() = appContext.applicationInfo.nativeLibraryDir

    init {
        refresh()
    }

    // ── binários do motor ────────────────────────────────────────────────────

    fun libProot(): File = File(nativeDir, "libproot.so")
    fun libLoader(): File = File(nativeDir, "libloader.so")
    private fun libLoader32(): File? = File(nativeDir, "libloader32.so").takeIf { it.exists() }

    /** O APK realmente carrega o motor? (mostrado na UI de diagnóstico) */
    fun engineReady(): Boolean = libProot().exists() && libLoader().exists()

    // ── rootfs ──────────────────────────────────────────────────────────────

    /** Recalcula os estados de instalação (chamar após copiar/extrair o rootfs). */
    fun refresh() {
        rootfsReady = File(rootfsDir, "etc/os-release").exists()
        bundlePresent = bundleTar.exists() ||
            runCatching { appContext.assets.openFd(BUNDLE_ASSET).close() }.isSuccess
    }

    /**
     * Copia o rootfs embutido no APK para files/linux/wolfi.tar.gz (uma vez).
     * A extração acontece no primeiro boot (script host, com tar do toybox).
     * Idempotente: re-copia quando o bundle do APK muda de tamanho (novo build).
     */
    private fun stageBundle() {
        runCatching {
            val expected = appContext.assets.openFd(BUNDLE_ASSET).use { it.length }
            if (expected <= 0) return@runCatching
            if (bundleTar.exists() && bundleTar.length() == expected) return@runCatching
            appContext.assets.open(BUNDLE_ASSET).use { input ->
                val tmp = File(linuxDir, "$BUNDLE_FILE.tmp")
                tmp.outputStream().use { out -> input.copyTo(out, 256 * 1024) }
                if (bundleTar.exists()) bundleTar.delete()
                tmp.renameTo(bundleTar)
            }
        }
    }

    /**
     * Instala/atualiza os scripts host e os arquivos auxiliares (stat/vmstat).
     * Sempre re-escritos: correções no app valem para instalações existentes.
     */
    private fun installScripts() {
        for (name in SCRIPTS) {
            runCatching {
                val target = File(binDir, name)
                appContext.assets.open("linux/bin/$name").use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                target.setExecutable(true)
            }
        }
        for (name in DATA_FILES) {
            runCatching {
                val target = File(linuxDir, name)
                appContext.assets.open("linux/bin/$name").use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
            }
        }
    }

    // ── sessão ──────────────────────────────────────────────────────────────

    /** Sobe o guest numa sessão nova de terminal. Retorna erro legível em [lastError]. */
    suspend fun start(): Boolean {
        if (running) return true
        lastError = null

        if (!engineReady()) {
            lastError = "Motor proot ausente no APK (libproot.so/libloader.so)."
            return false
        }
        installScripts()
        stageBundle()
        refresh()
        if (!rootfsReady) {
            lastError = "Rootfs do Wolfi ainda não instalado — o APK não trouxe o bundle e nenhuma distro foi baixada."
            return false
        }

        statusText = "BOOTING"
        lastError = null
        val argv = arrayOf("/system/bin/sh", "-c", File(binDir, "init-wolfi-host").absolutePath)
        val env = buildEnv()

        // 1) Caminho principal: PTY real (tty de verdade no guest).
        val started = withContext(Dispatchers.IO) {
            runCatching {
                val res = PtyNative.spawn(argv, env, files.absolutePath, 24, 80)
                if (res == null || res.size != 2 || res[0] <= 0 || res[1] < 0) {
                    null
                } else {
                    PtyProcess(res[0], res[1])
                }
            }.getOrNull()
        }

        return if (started != null) {
            pty = started
            val session = TerminalFactory.onMain { PtyTermSession(started, 80, 24) }
            withContext(Dispatchers.Main) { terminal.attach(session, "Wolfi") }
            onRunning()
            watchPty(started)
            true
        } else {
            // 2) Fallback sem PTY: mesmo comando por pipes (funciona, sem tty).
            val proc = withContext(Dispatchers.IO) {
                runCatching {
                    ProcessBuilder(argv.toList())
                        .directory(files)
                        .redirectErrorStream(true)
                        .apply { environment().putAll(envToMap(env)) }
                        .start()
                }.getOrNull()
            }
            if (proc == null) {
                statusText = "STOPPED"
                lastError = "Não foi possível iniciar o guest (PTY e pipe falharam)."
                return false
            }
            fallback = proc
            val session = TerminalFactory.onMain { ProcessTermSession(proc) }
            withContext(Dispatchers.Main) { terminal.attach(session, "Wolfi") }
            onRunning()
            watchProcess(proc)
            true
        }
    }

    /** Encerra o guest: SIGKILL no grupo (o proot tem --kill-on-exit) + limpeza da aba. */
    fun stop() {
        val p = pty
        TerminalFactory.onMain { terminal.closeQemuTab() } // finish() da sessão mata o grupo
        p?.let { runCatching { PtyNative.killTree(it.pid, PtyProcess.SIGKILL) } }
        runCatching { fallback?.destroy() }
        pty = null
        fallback = null
        watcher?.cancel()
        watcher = null
        running = false
        statusText = "STOPPED"
    }

    private fun onRunning() {
        running = true
        statusText = "RUNNING"
    }

    /** Aguarda o PTY terminar; ao sair, libera estado e aba (na main). */
    private fun watchPty(p: PtyProcess) {
        watcher = scope.launch {
            val code = runCatching { PtyNative.waitFor(p.pid) }.getOrDefault(-1)
            pty = null
            withContextMain {
                terminal.closeQemuTab()
                running = false
                statusText = "STOPPED"
                if (code != 0 && code != 137 && lastError == null) {
                    lastError = "O guest encerrou (código $code). Veja o terminal."
                }
            }
        }
    }

    private fun watchProcess(p: Process) {
        watcher = scope.launch {
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            fallback = null
            withContextMain {
                terminal.closeQemuTab()
                running = false
                statusText = "STOPPED"
                if (code != 0 && code != 137 && lastError == null) {
                    lastError = "O guest encerrou (código $code). Veja o terminal."
                }
            }
        }
    }

    // ── ambiente do host ────────────────────────────────────────────────────

    /** Env do processo host que executa o proot (herda o do app + overrides). */
    private fun buildEnv(): Array<String> {
        val env = System.getenv().toMutableMap()
        env["PATH"] = "/system/bin:/system/xbin:/product/bin:/apex/com.android.runtime/bin"
        env["HOME"] = files.absolutePath
        env["TMPDIR"] = tmpDir.absolutePath
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"
        env["LANG"] = "C.UTF-8"
        env["PROOT_TMP_DIR"] = prootTmp.absolutePath
        env["PROOT_LOADER"] = libLoader().absolutePath
        libLoader32()?.let { env["PROOT_LOADER_32"] = it.absolutePath }
        env["PROOT"] = libProot().absolutePath
        env["PHANTOM_FILES_DIR"] = files.absolutePath
        env["PHANTOM_NATIVE_DIR"] = nativeDir
        return env.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    private fun envToMap(env: Array<String>): Map<String, String> =
        env.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()

    private suspend fun withContextMain(block: () -> Unit) {
        withContext(Dispatchers.Main) { block() }
    }

    companion object {
        /** Distro padrão do motor proot. */
        const val ROOTFS_ID = "wolfi"

        /**
         * Bundle do rootfs dentro do APK (colocado pelo CI antes do Gradle).
         *
         * Nome SEM `.gz` de propósito: o AGP/aapt2 descomprime no build os assets
         * terminados em `.gz` (o arquivo vira um tar maior e renomeado dentro do
         * APK). O conteúdo aqui é gzip; o `tar` do Android (toybox) autodetecção
         * pelo magic byte na extração.
         */
        const val BUNDLE_ASSET = "linux/wolfi.rootfs"

        /** Nome do bundle já copiado para files/linux/ (o script host extrai daqui). */
        const val BUNDLE_FILE = "wolfi.rootfs"

        private val SCRIPTS = listOf("init-wolfi-host", "init-wolfi", "rm-wrapper")

        private val DATA_FILES = listOf("stat", "vmstat")
    }
}
