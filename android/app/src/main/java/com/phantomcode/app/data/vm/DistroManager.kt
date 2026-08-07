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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Nível de risco/peso de uma distro (lentidão, armazenamento) — mostrado no card. */
enum class DistroRisk(val label: String) {
    LOW("Leve"),
    MEDIUM("Moderada"),
    HIGH("Pesada"),
}

/**
 * Distro do catálogo (D1 — o usuário escolhe; Phantom é a oficial).
 *
 * Todas rodam headless (modo terminal apenas) — sem área gráfica.
 */
data class DistroInfo(
    val id: String,
    val name: String,
    val badge: String? = null,          // "Oficial · Recomendada" para a Phantom
    val description: String,            // o que é, em 1-2 linhas
    val recommendedFor: String,         // para quem é indicada
    val url: String,
    val sha256: String? = null,
    val sizeMb: Int,                    // download (~)
    val installSizeMb: Int,             // espaço em disco instalada (~)
    val ramMb: Int,                     // RAM mínima recomendada
    val risk: DistroRisk,               // nível de lentidão/peso
    val available: Boolean = true,      // false = "Em breve" (artefato não publicado)
    val packageManager: String,         // apt / apk
    val headless: Boolean = true,       // sempre true — terminal apenas
)

object DistroCatalog {
    val ALL: List<DistroInfo> = listOf(
        DistroInfo(
            id = "phantom",
            name = "Phantom",
            badge = "Oficial",
            description = "Nossa distro oficial, feita sob medida para o app: Debian bookworm arm64 com python3 e git. Configurada automaticamente pelo app.",
            recommendedFor = "Uso geral — o padrão do Phantom-Code",
            url = PhantomMirror.PHANTOM_URL,
            sha256 = PhantomMirror.PHANTOM_SHA256,
            sizeMb = 500,
            installSizeMb = 2048,
            ramMb = 1024,
            risk = DistroRisk.LOW,
            available = true,
            packageManager = "apt",
        ),
        DistroInfo(
            id = "ubuntu",
            name = "Ubuntu 24.04 minimal",
            badge = "Recomendada · padrão",
            description = "Ubuntu LTS 24.04 em versão mínima (arm64). Máxima compatibilidade com tutoriais e pacotes do ecossistema.",
            recommendedFor = "Quem já conhece Ubuntu e quer um uso geral sólido",
            url = PhantomMirror.UBUNTU_URL,
            sizeMb = 400,
            installSizeMb = 1600,
            ramMb = 1024,
            risk = DistroRisk.LOW,
            available = true,
            packageManager = "apt",
        ),
        DistroInfo(
            id = "debian",
            name = "Debian bookworm slim",
            description = "Debian 12 em versão enxuta (arm64). Muito estável e leve — base do Ubuntu e da própria Phantom.",
            recommendedFor = "Servidores de dev, builds e quem quer estabilidade máxima",
            url = PhantomMirror.DEBIAN_URL,
            sizeMb = 300,
            installSizeMb = 1200,
            ramMb = 512,
            risk = DistroRisk.LOW,
            available = false, // ⚠️ artefato não publicado — "Em breve"
            packageManager = "apt",
        ),
        DistroInfo(
            id = "alpine",
            name = "Alpine mini",
            description = "Distro ultra-leve (musl/busybox), ~20 MB de download. Consome pouquíssimo disco e RAM.",
            recommendedFor = "Aparelhos fracos e quem quer o mínimo possível",
            url = PhantomMirror.ALPINE_URL,
            sizeMb = 20,
            installSizeMb = 200,
            ramMb = 256,
            risk = DistroRisk.LOW,
            available = false, // ⚠️ artefato não publicado — "Em breve"
            packageManager = "apk",
        ),
        DistroInfo(
            id = "kali",
            name = "Kali Linux arm64",
            badge = "Pentest",
            description = "Distro de segurança ofensiva (base Debian) com centenas de ferramentas de pentest pré-instaladas: Nmap, Metasploit, Wireshark, sqlmap e mais.",
            recommendedFor = "Estudos e labs de segurança — usuários avançados",
            url = PhantomMirror.KALI_URL,
            sizeMb = 2500,
            installSizeMb = 8000,
            ramMb = 4096,
            risk = DistroRisk.HIGH,
            available = false, // ⚠️ imagem grande — "Em breve"
            packageManager = "apt",
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
/** Configurações iniciais escolhidas pelo usuário antes da instalação automática. */
data class DistroConfig(
    val hostname: String = "phantom",
    val user: String = "user",
    val diskSizeMb: Int = QemuPrefs.DEFAULT_DISK_MB,
)

class DistroManager(context: Context) {

    private val appContext: Context = context.applicationContext
    val linuxDir: File = File(context.filesDir, "linux").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO)

    val installStates = mutableStateMapOf<String, DistroInstallState>()

    var activeId by mutableStateOf<String?>(null)
        private set

    init {
        migrateLegacyPhantom()
        // Detecta distros já instaladas
        DistroCatalog.ALL.forEach { info ->
            installStates[info.id] = stateFor(info.id)
        }
        activeId = linuxDir.listFiles()?.firstOrNull { it.isDirectory && isInstalled(it.name) }?.name
    }

    /** Renomeia a instalação antiga quando o ID oficial ainda era phantom-base. */
    private fun migrateLegacyPhantom() {
        val old = File(linuxDir, "phantom-base")
        val current = File(linuxDir, "phantom")
        if (old.isDirectory && !current.exists()) {
            runCatching { old.renameTo(current) }
        }
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
    fun activeQemu(): File? = activeId?.let { id -> File(dirFor(id), "qemu-system-aarch64").takeIf { it.exists() } }

    /** Baixa, valida (SHA-256) e instala a distro em background. */
    fun install(info: DistroInfo) {
        install(info, DistroConfig(), null)
    }

    /**
     * Baixa e instala com as configurações do usuário ([DistroConfig]).
     *
     * O progresso é acompanhado em tempo real pelo terminal (aba de log) quando
     * [logSession] é informado — a instalação vira um processo visível, igual
     * ao console da VM.
     */
    fun install(
        info: DistroInfo,
        config: DistroConfig,
        logSession: LogTermSession?,
    ) {
        if (installStates[info.id]?.downloading == true) return
        installStates[info.id] = DistroInstallState(downloading = true)
        scope.launch {
            val result = runCatching { downloadAndInstall(info, config, logSession) }
            val err = result.exceptionOrNull()
            withContext(Dispatchers.Main) {
                val current = installStates[info.id] ?: DistroInstallState()
                if (err != null) {
                    installStates[info.id] = current.copy(
                        downloading = false,
                        error = err.message ?: "Falha no download",
                    )
                    logSession?.append("\n\u001b[31m✗ ${err.message ?: "Falha na instalação"}\u001b[0m\n")
                } else {
                    installStates[info.id] = current.copy(
                        downloading = false,
                        progress = 1f,
                        installed = true,
                        message = "Instalada",
                    )
                    activeId = info.id
                    logSession?.append("\n\u001b[32m✓ Distro instalada e configurada.\u001b[0m\n")
                }
            }
        }
    }

    fun setActive(info: DistroInfo) {
        if (isInstalled(info.id)) activeId = info.id
    }

    private suspend fun downloadAndInstall(
        info: DistroInfo,
        config: DistroConfig,
        logSession: LogTermSession?,
    ): Boolean = withContext(Dispatchers.IO) {
        fun log(msg: String) = logSession?.append(msg)
        val targetDir = dirFor(info.id).apply { mkdirs() }
        // Formato detectado pelo nome real do artefato na URL
        val artifactName = info.url.substringAfterLast('/').lowercase()
        val tmp = File(targetDir, "artifact.tmp")

        log("Instalando ${info.name}…\n")
        log("[phantom] hostname: ${config.hostname} · user: ${config.user}\n")

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
                var lastPct = -1
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    done += read
                    digest?.update(buf, 0, read)
                    if (total > 0) {
                        val p = done.toFloat() / total.toFloat()
                        val pct = (p * 100).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            log("\rBaixando… $pct% (${done / (1024 * 1024)} MB / ${total / (1024 * 1024)} MB)")
                            installStates[info.id] = (installStates[info.id] ?: DistroInstallState()).copy(progress = p)
                        }
                    }
                }
            }
        }
        log("\n")
        if (digest != null) {
            val got = digest.digest().joinToString("") { "%02x".format(it) }
            check(got.equals(info.sha256, ignoreCase = true)) { "SHA-256 inválido" }
            log("[phantom] SHA-256 ok ✓\n")
        }

        // Instala: extrai tarball ou move imagem (pelo nome real do artefato)
        log("[phantom] extraindo arquivos…\n")
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
        applyDiskSize(targetDir, config)
        writeConfig(targetDir, config)
        copyInitScript(targetDir)
        log("[phantom] pronto.\n")
        true
    }

    /** Expande o rootfs.img para o tamanho escolhido (padrão 3 GB) via setLength. */
    private fun applyDiskSize(targetDir: File, config: DistroConfig) {
        val img = File(targetDir, "rootfs.img")
        if (!img.exists() || img.length() >= config.diskSizeMb.toLong() * 1024 * 1024) return
        runCatching {
            RandomAccessFile(img, "rw").use { it.setLength(config.diskSizeMb.toLong() * 1024 * 1024) }
        }
    }

    /** Grava dark-code.conf (hostname/user) — lido pelo dark-code-init.sh no boot. */
    private fun writeConfig(targetDir: File, config: DistroConfig) {
        runCatching {
            File(targetDir, "dark-code.conf").writeText(
                "HOSTNAME=${config.hostname}\nUSER=${config.user}\n",
            )
        }
    }

    /** Copia o dark-code-init.sh (T18) e o phantom-agent.sh (T20) para dentro da distro instalada. */
    private fun copyInitScript(targetDir: File) {
        copyAssetToRoot("linux/dark-code-init.sh", "dark-code-init.sh", targetDir)
        copyAssetToRoot("linux/phantom-agent.sh", "phantom-agent.sh", targetDir)
    }

    private fun copyAssetToRoot(asset: String, name: String, targetDir: File) {
        runCatching {
            appContext.assets.open(asset).use { input ->
                File(targetDir, name).outputStream().use { out -> input.copyTo(out) }
            }
            File(targetDir, name).setExecutable(true)
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
