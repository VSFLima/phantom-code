package com.phantomcode.app.data.vm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

/** Como o QEMU bota a distro — usado na validação por tipo (M1). */
enum class DistroBoot(val label: String) {
    /** Kernel + initrd explícitos (arquivos `kernel`, `initrd.img` opcional e `rootfs.img`). */
    KERNEL_INITRD("kernel + initrd + rootfs"),
    /** Apenas imagem crua `rootfs.img` (precisa de kernel pareado p/ bootar em virt). */
    ROOTFS_ONLY("rootfs.img"),
}

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
    val available: Boolean = true,
    val packageManager: String,         // apt / apk
    val headless: Boolean = true,       // sempre true — terminal apenas
    val includesQemu: Boolean = false,  // true: o pacote já traz o qemu-system-aarch64 (ex.: Phantom)
    val boot: DistroBoot = DistroBoot.KERNEL_INITRD, // como o QEMU bota esta distro (M1)
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
            sizeMb = 356,
            installSizeMb = 2048,
            ramMb = 1024,
            risk = DistroRisk.LOW,
            available = true,
            packageManager = "apt",
            includesQemu = true, // o tarball Phantom traz rootfs.img + kernel + initrd.img + qemu-system-aarch64
            boot = DistroBoot.KERNEL_INITRD,
        ),
        DistroInfo(
            id = "ubuntu",
            name = "Ubuntu",
            badge = "Nova",
            description = "Ubuntu 24.04 (Noble) arm64 — o clássico da Canonical: completo, estável e com enorme compatibilidade com tutoriais.",
            recommendedFor = "Compatibilidade máxima com pacotes .deb e guias da comunidade",
            url = PhantomMirror.UBUNTU_URL,
            sha256 = PhantomMirror.UBUNTU_SHA256.ifBlank { null }, // preenchido após o build (T29)
            sizeMb = 350,
            installSizeMb = 1536,
            ramMb = 1024,
            risk = DistroRisk.MEDIUM,
            available = true,
            packageManager = "apt",
            includesQemu = true, // tarball autocontido (rootfs+kernel+initrd+qemu)
            boot = DistroBoot.KERNEL_INITRD,
        ),
        DistroInfo(
            id = "debian",
            name = "Debian",
            description = "Debian bookworm arm64 — a base sólida e minimalista que dá origem a tantas outras distros.",
            recommendedFor = "Estabilidade e simplicidade no dia a dia",
            url = PhantomMirror.DEBIAN_URL,
            sha256 = PhantomMirror.DEBIAN_SHA256.ifBlank { null }, // preenchido após o build (T29)
            sizeMb = 300,
            installSizeMb = 1280,
            ramMb = 1024,
            risk = DistroRisk.MEDIUM,
            available = true,
            packageManager = "apt",
            includesQemu = true,
            boot = DistroBoot.KERNEL_INITRD,
        ),
        DistroInfo(
            id = "kali",
            name = "Kali",
            badge = "Avançada",
            description = "Kali Linux rolling arm64 — a distro de segurança e testes de intrusão (muita ferramenta instalada).",
            recommendedFor = "Auditoria, pentest e estudo de segurança",
            url = PhantomMirror.KALI_URL,
            sha256 = PhantomMirror.KALI_SHA256.ifBlank { null }, // preenchido após o build (T29)
            sizeMb = 450,
            installSizeMb = 2048,
            ramMb = 1536,
            risk = DistroRisk.HIGH,
            available = true,
            packageManager = "apt",
            includesQemu = true,
            boot = DistroBoot.KERNEL_INITRD,
        ),
        DistroInfo(
            id = "alpine",
            name = "Alpine",
            badge = "Leve",
            description = "Alpine Linux 3.20 arm64 — minúscula e veloz, ideal para quem quer economia de espaço e RAM.",
            recommendedFor = "Máquinas modestas e tarefas leves",
            url = PhantomMirror.ALPINE_URL,
            sha256 = PhantomMirror.ALPINE_SHA256.ifBlank { null }, // preenchido após o build (T29)
            sizeMb = 150,
            installSizeMb = 512,
            ramMb = 512,
            risk = DistroRisk.LOW,
            available = true,
            packageManager = "apk",
            includesQemu = true,
            boot = DistroBoot.KERNEL_INITRD,
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
    val presetId: String = QemuPresets.BALANCED.id,
    val cores: Int = QemuPresets.BALANCED.cpu,
    val ramMb: Int = QemuPresets.BALANCED.ramMb,
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

    /**
     * Instalação VÁLIDA = todos os artefatos exigidos pelo TIPO de boot da
     * distro existem E, quando ela traz o QEMU embutido (`includesQemu`), o
     * binário também está lá.
     *
     * ANTES bastava `rootfs.img` OU `kernel` — uma instalação parcial/legada
     * (ex.: só `rootfs.img` deixado por um APK antigo com extração corrompida)
     * aparecia como "Instalada" e o QEMU nunca subia (falso positivo). Agora só
     * conta como instalada se for possível BOOTAR, alinhado com [QemuManager.start].
     */
    fun isInstalled(id: String): Boolean {
        val info = DistroCatalog.ALL.firstOrNull { it.id == id } ?: return false
        val d = dirFor(id)
        if (!d.isDirectory) return false
        val rootfs = File(d, "rootfs.img").exists() && File(d, "rootfs.img").length() > 0L
        val kernel = File(d, "kernel").exists() && File(d, "kernel").length() > 0L
        val initrd = File(d, "initrd.img").exists() && File(d, "initrd.img").length() > 0L
        val bootOk = when (info.boot) {
            DistroBoot.KERNEL_INITRD -> kernel && (rootfs || initrd)
            DistroBoot.ROOTFS_ONLY -> rootfs && kernel // o QEMU virt exige kernel pareado
        }
        val qemuOk = if (info.includesQemu) {
            val q = File(d, "qemu-system-aarch64")
            q.exists() && q.length() > 1_000_000L
        } else true
        return bootOk && qemuOk
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

    /** Registro do catálogo da distro ativa (null se não houver ou for instalação legada). */
    fun activeInfo(): DistroInfo? = activeId?.let { id -> DistroCatalog.ALL.firstOrNull { it.id == id } }

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
            completeInstall(info, result, logSession)
        }
    }

    /**
     * Instala a distro a partir de arquivos LOCAIS selecionados pelo usuário
     * (SAF — T-S1/T-S2). O seletor pode devolver:
     *   · um tarball `phantom.tar.gz` (como o baixado do mirror), OU
     *   · os arquivos avulsos: rootfs.img, kernel, initrd.img, qemu-system-aarch64.
     * Cada arquivo é copiado para o diretório da distro e validado antes de
     * configurar (mesmo fluxo da instalação por download — [finalizeInstall]).
     */
    fun installFromUris(
        info: DistroInfo,
        uris: List<Uri>,
        config: DistroConfig,
        logSession: LogTermSession?,
    ) {
        if (uris.isEmpty()) return
        if (installStates[info.id]?.downloading == true) return
        installStates[info.id] = DistroInstallState(downloading = true)
        scope.launch {
            val result = runCatching { installLocalAndFinalize(info, config, uris, logSession) }
            completeInstall(info, result, logSession)
        }
    }

    /** Estado final comum a qualquer instalação (download ou arquivos locais). */
    private suspend fun completeInstall(
        info: DistroInfo,
        result: Result<Boolean>,
        logSession: LogTermSession?,
    ) {
        val err = result.exceptionOrNull()
        withContext(Dispatchers.Main) {
            val current = installStates[info.id] ?: DistroInstallState()
            if (err != null) {
                File(dirFor(info.id), "artifact.tmp").delete()
                // Após uma falha, remove os artefatos PARCIAIS que a extração
                // pode ter deixado (ex.: só rootfs.img). Sem isso o diretório
                // continuaria existindo e a distro apareceria como "Instalada"
                // no próximo boot (falso positivo) sem dar pra bootar.
                runCatching { cleanArtifacts(dirFor(info.id)) }
                QemuManager.instance?.refreshBinary()
                installStates[info.id] = current.copy(
                    downloading = false,
                    error = err.message ?: "Falha no download",
                )
                logSession?.append("\n\u001b[31m✗ ${err.message ?: "Falha na instalação"}\u001b[0m\n")
                logSession?.setProgress(null, "Falha na instalação")
            } else {
                installStates[info.id] = current.copy(
                    downloading = false,
                    progress = 1f,
                    installed = true,
                    message = "Instalada",
                )
                activeId = info.id
                // A distro traz o qemu-system-aarch64 — informa o motor QEMU
                // que o binário embutido já está disponível (binaryReady=true).
                QemuManager.instance?.refreshBinary()
                logSession?.append("\n\u001b[32m✓ Distro instalada e configurada.\u001b[0m\n")
            }
        }
    }

    fun setActive(info: DistroInfo) {
        if (isInstalled(info.id)) activeId = info.id
    }

    /** Remove a distro do dispositivo (arquivos + estado) — permite reinstalar. */
    fun uninstall(id: String) {
        val d = dirFor(id)
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { d.deleteRecursively() }
            }
            if (activeId == id) {
                activeId = DistroCatalog.ALL.firstOrNull { it.id != id && isInstalled(it.id) }?.id
            }
            installStates[id] = DistroInstallState()
            QemuManager.instance?.refreshBinary()
        }
    }

    /** Atualiza hostname/usuário da distro sem baixar ou reinstalar arquivos. */
    fun configure(info: DistroInfo, config: DistroConfig) {
        if (isInstalled(info.id)) writeConfig(dirFor(info.id), config)
    }

    private suspend fun downloadAndInstall(
        info: DistroInfo,
        config: DistroConfig,
        logSession: LogTermSession?,
    ): Boolean = withContext(Dispatchers.IO) {
        fun log(msg: String) = logSession?.append(msg)
        val targetDir = dirFor(info.id).apply { mkdirs() }
        // Limpa artefatos de instalações parciais/falhas ANTIGAS (ex.: só
        // rootfs.img de um download que morreu) — evita mistura com os novos
        // arquivos e libera espaço antes do download.
        runCatching { cleanArtifacts(targetDir) }
        // Formato detectado pelo nome real do artefato na URL
        val artifactName = info.url.substringAfterLast('/').lowercase()
        val tmp = File(targetDir, "artifact.tmp")

        log("Instalando ${info.name}…\n")
        log("[phantom] hostname: ${config.hostname} · user: ${config.user}\n")
        logSession?.setProgress(0f, "Baixando ${info.name}…")

        // Download com progresso
        val conn = downloadConnection(info)
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
                            withContext(Dispatchers.Main) {
                                installStates[info.id] = (installStates[info.id] ?: DistroInstallState()).copy(progress = p)
                            }
                            logSession?.setProgress(p, "Baixando ${info.name}… $pct%")
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
        logSession?.setProgress(null, "Verificando SHA-256…")

        // Instala: extrai tarball ou move imagem (pelo nome real do artefato)
        log("[phantom] extraindo arquivos…\n")
        logSession?.setProgress(null, "Extraindo arquivos…")
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
        // ✅ Pós-extração compartilhado (download e arquivos locais):
        // validação por tipo de boot + config + init do guest + disco.
        finalizeInstall(targetDir, info, config, logSession)
        log("[phantom] pronto.\n")
        logSession?.setProgress(1f, "Instalação concluída ✓")
        true
    }

    /**
     * Pós-extração comum a download (nuvem) e arquivos locais (SAF):
     * valida os artefatos exigidos pelo tipo de boot, configura a distro
     * (hostname/user/disco) e prepara a pasta `share` com o init do guest.
     */
    private fun finalizeInstall(
        targetDir: File,
        info: DistroInfo,
        config: DistroConfig,
        logSession: LogTermSession?,
    ) {
        // ✅ VALIDAÇÃO pós-extração POR TIPO DE BOOT (M1): cada tipo exige os seus
        // arquivos. Antes bastava rootfs OU kernel — uma distro rootfs-only instalava
        // e o QEMU subia sem kernel (tela morta em silêncio).
        val rootfsFile = File(targetDir, "rootfs.img")
        val kernelFile = File(targetDir, "kernel")
        val initrdFile = File(targetDir, "initrd.img")
        val hasRootfs = rootfsFile.exists() && rootfsFile.length() > 0L
        val missing = when (info.boot) {
            DistroBoot.KERNEL_INITRD -> listOfNotNull(
                // Alinhado com o start(): kernel + (rootfs.img OU initrd.img) — initramfs bota sem rootfs.img.
                "rootfs.img/initrd.img".takeUnless { hasRootfs || (initrdFile.exists() && initrdFile.length() > 0L) },
                "kernel".takeUnless { kernelFile.exists() && kernelFile.length() > 0L },
            )
            DistroBoot.ROOTFS_ONLY -> listOfNotNull(
                "rootfs.img".takeUnless { hasRootfs },
            )
        }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "Extração incompleta (tipo ${info.boot.label}) — faltou: ${missing.joinToString(", ")}. Reinstale a distro.",
            )
        }
        if (info.includesQemu && (!File(targetDir, "qemu-system-aarch64").exists() ||
                File(targetDir, "qemu-system-aarch64").length() < 1_000_000L)
        ) {
            throw IllegalStateException(
                "O QEMU embutido não veio no pacote da distro — extração incompleta. Reinstale a Phantom.",
            )
        }
        applyDiskSize(targetDir, config)
        writeConfig(targetDir, config)
        copyInitScript(targetDir)
        // Defensivo: garante +x no QEMU embutido (o extrator preserva o bit do
        // cabeçalho tar, mas alguns arquivos/APKs antigos podem vir sem ele).
        runCatching { File(targetDir, "qemu-system-aarch64").takeIf { it.exists() }?.setExecutable(true) }
    }

    /** Remove artefatos de instalação anterior (parcial/falha) do diretório da distro. */
    private fun cleanArtifacts(targetDir: File) {
        listOf(
            "rootfs.img", "kernel", "initrd.img", "qemu-system-aarch64",
            "dark-code.conf", "dark-code-init.sh", "phantom-agent.sh",
        ).forEach { n -> File(targetDir, n).delete() }
        File(targetDir, "rootfs").deleteRecursively()
        File(targetDir, "share").deleteRecursively()
    }

    /**
     * Instala a partir de arquivos locais (SAF): categoriza cada URI pelo nome,
     * copia para o diretório da distro e valida a integridade dos artefatos
     * (T-S4) antes de configurar.
     */
    private suspend fun installLocalAndFinalize(
        info: DistroInfo,
        config: DistroConfig,
        uris: List<Uri>,
        logSession: LogTermSession?,
    ): Boolean = withContext(Dispatchers.IO) {
        fun log(msg: String) = logSession?.append(msg)
        val targetDir = dirFor(info.id).apply { mkdirs() }
        runCatching { cleanArtifacts(targetDir) }

        // 1) Categoriza os arquivos selecionados pelo nome do documento
        val picked = uris.mapNotNull { uri ->
            val name = displayName(uri)?.lowercase()
            val kind = when {
                name == null -> null
                name.endsWith(".tar.gz") || name.endsWith(".tgz") -> "tarball"
                name == "initrd.img" || name.contains("initrd") -> "initrd"
                name.endsWith(".qcow2") -> "qcow2"
                name.endsWith(".img") || name.endsWith(".ext4") -> "rootfs"
                name == "kernel" || name.contains("vmlinuz") || name.contains("image") -> "kernel"
                name.contains("qemu") && name.contains("aarch64") -> "qemu"
                else -> null
            }
            kind?.let { it to uri }
        }
        if (picked.isEmpty()) {
            error("Nenhum arquivo reconhecido. Selecione o pacote phantom.tar.gz ou os arquivos avulsos (rootfs.img, kernel, initrd.img, qemu-system-aarch64).")
        }
        picked.forEach { (kind, uri) ->
            log("[phantom] selecionado: ${displayName(uri) ?: uri} → $kind\n")
        }

        // 2) Copia (tarball extrai; avulsos vão direto para os nomes canônicos)
        logSession?.setProgress(0f, "Copiando arquivos…")
        picked.forEach { (kind, uri) ->
            val dest = when (kind) {
                "tarball" -> File(targetDir, "artifact.tmp")
                "initrd" -> File(targetDir, "initrd.img")
                "rootfs" -> File(targetDir, "rootfs.img")
                "qcow2" -> error("Imagens QCOW2 ainda não são suportadas pelo boot raw do QEMU; use rootfs.img/ext4")
                "kernel" -> File(targetDir, "kernel")
                "qemu" -> File(targetDir, "qemu-system-aarch64")
                else -> return@forEach
            }
            copyUri(uri, dest)
            if (kind == "qemu") runCatching { dest.setExecutable(true) }
        }
        val tarball = File(targetDir, "artifact.tmp")
        if (tarball.exists() && tarball.length() > 0L) {
            log("[phantom] extraindo tarball local…\n")
            logSession?.setProgress(null, "Extraindo tarball…")
            extractTarGz(tarball, targetDir)
            tarball.delete()
        }

        // 3) Valida integridade dos artefatos locais (T-S4)
        log("[phantom] validando arquivos…\n")
        logSession?.setProgress(null, "Validando integridade…")
        validateLocalArtifacts(targetDir)

        // 4) Fluxo comum (config, init do guest, disco)
        finalizeInstall(targetDir, info, config, logSession)
        log("[phantom] pronto.\n")
        logSession?.setProgress(1f, "Instalação concluída ✓")
        true
    }

    /** Nome exibido de uma URI do SAF (OpenableColumns.DISPLAY_NAME). */
    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME)) else null
        }
    }.getOrNull()

    /** Copia o conteúdo de uma URI de conteúdo do SAF para um arquivo local. */
    private fun copyUri(uri: Uri, dest: File) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir ${displayName(uri) ?: uri}")
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
    }

    /**
     * Validação de integridade de arquivos locais (T-S4): assinatura ext2 do
     * rootfs, magic da Image arm64 (ou ELF/gzip) do kernel e tamanho mínimo do
     * QEMU. Best-effort — o fluxo de boot em si é validado depois em
     * [finalizeInstall] (arquivos exigidos pelo tipo da distro).
     */
    private fun validateLocalArtifacts(targetDir: File) {
        val rootfs = File(targetDir, "rootfs.img")
        if (rootfs.exists()) {
            val magic = runCatching {
                RandomAccessFile(rootfs, "r").use { raf ->
                    raf.seek(0x438) // superblock ext (offset 1024) + s_magic (0x38)
                    raf.readShort()
                }
            }.getOrDefault(0)
            check(magic.toInt() and 0xFFFF == 0xEF53) {
                "rootfs.img não é uma imagem ext2/3/4 (assinatura inválida) — arquivo corrompido ou incompleto"
            }
        }
        val kernel = File(targetDir, "kernel")
        if (kernel.exists()) {
            val head = ByteArray(0x40)
            val n = kernel.inputStream().use { it.read(head) }
            val arm64 = n >= 0x40 &&
                head[0x38] == 'A'.code.toByte() && head[0x39] == 'R'.code.toByte() &&
                head[0x3A] == 'M'.code.toByte() && head[0x3B] == 'x'.code.toByte()
            val gz = n >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()
            val elf = n >= 4 && head[0] == 0x7F.toByte() && head[1] == 'E'.code.toByte() &&
                head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
            check(arm64 || gz || elf) {
                "kernel não parece ser uma Image arm64 (magic inválida) — arquivo corrompido ou de outra arquitetura"
            }
        }
        val qemu = File(targetDir, "qemu-system-aarch64")
        if (qemu.exists()) {
            check(qemu.length() > 1_000_000L) { "qemu-system-aarch64 muito pequeno — arquivo corrompido" }
        }
    }

    private fun downloadConnection(info: DistroInfo): HttpURLConnection {
        val connection = java.net.URL(info.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("User-Agent", "Phantom-Code-Android")
        return connection
    }

    /** Expande o rootfs.img para o tamanho escolhido (padrão 3 GB) via setLength. */
    private fun applyDiskSize(targetDir: File, config: DistroConfig) {
        val img = File(targetDir, "rootfs.img")
        if (!img.exists() || img.length() >= config.diskSizeMb.toLong() * 1024 * 1024) return
        runCatching {
            RandomAccessFile(img, "rw").use { it.setLength(config.diskSizeMb.toLong() * 1024 * 1024) }
        }
    }

    /** Grava dark-code.conf (hostname/user) — lido pelo dark-code-init.sh no boot.
     *  Escreve na raiz (compat) e na pasta `share` (T-D4 — a pasta exposta ao
     *  guest via 9p, onde o dark-code-init.sh realmente lê o arquivo). */
    private fun writeConfig(targetDir: File, config: DistroConfig) {
        val content = "HOSTNAME=${config.hostname}\nUSER=${config.user}\n"
        runCatching { File(targetDir, "dark-code.conf").writeText(content) }
        runCatching { File(shareDir(targetDir), "dark-code.conf").writeText(content) }
    }

    /** Pasta `share` — copiada para o guest no boot (9p darkcode-distro, T-D4).
     *  Contém o dark-code-init.sh + phantom-agent.sh + dark-code.conf. Só essa
     *  subpasta é exposta ao QEMU (o rootfs.img/ROMs ficam fora do alcance). */
    private fun shareDir(targetDir: File): File = File(targetDir, "share").apply { mkdirs() }

    /** Copia o dark-code-init.sh (T18) e o phantom-agent.sh (T20) para a pasta
     *  `share` da distro — o guest os executa no boot (T-D4). */
    private fun copyInitScript(targetDir: File) {
        val share = shareDir(targetDir)
        copyAssetToRoot("linux/dark-code-init.sh", "dark-code-init.sh", share)
        copyAssetToRoot("linux/phantom-agent.sh", "phantom-agent.sh", share)
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
        val root = dest.canonicalFile
        val header = ByteArray(512)
        val data = ByteArray(512)
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break
            val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000')
            if (name.isEmpty()) break
            // ⚠️ O campo size do header tar é OCTAL (base 8), não decimal:
            // "00000000144" octal = 100 bytes. Parsear como decimal (toLongOrNull()
            // sem radix) superestima o tamanho e faz o extrator engolir os
            // headers dos arquivos seguintes como dados — kernel/initrd/qemu
            // eram perdidos e a distro instalava corrompida.
            val size = String(header, 124, 12, Charsets.UTF_8)
                .trimEnd('\u0000', ' ')
                .toLongOrNull(8) ?: 0L
            // Modo octal do arquivo (offset 100, 8 bytes) — o qemu embutido na
            // distro precisa do bit de execução para o ProcessBuilder rodar.
            val mode = String(header, 100, 8, Charsets.UTF_8).trimEnd('\u0000', ' ').toIntOrNull(8) ?: 0
            val type = header[156].toInt().toChar()
            val target = File(dest, name.trimStart('.', '/')).canonicalFile
            check(target == root || target.path.startsWith(root.path + File.separator)) {
                "Entrada TAR inválida: $name"
            }
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
                    // Preserva o bit de execução do cabeçalho tar (ex.: o
                    // qemu-system-aarch64 da Phantom — sem +x o QEMU não inicia).
                    if ((mode and 0x40) != 0) runCatching { target.setExecutable(true) }
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
