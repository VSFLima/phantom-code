package com.phantomcode.app.data.vm

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Distro do catálogo (D1 — o usuário escolhe; Phantom Base é a oficial). */
data class DistroInfo(
    val id: String,
    val name: String,
    val badge: String? = null,          // "Oficial · Recomendada" para a Phantom Base
    val url: String,
    val sha256: String? = null,
    val sizeMb: Int,
    val packageManager: String,         // apt / apk
)

object DistroCatalog {
    val ALL: List<DistroInfo> = listOf(
        DistroInfo(
            id = "phantom-base",
            name = "Phantom Base",
            badge = "Oficial · Recomendada",
            url = PhantomMirror.PHANTOM_BASE_URL,
            sha256 = PhantomMirror.PHANTOM_BASE_SHA256,
            sizeMb = 60,
            packageManager = "apk",
        ),
        DistroInfo(
            id = "ubuntu",
            name = "Ubuntu 24.04 minimal",
            url = PhantomMirror.UBUNTU_URL,
            sizeMb = 400,
            packageManager = "apt",
        ),
        DistroInfo(
            id = "debian",
            name = "Debian bookworm slim",
            url = PhantomMirror.DEBIAN_URL,
            sizeMb = 300,
            packageManager = "apt",
        ),
        DistroInfo(
            id = "alpine",
            name = "Alpine mini",
            url = PhantomMirror.ALPINE_URL,
            sizeMb = 20,
            packageManager = "apk",
        ),
    )
}

/** Estado de instalação de uma distro (UI reativa). */
data class DistroInstallState(
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val installed: Boolean = false,
    val error: String? = null,
)

/**
 * Gerencia distros em `filesDir/linux/<id>/` (D3 — workspace independente da rootfs).
 *
 * Estrutura aceita após instalação:
 *   linux/<id>/rootfs.img   → imagem crua (boot por -drive)
 *   linux/<id>/kernel       → Image arm64 + linux/<id>/initrd.img (boot por -kernel)
 *   linux/<id>/rootfs/      → rootfs extraída (precisa de kernel/imagem p/ bootar)
 */
class DistroManager(context: Context) {

    private val appContext: Context = context.applicationContext
    val linuxDir: File = File(context.filesDir, "linux").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO)

    val installStates = mutableStateMapOf<String, DistroInstallState>()

    var activeId by mutableStateOf<String?>(null)
        private set

    init {
        // Detecta distros já instaladas
        DistroCatalog.ALL.forEach { info ->
            installStates[info.id] = stateFor(info.id)
        }
        activeId = linuxDir.listFiles()?.firstOrNull { it.isDirectory && isInstalled(it.name) }?.name
    }

    fun dirFor(id: String): File = File(linuxDir, id)

    fun isInstalled(id: String): Boolean {
        val d = dirFor(id)
        return d.isDirectory && (File(d, "rootfs.img").exists() ||
            File(d, "kernel").exists() ||
            File(d, "rootfs").isDirectory)
    }

    private fun stateFor(id: String): DistroInstallState =
        DistroInstallState(installed = isInstalled(id))

    /** Busca o rootfs (imagem) da distro ativa para o QEMU. */
    fun activeRootfsImage(): File? {
        val id = activeId ?: return null
        val d = dirFor(id)
        return when {
            File(d, "rootfs.img").exists() -> File(d, "rootfs.img")
            File(d, "kernel").exists() -> null // boot por kernel (imagem opcional)
            else -> null
        }
    }

    fun activeKernel(): File? = activeId?.let { id -> File(dirFor(id), "kernel").takeIf { it.exists() } }
    fun activeInitrd(): File? = activeId?.let { id -> File(dirFor(id), "initrd.img").takeIf { it.exists() } }

    /** Baixa, valida (SHA-256) e instala a distro em background. */
    fun install(info: DistroInfo) {
        if (installStates[info.id]?.downloading == true) return
        installStates[info.id] = DistroInstallState(downloading = true)
        scope.launch {
            val result = runCatching { downloadAndInstall(info) }
            val err = result.exceptionOrNull()
            withContext(Dispatchers.Main) {
                val current = installStates[info.id] ?: DistroInstallState()
                if (err != null) {
                    installStates[info.id] = current.copy(
                        downloading = false,
                        error = err.message ?: "Falha no download",
                    )
                } else {
                    installStates[info.id] = current.copy(
                        downloading = false,
                        progress = 1f,
                        installed = true,
                        message = "Instalada",
                    )
                    activeId = info.id
                }
            }
        }
    }

    fun setActive(info: DistroInfo) {
        if (isInstalled(info.id)) activeId = info.id
    }

    private suspend fun downloadAndInstall(info: DistroInfo): Boolean = withContext(Dispatchers.IO) {
        val targetDir = dirFor(info.id).apply { mkdirs() }
        // Formato detectado pelo nome real do artefato na URL
        val artifactName = info.url.substringAfterLast('/').lowercase()
        val tmp = File(targetDir, "artifact.tmp")

        // Download com progresso
        val conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Phantom-Code/0.1.0")
        }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode} — artefato não publicado ainda")
        }
        val total = conn.contentLengthLong
        val digest = info.sha256?.let { MessageDigest.getInstance("SHA-256") }
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    digest?.update(buf, 0, read)
                    if (total > 0) {
                        val p = done.toFloat() / total.toFloat()
                        installStates[info.id] = (installStates[info.id] ?: DistroInstallState()).copy(progress = p)
                    }
                }
            }
        }
        if (digest != null) {
            val got = digest.digest().joinToString("") { "%02x".format(it) }
            check(got.equals(info.sha256, ignoreCase = true)) { "SHA-256 inválido" }
        }

        // Instala: extrai tarball ou move imagem (pelo nome real do artefato)
        when {
            artifactName.endsWith(".tar.gz") || artifactName.endsWith(".tgz") -> extractTarGz(tmp, targetDir)
            artifactName.endsWith(".gz") -> extractGz(tmp, File(targetDir, "rootfs.img"))
            artifactName.endsWith(".img") || artifactName.endsWith(".ext4") || artifactName.endsWith(".qcow2") ->
                tmp.renameTo(File(targetDir, "rootfs.img"))
            else -> {
                // assume tarball; tenta extrair
                extractTarGz(tmp, targetDir)
            }
        }
        tmp.delete()
        copyInitScript(targetDir)
        true
    }

    /** Copia o dark-code-init.sh (T18) para dentro da distro instalada. */
    private fun copyInitScript(targetDir: File) {
        runCatching {
            appContext.assets.open("linux/dark-code-init.sh").use { input ->
                File(targetDir, "dark-code-init.sh").outputStream().use { out -> input.copyTo(out) }
            }
            File(targetDir, "dark-code-init.sh").setExecutable(true)
        }
    }

    private fun extractTarGz(archive: File, target: File) {
        GZIPInputStream(archive.inputStream()).use { gz ->
            // Extração simples de tarball (sem entrada tar — usamos a estrutura
            // direta: kernel/, rootfs.img no topo). Para tarballs padrão
            // (com pastas), o app preserva a hierarquia:
            TarExtractor.extract(gz, target)
        }
    }

    private fun extractGz(file: File, dest: File) {
        GZIPInputStream(file.inputStream()).use { gz -> dest.outputStream().use { it.write(gz.readBytes()) } }
    }
}

/** Extrator mínimo de tarball (formato ustar) — sem APIs Java 9+. */
object TarExtractor {
    fun extract(input: java.io.InputStream, dest: File) {
        dest.mkdirs()
        val header = ByteArray(512)
        val data = ByteArray(512)
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break
            val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000')
            if (name.isEmpty()) break
            val size = String(header, 124, 12, Charsets.UTF_8)
                .trimEnd('\u0000', ' ')
                .toLongOrNull() ?: 0L
            val type = header[156].toInt().toChar()
            val target = File(dest, name.trimStart('.', '/'))
            when {
                type == '5' || name.endsWith("/") -> target.mkdirs()
                else -> {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        var remaining = size
                        while (remaining > 0) {
                            val n = input.read(data, 0, minOf(data.size.toLong(), remaining).toInt())
                            if (n <= 0) break
                            out.write(data, 0, n)
                            remaining -= n
                        }
                    }
                }
            }
            // padding para alinhamento de 512 bytes
            val pad = ((512 - (size % 512)).toInt()) % 512
            var skipped = 0
            while (skipped < pad) {
                val n = input.read(data, 0, minOf(pad - skipped, data.size))
                if (n <= 0) break
                skipped += n
            }
        }
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off > 0
            off += n
        }
        return true
    }
}
