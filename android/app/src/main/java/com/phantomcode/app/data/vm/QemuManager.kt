package com.phantomcode.app.data.vm

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.git.GitManager
import com.phantomcode.app.data.git.GithubAssetClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Motor da VM QEMU headless (D5 — ambiente oficial).
 * Monta o comando do Documento Mestre §8.1 e gerencia o processo:
 *   -M virt,accel=tcg -cpu cortex-a72 -smp N -m XM
 *   -kernel Image (se houver) -append "root=/dev/vda rw console=ttyAMA0"
 *   -drive rootfs.ext4 + virtio-blk · -virtfs 9p do workspace · SLIRP · -nographic
 */
class QemuManager(
    context: Context,
    private val workspace: WorkspaceManager,
    private val distros: DistroManager,
) {

    private val appContext: Context = context.applicationContext
    private val git = GitManager(appContext)
    private val qemuDir: File = File(appContext.filesDir, "qemu").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        instance = this
    }

    /** Publica uma mudança de estado na main thread (seguro para a UI). */
    private fun onMain(block: () -> Unit) = mainHandler.post(block)

    var preset by mutableStateOf(QemuPresets.BALANCED)
    var running by mutableStateOf(false)
    var statusText by mutableStateOf("STOPPED")
    var lastError by mutableStateOf<String?>(null)
    var binaryReady by mutableStateOf(false)
    var binaryInstalling by mutableStateOf(false)

    /** Bloqueia o auto-início após um encerramento explícito (app ou notificação). */
    var autoStartSuppressed by mutableStateOf(false)

    private val prefs = QemuPrefs(context)

    private var process: Process? = null
    private var watcher: Job? = null

    /** Socket unix do console via virtio-serial (T16) — removido no stop. */
    private var serialSocket: File? = null

    /** Socket unix do canal de controle (T20, phantom-agent.sh) — removido no stop. */
    private var ctrlSocket: File? = null

    /** Scanner de pacotes do guest (T20) — canal via 2ª porta do virtio-serial. */
    val scanner = PackageScanner()

    val terminal = TerminalManager()

    /** Binário QEMU: a distro traz o seu (a Phantom vem com qemu no pacote); o
     *  fallback global (extraído/baixado) só entra quando não há distro instalada. */
    fun binary(): File = distros.activeQemu() ?: File(qemuDir, "qemu-system-aarch64")

    init {
        refreshBinary()
        preset = prefs.effectivePreset(appContext)
    }

    /** Recalcula binaryReady (ex.: chamado após instalar a Phantom no Toolbox).
     *  Requer EXISTÊNCIA + PERMISSÃO DE EXECUÇÃO — sem +x o ProcessBuilder
     *  falha com "Permission denied" e o QEMU nunca sobe. */
    fun refreshBinary() {
        val b = binary()
        binaryReady = b.exists() && b.canExecute()
    }

    /** Persiste o preset escolhido e atualiza o estado (valores custom incluídos). */
    fun setPreset(p: QemuPreset, customCores: Int? = null, customRamMb: Int? = null) {
        prefs.presetId = p.id
        if (customCores != null) prefs.customCores = customCores
        if (customRamMb != null) prefs.customRamMb = customRamMb
        preset = prefs.effectivePreset(appContext)
    }

    /** Resumo dos limites do aparelho (para a UI guiar o usuário). */
    fun deviceSummary(): String {
        val cores = DeviceCapabilities.cores(appContext)
        val ram = DeviceCapabilities.totalRamMb(appContext)
        val maxRam = DeviceCapabilities.maxRamMb(appContext)
        return "$cores núcleos · $ram MB RAM · até $maxRam MB para a VM"
    }

    /** Tamanho atual do HD da distro (MB), padrão 3 GB. */
    fun diskSizeMb(): Int = prefs.diskSizeMb

    fun setDiskSizeMb(sizeMb: Int) {
        prefs.diskSizeMb = sizeMb
    }

    /** Garante o binário QEMU: 1º o da distro instalada (Phantom traz no pacote),
     *  2º extraído de APK antigo (assets/qemu), 3º download (fallback). */
    suspend fun ensureBinary(onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (binaryReady) return@withContext true
        // 1) QEMU que já vem dentro do pacote da distro instalada (ex.: Phantom).
        if (distros.activeQemu() != null) {
            // Defensivo: garante +x no binário vindo da distro (extrator preserva
            // o bit do tar, mas nunca custa confirmar antes de subir a VM).
            runCatching { distros.activeQemu()?.setExecutable(true) }
            onMain {
                binaryInstalling = false
                binaryReady = true
            }
            return@withContext true
        }
        // 2) QEMU extraído de um APK antigo que ainda tinha assets/qemu (compat).
        if (extractNativeQemu()) {
            onMain {
                binaryInstalling = false
                binaryReady = true
            }
            return@withContext true
        }
        binaryInstalling = true
        val target = binary()
        val tmp = File(qemuDir, "qemu.tmp")
        runCatching {
            val token = git.token ?: error("Autentique o GitHub antes de baixar o QEMU")
            val conn = GithubAssetClient.openReleaseAsset("qemu-aarch64", "qemu-system-aarch64", token)
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            val digest = PhantomMirror.QEMU_BINARY_SHA256?.let { MessageDigest.getInstance("SHA-256") }
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        digest?.update(buf, 0, read)
                        if (total > 0) onProgress(done.toFloat() / total.toFloat())
                    }
                }
            }
            digest?.let { d ->
                val got = d.digest().joinToString("") { "%02x".format(it) }
                check(got.equals(PhantomMirror.QEMU_BINARY_SHA256, ignoreCase = true)) { "SHA-256 inválido" }
            }
            tmp.setExecutable(true)
            tmp.renameTo(target)
        }.onFailure {
            tmp.delete()
            onMain { lastError = it.message }
            onMain { binaryInstalling = false }
            return@withContext false
        }
        onMain { binaryInstalling = false }
        onMain { binaryReady = target.exists() }
        true
    }

    /**
     * Compat: extrai o QEMU de assets/qemu/ (APKs antigos que ainda embutiam
     * o binário) para filesDir/qemu/. Nos APKs novos o asset não existe e este
     * método falha rápido — o caminho normal é o QEMU do pacote da distro.
     */
    private fun extractNativeQemu(): Boolean {
        val target = File(qemuDir, "qemu-system-aarch64")
        if (target.exists() && target.length() > 1_000_000L) return true
        return runCatching {
            appContext.assets.open("qemu/qemu-system-aarch64").use { input ->
                val tmp = File(qemuDir, "qemu.native.tmp")
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                    }
                }
                tmp.setExecutable(true)
                tmp.renameTo(target)
            }
        }.isSuccess && target.exists()
    }

    /** Sobe a VM com a distro ativa. Retorna erro legível quando não dá. */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (running) return@withContext true
        autoStartSuppressed = false
        lastError = null
        // 1) A distro vem PRIMEIRO: o QEMU já está dentro do pacote Phantom
        //    (rootfs.img + kernel + initrd.img + qemu-system-aarch64).
        val rootfs = distros.activeRootfsImage()
        val kernel = distros.activeKernel()
        if (rootfs == null && kernel == null) {
            onMain { lastError = "Nenhuma distro instalada — instale a Phantom no Toolbox (o QEMU vem junto na instalação)" }
            return@withContext false
        }
        // 2) Binário: a Phantom traz o qemu dentro da distro; só distros
        //    de terceiros usam o fallback global baixado da nuvem.
        if (!binaryReady && !ensureBinary()) {
            onMain { lastError = "Binário QEMU não disponível: ${lastError ?: "erro desconhecido"}" }
            return@withContext false
        }

        val cmd = mutableListOf(
            binary().absolutePath,
            "-M", "virt,accel=tcg",
            "-cpu", "cortex-a72",
            "-smp", preset.cpu.toString(),
            "-m", "${preset.ramMb}M",
        )
        if (kernel != null) {
            cmd += listOf("-kernel", kernel.absolutePath)
            distros.activeInitrd()?.let { cmd += listOf("-initrd", it.absolutePath) }
            // console=hvc0: kernel boota o console no virtio-serial (T16);
            // ttyAMA0 mantido como redundância no stdio.
            cmd += listOf("-append", "root=/dev/vda rw console=ttyAMA0 console=hvc0")
        }
        if (rootfs != null) {
            cmd += listOf(
                "-drive", "if=none,file=${rootfs.absolutePath},id=hd0",
                "-device", "virtio-blk-device,drive=hd0",
            )
        }
        // Ponte de terminal (T16): virtio-serial → socket local → terminal do app.
        // QEMU cria o socket (server=on, wait=off) e o app conecta como cliente.
        val sock = File(qemuDir, "term.sock")
        sock.delete()
        serialSocket = sock
        // Canal de controle (T20): 2ª porta do virtio-serial → phantom-agent.sh.
        val ctrlSock = File(qemuDir, "ctrl.sock")
        ctrlSock.delete()
        ctrlSocket = ctrlSock
        cmd += listOf(
            // Ponte de arquivos: workspace do Android dentro do guest (D3)
            "-virtfs", "local,path=${workspace.root.absolutePath},mount_tag=darkcode-ws,security_model=none,id=ws0",
            // Rede SLIRP (NAT) — internet no guest sem root.
            // hostfwd: porta 80 do guest (servidor PHP/Python/Node do Preview
            // Hub VM) fica acessível no app via http://127.0.0.1:8384 (D24).
            "-netdev", "user,id=net0,hostfwd=tcp::8384-:80",
            "-device", "virtio-net-device,netdev=net0",
            // Console do guest pelo socket unix (hvc0) — o widget de terminal do app
            "-chardev", "socket,id=term0,path=${sock.absolutePath},server=on,wait=off",
            "-device", "virtio-serial-device",
            "-device", "virtconsole,chardev=term0",
            // Canal app↔guest (T20): virtserialport → /dev/vport1p1 no guest
            "-chardev", "socket,id=ctrl0,path=${ctrlSock.absolutePath},server=on,wait=off",
            "-device", "virtserialport,chardev=ctrl0,name=phantom.ctrl",
            // Monitor QEMU + uart0 no stdio (headless)
            "-nographic",
        )

        return@withContext runCatching {
            // Defensivo: garante +x antes de executar (evita Permission denied).
            runCatching { binary().setExecutable(true) }
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            process = p
            val serial = connectSerialSocket(sock)
            if (serial != null) {
                terminal.attach(serial)
            } else {
                terminal.attach(p)
            }
            connectControlSocket()
            onMain {
                running = true
                statusText = "RUNNING"
                // FGS (T23): mantém a VM viva em background + notificação persistente
                VmForegroundService.start(appContext)
            }
            // Drena o stdout em buffer rotativo: mantém as ÚLTIMAS linhas do
            // processo (console + erros) para diagnóstico se o QEMU morrer rápido.
            // ⚠️ Só quando o terminal usa o SOCKET — no fallback por stdio o
            // jackpal já consome esse mesmo stream (ler aqui roubaria os dados
            // do terminal).
            val tail = StringBuilder()
            watcher = scope.launch {
                if (serial != null) {
                    val buf = ByteArray(4096)
                    val input = p.inputStream
                    while (true) {
                        val n = runCatching { input.read(buf) }.getOrDefault(-1)
                        if (n <= 0) break
                        tail.append(String(buf, 0, n, Charsets.UTF_8))
                        if (tail.length > 8192) tail.delete(0, tail.length - 4096)
                    }
                } else {
                    p.waitFor()
                }
                runCatching { sock.delete() }
                val exit = p.exitValue()
                onMain {
                    running = false
                    statusText = "STOPPED"
                    // QEMU morreu rápido (falha de boot/config) — expõe a saída
                    // real do processo para o usuário ver o porquê.
                    if (exit != 0) {
                        lastError = "QEMU saiu (código $exit): ${tail.trim().takeLast(200).ifBlank { "sem saída — veja o log" }}"
                    }
                    VmForegroundService.stop(appContext)
                }
                terminal.stop()
            }
            true
        }.getOrElse {
            onMain { lastError = it.message ?: "Falha ao iniciar o QEMU" }
            false
        }
    }

    /**
     * Conecta ao socket do console (virtio-serial). QEMU cria o socket com
     * `server=on,wait=off`; tenta por ~5s. Null se falhar → fallback p/ stdio.
     */
    private fun connectSerialSocket(sock: File): SocketTermSession? {
        repeat(50) {
            if (sock.exists()) {
                val s = LocalSocket()
                val ok = runCatching {
                    s.connect(LocalSocketAddress(sock.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
                }.isSuccess
                if (ok) return runCatching { SocketTermSession(s) }.getOrNull()
                runCatching { s.close() }
            }
            Thread.sleep(100)
        }
        return null
    }

    /**
     * Conecta ao socket de controle (2ª porta do virtio-serial, T20). O agente
     * do guest (`phantom-agent.sh`) escuta em /dev/vport1p1 e responde SCAN/RUN.
     */
    private fun connectControlSocket() {
        val sock = ctrlSocket ?: return
        scope.launch {
            repeat(60) {
                if (sock.exists()) {
                    val s = LocalSocket()
                    val ok = runCatching {
                        s.connect(LocalSocketAddress(sock.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
                    }.isSuccess
                    if (ok) {
                        scanner.attach(s)
                        return@launch
                    }
                    runCatching { s.close() }
                }
                delay(100)
            }
        }
    }

    fun stop() {
        runCatching { process?.destroy() }
        watcher?.cancel()
        terminal.stop()
        scanner.disconnect()
        runCatching { serialSocket?.delete() }
        serialSocket = null
        runCatching { ctrlSocket?.delete() }
        ctrlSocket = null
        running = false
        statusText = "STOPPED"
        autoStartSuppressed = true
        VmForegroundService.stop(appContext)
    }

    companion object {
        /** Referência do manager ativo — usada pela notificação (VmForegroundService) para parar a sessão. */
        var instance: QemuManager? = null

        /** Porta do servidor web do guest exposta no app (hostfwd do QEMU, D24). */
        const val VM_SERVER_PORT = 8384

        /** URL base do servidor web da VM visto pelo app. */
        const val VM_SERVER_BASE_URL = "http://127.0.0.1:$VM_SERVER_PORT"

        /** Caminho do workspace dentro do guest (montado via virtio-9p). */
        const val GUEST_WORKSPACE = "/home/user/workspace"
    }
}
