package com.phantomcode.app.data.remote

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.phantomcode.app.data.secrets.SecretsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Upload SFTP/SSH (T22 — Editor: Upload SFTP).
 *
 * Usa as chaves do catálogo de integrações (Toolbox → Integrações):
 *   SFTP_HOST → user@servidor:porta  (ou apenas servidor; padrão :22)
 *   SFTP_USER → usuário (opcional se o host já trouxer user@)
 *   SFTP_PASS → senha
 *
 * `upload(absPath, relPath)` envia o arquivo do workspace para a pasta remota
 * equivalente (criando os diretórios intermediários). Roda em IO; retorna
 * [FtpResult] legível para snackbar.
 */
class SftpClient(context: Context) {

    private val secrets = SecretsManager(context)

    fun isConfigured(): Boolean =
        !(secrets.get(KEY_HOST) ?: "").isBlank()

    fun configSummary(): String {
        val host = secrets.get(KEY_HOST) ?: return "SFTP não configurado"
        return host.replaceFirst(Regex("^sftp://"), "").substringBefore('/')
    }

    /** Sobe um arquivo do workspace para o servidor SFTP. Roda em IO. */
    suspend fun upload(absPath: String, relPath: String): FtpResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext FtpResult(false, "Configure SFTP nas Integrações (SFTP_HOST)")
        }
        runCatching {
            val spec = (secrets.get(KEY_HOST) ?: "").trim().replaceFirst(Regex("^sftp://"), "").substringBefore('/')
            val at = spec.lastIndexOf('@')
            val userFromHost = if (at >= 0) spec.substring(0, at) else ""
            val rest = if (at >= 0) spec.substring(at + 1) else spec
            val host = rest.substringBefore(':')
            val port = rest.substringAfter(':', "").toIntOrNull() ?: 22
            val user = (secrets.get(KEY_USER) ?: "").trim().ifBlank { userFromHost }
            val pass = secrets.get(KEY_PASS) ?: ""
            if (user.isBlank()) return@runCatching FtpResult(false, "Usuário SFTP não informado (SFTP_USER ou user@host)")

            val file = File(absPath)
            if (!file.isFile) return@runCatching FtpResult(false, "Arquivo local não encontrado")

            val jsch = JSch()
            val session: Session = jsch.getSession(user, host, port)
            if (pass.isNotBlank()) session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(15_000)

            val channel: ChannelSftp = session.openChannel("sftp") as ChannelSftp
            try {
                channel.connect(15_000)

                val remotePath = relPath.replace(File.separatorChar, '/')
                val remoteDir = remotePath.substringBeforeLast('/', "")
                val remoteName = remotePath.substringAfterLast('/', "")

                // Cria os diretórios remotos no caminho (ignora "já existe")
                if (remoteDir.isNotBlank()) {
                    val parts = remoteDir.split('/').filter { it.isNotBlank() }
                    var current = ""
                    for (part in parts) {
                        current = if (current.isBlank()) part else "$current/$part"
                        runCatching { channel.mkdir(current) }
                    }
                    channel.cd(remoteDir)
                }
                channel.put(file.absolutePath, remoteName, ChannelSftp.OVERWRITE)
                FtpResult(true, "Enviado (SFTP): $remotePath")
            } finally {
                runCatching { channel.disconnect() }
                runCatching { session.disconnect() }
            }
        }.getOrElse { FtpResult(false, it.message ?: "Falha no upload SFTP") }
    }

    companion object {
        private const val KEY_HOST = "sftp_host"
        private const val KEY_USER = "sftp_user"
        private const val KEY_PASS = "sftp_pass"
    }
}
