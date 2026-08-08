package com.phantomcode.app.data.remote

import android.content.Context
import com.phantomcode.app.data.secrets.SecretsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File

/** Resultado de uma operação FTP. */
data class FtpResult(val success: Boolean, val message: String)

/**
 * Upload FTP (P2.2 · D8).
 *
 * Usa as chaves do catálogo de integrações (Toolbox → Integrações):
 *   FTP_HOST → ftp://servidor:porta  (formato de exemplo do catálogo)
 *   FTP_USER → usuário
 *   FTP_PASS → senha
 *
 * `upload(relPath)` envia o arquivo do workspace para a pasta remota.
 * Roda em IO; retorna [FtpResult] legível para snackbar.
 */
class FtpClient(context: Context) {

    private val secrets = SecretsManager(context)

    fun isConfigured(): Boolean =
        !(secrets.get(KEY_HOST) ?: "").isBlank() &&
            !(secrets.get(KEY_USER) ?: "").isBlank() &&
            !(secrets.get(KEY_PASS) ?: "").isBlank()

    fun configSummary(): String {
        val host = secrets.get(KEY_HOST) ?: return "FTP não configurado"
        return host.replaceFirst(Regex("^ftps?://"), "").substringBefore('/')
    }

    /**
     * Sobe um arquivo do workspace para o servidor FTP. Roda em IO.
     *
     * @param absPath caminho absoluto do arquivo local (para leitura).
     * @param relPath caminho relativo ao workspace (define a pasta remota).
     */
    suspend fun upload(absPath: String, relPath: String): FtpResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext FtpResult(false, "Configure FTP nas Integrações (FTP_HOST/USER/PASS)")
        }
        runCatching {
            val host = (secrets.get(KEY_HOST) ?: "").trim()
            val hostOnly = host.replaceFirst(Regex("^ftps?://"), "").substringBefore('/').substringBefore(':')
            val port = host.substringAfter(':', "").substringBefore('/').toIntOrNull() ?: 21
            val user = secrets.get(KEY_USER) ?: ""
            val pass = secrets.get(KEY_PASS) ?: ""
            val file = File(absPath)

            val ftp = FTPClient()
            try {
                ftp.connectTimeout = 15000
                ftp.connect(hostOnly, port)
                if (!ftp.login(user, pass)) return@runCatching FtpResult(false, "Login FTP falhou (usuário/senha)")
                ftp.enterLocalPassiveMode()
                ftp.setFileType(FTP.BINARY_FILE_TYPE)

                val remotePath = relPath.replace(File.separatorChar, '/')
                val remoteDir = remotePath.substringBeforeLast('/', "")

                // Cria os diretórios remotos no caminho (mkdir recursivo)
                if (remoteDir.isNotBlank()) {
                    val parts = remoteDir.split('/').filter { it.isNotBlank() }
                    var current = ""
                    for (part in parts) {
                        current = if (current.isBlank()) part else "$current/$part"
                        ftp.makeDirectory(current)
                    }
                }

                val ok = file.inputStream().use { input ->
                    ftp.storeFile(remotePath, input)
                }
                if (!ok) return@runCatching FtpResult(false, "Falha ao enviar (resposta do servidor: ${ftp.replyString?.trim()})")
                FtpResult(true, "Enviado: $remotePath")
            } finally {
                runCatching { ftp.logout() }
                runCatching { ftp.disconnect() }
            }
        }.getOrElse { FtpResult(false, it.message ?: "Falha no upload FTP") }
    }

    companion object {
        private const val KEY_HOST = "ftp_host"
        private const val KEY_USER = "ftp_user"
        private const val KEY_PASS = "ftp_pass"
    }
}
