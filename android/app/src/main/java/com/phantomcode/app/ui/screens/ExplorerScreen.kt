package com.phantomcode.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.FileEntry
import com.phantomcode.app.data.SessionManager
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.formatBytes
import com.phantomcode.app.ui.components.PhantomActionSheet
import com.phantomcode.app.ui.components.PhantomConfirmDialog
import com.phantomcode.app.ui.components.PhantomDialog
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.components.fileTypeIcon
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TreeNode(val entry: FileEntry, val depth: Int)

private sealed interface ExplorerDialog {
    data class New(val parentPath: String, val isDir: Boolean) : ExplorerDialog
    data class Rename(val entry: FileEntry) : ExplorerDialog
}

/** Ação de transferência pendente (cortar/copiar): aguarda uma pasta destino para colar. */
private data class PendingOp(val srcRelPath: String, val isMove: Boolean)

private fun visibleTree(ws: WorkspaceManager, expanded: Map<String, Boolean>): List<TreeNode> {
    val out = mutableListOf<TreeNode>()
    fun walk(rel: String, depth: Int) {
        ws.list(rel).forEach { e ->
            out += TreeNode(e, depth)
            if (e.isDir && expanded[e.relPath] == true) walk(e.relPath, depth + 1)
        }
    }
    walk("", 0)
    return out
}

/** Explorer real do workspace (T11): árvore de arquivos com criar/renomear/excluir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(onOpenFile: (String) -> Unit) {
    val context = LocalContext.current
    val workspace = remember { WorkspaceManager(context) }
    val session = remember { SessionManager(context) }
    val palette = LocalThemeController.current.currentPalette()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var tick by remember { mutableStateOf(0) }
    val tree = remember(tick) { visibleTree(workspace, expanded) }

    var fabMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<FileEntry?>(null) }
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var pendingOp by remember { mutableStateOf<PendingOp?>(null) }
    var downloadTarget by remember { mutableStateOf<FileEntry?>(null) }

    // Abre automaticamente o projeto ativo (vindo da Home)
    LaunchedEffect(Unit) {
        session.activeProject?.let { project ->
            expanded[project] = true
            tick++
            session.activeProject = null
        }
    }

    fun notify(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    // SAF: salvar arquivo em local escolhido pelo usuário (P2.2 · download)
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val target = downloadTarget
        downloadTarget = null
        if (uri != null && target != null && !target.isDir) {
            scope.launch(Dispatchers.IO) {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(workspace.readText(target.relPath).toByteArray())
                    }
                }.isSuccess
                withContext(Dispatchers.Main) {
                    notify(if (ok) "Baixado: ${target.name}" else "Erro ao baixar ${target.name}")
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = "Explorer")
                Spacer(Modifier.width(10.dp))
                Text("/workspace", color = palette.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Atualizar",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(20.dp).clickable { tick++ },
                )
            }

            if (tree.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = palette.border, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Workspace vazio", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Crie um arquivo ou pasta com o botão +.", color = palette.textSecondary, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tree, key = { it.entry.relPath }) { node ->
                        val e = node.entry
                        val isOpen = e.isDir && expanded[e.relPath] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (e.isDir) {
                                            expanded[e.relPath] = !isOpen
                                            tick++
                                        } else {
                                            onOpenFile(e.relPath)
                                        }
                                    },
                                    onLongClick = { actionTarget = e },
                                )
                                .padding(
                                    start = 8.dp + (node.depth * 14).dp,
                                    end = 14.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (e.isDir) {
                                Icon(
                                    if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Icon(
                                    if (isOpen) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = palette.accentPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Spacer(Modifier.width(18.dp))
                                val fileIcon = fileTypeIcon(e.name)
                                Icon(
                                    fileIcon.icon,
                                    contentDescription = null,
                                    tint = fileIcon.tint,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                e.name,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!e.isDir) {
                                Spacer(Modifier.width(8.dp))
                                Text(formatBytes(e.sizeBytes), color = palette.textSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // FAB — menu de criação (raiz do workspace)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (pendingOp != null) palette.accentSecondary else palette.accentPrimary)
                .clickable { fabMenu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (pendingOp != null) Icons.Filled.ContentPaste else Icons.Filled.Add,
                contentDescription = "Novo",
                tint = androidx.compose.ui.graphics.Color.White,
            )
            DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Novo arquivo") },
                    leadingIcon = { Icon(Icons.Filled.NoteAdd, null) },
                    onClick = {
                        fabMenu = false
                        dialog = ExplorerDialog.New(parentPath = "", isDir = false)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Nova pasta") },
                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                    onClick = {
                        fabMenu = false
                        dialog = ExplorerDialog.New(parentPath = "", isDir = true)
                    },
                )
                if (pendingOp != null) {
                    DropdownMenuItem(
                        text = { Text("Colar na raiz (${pendingOp?.srcRelPath ?: ""})") },
                        leadingIcon = { Icon(Icons.Filled.ContentPaste, null) },
                        onClick = {
                            fabMenu = false
                            pendingOp?.let { op ->
                                val ok = if (op.isMove) workspace.moveTo(op.srcRelPath, "")
                                else workspace.copyTo(op.srcRelPath, "")
                                pendingOp = null
                                notify(if (ok) "${if (op.isMove) "Movido" else "Copiado"} para a raiz" else "Erro ao colar")
                                tick++
                            }
                        },
                    )
                }
            }
        }

        // Menu de contexto (long-press): abrir, criar aqui (pastas), cortar/copiar/colar, baixar
        actionTarget?.let { target ->
            PhantomActionSheet(
                title = target.name,
                actions = buildList {
                    add("Abrir" to if (target.isDir) Icons.Filled.FolderOpen else Icons.Filled.Description)
                    if (target.isDir) {
                        add("Novo arquivo aqui" to Icons.Filled.NoteAdd)
                        add("Nova pasta aqui" to Icons.Filled.CreateNewFolder)
                        add("Colar aqui" to Icons.Filled.ContentPaste)
                    }
                    add("Cortar" to Icons.Filled.ContentCut)
                    add("Copiar" to Icons.Filled.ContentCopy)
                    if (!target.isDir) {
                        add("Baixar" to Icons.Filled.Download)
                    }
                    add("Renomear" to Icons.Filled.Edit)
                    add("Excluir" to Icons.Filled.Delete)
                    add("Copiar caminho" to Icons.Filled.ContentCopy)
                },
                onAction = { index ->
                    actionTarget = null
                    when (index) {
                        // Arquivo: 0=Abrir 1=Cortar 2=Copiar 3=Baixar 4=Renomear 5=Excluir 6=Cam. copiado
                        // Pasta:   0=Abrir 1=Novo arq 2=Nova pasta 3=Colar 4=Cortar 5=Copiar 6=Renom. 7=Excluir 8=Cam.
                        0 -> {
                            if (target.isDir) {
                                expanded[target.relPath] = expanded[target.relPath] != true
                                tick++
                            } else {
                                onOpenFile(target.relPath)
                            }
                        }
                        1 -> if (target.isDir) {
                            dialog = ExplorerDialog.New(parentPath = target.relPath, isDir = false)
                        } else {
                            pendingOp = PendingOp(target.relPath, isMove = true)
                            notify("Cortado: ${target.name} — toque em uma pasta e escolha Colar")
                        }
                        2 -> if (target.isDir) {
                            dialog = ExplorerDialog.New(parentPath = target.relPath, isDir = true)
                        } else {
                            pendingOp = PendingOp(target.relPath, isMove = false)
                            notify("Copiado: ${target.name} — toque em uma pasta e escolha Colar")
                        }
                        3 -> if (target.isDir) {
                            pendingOp?.let { op ->
                                if (op.srcRelPath == target.relPath) {
                                    pendingOp = null
                                    notify("Colar cancelado (mesma pasta)")
                                } else {
                                    val ok = if (op.isMove) workspace.moveTo(op.srcRelPath, target.relPath)
                                    else workspace.copyTo(op.srcRelPath, target.relPath)
                                    pendingOp = null
                                    notify(if (ok) "${if (op.isMove) "Movido" else "Copiado"} para ${target.relPath}" else "Erro ao colar")
                                    tick++
                                }
                            } ?: run { notify("Nada para colar") }
                        } else {
                            downloadTarget = target
                            downloadLauncher.launch(target.name)
                        }
                        4 -> if (target.isDir) {
                            pendingOp = PendingOp(target.relPath, isMove = true)
                            notify("Cortado: ${target.name} — toque em uma pasta e escolha Colar")
                        } else {
                            dialog = ExplorerDialog.Rename(target)
                        }
                        5 -> if (target.isDir) {
                            pendingOp = PendingOp(target.relPath, isMove = false)
                            notify("Copiado: ${target.name} — toque em uma pasta e escolha Colar")
                        } else {
                            deleteTarget = target
                        }
                        6 -> if (target.isDir) {
                            dialog = ExplorerDialog.Rename(target)
                        } else {
                            clipboard.setText(AnnotatedString(target.relPath))
                            notify("Caminho copiado: ${target.relPath}")
                        }
                        7 -> if (target.isDir) {
                            deleteTarget = target
                        }
                        8 -> {
                            clipboard.setText(AnnotatedString(target.relPath))
                            notify("Caminho copiado: ${target.relPath}")
                        }
                    }
                },
                onDismiss = { actionTarget = null },
            )
        }

        // Diálogos de criação/renomear
        when (val d = dialog) {
            is ExplorerDialog.New -> PhantomDialog(
                title = if (d.isDir) "Nova pasta" else "Novo arquivo",
                placeholder = if (d.isDir) "nome-da-pasta" else "nome.ext",
                confirmText = "Criar",
                onConfirm = { name ->
                    val rel = if (d.parentPath.isEmpty()) name else "${d.parentPath}/$name"
                    val ok = if (d.isDir) workspace.createDir(rel) else workspace.createFile(rel)
                    dialog = null
                    if (ok) notify(if (d.isDir) "Pasta criada" else "Arquivo criado") else notify("Erro ao criar")
                    if (!d.isDir) {
                        val parent = d.parentPath
                        if (parent.isNotEmpty()) {
                            expanded[parent] = true
                        }
                    }
                    tick++
                },
                onDismiss = { dialog = null },
            )

            is ExplorerDialog.Rename -> PhantomDialog(
                title = "Renomear",
                initialValue = d.entry.name,
                confirmText = "Renomear",
                onConfirm = { newName ->
                    dialog = null
                    if (workspace.rename(d.entry.relPath, newName)) notify("Renomeado") else notify("Erro ao renomear")
                    tick++
                },
                onDismiss = { dialog = null },
            )

            null -> Unit
        }

        // Confirmação de exclusão
        deleteTarget?.let { target ->
            PhantomConfirmDialog(
                title = "Excluir",
                message = "Excluir '${target.name}'?${if (target.isDir) " (pasta e todo o conteúdo)" else ""}",
                onConfirm = {
                    deleteTarget = null
                    if (workspace.delete(target.relPath)) notify("Excluído") else notify("Erro ao excluir")
                    tick++
                },
                onDismiss = { deleteTarget = null },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
