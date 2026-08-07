package com.phantomcode.app.data.git

import android.content.Context
import com.phantomcode.app.data.secrets.SecretCategory
import com.phantomcode.app.data.secrets.SecretsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class GitChange(val path: String, val status: Char)

data class GitStatus(
    val branch: String,
    val changes: List<GitChange>,
    val clean: Boolean,
)

data class GitCommitInfo(
    val shortId: String,
    val message: String,
    val author: String,
    val date: String,
)

data class GithubRepo(
    val fullName: String,
    val name: String,
    val description: String,
    val private: Boolean,
    val defaultBranch: String,
)

data class GithubRelease(
    val name: String,
    val tag: String,
    val publishedAt: String,
)

/** Git nativo via JGit (T19) — status, clone, commit, push/pull e log.
 *  Token (T20): armazenado criptografado no Android Keystore via SecretsManager. */
class GitManager(context: Context) {

    private val secrets = SecretsManager(context)
    private val legacyPrefs = context.getSharedPreferences("phantom_git", Context.MODE_PRIVATE)

    /** Token GitHub (PAT) — criptografado no Android Keystore (D8). Migra do prefs antigo automaticamente. */
    var token: String?
        get() {
            secrets.get(KEY_TOKEN)?.let { return it }
            val legacy = legacyPrefs.getString(KEY_TOKEN, null)
            if (!legacy.isNullOrBlank()) {
                // Migração única: prefs → Keystore (T20)
                secrets.save(KEY_TOKEN, legacy, SecretCategory.GIT, "GITHUB_TOKEN", exposeToLinux = false)
                legacyPrefs.edit().remove(KEY_TOKEN).apply()
                return legacy
            }
            return null
        }
        set(value) {
            if (value.isNullOrBlank()) secrets.delete(KEY_TOKEN)
            else secrets.save(KEY_TOKEN, value.trim(), SecretCategory.GIT, "GITHUB_TOKEN", exposeToLinux = false)
        }

    fun isRepo(dir: File): Boolean = File(dir, ".git").exists()

    private fun credentials(): UsernamePasswordCredentialsProvider? =
        token?.takeIf { it.isNotBlank() }
            ?.let { UsernamePasswordCredentialsProvider("oauth2", it) }

    suspend fun status(dir: File): GitStatus? = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(dir).use { git ->
                val st = git.status().call()
                val changes = buildList {
                    st.added.forEach { add(GitChange(it, 'A')) }
                    st.modified.forEach { add(GitChange(it, 'M')) }
                    st.removed.forEach { add(GitChange(it, 'D')) }
                    st.untracked.take(300).forEach { add(GitChange(it, 'U')) }
                    st.conflicting.forEach { add(GitChange(it, 'C')) }
                }
                GitStatus(
                    branch = git.repository.branch ?: "(sem branch)",
                    changes = changes.sortedWith(compareBy({ it.status }, { it.path.lowercase() })),
                    clean = changes.isEmpty(),
                )
            }
        }.getOrNull()
    }

    suspend fun initRepo(dir: File): Boolean = withContext(Dispatchers.IO) {
        runCatching { Git.init().setDirectory(dir).call().close() }.isSuccess
    }

    /** Clona em `target`; retorna null em sucesso ou mensagem de erro. */
    suspend fun clone(url: String, target: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            Git.cloneRepository()
                .setURI(url.trim())
                .setDirectory(target)
                .setCredentialsProvider(credentials())
                .call()
                .close()
            null
        }.getOrElse { it.message }
    }

    suspend fun commit(dir: File, message: String): String? = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext "Mensagem vazia"
        runCatching {
            Git.open(dir).use { git ->
                git.add().addFilepattern(".").call()
                git.commit()
                    .setMessage(message.trim())
                    .setAuthor("Phantom-Code", "phantom@localhost")
                    .setCommitter("Phantom-Code", "phantom@localhost")
                    .call()
            }
            null
        }.getOrElse { it.message }
    }

    /** Envia os commits para o remote (branch explícita) e configura o upstream. */
    suspend fun push(dir: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(dir).use { git ->
                val remotes = git.remoteList().call()
                if (remotes.isEmpty()) return@withContext "Nenhum remote — use \"Publicar no GitHub\" primeiro"
                val remoteName = remotes.first().name
                val branch = git.repository.branch ?: "main"
                val results = git.push()
                    .setRemote(remoteName)
                    .setRefSpecs(RefSpec("$branch:$branch"))
                    .setCredentialsProvider(credentials())
                    .call()
                results.joinToString("; ") { r ->
                    val ok = r.remoteUpdates.all { u ->
                        u.status == RemoteRefUpdate.Status.OK ||
                            u.status == RemoteRefUpdate.Status.UP_TO_DATE
                    }
                    if (ok) {
                        // Configura o upstream para o próximo Pull funcionar sem argumentos
                        runCatching { git.branchSetUpstream().setName(branch).setUpstreamName("$remoteName/$branch").call() }
                        "push ok"
                    } else {
                        r.messages.trim().ifBlank { "falha no push" }
                    }
                }
            }
        }.getOrElse { it.message ?: "Falha no push" }
    }

    /** Baixa as mudanças do remote e atualiza o projeto local (fetch + merge explícito).
     *  Não depende de upstream configurado: procura refs/remotes/<remote>/<branch>.
     *  Traz TODOS os arquivos novos/alterados do remote para o workspace. */
    suspend fun pull(dir: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(dir).use { git ->
                val remotes = git.remoteList().call()
                if (remotes.isEmpty()) return@withContext "Nenhum remote configurado — use \"Publicar no GitHub\" primeiro"
                val remoteName = remotes.first().name
                val branch = git.repository.branch ?: "main"

                // 1) Fetch completo do remote (traz refs e objetos, remove refs apagadas)
                git.fetch()
                    .setRemote(remoteName)
                    .setCredentialsProvider(credentials())
                    .setRemoveDeletedRefs(true)
                    .call()

                // 2) Ref remota do branch atual
                val remoteRef = git.repository.findRef("refs/remotes/$remoteName/$branch")
                    ?: return@withContext "O remote não tem a branch '$branch' — rode Push primeiro para criá-la"

                // 3) Merge (fast-forward quando possível, merge commit quando divergiu)
                val result = git.merge()
                    .include(remoteRef)
                    .setMessage("Pull de $remoteName/$branch (Phantom-Code)")
                    .call()
                when (result.mergeStatus) {
                    MergeResult.MergeStatus.ALREADY_UP_TO_DATE -> "Já atualizado"
                    MergeResult.MergeStatus.FAST_FORWARD -> "Pull OK — $branch atualizada"
                    MergeResult.MergeStatus.MERGED -> "Pull OK (merge de $remoteName/$branch)"
                    MergeResult.MergeStatus.CONFLICTING -> "Conflitos em ${result.conflicts.size} arquivo(s) — resolva e faça commit"
                    MergeResult.MergeStatus.FAILED -> "Merge falhou: ${result.failingPaths?.keys?.joinToString(", ") ?: "erro desconhecido"}"
                    else -> "Pull: ${result.mergeStatus}"
                }
            }
        }.getOrElse { it.message ?: "Falha no pull" }
    }

    suspend fun log(dir: File, max: Int = 6): List<GitCommitInfo> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(dir).use { git ->
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                git.log().setMaxCount(max).call().map { c ->
                    GitCommitInfo(
                        shortId = c.id.abbreviate(7).name(),
                        message = c.fullMessage.trim().lines().firstOrNull() ?: "",
                        author = c.authorIdent?.name ?: "?",
                        date = c.authorIdent?.let { fmt.format(it.`when`) } ?: "",
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Repositórios do usuário autenticado para a aba GitHub. */
    suspend fun githubRepos(): Result<List<GithubRepo>> = withContext(Dispatchers.IO) {
        apiGet("https://api.github.com/user/repos?per_page=100&sort=updated").map { body ->
            val json = JSONArray(body)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    add(
                        GithubRepo(
                            fullName = item.optString("full_name"),
                            name = item.optString("name"),
                            description = item.optString("description"),
                            private = item.optBoolean("private"),
                            defaultBranch = item.optString("default_branch", "main"),
                        ),
                    )
                }
            }
        }
    }

    /** Releases do repositório selecionado. */
    suspend fun githubReleases(fullName: String): Result<List<GithubRelease>> = withContext(Dispatchers.IO) {
        apiGet("https://api.github.com/repos/${fullName}/releases?per_page=20").map { body ->
            val json = JSONArray(body)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    add(
                        GithubRelease(
                            name = item.optString("name").ifBlank { item.optString("tag_name") },
                            tag = item.optString("tag_name"),
                            publishedAt = item.optString("published_at").take(10),
                        ),
                    )
                }
            }
        }
    }

    /** Cria um repositório no GitHub (POST /user/repos). Retorna o full_name ou erro. */
    suspend fun createGithubRepo(name: String, description: String = "", private: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = token?.takeIf { it.isNotBlank() } ?: error("Autentique o GitHub primeiro")
                val payload = JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("private", private)
                    .put("auto_init", false)
                val conn = (URL("https://api.github.com/user/repos").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("Authorization", "Bearer $auth")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = conn.responseCode
                val body = conn.inputStream?.bufferedReader()?.readText().orEmpty()
                conn.disconnect()
                if (code in 200..299) {
                    JSONObject(body).optString("full_name", name)
                } else {
                    val msg = JSONObject(body).optString("message", "HTTP $code")
                    error(msg)
                }
            }
        }

    /** Username do GitHub autenticado (para montar a URL do remote). */
    suspend fun githubLogin(): Result<String> = withContext(Dispatchers.IO) {
        apiGet("https://api.github.com/user").map { JSONObject(it).optString("login") }
    }

    /**
     * Sincroniza um projeto LOCAL para o GitHub criando o repositório
     * automaticamente (se não existir remote) e fazendo o primeiro push.
     *
     * Retorna mensagem legível (sucesso ou erro).
     */
    suspend fun syncLocalToGithub(
        dir: File,
        repoName: String = dir.name,
        description: String = "Projeto do Phantom-Code",
        isPrivate: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) return@withContext "Autentique o GitHub primeiro"
        runCatching {
            Git.open(dir).use { git ->
                // 1) Remote já existe? → só push.
                val remotes = git.remoteList().call()
                if (remotes.isNotEmpty()) {
                    val r = git.push().setCredentialsProvider(credentials()).call()
                    val ok = r.all { rr -> rr.remoteUpdates.all { u ->
                        u.status == RemoteRefUpdate.Status.OK || u.status == RemoteRefUpdate.Status.UP_TO_DATE
                    } }
                    return@withContext if (ok) "Push enviado para ${remotes.first().name}" else "Falha no push"
                }
                // 2) Sem remote → cria o repositório no GitHub.
                val created = createGithubRepo(repoName, description, isPrivate).getOrElse { return@withContext "Erro ao criar: ${it.message}" }
                val login = githubLogin().getOrElse { return@withContext "Erro ao obter usuário: ${it.message}" }
                val remoteUrl = "https://github.com/$created.git"
                git.remoteAdd().setName("origin").setUri(remoteUrl).call()

                // 3) Branch main + commit inicial se não houver nenhum.
                val branch = git.repository.branch ?: "main"
                if (branch != "main") {
                    git.branchCreate().setName("main").force(true).call()
                    git.checkout().setName("main").call()
                }
                if (git.log().call().asSequence().none()) {
                    git.add().addFilepattern(".").call()
                    git.commit()
                        .setMessage("Início do projeto (Phantom-Code)")
                        .setAuthor("Phantom-Code", "phantom@localhost")
                        .setCommitter("Phantom-Code", "phantom@localhost")
                        .call()
                }

                // 4) Push do branch.
                val r = git.push().setCredentialsProvider(credentials()).call()
                val ok = r.all { rr -> rr.remoteUpdates.all { u ->
                    u.status == RemoteRefUpdate.Status.OK || u.status == RemoteRefUpdate.Status.UP_TO_DATE
                } }
                if (ok) {
                    runCatching { git.branchSetUpstream().setName(branch).setUpstreamName("origin/$branch").call() }
                    "✓ Repositório $login/$repoName criado e projeto enviado"
                } else {
                    "Repositório criado, mas o push falhou — tente Push depois"
                }
            }
        }.getOrElse { it.message ?: "Falha na sincronização" }
    }

    private fun apiGet(url: String): Result<String> = runCatching {
        val auth = token?.takeIf { it.isNotBlank() } ?: error("Autentique o GitHub primeiro")
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $auth")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }.let { conn ->
            conn.inputStream.use { it.bufferedReader().readText() }
        }
    }

    companion object {
        private const val KEY_TOKEN = "github_token"
    }
}
