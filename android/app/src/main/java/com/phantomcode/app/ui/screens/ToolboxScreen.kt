package com.phantomcode.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SmartToy
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
import com.phantomcode.app.data.backup.BackupManager
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
            SectionLabel(text = "IAs · Linguagens · Ferramentas")
            Spacer(Modifier.height(8.dp))
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phantom AI Suite (D12)", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Scanner de pacotes + roteador de IAs — próxima etapa", color = palette.textSecondary, fontSize = 11.sp)
                    }
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
                        // T29: o binário QEMU é instalado junto com a distro (da nuvem).
                        // Sem passos manuais separados — o usuário só escolhe a distro.
                        if (!qemu.binaryReady) {
                            logTab.append("[phantom] Binário QEMU ausente — baixando do servidor…\n")
                            var lastPct = -1
                            val ok = qemu.ensureBinary { pct ->
                                val p = (pct * 100).toInt()
                                if (p != lastPct && p % 5 == 0) {
                                    lastPct = p
                                    logTab.append("[phantom] binário QEMU: $p%…\n")
                                }
                            }
                            if (!ok) {
                                logTab.append("\n\u001b[31m✗ Binário QEMU não instalado: ${qemu.lastError ?: "erro desconhecido"}\u001b[0m\n")
                                return@launch
                            }
                            logTab.append("[phantom] ✓ Binário QEMU pronto — instalando a distro…\n\n")
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
