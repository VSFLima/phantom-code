package com.phantomcode.app.data.backup

import android.content.Context
import android.net.Uri
import com.phantomcode.app.data.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Resultado de uma operação de backup/restauração. */
data class BackupResult(
    val ok: Boolean,
    val message: String,
    val fileCount: Int = 0,
)

/**
 * Backup local (T21 · D2): workspace → ZIP (SAF, ACTION_CREATE_DOCUMENT) com
 * manifest (metadados + lista de arquivos) e restauração com merge.
 *
 * Regras D2: a restauração NUNCA apaga silenciosamente o que já existe no
 * workspace — apenas sobrescreve/recria os arquivos que vêm do backup.
 */
class BackupManager(context: Context) {

    private val workspace = WorkspaceManager(context)
    private val contentResolver = context.contentResolver

    /** Nome sugerido para o arquivo: `phantom-backup-<data>.zip`. */
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "phantom-backup-$stamp.zip"
    }

    /** Gera o ZIP do workspace em `uri` (SAF). Roda em IO. */
    suspend fun createBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val out: OutputStream = contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupResult(false, "Não foi possível abrir o destino")
            out.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    val root = workspace.root
                    val manifest = JSONObject()
                        .put("app", "phantom-code")
                        .put("type", "workspace-backup")
                        .put("created_at", System.currentTimeMillis())
                        .put("workspace", root.name)
                    val files = ArrayList<String>()

                    root.walkTopDown().filter { it.isFile && it.name != ".gitkeep" }.forEach { f ->
                        val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                        files.add(rel)
                        zip.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }

                    // Manifest com a lista (útil para validação e logs)
                    manifest.put("files", files)
                    zip.putNextEntry(ZipEntry("phantom-manifest.json"))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    BackupResult(true, "Backup criado", fileCount = files.size)
                }
            }
        }.getOrElse { BackupResult(false, it.message ?: "Falha no backup") }
    }

    /**
     * Gera o ZIP de UM projeto (pasta do workspace) em `uri` (SAF). Mesma
     * lógica do backup completo, mas limitado a `projectName` — inclui o
     * repositório Git se existir (para levar histórico junto). Roda em IO.
     */
    suspend fun backupProject(uri: Uri, projectName: String): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val projectDir = workspace.resolve(projectName)
            if (!projectDir.isDirectory) return@withContext BackupResult(false, "Projeto não encontrado")
            val out: OutputStream = contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupResult(false, "Não foi possível abrir o destino")
            out.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    val manifest = JSONObject()
                        .put("app", "phantom-code")
                        .put("type", "project-backup")
                        .put("project", projectName)
                        .put("created_at", System.currentTimeMillis())
                    val files = ArrayList<String>()

                    projectDir.walkTopDown().filter { it.isFile && it.name != ".gitkeep" }.forEach { f ->
                        val rel = f.relativeTo(projectDir).path.replace(File.separatorChar, '/')
                        files.add(rel)
                        zip.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }

                    manifest.put("files", files)
                    zip.putNextEntry(ZipEntry("phantom-manifest.json"))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    BackupResult(true, "Projeto exportado", fileCount = files.size)
                }
            }
        }.getOrElse { BackupResult(false, it.message ?: "Falha ao exportar") }
    }

    /** Restaura de `uri` (SAF) com merge no workspace. Roda em IO. */
    suspend fun restore(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val input = contentResolver.openInputStream(uri)
                ?: return@withContext BackupResult(false, "Não foi possível ler o backup")
            input.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var restored = 0
                    var entry: ZipEntry?
                    while (zip.nextEntry.also { entry = it } != null) {
                        val name = entry!!.name
                        if (name == "phantom-manifest.json" || name.endsWith('/')) {
                            zip.closeEntry()
                            continue
                        }
                        // Merge: sobrescreve/recria; nunca apaga o que já existe.
                        // Entrada inválida (path traversal) é pulada — não aborta o restore.
                        val ok = runCatching {
                            val target = workspace.resolve(name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out -> zip.copyTo(out) }
                        }.isSuccess
                        if (ok) restored++
                        zip.closeEntry()
                    }
                    BackupResult(true, "Restauração concluída", fileCount = restored)
                }
            }
        }.getOrElse { BackupResult(false, it.message ?: "Falha na restauração") }
    }
}
