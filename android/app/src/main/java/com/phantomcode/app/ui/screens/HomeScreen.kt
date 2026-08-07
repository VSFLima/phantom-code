package com.phantomcode.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GitHub
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.phantomcode.app.data.SessionManager
import com.phantomcode.app.data.StorageHelper
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.ProjectOrigin
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomDialog
import com.phantomcode.app.ui.components.PhantomLogo
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.QemuStatusPill
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val context = LocalContext.current
    val workspace = remember { WorkspaceManager(context) }
    val session = remember { SessionManager(context) }
    val palette = LocalThemeController.current.currentPalette()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tick by remember { mutableStateOf(0) }
    val projects = remember(tick) { workspace.projects() }
    val lastOpen = remember(tick) { session.lastOpenPath }
    var newProjectDialog by remember { mutableStateOf(false) }

    // ── Permissão de armazenamento (pasta pública Phantom-Code) ──
    var storageGranted by remember { mutableStateOf(StorageHelper.hasStorageAccess(context)) }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> storageGranted = granted || StorageHelper.hasStorageAccess(context); tick++ }
    val storageSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { storageGranted = StorageHelper.hasStorageAccess(context); tick++ }

    fun notify(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: "importado"
            scope.launch(Dispatchers.IO) {
                val ok = importFolder(context, uri, workspace.resolve(name))
                withContext(Dispatchers.Main) {
                    if (ok) {
                        session.addRecent(name)
                        notify("Projeto importado: $name")
                    } else {
                        notify("Erro ao importar")
                    }
                    tick++
                }
            }
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }
                .getOrNull()
                ?.substringBeforeLast('.')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "projeto-importado"
            scope.launch(Dispatchers.IO) {
                val ok = importZip(context, uri, workspace.resolve(name))
                withContext(Dispatchers.Main) {
                    if (ok) {
                        session.addRecent(name)
                        notify("ZIP importado: $name")
                    } else {
                        notify("Erro ao importar ZIP")
                    }
                    tick++
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            PhantomLogo(size = 84.dp)
            Spacer(Modifier.height(12.dp))
            Text("PHANTOM-CODE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, letterSpacing = 4.sp)
            Text("IDE · TERMINAL LINUX · GIT · IA", fontSize = 11.sp, color = palette.textSecondary, letterSpacing = 2.sp)
            Spacer(Modifier.height(14.dp))
            QemuStatusPill(running = LocalVm.current.qemu.running)
            Spacer(Modifier.height(14.dp))

            // Aviso/estado de armazenamento (pasta pública do app)
            PhantomCard(
                modifier = Modifier.fillMaxWidth(),
                glow = !storageGranted,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Storage,
                        contentDescription = null,
                        tint = if (storageGranted) palette.success else palette.accentBright,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (storageGranted) "Armazenamento liberado" else "Permitir acesso à pasta",
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (storageGranted) {
                                workspace.displayPath
                            } else {
                                "O app usará a pasta ${StorageHelper.APP_DIR_NAME} no armazenamento interno."
                            },
                            color = palette.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                        )
                    }
                    if (!storageGranted) {
                        PhantomPrimaryButton(
                            text = "Permitir",
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    storageSettingsLauncher.launch(StorageHelper.permissionIntent(context))
                                } else {
                                    storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Hub inicial: informações e ações principais do ambiente
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Central Phantom", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Seu hub de projetos, Linux, Git e ferramentas", color = palette.textSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Use a barra lateral para acessar cada área do app.",
                    color = palette.textSecondary,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(22.dp))

            // Continuar sessão (D18)
            if (lastOpen != null && workspace.resolve(lastOpen).exists()) {
                PhantomCard(modifier = Modifier.fillMaxWidth(), glow = true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenFile(lastOpen) }
                            .padding(vertical = 2.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = palette.success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuar de", color = palette.textSecondary, fontSize = 11.sp)
                            Text(lastOpen.substringAfterLast('/'), color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
                    }
                }
            } else {
                PhantomCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Nenhuma sessão anterior", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Abra um projeto ou crie um novo para começar.", color = palette.textSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))

            SectionLabel(text = "Recent Projects", modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            if (projects.isEmpty()) {
                PhantomCard(modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhum projeto ainda", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Crie, importe ou clone um projeto.", color = palette.textSecondary, fontSize = 12.sp)
                }
            } else {
                PhantomCard(modifier = Modifier.fillMaxWidth()) {
                    projects.take(6).forEach { project ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    session.addRecent(project)
                                    session.activeProject = project
                                    onOpenProject(project)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val origin = workspace.projectOrigin(project)
                            Icon(
                                imageVector = when (origin) {
                                    ProjectOrigin.GITHUB -> Icons.Filled.GitHub
                                    ProjectOrigin.GIT -> Icons.Filled.AccountTree
                                    ProjectOrigin.LOCAL -> Icons.Filled.Folder
                                },
                                contentDescription = when (origin) {
                                    ProjectOrigin.GITHUB -> "Projeto GitHub"
                                    ProjectOrigin.GIT -> "Projeto Git"
                                    ProjectOrigin.LOCAL -> "Projeto local"
                                },
                                tint = when (origin) {
                                    ProjectOrigin.GITHUB -> palette.textPrimary
                                    ProjectOrigin.GIT -> palette.accentSecondary
                                    ProjectOrigin.LOCAL -> palette.accentPrimary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(project, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            PhantomPrimaryButton(
                text = "Novo Projeto",
                icon = Icons.Filled.Add,
                onClick = { newProjectDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PhantomOutlinedButton(
                text = "Importar Pasta (SAF)",
                icon = Icons.Filled.FolderOpen,
                onClick = { importLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PhantomOutlinedButton(
                text = "Importar Projeto ZIP",
                icon = Icons.Filled.FolderOpen,
                onClick = { zipLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Terminal dock no rodapé · expande com ▲", color = palette.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (newProjectDialog) {
            PhantomDialog(
                title = "Novo projeto",
                placeholder = "nome-do-projeto",
                confirmText = "Criar",
                onConfirm = { name ->
                    newProjectDialog = false
                    if (workspace.createDir(name)) {
                        session.addRecent(name)
                        session.activeProject = name
                        onOpenProject(name)
                    } else {
                        notify("Erro ao criar projeto")
                    }
                },
                onDismiss = { newProjectDialog = false },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

/** Importa uma pasta do aparelho (SAF) para dentro do workspace. */
private suspend fun importFolder(context: android.content.Context, uri: Uri, target: File): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            fun copyTree(doc: DocumentFile, dest: File) {
                dest.mkdirs()
                doc.listFiles().forEach { child ->
                    val name = child.name ?: return@forEach
                    val f = File(dest, name)
                    if (child.isDirectory) {
                        copyTree(child, f)
                    } else {
                        context.contentResolver.openInputStream(child.uri)?.use { input ->
                            f.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                }
            }
            copyTree(DocumentFile.fromTreeUri(context, uri)!!, target)
        }.isSuccess
    }

/** Importa um ZIP sem permitir que entradas escapem da pasta do projeto. */
private suspend fun importZip(context: android.content.Context, uri: Uri, target: File): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            target.mkdirs()
            val root = target.canonicalFile
            ZipInputStream(context.contentResolver.openInputStream(uri) ?: error("ZIP indisponível")).use { zip ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val output = File(target, entry.name).canonicalFile
                    check(output == root || output.path.startsWith(root.path + File.separator)) {
                        "Entrada ZIP inválida"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { out ->
                            var count: Int
                            while (zip.read(buffer).also { count = it } != -1) {
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }.isSuccess
    }
