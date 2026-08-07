package com.phantomcode.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.ai.AiAgent
import com.phantomcode.app.data.ai.AiLock
import com.phantomcode.app.data.ai.AiSuiteManager
import com.phantomcode.app.data.backup.BackupManager
import com.phantomcode.app.data.backup.CloudBackupManager
import com.phantomcode.app.data.secrets.SecretsManager
import com.phantomcode.app.data.vm.DistroCatalog
import com.phantomcode.app.data.vm.DistroInfo
import com.phantomcode.app.data.vm.GuestPackage
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.data.vm.PackageCategory
import com.phantomcode.app.data.vm.QemuManager
import com.phantomcode.app.ui.components.AddSecretKeyDialog
import com.phantomcode.app.ui.components.DistroCard
import com.phantomcode.app.ui.components.DistroConfigDialog
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.SecretKeyCard
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.PhantomPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolboxScreen(
    onOpenTerminal: () -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
) {
    val vm = LocalVm.current
    val palette = LocalThemeController.current.currentPalette()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val qemu = vm.qemu

    // ── Secrets (D8) ──
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val secrets = remember { SecretsManager(context) }
    var keysTick by remember { mutableIntStateOf(0) }
    var addKeyDialog by remember { mutableStateOf(false) }

    // ── Distros (D1) — instalação com config + terminal ──
    var installTarget by remember { mutableStateOf<DistroInfo?>(null) }

    // ── Backup (T21 · D2) ──
    val backup = remember { BackupManager(context) }
    val cloudBackup = remember { CloudBackupManager(context) }
    var cloudBusy by remember { mutableStateOf(false) }
    val aiSuite = remember { AiSuiteManager(context) }
    var backupBusy by remember { mutableStateOf(false) }
    val backupCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            scope.launch {
                val result = backup.createBackup(uri)
                backupBusy = false
                snackbar.showSnackbar("${result.message}${if (result.ok) " · ${result.fileCount} arquivos" else ""}")
            }
        }
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            scope.launch {
                val result = backup.restore(uri)
                withContext(Dispatchers.Main) {
                    backupBusy = false
                    snackbar.showSnackbar("${result.message}${if (result.ok) " · ${result.fileCount} arquivos" else ""}")
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            SectionLabel(text = "Toolbox")
            Spacer(Modifier.height(12.dp))

            // ── Status do ambiente (real) ──────────────────────────
            PhantomCard(glow = qemu.running, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (qemu.running) palette.success else palette.border, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("QEMU LINUX", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            qemu.statusText + " · " + qemu.preset.label,
                            color = if (qemu.running) palette.success else palette.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                        qemu.lastError?.let {
                            Text(it, color = palette.error, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                    if (qemu.running) {
                        PhantomOutlinedButton(text = "Parar", icon = Icons.Filled.Stop, onClick = { qemu.stop() })
                    } else {
                        PhantomPrimaryButton(
                            text = "Iniciar",
                            icon = Icons.Filled.PlayArrow,
                            onClick = {
                                scope.launch {
                                    val ok = qemu.start()
                                    if (!ok) scope.launch { snackbar.showSnackbar(qemu.lastError ?: "Falha ao iniciar") }
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Pacotes do Linux (T20) ───────────────────────────
            GuestPackagesSection(qemu = qemu, onOpenTerminal = onOpenTerminal)

            Spacer(Modifier.height(20.dp))

            // ── AI Suite (Fase A) ────────────────────────────────
            AiSuiteSection(
                aiSuite = aiSuite,
                qemuRunning = qemu.running,
                guestPackages = qemu.scanner.packages,
                onSnack = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
            )

            Spacer(Modifier.height(20.dp))

            // ── Distros (D1) ───────────────────────────────────────
            SectionLabel(text = "Distros")
            Spacer(Modifier.height(4.dp))
            Text(
                "Escolha a sua. Toque no card para ver descrição, consumo e riscos.",
                color = palette.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            DistroCatalog.ALL.forEach { info ->
                DistroCard(
                    info = info,
                    isActive = vm.distros.activeId == info.id,
                    state = vm.distros.installStates[info.id] ?: com.phantomcode.app.data.vm.DistroInstallState(),
                    onClickInstall = { installTarget = info },
                    onClickUse = { vm.distros.setActive(info) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "Integrações & API Keys (D8)")
            Spacer(Modifier.height(4.dp))
            Text(
                "Secrets criptografados no Android Keystore — nunca em texto plano. As marcadas \"Expor ao Linux\" viram variáveis de ambiente na VM.",
                color = palette.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            val keys = remember(keysTick) { secrets.list() }
            if (keys.isEmpty()) {
                PhantomCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Nenhuma chave salva ainda.", color = palette.textSecondary, fontSize = 12.sp)
                    Text("Ex.: GitHub token, OpenAI, Supabase, cloud…", color = palette.border, fontSize = 11.sp)
                }
            } else {
                keys.forEach { entry ->
                    SecretKeyCard(
                        entry = entry,
                        onCopy = {
                            clipboard.setText(AnnotatedString("\$${entry.envVar}"))
                            scope.launch { snackbar.showSnackbar("\$${entry.envVar} copiado") }
                        },
                        onDelete = {
                            secrets.delete(entry.alias)
                            keysTick++
                            scope.launch { snackbar.showSnackbar("Chave '${entry.alias}' revogada") }
                        },
                        onToggleExpose = { expose ->
                            secrets.setExposeToLinux(entry.alias, expose)
                            keysTick++
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            PhantomPrimaryButton(
                text = "Adicionar chave",
                icon = Icons.Filled.Add,
                onClick = { addKeyDialog = true },
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel(text = "Backup (D2)")
            Spacer(Modifier.height(4.dp))
            Text(
                "Salve o workspace em um ZIP. A restauração faz merge — nunca apaga o que já existe.",
                color = palette.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Row {
                    PhantomPrimaryButton(
                        text = "Criar backup",
                        icon = Icons.Filled.Download,
                        enabled = !backupBusy,
                        onClick = { backupCreateLauncher.launch(backup.suggestedFileName()) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PhantomOutlinedButton(
                        text = "Restaurar",
                        icon = Icons.Filled.Restore,
                        enabled = !backupBusy,
                        onClick = { backupRestoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (backupBusy) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.accentPrimary,
                        trackColor = palette.surfaceAlt,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel(text = "Backup na nuvem (WebDAV · T22)")
            Spacer(Modifier.height(4.dp))
            Text(
                "Envie o workspace para seu Nextcloud/ownCloud. Configure WEBDAV_URL, WEBDAV_USER e WEBDAV_PASS nas Integrações acima.",
                color = palette.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (cloudBackup.isConfigured()) palette.success else palette.border, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (cloudBackup.isConfigured())
                            "WebDAV: ${cloudBackup.configSummary()}"
                        else
                            "WebDAV não configurado",
                        color = if (cloudBackup.isConfigured()) palette.textPrimary else palette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    PhantomPrimaryButton(
                        text = "Enviar backup",
                        icon = Icons.Filled.CloudUpload,
                        enabled = !cloudBusy && cloudBackup.isConfigured(),
                        onClick = {
                            cloudBusy = true
                            scope.launch {
                                val result = cloudBackup.upload()
                                cloudBusy = false
                                snackbar.showSnackbar("${result.message}${if (result.ok) " · ${result.fileCount} arquivos" else ""}")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PhantomOutlinedButton(
                        text = "Restaurar",
                        icon = Icons.Filled.CloudDownload,
                        enabled = !cloudBusy && cloudBackup.isConfigured(),
                        onClick = {
                            cloudBusy = true
                            scope.launch {
                                val result = cloudBackup.restoreLatest()
                                cloudBusy = false
                                snackbar.showSnackbar("${result.message}${if (result.ok) " · ${result.fileCount} arquivos" else ""}")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (cloudBusy) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.accentPrimary,
                        trackColor = palette.surfaceAlt,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        if (addKeyDialog) {
            AddSecretKeyDialog(
                onSave = { name, value, category, envVar, expose ->
                    secrets.save(
                        alias = name.replace(' ', '_').lowercase(),
                        value = value,
                        category = category,
                        envVar = envVar.ifBlank { name.replace(' ', '_').uppercase() },
                        exposeToLinux = expose,
                    )
                    addKeyDialog = false
                    keysTick++
                    scope.launch { snackbar.showSnackbar("Chave salva com segurança") }
                },
                onDismiss = { addKeyDialog = false },
                onOpenService = { url ->
                    addKeyDialog = false
                    onOpenBrowser(url)
                },
            )
        }

        installTarget?.let { info ->
            DistroConfigDialog(
                info = info,
                initialDiskMb = qemu.diskSizeMb(),
                onConfirm = { config ->
                    installTarget = null
                    // Abre o terminal com uma aba de log para acompanhar a instalação
                    val logTab = qemu.terminal.addLogTab("Instalando ${info.name}")
                    onOpenTerminal()
                    scope.launch {
                        // T29/T30: o app DETECTA se a distro escolhida já traz o QEMU no
                        // pacote (Phantom) — nesse caso pula a etapa do binário e parte
                        // direto para a instalação. Distros sem QEMU no pacote usam o
                        // binário embutido no APK (extraído na 1ª vez) ou o download.
                        if (info.includesQemu) {
                            logTab.append("[phantom] QEMU já vem no pacote da distro — pulando etapa do binário.\n\n")
                        } else if (!qemu.binaryReady) {
                            logTab.append("[phantom] Preparando o QEMU…\n")
                            var lastPct = -1
                            val ok = qemu.ensureBinary { pct ->
                                val p = (pct * 100).toInt()
                                if (p != lastPct && p % 5 == 0) {
                                    lastPct = p
                                    logTab.append("[phantom] QEMU: $p%…\n")
                                }
                            }
                            if (!ok) {
                                logTab.append("\n\u001b[31m✗ QEMU não disponível: ${qemu.lastError ?: "erro desconhecido"}\u001b[0m\n")
                                return@launch
                            }
                            logTab.append("[phantom] ✓ QEMU pronto — instalando a distro…\n\n")
                        }
                        vm.distros.install(info, config, logTab)
                    }
                },
                onDismiss = { installTarget = null },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

/** Formata o tamanho em KB → "1,2 MB" / "340 KB". */
private fun sizeText(sizeKb: Long?): String {
    val kb = sizeKb ?: return "—"
    return if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else "$kb KB"
}

/** InfoRow dos detalhes de um pacote. */
@Composable
private fun PkgInfoRow(label: String, value: String, palette: PhantomPalette) {
    Row {
        Text("$label: ", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = palette.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun GuestPackageCard(
    pkg: GuestPackage,
    palette: PhantomPalette,
    onClickDetails: () -> Unit,
    onClickRemove: () -> Unit,
    onClickTerminal: () -> Unit,
) {
    PhantomCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(if (pkg.running) palette.success else palette.border, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pkg.name,
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${pkg.version} · ${sizeText(pkg.sizeKb)}",
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (pkg.running) {
            Text(
                "rodando",
                color = palette.success,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClickTerminal) {
                Icon(Icons.Filled.Restore, "Abrir no terminal", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
            PhantomOutlinedButton(text = "Detalhes", onClick = onClickDetails)
            if (pkg.category != PackageCategory.SYS) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onClickRemove) {
                    Icon(Icons.Filled.Delete, "Desinstalar", tint = palette.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Scanner de pacotes do guest (T20): IAs, Linguagens, Ferramentas, Sistema.
 * Busca + refresh manual + auto-atualização (o scanner re-escaneia a cada 20s
 * enquanto a VM roda — pega instalações via apt/pip).
 */
@Composable
private fun GuestPackagesSection(
    qemu: QemuManager,
    onOpenTerminal: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    val scanner = qemu.scanner
    var query by remember { mutableStateOf("") }
    var details by remember { mutableStateOf<GuestPackage?>(null) }
    var confirmRemove by remember { mutableStateOf<GuestPackage?>(null) }

    SectionLabel(text = "Pacotes do Linux")
    Spacer(Modifier.height(4.dp))
    Text(
        "Scanner do guest: IAs, linguagens, ferramentas e sistema (T20).",
        color = palette.textSecondary,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(8.dp))

    if (!qemu.running) {
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Disponível quando o Linux estiver ativo — inicie a VM acima.",
                color = palette.textSecondary,
                fontSize = 12.sp,
            )
        }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.surfaceAlt)
                .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(palette.accentSecondary),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Buscar…", color = palette.textSecondary, fontSize = 13.sp)
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        PhantomOutlinedButton(text = "Atualizar", icon = Icons.Filled.Refresh, onClick = { scanner.refresh() })
    }

    Spacer(Modifier.height(8.dp))

    if (scanner.scanning && scanner.packages.isEmpty()) {
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Text("Escaneando o guest…", color = palette.textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.accentPrimary, trackColor = palette.surfaceAlt)
        }
        return
    }

    val queryNorm = query.trim()
    val filtered = scanner.packages.filter { queryNorm.isEmpty() || it.name.contains(queryNorm, ignoreCase = true) }

    if (filtered.isEmpty()) {
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (scanner.packages.isEmpty())
                    "Nenhum pacote detectado — confira o phantom-agent.sh no guest (dark-code-init.sh)."
                else
                    "Nada encontrado para \"$queryNorm\".",
                color = palette.textSecondary,
                fontSize = 12.sp,
            )
        }
        return
    }

    PackageCategory.entries.forEach { cat ->
        val items = filtered.filter { it.category == cat }
        if (items.isEmpty()) return@forEach
        Text(
            cat.label,
            color = palette.accentSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        items.forEach { pkg ->
            GuestPackageCard(
                pkg = pkg,
                palette = palette,
                onClickDetails = { details = pkg },
                onClickRemove = { confirmRemove = pkg },
                onClickTerminal = onOpenTerminal,
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    details?.let { pkg ->
        AlertDialog(
            onDismissRequest = { details = null },
            containerColor = palette.surface,
            title = { Text(pkg.name, color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    PkgInfoRow("Categoria", pkg.category.label, palette)
                    PkgInfoRow("Versão", pkg.version, palette)
                    PkgInfoRow("Tamanho", sizeText(pkg.sizeKb), palette)
                    PkgInfoRow("Status", if (pkg.running) "Rodando" else "Parado", palette)
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Fechar", color = palette.accentPrimary) } },
            dismissButton = {
                if (pkg.category != PackageCategory.SYS) {
                    TextButton(onClick = { details = null; confirmRemove = pkg }) { Text("Desinstalar", color = palette.error) }
                }
            },
        )
    }

    confirmRemove?.let { pkg ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = palette.surface,
            title = { Text("Desinstalar ${pkg.name}?", color = palette.textPrimary) },
            text = { Text("Roda apt-get remove -y ${pkg.name} no guest.", color = palette.textSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    scanner.run("apt-get remove -y ${pkg.name}")
                }) { Text("Desinstalar", color = palette.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar", color = palette.textSecondary) } },
        )
    }
}

/**
 * AI Suite (Fase A do roteador de IAs — docs/roteador-ias.md):
 * registro de agentes (manual + auto-scan do guest), reservas de arquivos
 * (Conflict Guard) e kill switch. Delegação com aprovação humana é a Fase B.
 */
@Composable
private fun AiSuiteSection(
    aiSuite: AiSuiteManager,
    qemuRunning: Boolean,
    guestPackages: List<GuestPackage>,
    onSnack: (String) -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    var tick by remember { mutableIntStateOf(0) }
    var registerOpen by remember { mutableStateOf(false) }
    var regName by remember { mutableStateOf("") }
    var regInvoke by remember { mutableStateOf("") }
    var regType by remember { mutableStateOf("local") }

    val agents = remember(tick, guestPackages.size) { aiSuite.allAgents(guestPackages) }
    val locks = remember(tick) { aiSuite.guard.snapshot() }

    SectionLabel(text = "AI Suite — roteador entre IAs")
    Spacer(Modifier.height(4.dp))
    Text(
        "Fase A: registro de agentes + reservas de arquivos (Conflict Guard). Delegação com aprovação do dono chega na Fase B.",
        color = palette.textSecondary,
        fontSize = 11.sp,
    )
    Spacer(Modifier.height(8.dp))

    if (!qemuRunning) {
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Agentes do guest aparecem quando o Linux estiver ativo. Você ainda pode registrar IAs manualmente.",
                color = palette.textSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (aiSuite.paused) "⏸ Suite pausada" else "● Suite ativa",
            color = if (aiSuite.paused) palette.error else palette.success,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (aiSuite.paused) {
            PhantomOutlinedButton(text = "Retomar", icon = Icons.Filled.PlayArrow, onClick = { aiSuite.resume(); tick++ })
        } else {
            PhantomOutlinedButton(text = "Pausar todas", icon = Icons.Filled.Stop, onClick = { aiSuite.pauseAll(); tick++ })
        }
        Spacer(Modifier.width(8.dp))
        PhantomPrimaryButton(text = "Registrar IA", icon = Icons.Filled.Add, onClick = { registerOpen = true })
    }

    Spacer(Modifier.height(10.dp))
    Text("Agentes", color = palette.accentSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    if (agents.isEmpty()) {
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Text("Nenhuma IA registrada ainda.", color = palette.textSecondary, fontSize = 12.sp)
        }
    } else {
        agents.forEach { agent ->
            AiAgentRow(agent, palette)
            Spacer(Modifier.height(6.dp))
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Reservas ativas (locks)", color = palette.accentSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    if (locks.isEmpty()) {
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Text("Nenhum arquivo reservado no momento.", color = palette.textSecondary, fontSize = 12.sp)
        }
    } else {
        locks.forEach { lock ->
            AiLockRow(lock, palette)
            Spacer(Modifier.height(6.dp))
        }
    }

    if (registerOpen) {
        AlertDialog(
            onDismissRequest = { registerOpen = false },
            containerColor = palette.surface,
            title = { Text("Registrar IA no Suite", color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    BasicTextField(
                        value = regName,
                        onValueChange = { regName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(palette.accentSecondary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (regName.isEmpty()) Text("Nome (ex.: CodeLlama local)", color = palette.textSecondary, fontSize = 13.sp)
                            inner()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = regInvoke,
                        onValueChange = { regInvoke = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(palette.accentSecondary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (regInvoke.isEmpty()) Text("Comando (ex.: ollama run codellama)", color = palette.textSecondary, fontSize = 13.sp)
                            inner()
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tipo:", color = palette.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        listOf("local", "cloud").forEach { t ->
                            val selected = regType == t
                            Text(
                                t,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                                    .border(1.dp, if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    .clickable { regType = t }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                color = if (selected) palette.accentPrimary else palette.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (regName.isNotBlank() && regInvoke.isNotBlank()) {
                        aiSuite.registerAgent(regName.trim(), regInvoke.trim(), regType)
                        regName = ""
                        regInvoke = ""
                        regType = "local"
                        registerOpen = false
                        tick++
                        onSnack("IA registrada no Suite")
                    }
                }) { Text("Registrar", color = palette.accentPrimary) }
            },
            dismissButton = { TextButton(onClick = { registerOpen = false }) { Text("Cancelar", color = palette.textSecondary) } },
        )
    }
}

@Composable
private fun AiAgentRow(agent: AiAgent, palette: PhantomPalette) {
    PhantomCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (agent.status == "online") palette.success else palette.border, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agent.name, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${agent.type} · ${agent.invoke} · ${agent.skills.entries.sortedByDescending { it.value }.take(3).joinToString(", ") { "${it.key}:${it.value}" }}",
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (agent.id.startsWith("scan-")) {
                Text("auto", color = palette.accentSecondary, fontSize = 9.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun AiLockRow(lock: AiLock, palette: PhantomPalette) {
    PhantomCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lock.mode.toString(),
                color = palette.accentPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                lock.path,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(lock.owner, color = palette.textSecondary, fontSize = 9.sp)
        }
    }
}
