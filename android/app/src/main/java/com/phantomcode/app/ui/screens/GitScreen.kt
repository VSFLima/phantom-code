package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.git.GitChange
import com.phantomcode.app.data.git.GitCommitInfo
import com.phantomcode.app.data.git.GitManager
import com.phantomcode.app.data.git.GitStatus
import com.phantomcode.app.data.git.GithubIssue
import com.phantomcode.app.data.git.GithubPr
import com.phantomcode.app.data.git.GithubRelease
import com.phantomcode.app.data.git.GithubRepo
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomDialog
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.launch
import java.io.File

/** Tela Git (T19): status real do repositório do projeto ativo. */
@Composable
fun GitScreen() {
    val context = LocalContext.current
    val vm = LocalVm.current
    val palette = LocalThemeController.current.currentPalette()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val git = remember { GitManager(context) }

    var projects by remember { mutableStateOf(vm.workspace.projects()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    var status by remember { mutableStateOf<GitStatus?>(null) }
    var commits by remember { mutableStateOf<List<GitCommitInfo>>(emptyList()) }
    var commitMsg by remember { mutableStateOf("") }

    var tokenDialog by remember { mutableStateOf(false) }
    var cloneDialog by remember { mutableStateOf(false) }
    var publishDialog by remember { mutableStateOf(false) }
    var publishName by remember { mutableStateOf("") }
    var remoteRepos by remember { mutableStateOf<List<GithubRepo>>(emptyList()) }
    var selectedRemote by remember { mutableStateOf<GithubRepo?>(null) }
    var remoteReleases by remember { mutableStateOf<List<GithubRelease>>(emptyList()) }
    var remoteIssues by remember { mutableStateOf<List<GithubIssue>>(emptyList()) }
    var remotePrs by remember { mutableStateOf<List<GithubPr>>(emptyList()) }
    var remoteBusy by remember { mutableStateOf(false) }
    var downloadingTag by remember { mutableStateOf<String?>(null) }

    val repoDir = selected?.let { File(vm.workspace.root, it) }

    fun notify(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    fun loadGithubRepos() {
        if (git.token.isNullOrBlank() || remoteBusy) return
        remoteBusy = true
        scope.launch {
            val result = git.githubRepos()
            remoteBusy = false
            result.onSuccess { remoteRepos = it }.onFailure { notify("GitHub: ${it.message}") }
        }
    }

    fun selectRemote(repo: GithubRepo) {
        selectedRemote = repo
        remoteReleases = emptyList()
        remoteIssues = emptyList()
        remotePrs = emptyList()
        scope.launch {
            git.githubReleases(repo.fullName).onSuccess { remoteReleases = it }
            git.githubIssues(repo.fullName).onSuccess { remoteIssues = it }
            git.githubPrs(repo.fullName).onSuccess { remotePrs = it }
        }
    }

    fun downloadRelease(release: GithubRelease) {
        if (downloadingTag != null) return
        downloadingTag = release.tag
        scope.launch {
            val result = git.downloadReleaseAsset(release, File(vm.workspace.root, "Downloads"))
            downloadingTag = null
            result.onSuccess { notify("Baixado em Downloads: $it") }
                .onFailure { notify("Download: ${it.message}") }
        }
    }

    fun cloneRemote(repo: GithubRepo) {
        val target = File(vm.workspace.root, repo.name)
        if (target.exists()) {
            notify("O projeto ${repo.name} já existe no workspace")
            return
        }
        busy = true
        scope.launch {
            // 1º tenta o clone JGit (completo, com .git para push/pull).
            val error = git.clone("https://github.com/${repo.fullName}.git", target)
            // Se falhou OU veio sem arquivos (só pastas), cai no ZIP da API,
            // que traz TODOS os arquivos de todas as pastas com certeza.
            val ok = error == null && runCatching {
                target.walkTopDown().any { it.isFile && !it.path.contains(File.separator + ".git" + File.separator) }
            }.getOrDefault(false)
            val finalError = if (ok) {
                null
            } else {
                // Garante diretório limpo para o ZIP
                runCatching { target.deleteRecursively() }
                git.cloneViaZip(repo, target)
            }
            busy = false
            if (finalError == null) {
                projects = vm.workspace.projects()
                selected = repo.name
                tick++
                notify("Projeto baixado: ${repo.name}")
            } else {
                notify("Erro ao baixar: $finalError")
            }
        }
    }

    LaunchedEffect(Unit) {
        val p = vm.workspace.projects()
        projects = p
        if (selected == null && p.isNotEmpty()) selected = p.first()
    }

    LaunchedEffect(git.token) {
        if (git.token != null) loadGithubRepos()
    }

    LaunchedEffect(tick, selected, repoDir) {
        if (repoDir != null) {
            if (git.isRepo(repoDir)) {
                status = git.status(repoDir)
                commits = git.log(repoDir)
            } else {
                status = null
                commits = emptyList()
            }
        } else {
            status = null
            commits = emptyList()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = "Git")
                Spacer(Modifier.width(10.dp))
                Text(
                    if (git.token == null) "sem token" else "token configurado",
                    color = if (git.token == null) palette.textSecondary else palette.success,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.weight(1f))
                PhantomOutlinedButton(
                    text = if (git.token == null) "Autenticar GitHub" else "GitHub conectado",
                    icon = Icons.Filled.Key,
                    onClick = { tokenDialog = true },
                )
            }

            Spacer(Modifier.height(10.dp))

            SectionLabel(text = "GitHub")
            Spacer(Modifier.height(8.dp))
            if (git.token == null) {
                PhantomCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Autentique o GitHub para ver seus projetos e releases.", color = palette.textSecondary, fontSize = 12.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (remoteBusy) "Carregando projetos…" else "${remoteRepos.size} projeto(s) disponível(is)",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    PhantomOutlinedButton(
                        text = "Atualizar",
                        icon = Icons.Filled.Refresh,
                        enabled = !remoteBusy,
                        onClick = ::loadGithubRepos,
                    )
                }
                Spacer(Modifier.height(8.dp))
                remoteRepos.forEach { repo ->
                    PhantomCard(
                        modifier = Modifier.fillMaxWidth().clickable { selectRemote(repo) },
                        glow = selectedRemote?.fullName == repo.fullName,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(repo.fullName, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    repo.description.ifBlank { "Sem descrição" },
                                    color = palette.textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(if (repo.private) "privado" else "público", color = palette.accentSecondary, fontSize = 10.sp)
                        }
                        if (selectedRemote?.fullName == repo.fullName) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("branch: ${repo.defaultBranch}", color = palette.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                                        PhantomPrimaryButton(
                                    text = "Baixar projeto",
                                    icon = Icons.Filled.CloudDownload,
                                    enabled = !busy,
                                    onClick = { cloneRemote(repo) },
                                )
                            }
                            if (remoteReleases.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Releases", color = palette.accentSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                remoteReleases.take(5).forEach { release ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${release.name} · ${release.tag}", color = palette.textSecondary, fontSize = 10.sp)
                                            Text(
                                                if (release.assets.isEmpty()) "sem arquivos binários · ${release.publishedAt}"
                                                else "${release.assets.first().name} · ${release.publishedAt}",
                                                color = palette.textSecondary,
                                                fontSize = 9.sp,
                                            )
                                        }
                                        if (release.assets.isNotEmpty()) {
                                            Spacer(Modifier.width(8.dp))
                                            PhantomOutlinedButton(
                                                text = if (downloadingTag == release.tag) "…" else "Baixar",
                                                icon = Icons.Filled.CloudDownload,
                                                enabled = downloadingTag == null,
                                                onClick = { downloadRelease(release) },
                                            )
                                        }
                                    }
                                }
                            }
                            if (remoteIssues.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Issues abertas (${remoteIssues.size})", color = palette.accentSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                remoteIssues.take(6).forEach { issue ->
                                    Text(
                                        "#${issue.number} ${issue.title}",
                                        color = palette.textPrimary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${issue.user} · ${issue.createdAt}" + issue.labels.joinToString("") { " · #$it" },
                                        color = palette.textSecondary,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                            if (remotePrs.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Pull requests (${remotePrs.size})", color = palette.accentSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                remotePrs.take(6).forEach { pr ->
                                    Text(
                                        "#${pr.number} ${pr.title}",
                                        color = palette.textPrimary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${pr.user} · ${pr.createdAt} · ${if (pr.merged) "mergeado" else pr.state}",
                                        color = palette.textSecondary,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Text("Trabalho em equipe", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cada colaborador autentica o próprio GitHub. Use Pull para receber mudanças e Push para compartilhar seus commits no repositório. Para subir um projeto local, use Publicar — o repositório é criado automaticamente e privado.",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                PhantomPrimaryButton(
                    text = "Publicar projeto no GitHub",
                    icon = Icons.Filled.CloudUpload,
                    enabled = repoDir != null && git.token != null && !busy,
                    onClick = {
                        publishName = repoDir?.name ?: ""
                        publishDialog = true
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Seleção de projeto
            if (projects.isEmpty()) {
                Spacer(Modifier.height(48.dp))
                Column(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.AccountTree, contentDescription = null, tint = palette.border, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhum projeto no workspace", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Crie um projeto no Explorer e depois volte aqui.", color = palette.textSecondary, fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    projects.forEach { project ->
                        val isSel = project == selected
                        Text(
                            text = project,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSel) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                                .border(1.dp, if (isSel) palette.accentPrimary else palette.border.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .clickable { selected = project; tick++ }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (isSel) palette.accentPrimary else palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (repoDir != null && !git.isRepo(repoDir)) {
                    // Sem repositório ainda
                    PhantomCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Este projeto ainda não é um repositório Git.", color = palette.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            PhantomPrimaryButton(
                                text = "git init",
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        val ok = git.initRepo(repoDir)
                                        busy = false
                                        if (ok) notify("Repositório iniciado") else notify("Erro ao iniciar")
                                        tick++
                                    }
                                },
                            )
                            Spacer(Modifier.width(10.dp))
                            PhantomOutlinedButton(
                                text = "Clonar",
                                icon = Icons.Filled.CloudDownload,
                                onClick = { cloneDialog = true },
                            )
                        }
                    }
                } else if (repoDir != null) {
                    status?.let { st ->
                        // Status card
                        PhantomCard(glow = !st.clean, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(if (st.clean) palette.success else palette.accentBright, RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(st.branch, color = palette.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (st.clean) "limpo" else "${st.changes.size} mudanças",
                                    color = if (st.clean) palette.success else palette.accentBright,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                if (st.clean) {
                                    Text("✓", color = palette.success, fontSize = 14.sp)
                                }
                            }
                            if (!st.clean) {
                                Spacer(Modifier.height(10.dp))
                                st.changes.take(40).forEach { change ->
                                    ChangeRow(change)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))

                        // Commit
                        PhantomCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = commitMsg,
                                    onValueChange = { commitMsg = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
                                    cursorBrush = SolidColor(palette.accentSecondary),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        busy = true
                                        scope.launch {
                                            val err = git.commit(repoDir, commitMsg)
                                            busy = false
                                            notify(err ?: "Commit feito")
                                            commitMsg = ""
                                            tick++
                                        }
                                    }),
                                    decorationBox = { inner ->
                                        if (commitMsg.isEmpty()) {
                                            Text("Mensagem do commit…", color = palette.textSecondary, fontSize = 13.sp)
                                        }
                                        inner()
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                                PhantomPrimaryButton(
                                    text = "Commit",
                                    enabled = !busy && commitMsg.isNotBlank(),
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val err = git.commit(repoDir, commitMsg)
                                            busy = false
                                            notify(err ?: "Commit feito")
                                            commitMsg = ""
                                            tick++
                                        }
                                    },
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row {
                                PhantomOutlinedButton(
                                    text = "Sincronizar",
                                    icon = Icons.Filled.Sync,
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val result = git.pull(repoDir)
                                            busy = false
                                            notify(result ?: "Projeto sincronizado")
                                            tick++
                                        }
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                                PhantomOutlinedButton(
                                    text = "Push",
                                    icon = Icons.Filled.CloudUpload,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val err = git.push(repoDir)
                                            busy = false
                                            notify(err ?: "Push enviado")
                                            tick++
                                        }
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                                PhantomOutlinedButton(
                                    text = "Pull",
                                    icon = Icons.Filled.CloudDownload,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val err = git.pull(repoDir)
                                            busy = false
                                            notify(err ?: "Pull ok")
                                            tick++
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))

                        // Log de commits
                        SectionLabel(text = "Commits")
                        Spacer(Modifier.height(8.dp))
                        PhantomCard(modifier = Modifier.fillMaxWidth()) {
                            if (commits.isEmpty()) {
                                Text("Nenhum commit ainda.", color = palette.textSecondary, fontSize = 12.sp)
                            }
                            commits.forEach { c ->
                                Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.shortId, color = palette.accentSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(c.message, color = palette.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${c.author} · ${c.date}", color = palette.textSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (tokenDialog) {
            PhantomDialog(
                title = "Token GitHub (PAT)",
                placeholder = "ghp_xxxxxxxxxxxx",
                confirmText = "Salvar",
                isSecret = true,
                onConfirm = { value ->
                    tokenDialog = false
                    git.token = value
                    tick++
                    notify("Token salvo")
                },
                onDismiss = { tokenDialog = false },
            )
        }

        if (publishDialog) {
            PhantomDialog(
                title = "Publicar no GitHub (privado)",
                placeholder = "nome-do-repositorio",
                initialValue = publishName,
                confirmText = "Criar repositório e enviar",
                onConfirm = { name ->
                    val target = repoDir
                    if (target != null) {
                        publishDialog = false
                        busy = true
                        scope.launch {
                            val msg = git.syncLocalToGithub(
                                dir = target,
                                repoName = name.trim().ifBlank { target.name },
                                description = "Projeto ${target.name} do Phantom-Code",
                                isPrivate = true,
                            )
                            busy = false
                            notify(msg ?: "Projeto publicado")
                            tick++
                        }
                    } else {
                        publishDialog = false
                    }
                },
                onDismiss = { publishDialog = false },
            )
        }

        if (cloneDialog) {
            PhantomDialog(
                title = "Clonar repositório",
                placeholder = "https://github.com/usuario/repo.git",
                confirmText = "Clonar",
                onConfirm = { url ->
                    cloneDialog = false
                    val name = url.trim().substringAfterLast('/').removeSuffix(".git").ifBlank { "repositorio" }
                    busy = true
                    scope.launch {
                        val err = git.clone(url, File(vm.workspace.root, name))
                        busy = false
                        if (err == null) {
                            projects = vm.workspace.projects()
                            selected = name
                            notify("Clonado em $name")
                        } else {
                            notify("Erro: $err")
                        }
                        tick++
                    }
                },
                onDismiss = { cloneDialog = false },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun ChangeRow(change: GitChange) {
    val palette = LocalThemeController.current.currentPalette()
    val color = when (change.status) {
        'A' -> palette.success
        'M' -> palette.accentSecondary
        'D' -> palette.error
        'C' -> palette.error
        else -> palette.textSecondary
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            change.status.toString(),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            change.path,
            color = palette.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
