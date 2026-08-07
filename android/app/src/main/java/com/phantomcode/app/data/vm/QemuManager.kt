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

    /** Binário QEMU usado no boot: 1º o embutido no app (extraído), 2º o da distro, 3º fallback. */
    fun binary(): File {
        val native = File(qemuDir, "qemu-system-aarch64")
        if (native.exists()) return native
        return distros.activeQemu() ?: native
    }

    init {
        binaryReady = binary().exists()
        preset = prefs.effectivePreset(appContext)
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

    /** Garante o binário QEMU: 1º embutido no APK, 2º o da distro, 3º download (fallback). */
    suspend fun ensureBinary(onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (binaryReady) return@withContext true
        // 1) QEMU nativo embutido no APK (assets/qemu — T30): extrai na 1ª execução,
        //    sem depender de download nem de o pacote da distro trazer o binário.
        if (extractNativeQemu()) {
            onMain {
                binaryInstalling = false
                binaryReady = true
            }
            return@withContext true
        }
        // 2) QEMU que já vem dentro do pacote da distro instalada.
        if (distros.activeQemu() != null) {
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
            val conn = (URL(PhantomMirror.QEMU_BINARY_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
            }
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
     * Extrai o QEMU embutido no APK (assets/qemu/qemu-system-aarch64, T30)
     * para filesDir/qemu/ e marca como executável. O binário vem de fábrica no
     * APK (o workflow Build APK o injeta em assets) — nada de download.
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
            // Rede SLIRP (NAT) — internet no guest sem root
            "-netdev", "user,id=net0",
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
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            process = p
            val serial = connectSerialSocket(sock)
            if (serial != null) {
                terminal.attach(serial)
                // Consome o stdout (console ttyAMA0 redundante) para o pipe não
                // encher e travar a VM quando o terminal usa o socket.
                scope.launch {
                    try {
                        val buf = ByteArray(4096)
                        val input = p.inputStream
                        while (input.read(buf) != -1) { /* descarta */ }
                    } catch (_: Exception) {
                    }
                }
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
            watcher = scope.launch {
                p.waitFor()
                runCatching { sock.delete() }
                onMain {
                    running = false
                    statusText = "STOPPED"
                    VmForegroundService.stop(appContext)
                }
                terminal.stop()
            }
            true
        }.getOrElse {
            onMain { lastError = it.message }
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
    }
}
