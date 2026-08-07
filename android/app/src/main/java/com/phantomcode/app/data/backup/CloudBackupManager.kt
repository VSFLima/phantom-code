package com.phantomcode.app.data.backup

import android.content.Context
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.secrets.SecretsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import android.util.Base64
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Backup cloud (T22 · D8) via WebDAV.
 *
 * Usa as chaves do catálogo de integrações (Toolbox → Integrações):
 *   WEBDAV_URL  → https://servidor/dav/phantom-code/
 *   WEBDAV_USER → usuário
 *   WEBDAV_PASS → senha
 *
 * Sobe o ZIP do workspace para `WEBDAV_URL/phantom-backup-<data>.zip` com
 * PUT (Basic Auth) e restaura pelo GET mais recente. Sem dependências —
 * WebDAV é HTTP puro, funciona com Nextcloud/ownCloud/Box/qualquer servidor.
 */
class CloudBackupManager(context: Context) {

    private val workspace = WorkspaceManager(context)
    private val secrets = SecretsManager(context)

    /** Se o WebDAV está configurado (URL + usuário + senha no catálogo). */
    fun isConfigured(): Boolean =
        !(secrets.get(KEY_URL) ?: "").isBlank() &&
            !(secrets.get(KEY_USER) ?: "").isBlank() &&
            !(secrets.get(KEY_PASS) ?: "").isBlank()

    fun configSummary(): String {
        val url = secrets.get(KEY_URL) ?: return "WebDAV não configurado"
        return url.replaceFirst(Regex("^https?://"), "").substringBefore('/')
    }

    private fun baseUrl(): String = (secrets.get(KEY_URL) ?: "").trimEnd('/')

    private fun auth(): String {
        val user = secrets.get(KEY_USER) ?: ""
        val pass = secrets.get(KEY_PASS) ?: ""
        // android.util.Base64: funciona em TODAS as APIs (java.util.Base64 é API 26+)
        return "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
    }

    /** Sobe o ZIP do workspace para a nuvem. Roda em IO. */
    suspend fun upload(): BackupResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext BackupResult(false, "Configure WebDAV nas Integrações (WEBDAV_URL/USER/PASS)")
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val fileName = "phantom-backup-$stamp.zip"
            val url = URL("${baseUrl()}/$fileName")
            val zipFile = File.createTempFile("phantom-backup", ".zip", workspace.root)

            // 1) Gera o ZIP em arquivo temporário (mesma lógica do BackupManager)
            var fileCount = 0
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                val root = workspace.root
                root.walkTopDown().filter { it.isFile && it.name != ".gitkeep" }.forEach { f ->
                    val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    fileCount++
                }
            }

            // 2) PUT com Basic Auth
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Authorization", auth())
                setRequestProperty("Content-Type", "application/zip")
                doOutput = true
            }
            zipFile.inputStream().use { input ->
                conn.outputStream.use { out -> input.copyTo(out) }
            }
            val code = conn.responseCode
            zipFile.delete()
            if (code in 200..299) {
                BackupResult(true, "Backup enviado ($fileName)", fileCount = fileCount)
            } else {
                BackupResult(false, "HTTP $code ao enviar ($fileName)")
            }
        }.getOrElse { BackupResult(false, it.message ?: "Falha no upload") }
    }

    /** Restaura o ZIP mais recente do WebDAV para o workspace (merge). */
    suspend fun restoreLatest(): BackupResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext BackupResult(false, "Configure WebDAV nas Integrações (WEBDAV_URL/USER/PASS)")
        runCatching {
            // Lista o diretório (PROPFIND) e escolhe o .zip mais recente
            val dirUrl = URL(baseUrl())
            val listConn = (dirUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Authorization", auth())
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "application/xml")
            }
            val body = listConn.inputStream?.bufferedReader()?.readText().orEmpty()
            listConn.disconnect()

            val hrefs = Regex("<d:href>([^<]+\\.zip)</d:href>").findAll(body).map { it.groupValues[1] }.toList()
            if (hrefs.isEmpty()) return@withContext BackupResult(false, "Nenhum backup encontrado no WebDAV")

            val latest = hrefs.maxByOrNull { it } ?: return@withContext BackupResult(false, "Nenhum backup")
            val fileUrl = URL(latest)
            val getConn = (fileUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Authorization", auth())
            }
            if (getConn.responseCode !in 200..299) {
                getConn.disconnect()
                return@withContext BackupResult(false, "HTTP ${getConn.responseCode} ao baixar")
            }
            val zipFile = File.createTempFile("phantom-restore", ".zip", workspace.root)
            getConn.inputStream.use { input -> zipFile.outputStream().use { out -> input.copyTo(out) } }
            getConn.disconnect()

            // 3) Restaura com merge (nunca apaga o existente)
            var restored = 0
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zip ->
                var entry: java.util.zip.ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name
                    if (name.endsWith('/')) {
                        zip.closeEntry()
                        continue
                    }
                    val ok = runCatching {
                        val target = workspace.resolve(name)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> zip.copyTo(out) }
                    }.isSuccess
                    if (ok) restored++
                    zip.closeEntry()
                }
            }
            zipFile.delete()
            BackupResult(true, "Restaurado ($restored arquivos)", fileCount = restored)
        }.getOrElse { BackupResult(false, it.message ?: "Falha na restauração") }
    }

    companion object {
        private const val KEY_URL = "webdav_url"
        private const val KEY_USER = "webdav_user"
        private const val KEY_PASS = "webdav_pass"
    }
}
