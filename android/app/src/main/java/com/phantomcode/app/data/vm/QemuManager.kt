package com.phantomcode.app.data.vm

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.phantomcode.app.data.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** Publica uma mudança de estado na main thread (seguro para a UI). */
    private fun onMain(block: () -> Unit) = mainHandler.post(block)

    var preset by mutableStateOf(QemuPresets.BALANCED)
    var running by mutableStateOf(false)
    var statusText by mutableStateOf("STOPPED")
    var lastError by mutableStateOf<String?>(null)
    var binaryReady by mutableStateOf(false)
    var binaryInstalling by mutableStateOf(false)

    private var process: Process? = null
    private var watcher: Job? = null

    val terminal = TerminalManager()

    fun binary(): File = File(qemuDir, "qemu-system-aarch64")

    init {
        binaryReady = binary().exists()
    }

    /** Baixa o binário QEMU arm64 (com checksum se informado) e marca como executável. */
    suspend fun ensureBinary(onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (binaryReady) return@withContext true
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

    /** Sobe a VM com a distro ativa. Retorna erro legível quando não dá. */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (running) return@withContext true
        lastError = null
        if (!binaryReady && !ensureBinary()) {
            onMain { lastError = "Binário QEMU não instalado: ${lastError ?: "erro desconhecido"}" }
            return@withContext false
        }
        val rootfs = distros.activeRootfsImage()
        val kernel = distros.activeKernel()
        if (rootfs == null && kernel == null) {
            onMain { lastError = "Nenhuma distro instalada — instale a Phantom Base no Toolbox" }
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
            cmd += listOf("-append", "root=/dev/vda rw console=ttyAMA0")
        }
        if (rootfs != null) {
            cmd += listOf(
                "-drive", "if=none,file=${rootfs.absolutePath},id=hd0",
                "-device", "virtio-blk-device,drive=hd0",
            )
        }
        cmd += listOf(
            // Ponte de arquivos: workspace do Android dentro do guest (D3)
            "-virtfs", "local,path=${workspace.root.absolutePath},mount_tag=darkcode-ws,security_model=none,id=ws0",
            // Rede SLIRP (NAT) — internet no guest sem root
            "-netdev", "user,id=net0",
            "-device", "virtio-net-device,netdev=net0",
            // Console headless via stdio → terminal do app
            "-nographic",
        )

        return@withContext runCatching {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            process = p
            terminal.attach(p)
            onMain {
                running = true
                statusText = "RUNNING"
                // FGS (T23): mantém a VM viva em background + notificação persistente
                VmForegroundService.start(appContext)
            }
            watcher = scope.launch {
                p.waitFor()
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

    fun stop() {
        runCatching { process?.destroy() }
        watcher?.cancel()
        terminal.stop()
        running = false
        statusText = "STOPPED"
        VmForegroundService.stop(appContext)
    }
}
