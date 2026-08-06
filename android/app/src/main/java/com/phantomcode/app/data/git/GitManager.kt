package com.phantomcode.app.data.git

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/** Git nativo via JGit (T19) — status, clone, commit, push/pull e log. */
class GitManager(context: Context) {

    private val prefs = context.getSharedPreferences("phantom_git", Context.MODE_PRIVATE)

    /** Token GitHub (PAT) — fica em SharedPreferences; exposto à Toolbox/Keystore na T20. */
    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove(KEY_TOKEN).apply()
            else prefs.edit().putString(KEY_TOKEN, value.trim()).apply()
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
                    if (r.isSuccessfulRemoteUpdate) "push ok" else r.messages.trim().ifBlank { "falha no push" }
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
                        date = c.authorIdent?.when?.let { fmt.format(it) } ?: "",
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_TOKEN = "github_token"
    }
}
