package com.phantomcode.app.data.git

import android.content.Context
import com.phantomcode.app.data.secrets.SecretCategory
import com.phantomcode.app.data.secrets.SecretsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

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

    suspend fun push(dir: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(dir).use { git ->
                val results = git.push().setCredentialsProvider(credentials()).call()
                results.joinToString("; ") { r ->
                    val ok = r.remoteUpdates.all { u ->
                        u.status == RemoteRefUpdate.Status.OK ||
                            u.status == RemoteRefUpdate.Status.UP_TO_DATE
                    }
                    if (ok) "push ok" else r.messages.trim().ifBlank { "falha no push" }
                }
            }
        }.getOrElse { it.message }
    }

    suspend fun pull(dir: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(dir).use { git ->
                val result = git.pull().setCredentialsProvider(credentials()).call()
                when {
                    result.isSuccessful -> "Pull OK"
                    else -> result.fetchResult?.messages?.trim()?.ifBlank { "Pull sem mudanças" } ?: "Pull sem mudanças"
                }
            }
        }.getOrElse { it.message }
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
